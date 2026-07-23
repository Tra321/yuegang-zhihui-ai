package com.yuegang.zhihui.common.mq;

import com.yuegang.zhihui.common.core.DomainEvent;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.LinkedHashMap;
import java.util.Map;

/** 从标准的领域事件模型中派生出稳定的消息队列（传输层）Header 信息。 */
public class MqMessageHeaders { // 消息头工具类

    // 定义精准且标准的时间格式器（包含纳秒及 UTC 偏移）
    private static final DateTimeFormatter STABLE_OFFSET_TIME = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd'T'HH:mm:ss") // 年月日时分秒
            .appendFraction(ChronoField.NANO_OF_SECOND,  0,  9,  true) //处理纳秒部分，最多9位
            .appendOffsetId() // 处理时区偏移
            .toFormatter(); // 构建完成

    private MqMessageHeaders() { } // 禁止实例化

    public static Map<String, String> from(DomainEvent<?> event) { // 转换方法开始
        MqEnvelopePolicy.validate(event); // 先执行合理性校验
        var headers = new LinkedHashMap<String, String>(); // 创建有序 Map
        headers.put("eventId", event.eventId()); // 存入 ID
        headers.put("eventType", event.eventType()); // 存入业务类型
        headers.put("eventVersion", Integer.toString(event.eventVersion())); // 存入版本
        headers.put("occurredAt", STABLE_OFFSET_TIME.format(event.occurredAt())); // 存入格式化后的发生时间
        headers.put("traceId", event.traceId()); // 存入全链路追踪号
        headers.put("producer", event.producer()); // 存入来源微服务名称
        headers.put("businessKey", event.businessKey()); // 存入业务主键
        headers.put("contentType", "application/json"); // 统一标记内容格式为 JSON
        return Map.copyOf(headers); // 返回不可修改的副本 Map
    }
}