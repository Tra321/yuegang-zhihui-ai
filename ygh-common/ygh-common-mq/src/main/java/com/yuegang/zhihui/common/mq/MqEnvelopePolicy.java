package com.yuegang.zhihui.common.mq;

import com.yuegang.zhihui.common.core.DomainEvent;

import java.util.Objects;

/** 针对自定义的领域事件完成中间件的传值操作 */
// 防御性的边界校验
public final class MqEnvelopePolicy {

    private MqEnvelopePolicy() { } // 默认构造

    /** 校验事件是否具备进入消息队列传输的安全资格。 */
    public static void validate(DomainEvent<?> event) { // 静态校验方法
        Objects.requireNonNull(event, "event must not be null"); // 事件不能为空
        require(event.eventId(), "eventId", "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"); // ID 格式校验
        require(event.eventType(), "eventType", "[A-Z][A-Z0-9_]{0,63}"); // 类型大写格式
        require(event.traceId(), "traceId", "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"); // 链路追踪 ID 校验
        require(event.producer(), "producer", "[a-z0-9-][a-z0-9-]{0,63}"); // 生产者名称校验
        require(event.businessKey(), "businessKey", "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"); // 业务键校验
        if (event.eventVersion() < 1 || event.occurredAt() == null || event.payload() == null) {
            throw new IllegalArgumentException("event envelope is incomplete"); // 报文不完整报错
        }
    }

    private static void require(String value, String name, String pattern) { // 正则强制匹配工具
        if (value == null || !value.matches(pattern)) { // 若不匹配
            throw new IllegalArgumentException(name + " is not safe for MQ transport"); // 抛出安全异常
        }
    }
}
