package com.yuegang.zhihui.common.mq;

public interface DomainEventPublisher extends AutoCloseable { // 定义领域事件发布接口，继承自动关闭接口

    /** 发布事件核心方法：ID、聚合根ID、类型、JSON负载 */
    void publish(String eventId, String aggregateId, String eventType, String payload);

    @Override
    default void close() {} // 默认关闭实现为空
}
