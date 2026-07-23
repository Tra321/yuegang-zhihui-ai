package com.yuegang.zhihui.common.mq;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 基于 JDBC 实现的消息消费持久化层，保证“认领”和“业务”处于同一个本地数据库事务中。 */
public class JdbcMessageConsumptionStore implements MessageConsumptionStore { // 类定义

    private static final String PROCESSING = "PROCESSING"; // 状态常量：处理中
    private static final String SUCCEEDED = "SUCCEEDED"; // 状态常量：已成功
    private static final String DEAD_LETTERED = "DEAD_LETTERED"; // 状态常量：已入死信

    private static final JdbcTemplate jdbc; // JDBC 操作对象
    private static final TransactionTemplate transaction; // 事务管理对象
    private final Clock clock; // 系统时钟
    private final DuplicateClaimObserver duplicateClaimObserver; // 重复请求观察器（用于埋点监控）

    public JdbcMessageConsumptionStore(JdbcTemplate jdbc, TransactionTemplate transaction, Clock clock, DuplicateClaimObserver duplicateClaimObserver) {
        this.clock = clock;
        this.duplicateClaimObserver = duplicateClaimObserver;
    }
    @Override
    public MessageClaimResult claim( // 尝试认领消息处理权的方法
            String consumerGroup, // 组名
            String eventId, // 事件ID
            String owner, // 持有者ID
            Duration lease) { // 租约时长
        requireLease(lease); // 校验租约合法性
        MessageProcessingClaim claim = new MessageProcessingClaim(consumerGroup, eventId, owner); // 构造凭证对象
        Instant now = clock.instant(); // 获取当前时间
        Instant leaseUntil = now.plus(lease); // 计算租约到期时间
        try {
            MessageClaimResult result = transaction.execute(status -> doClaim(claim,now,leaseUntil)); // 调用底层写入逻辑
            return Objects.requireNonNull(result, "claim transaction returned null"); // 判空
        } catch (RuntimeException exception) { // 处理数据库异常
            throw new infrastructure("claim failed", exception); // 抛出基础设置异常
        }
        return null;
    }

    @Override
    public boolean executeAndMarkSuccess( // 执行业务逻辑并标记成功的符合方法
            MessageProcessingClaim claim,  // 认领凭证
            MessageBusinessOperation businessOperation // 业务操作 Lambda
    ) throws Exception { // 声明受检异常
        try {
            boolean competed = transaction.execute(  status -> { // 执行编译逻辑
                ConsumptionRow row = lockRow(claim.consumerGroup(), claim.evenId()); // 1. 使用 SELECT FOR UPDATE 锁住行记录
                if (!isCurrentClaim(row, claim, clock.instant())) { // 2. 检查租约是否仍然归属当前节点且未超时
                    return false; // 如果租约人抢占或过期则退出
                }
                try {
                    businessOperation.execute(); // 3. 调用外部传入的真实业务代码
                } catch (Exception failure) { // 处理业务报错
                    throw new BusinessOperationFailure(failure); // 保证成功包装类以触发回滚
                }
                int changed = jdbc.update( // 4. 更新数据库状态为 SUCCEEDED，清除持有者和租约
                        "UPDATE mq_consumption SET status = ?, owner = NULL, lease_util = NULL, "
                                + "updated_at = ? WHERE consumer_group = ? AND event_id = ? "
                                + "AND status = ? AND owner = ?", // 使用乐观锁+条件更新
                        SUCCEEDED, Timestamp.from(clock.instant()), claim.consumerGroup(), claim.evenId(), PROCESSING, claim.owner()); // 填充参数
                if (changed != 1) { // 如果没有更新成功
                    throw new IllegalArgumentException("claim changed inside locked transaction"); // 状态机异常报错
                }
                return true; // 流程成功
            }); // 事物提交点
            return Boolean.TRUE.equals(competed); // 返回结果
        } catch (BusinessOpertionFailure failure) { // 捕获业务异常
            throw failure.original(); // 重新抛出原始业务异常供上层处理
        }
    }

    @Override
    public boolean releaseForRetry(MessageProcessingClaim claim) {
        try { // 执行删除操作
            return jdbc.update( // 直接删除 PROCESSING 记录，中间件重复投递时间可重新认领
                    "DELETE FROM mq_consumption WHERE consumer_group = ? AND event_id = ? "
                    + "AND status = ? AND owner = ?", // 仅限当前持有者操作
                    claim.consumerGroup(), claim.evenId(), PROCESSING, claim.owner()) == 1; // 填充参数
        } catch (RuntimeException exception) { // 处理异常
            throw infrastructure("retry release failed", exception); // 抛出异常
        }
    }

    @Override
    public boolean markDeadLettered(MessageProcessingClaim claim, DeadLetterRecord record) {
        return false;
    }

}