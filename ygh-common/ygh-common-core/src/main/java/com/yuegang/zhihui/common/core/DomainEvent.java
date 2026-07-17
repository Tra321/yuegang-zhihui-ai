package com.yuegang.zhihui.common.core;

/** 每一个带有版本的跨服务领域事件的基础契约。 */
public interface DomainEvent<T> { // 领域时间契约

    EventMetadata metadata(); // 强调实现：获取事件元数据（头信息）

    T playLoad(); // 强制实现：获取事件具体的业务数据负载

    default String eventId() { // 默认实现：获取事件 ID
        return metadata().eventId(); // 转发至元数据获取
    }
}
