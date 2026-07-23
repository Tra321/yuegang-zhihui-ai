package com.yuegang.zhihui.common.mq;

public enum MessageConsumptionResult { // 整个消息生命周期在应用层的反馈结果枚举
    ACKNOWLEDGED,    // 处理成功：通知中间件删除消息
    DUPLICATE,    // 业务重复：通知中间件删除消息（防止重传）
    RETRY,    // 需要重试：通知中间件稍后重新投递
    DEAD_LETTERED    // 转入死信：业务已彻底失败，不再重试
}
