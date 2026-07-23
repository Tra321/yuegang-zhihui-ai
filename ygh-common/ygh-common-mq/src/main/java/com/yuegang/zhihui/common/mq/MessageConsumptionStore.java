package com.yuegang.zhihui.common.mq;

import java.time.Duration;

/**
 * 具有持久化能力的消费者幂等性边界协议。
 * 实现类必须在每次状态变更时严格对比持有者令牌
 * 业务操作混合状态流转到 SUCCESSES 必须在同一个本地数据库事务中提交。
 * 如果租约凭证已过期或失效，严格执行业务操作并返回 false。
 * 如果业务代码抛出异常，整个本地事务必须回滚，防止业务已执行但状态未标记成功。
 */
public interface MessageConsumptionStore { // 定义存储契约

    MessageClaimResult claim( // 尝试认领接口
                              String consumerGroup, // 组标识
                              String eventId, // 消息唯一标识
                              String owner, // 本次节点的唯一令牌
                              Duration lease // 租约有效时间
    );

    boolean executeAndMarkSuccess( // 执行业务并结案
            MessageProcessingClaim claim, // 必须持有的有效凭证
            MessageBusinessOperation businessOperation // 待执行的业务逻辑
    ) throws Exception; // 业务异常向上抛出

    boolean releaseForRetry(MessageProcessingClaim claim); // 主动释放权力的接口，用于手动放弃处理

    boolean markDeadLettered(MessageProcessingClaim claim, DeadLetterRecord record); // 原子地将消息标记为死信并存入审计表
}
