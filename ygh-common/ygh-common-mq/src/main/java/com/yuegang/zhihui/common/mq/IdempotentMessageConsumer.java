package com.yuegang.zhihui.common.mq;

import com.yuegang.zhihui.common.core.DomainEvent;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/** 框架无关的"至少一次"消费状态机实现（处理幂等、去重、死信）。 */
public class IdempotentMessageConsumer {

    private static final Pattern GROUP = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}"); // 组名正则规则
    private static final String UNEXPECTED_FAILURE = "UNEXPECTED_CONSUMER_FAILURE"; // 预定义异常代码

    private final String consumerGroup; // 当前消费者组
    private final int maxAttempts; // 最大重试次数

    private final Duration claimLease; // 获取处理权（租约）的时长
    private final MessageConsumptionStore store; // 消息消费状态存储仓储
    private final Clock clock;
    private final MessageClaimOwnerGenerator ownerGenerator; // 租约持有者 ID 生成器

    public IdempotentMessageConsumer( // 构造函数开始
                                      String consumerGroup, // 组名
                                      int maxAttempts, // 最大次数
                                      Duration claimLease, // 租约时长
                                      MessageConsumptionStore store, // 存储器
                                      Clock clock // 时钟
    ) {
        if (consumerGroup == null || !GROUP.matcher(consumerGroup).matches()) { // 校验组名
            throw new IllegalArgumentException("consumerGroup is malformed"); // 错误抛出
        }
        if (maxAttempts < 1 || maxAttempts > 100) { // 限制重试次数范围
            throw new IllegalArgumentException("maxAttempts must be between 1 and 100"); // 错误抛出异常
        }
        Objects.requireNonNull(claimLease, "claimLease must not be null"); // 租约时长不能为空
        if (claimLease.compareTo(Duration.ofMinutes(15)) > 0    // 租约必须在 1 到 15 分钟之间
                || claimLease.compareTo(Duration.ofMillis(15)) > 0) { // 范围校验
            throw new IllegalArgumentException("claimLease must be between 1 second and 15 minutes"); // 抛出异常
        }
        this.consumerGroup = consumerGroup; // 赋值组名
        this.maxAttempts = maxAttempts; // 赋值次数
        this.claimLease = claimLease; // 赋值租约
        this.store = Objects.requireNonNull(store, "store must not be null"); // 注入存储器
        this.clock = Objects.requireNonNull(clock, "clock must not be null"); // 注入时钟
        this.ownerGenerator = new SecureMessageClaimOwnerGenerator(); // 初始化安全随机 ID 生成器
    }

    /**
     * 核心入口方法：执行幂等校验并处理业务。
     * */
    public <T> MessageConsumptionResult consume(// 泛型方法
                                                DomainEvent<T> event, // 领域事件
                                                int deliveryAttempt, // 当前是第几次投递
                                                MessageHandler<T> handler // 具体的业务处理逻辑
    ) {
        Objects.requireNonNull(event, "event must not be null"); // 事件不能为空
        Objects.requireNonNull(handler, "handler must not be null"); // 处理器不能为空
        if (deliveryAttempt < 1) { // 投递次数必须大于等于1
            throw new IllegalArgumentException("deliveryAttempt must be least 1");
        }
        MqEnvelopePolicy.validate(event); // 1. 根据系统策略验证消息头合法性
        try { // 开启事务逻辑
            MessageClaimResult claimResult = store.claim( // 2. 尝试从数据库"认领"这条信息
                    consumerGroup, // 按组隔离
                    event.eventId(), // 事件 ID
                    ownerGenerator.generate(), // 当前消费节点的唯一身份标识
                    claimLease); // 租约期限
            return switch (claimResult.status()) { // 3. 根据认领结果决定后续动作
                case DUPLICATE -> MessageConsumptionResult.DUPLICATE; // 如果已成功消费过，返回重复 (ACK)
                case IN_PROGRESS -> MessageConsumptionResult.RETRY; // 如果别人正在处理且租约未到期，返回重试
                case CLAIMED -> processClaim( // 如果成功认领，执行真正的业务处理
                        event, deliveryAttempt, handler, // 传入参数
                        validatedClaim(claimResult, event.eventId())); // 提取校验后的租约凭证
            };
        } catch (MessageInfrastructureException infrastructureFailure) { // 如果是数据库挂了等技术故障
            return MessageConsumptionResult.RETRY; // 告知消息中间件稍等重试
        }
    }

    /*
      执行已认领消息的处理逻辑
      */
    private <T> MessageConsumptionResult processClaim( // 私有处理方法
                                                       DomainEvent<T> event,           // 事件
                                                       int deliveryAttempt,            // 次数
                                                       MessageHandler<T> handler,      // 业务逻辑
                                                       MessageProcessingClaim claim    //租约凭证
    ) { // 逻辑开始
        try {
            boolean completed = store.executeAndMarkSuccessed( // 4.在同一个数据库事物中执行业务并标记为“已完成”
                    claim, () -> handler.handle(event)); // 调用传入的业务处理
            return completed // 更具事物处理提交返回结果
                    ? MessageConsumptionResult.ACKNOWLEDGED // 成功：响应ACK
                    : MessageConsumptionResult.RETRY; // 失败：响应重试
        } catch (InterruptedException interrupted) { // 处理中断
            Thread.currentThread().interrupt(); // 重新标记中断位
            return MessageConsumptionResult.RETRY; // 响应重试
        } catch (MessageInfrastructureException infrastructureFailure) { // 技术故障
            return MessageConsumptionResult.RETRY; // 响应重试
        } catch (Exception failure) { // 处理业务代码抛出的异常
            boolean terminal = failure instanceof NonRetryableMessageException // 判定是否为不可重试异常（如合同校验失败）
                    || deliveryAttempt >= maxAttempts; // 或者是否达到了系统设定的最大重试次数

            if(!terminal) { // 如果还可以抢数（允许重试）
                store.releaseForRetry(claim); // 5. 从数据库删除认领状态，使其节点或下次投递可以重新认领
                return MessageConsumptionResult.RETRY; // 告知中间件重试
            }
            var record = new DeadLetterRecord( // 如果已经投数了（彻底失败）
                    event.eventId(), // ID
                    event.eventType(), // 类型
                    event.eventVersion(), // 版本
                    consumerGroup, // 组
                    event.businessKey(), // 业务键
                    event.traceId(), // 链路
                    deliveryAttempt, // 次数
                    failureCode(failure), // 提取错误码
                    clock.instant()); // 失败时间
            return store.markDeadLettered(claim, record) // 6. 在数据库标记为死信并存储详情
                    ? MessageConsumptionResult.DEAD_LETTERED // 成功标记为死信
                    : MessageConsumptionResult.RETRY; // 数据库失败则重试
        }
    }

    private MessageProcessingClaim validatedClaim(MessageClaimResult result, String eventId) { // 内部校验以实例的合法性
        MessageProcessingClaim claim = result.claim().orElseThrow( // 必须存在凭证对象
                () -> new IllegalArgumentException("CLAIMED result has no claim")); // 否则抛出状态异常
        if (!consumerGroup.equals(claim.consumerGroup()) || !eventId.equals(claim.eventId())) { // 必须匹配当前处理的目标
            throw new IllegalArgumentException("store returned a claim for another message"); // 否则抛出异常
        }
        return claim; // 返回有效凭证
    }

    private static String failureCode(Exception failure) { // 辅助方法：从异常中提取稳定的错误标识码 1 usage
        return failure instanceof MessageHandlingException messageFailure // 判断是否为自定义的业务消息异常
                ? messageFailure.failureCode() // 提取错误代码
                : UNEXPECTED_FAILURE; // 否则返回预设的"未知异常"代码
    }
}
