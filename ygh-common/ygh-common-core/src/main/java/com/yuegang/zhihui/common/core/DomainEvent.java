package com.yuegang.zhihui.common.core;

import java.time.OffsetDateTime;

/** 每一个带有版本的跨服务领域事件的基础契约。 */
public interface DomainEvent<T> { // 领域时间契约

    EventMetadata metadata(); // 强调实现：获取事件元数据（头信息）

    T payload(); // 强制实现：获取事件具体的业务数据负载

    default String eventId() { // 默认实现：获取事件 ID
        return metadata().eventId(); // 转发至元数据获取
    }

    default String eventType() { return metadata().eventType(); }

    default String traceId() { return metadata().traceId(); }

    default String producer() { return metadata().producer(); }

    default String businessKey() { return metadata().businessKey(); }

    default int eventVersion() { return metadata().eventVersion(); }


}