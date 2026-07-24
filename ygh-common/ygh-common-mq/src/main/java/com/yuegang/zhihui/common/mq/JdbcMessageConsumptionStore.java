package com.yuegang.zhihui.common.mq;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 基于 JDBC 实现的消息消费持久化层，保证"认领"和"业务"处于同一个本地数据库事务中。 */
public class JdbcMessageConsumptionStore implements MessageConsumptionStore { // 类定义
    // 状态常量
    private static final String PROCESSING = "PROCESSING";    // 处理中
    private static final String SUCCEEDED = "SUCCEEDED";      // 已成功
    private static final String DEAD_LETTERED = "DEAD_LETTERED"; // 已入死信

    private final JdbcTemplate jdbc;             // JDBC 操作对象
    private final TransactionTemplate transaction;// 事务管理对象
    private final Clock clock;                           // 系统时钟
    private final DuplicateClaimObserver duplicateClaimObserver; // 重复认领观察器（监控埋点）

    /**
     * 构造函数：注入 JDBC 模板、事务管理器和时钟.
     */
    public JdbcMessageConsumptionStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            Clock clock
    ) { // 调用重载构造函数，默认不执行重复认领观察逻辑
        this(jdbc, transactionManager, clock, (consumerGroup, eventId) -> {});
    }

    /**
     * 全参构造函数，允许注入自定义的重复认领观察者。
     */
    JdbcMessageConsumptionStore(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            Clock clock,
            DuplicateClaimObserver duplicateClaimObserver
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "JDBC must not be null"); // 校验并注入 jdbc
        this.transaction = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager must not be null")); // 校验并根据事务管理器创建事务模板
        this.clock = Objects.requireNonNull(clock, "clock must not be null"); // 校验并注入时钟
        this.duplicateClaimObserver = Objects.requireNonNull(
                duplicateClaimObserver, "duplicateClaimObserver must not be null"); // 校验并注入观察者
    }

    public JdbcMessageConsumptionStore(JdbcTemplate jdbc, TransactionTemplate transaction, JdbcTemplate jdbc1, TransactionTemplate transaction1, Clock clock, DuplicateClaimObserver duplicateClaimObserver) {
        this.jdbc = jdbc1;
        this.transaction = transaction1;
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
        Instant leaseUntil;//定义租约到期时间点
        try { // 开启计算块
            leaseUntil = now.plus(lease); // 计算租约到期时间点
        } catch (DateTimeException | ArithmeticException exception) { // 捕获时间溢出或异常
            throw new IllegalArgumentException("lease exceeds the supported time range", exception);//抛出参数非法异常
        }
        try { // 开启事务执行块
            MessageClaimResult result = transaction.execute( status -> doClaim(claim, now, leaseUntil)); // 在事务中执行实际的认领逻辑
            return Objects.requireNonNull(result, "claim transaction returned null"); // 确保结果不为空并返回
        } catch (RuntimeException exception) { // 捕获数据库运行异常
            throw infrastructure("claim failed", exception); // 封装为基础设施异常抛出
        }
    }

    @Override
    public boolean executeAndMarkSuccessed( // 执行业务逻辑并标记成功的复合方法
                                            MessageProcessingClaim claim, // 认领凭证
                                            MessageBusinessOperation businessOperation // 业务操作 Lambda
    ) throws Exception { // 声明受检异常
        Objects.requireNonNull(claim, "claim must not be null");    // 认领凭证
        Objects.requireNonNull(businessOperation, "businessOperation must not be null");    // 业务操作Lambda
        try {// 开启业务执行行块
            boolean competed = transaction.execute( status -> { // 执行编排逻辑
                ConsumptionRow row = lockRow(claim.consumerGroup(), claim.eventId()); // 1. 使用 SELECT FOR UPDATE 锁住行记录
                if (!isCurrentClaim(row, claim, clock.instant())) { // 2. 检查租约是否仍然归属当前节点且未超时
                    return false; // 如果被别人抢占或过期则退出
                }
                try {   // 业务执行子块
                    businessOperation.execute(); // 3. 调用外部传入的真实业务代码
                } catch (Exception failure) { // 处理业务报错
                    throw new BusinessOperationFailure(failure); // 包装异常触发事务回滚
                }
                int changed = jdbc.update( // 4. 更新数据库状态为 SUCCEEDED，清除持有者和租约
                        "UPDATE mq_consumption SET status = ?, owner = NULL, lease_until = NULL, "
                                + "updated_at = ? WHERE consumer_group = ? AND event_id = ? "
                                + "AND status = ? AND owner = ?", // 条件更新
                        SUCCEEDED,  // 设置状态为成功
                        Timestamp.from(clock.instant()),    // 设置更新时间
                        claim.consumerGroup(),  // 匹配消费者组
                        claim.eventId(),    // 匹配时间ID
                        PROCESSING, // 当前状态必须是处理中
                        claim.owner()); // 当前持有者必须是自己
                if (changed != 1) { // 如果没有更新成功，理论上受事务所保护不应发生
                    throw new IllegalArgumentException("claim changed inside locked transaction"); // 状态机异常报错
                }
                return true; //流程成功
            }); //事务提交点
            return Boolean.TRUE.equals(competed); // 返回结果
        } catch (BusinessOperationFailure failure) { // 捕获业务异常
            throw failure.original(); // 重新抛出原始业务异常供上层处理
        } catch (MessageInfrastructureException infrastructure) { // 捕获基础设施异常
            throw infrastructure; // 直接抛出
        } catch (RuntimeException exception) { // 捕获其他运行时异常
            throw infrastructure("completion failed", exception); // 封装后抛出
        }
    }


    @Override // 实现接口方法：释放租约以便下次完成
    public boolean releaseForRetry(MessageProcessingClaim claim) {
        Objects.requireNonNull(claim, "claim must not be null"); // 校验
        try { // 执行删除操作
            return jdbc.update( // 直接删除 PROCESSING 记录，中间件重复投递时间可重新认领
                    "DELETE FROM mq_consumption WHERE consumer_group = ? AND event_id = ? "
                            + "AND status = ? AND owner = ?", // 仅限当前持有者操作
                    claim.consumerGroup(), claim.eventId(), PROCESSING, claim.owner()) == 1; // 执行并返回是否成功
        } catch (MessageInfrastructureException infrastructure) { // 异常处理
            throw infrastructure; // 向上抛出
        } catch (RuntimeException exception) { // 处理异常
            throw infrastructure("retry release failed", exception); // 抛出异常
        }
    }

    @Override
    public boolean markDeadLettered(MessageProcessingClaim claim, DeadLetterRecord record) {
        Objects.requireNonNull(claim, "claim must not be null"); // 校验凭证
        Objects.requireNonNull(record, "record must not be null"); // 校验死信记录
        if (!claim.consumerGroup().equals(record.customerGroup()) || !claim.eventId().equals(record.eventId())) { // 校验死信是否与当前凭据匹配
            throw new IllegalArgumentException("dead-letter record does not match claim"); // 匹配失败抛错
        }
        try { // 开启事务块
            Boolean completed = transaction.execute( status -> { // 执行数据库事务
                ConsumptionRow row = lockRow(claim.consumerGroup(), claim.eventId()); // 加锁获取当前行
                if (!isCurrentClaim(row, claim, clock.instant())) { // 校验权属
                    return false; // 失去所有权则返回 false
                }
                jdbc.update( // 1. 将详细的失败事务插入死信表
                        "INSERT INTO mq_dead_letter (consumer_group, event_id, event_type,"
                                + "event_version, business_key, trace_id," +
                                " delivery_attempt,"
                                + "failure_code, failed_at) VALUES (?,?,?,?,?,?,?,?,?)",
                        record.customerGroup(),
                        record.eventId(),
                        record.eventType(),
                        record.eventVersion(),
                        record.businessKey(),
                        record.traceId(),
                        record.deliveryAttempt(),
                        record.failureCode(),
                        Timestamp.from(record.failedAt())); // 填充插入参数

                int changed = jdbc.update( // 2. 更新主表状态为 DEAD_LETTERED
                        "UPDATE mq_consumption SET status = ?, owner = NULL, lease_until=NULL,"
                                + "updated_at = ? WHERE consumer_group = ? AND event_id = ?"
                                + "AND status = ? AND owner = ?",
                        DEAD_LETTERED,
                        Timestamp.from(clock.instant()),
                        claim.consumerGroup(),
                        claim.eventId(),
                        PROCESSING,
                        claim.owner()); // 填充更新参数
                if (changed != 1) { // 状态检查
                    throw new IllegalArgumentException("claim changed inside locked transaction");
                }
                return true; // 完成入库
            }); // 结束事务
            return Boolean.TRUE.equals(completed); // 返回结果
        } catch (MessageInfrastructureException infrastructure) { // 基础设施异常
            throw infrastructure;
        } catch (RuntimeException exception) { // 其他异常
            throw infrastructure("dead-letter persistence failed", exception);
        }
    }
    /**
     * 内部实际的认领数据库操作
     */
    private MessageClaimResult doClaim(
            MessageProcessingClaim claim,
            Instant now,
            Instant leaseUntil
    ) {
        try { // 尝试插入数据库认领
            jdbc.update( // 如果是第一次插入，插入 PROCESSING 状态的行
                    "INSERT INTO mq_consumption (consumer_group, event_id, status, owner, "
                            + "lease_until, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    claim.consumerGroup(),
                    claim.eventId(),
                    PROCESSING,
                    claim.owner(),
                    Timestamp.from(leaseUntil),
                    Timestamp.from(now),
                    Timestamp.from(now)); // 插入成功，代表认领成功
            return MessageClaimResult.claimed(claim);   // 插入成功，代表认领成功
        } catch (DuplicateKeyException duplicate) { // 如果数据库主键冲突（已存在该消息记录）
            duplicateClaimObserver.afterDuplicate(claim.consumerGroup(), claim.eventId()); // 通知观察者
            ConsumptionRow existing = findRow(claim.consumerGroup(), claim.eventId()); // 查询现在有记录的状态
            if (existing == null) { // 竞态场景：在主键冲突后到查询前行被删除了
                return MessageClaimResult.inProgress(); // 判定为处理中，等待下次重试
            }
            if (SUCCEEDED.equals(existing.status()) || DEAD_LETTERED.equals(existing.status())) { // 如果状态是成功或死信
                return MessageClaimResult.duplicate(); // 判定为重复消息，告知上层无需再做
            }
            if (existing.leaseUntil() != null && existing.leaseUntil().isAfter(now)) { // 如果租约还没到
                return MessageClaimResult.inProgress(); // 判定为其它节点正在处理中
            }
            int changed = jdbc.update( // 场景：前一个认领者的租约已超时，当前节点可以强制抢占处理权
                    "UPDATE mq_consumption SET owner = ?, lease_until = ?, updated_at = ?"
                            + "WHERE consumer_group = ? AND event_id = ? AND status = ?"
                            + "AND lease_until <= ?", // 乐观锁，仅当前租约确实已到期时更新
                    claim.owner(),
                    Timestamp.from(leaseUntil),
                    Timestamp.from(now),
                    claim.consumerGroup(),
                    claim.eventId(),
                    PROCESSING,
                    Timestamp.from(now)); // 填充参数
            return changed == 1 // 抢占是否成功
                    ? MessageClaimResult.claimed(claim) // 成功抢占
                    : MessageClaimResult.inProgress(); // 抢占失败（可重试）
        }
    }


    /** 获取数据库的当前行信息 */
    private ConsumptionRow findRow(String consumerGroup, String eventId) {
        return jdbc.query( // 普通查询
                "SELECT status, owner, lease_until FROM mq_consumption "
                        + "WHERE consumer_group = ? AND event_id = ?",
                resultSet -> resultSet.next() // 处理结果集
                        ? new ConsumptionRow(
                        resultSet.getString("status"),
                        resultSet.getString("owner"),
                        toInstant(resultSet.getTimestamp("lease_until")))
                        : null,
                consumerGroup,
                eventId);
    }

    /** 使用排他锁获取数据库当前的行信息 */
    private ConsumptionRow lockRow(String consumerGroup, String eventId) {
        return jdbc.query( // 查询加锁
                "SELECT status, owner, lease_until FROM mq_consumption "
                        + "WHERE consumer_group = ? AND event_id = ? FOR UPDATE", // FOR UPDATE 触发数据库行锁
                resultSet -> resultSet.next() // 处理结果
                        ? new ConsumptionRow(
                        resultSet.getString("status"),
                        resultSet.getString("owner"),
                        toInstant(resultSet.getTimestamp("lease_until")))
                        : null,
                consumerGroup,
                eventId);
    }


    /** 校验当前内存中的凭证是否匹配数据库快照且未超时 */
    private static boolean isCurrentClaim(
            ConsumptionRow row,
            MessageProcessingClaim claim,
            Instant now
    ) { // 必须满足：有数据、状态是处理中、持有者一致、租约未过期
        return row != null
                && PROCESSING.equals(row.status())
                && claim.owner().equals(row.owner())
                && row.leaseUntil() != null
                && row.leaseUntil().isAfter(now);
    }


    /** Timestamp 转 Instant 的转换辅助 */
    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    /** 构造基础设施的异常的辅助方法 */
    private static MessageInfrastructureException infrastructure(
            String operation,
            RuntimeException cause
    ) {
        return new MessageInfrastructureException(operation, cause);
    }

    /** 内部记录类：侧映数据库行数据 */
    private record ConsumptionRow(String status, String owner, Instant leaseUntil) {
    }

    /** 业务操作失败的内部封装异常类，用于控制事务回滚 */
    private static final class BusinessOperationFailure extends RuntimeException {
        private final Exception original; // 持有原始异常

        private BusinessOperationFailure(Exception original) {
            super(null, original, false, false); // 禁止堆栈填充以提供性能
            this.original = original; // 存储原始异常
        }

        private Exception original() {
            return original; // 获取原始异常类
        }
    }

    /** 校验租约合法性范围（1秒至15分钟） */
    private static void requireLease(Duration lease) {
        Objects.requireNonNull(lease, "lease must not be null"); // 判空
        if (lease.compareTo(Duration.ofSeconds(1)) < 0
                || lease.compareTo(Duration.ofMinutes(15)) > 0) { // 范围校验
            throw new IllegalArgumentException("lease must be between 1 second and 15 minutes"); // 抛出异常
        }
    }

    /**
     * 函数式接口：用于重复认领时回调监听
     */
    @FunctionalInterface
    interface DuplicateClaimObserver {
        void afterDuplicate(String consumerGroup, String eventId);
    }
}

