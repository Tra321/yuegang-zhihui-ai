package com.yuegang.zhihui.common.core;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

// 替换错误的 AssertionsForClassTypes，使用标准 Assertions 支持全类型断言
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DomainEventTest { // 定义领域事件测试类

    private static final OffsetDateTime OCCURRED_AT = // 定义一个固定的测试发生时间
            OffsetDateTime.of(2026, 7, 17, 15, 0, 0, 0, ZoneOffset.ofHours(8)); //设为 2026-07-17 东八区时间

    @Test
    void eventCarriesTheCompleteVersionedTraceableEnvelope() { // 测试事件是否携带完整的、带版本的、可追踪的外壳
        DomainEvent<OrderCreatedPayload> event = VersionedDomainEvent.of(new EventMetadata( // 使用工厂方法创建事件
                "evt-9007199254740993", // 事件 ID
                "ORDER_CREATED",        // 事件类型
                1,                      // 事件版本
                OCCURRED_AT,            // 发生时间
                "trace-001",            // 追踪 ID
                "ygh-order-service",    // 生产者服务名
                "order-20260717-001"    // 业务主键
        ), new OrderCreatedPayload("9007199254740993")); // 传入数据负载

        assertThat(event.metadata().eventId()).isEqualTo("evt-9007199254740993"); // 断言事件 ID 正确
        assertThat(event.metadata().eventType()).isEqualTo("ORDER_CREATED"); // 断言事件类型正确
        assertThat(event.metadata().eventVersion()).isEqualTo(1); // 断言事件版本正确
        assertThat(event.metadata().occurredAt()).isEqualTo(OCCURRED_AT); // 断言时间正确

        assertThat(event.metadata().occurredAt().getOffset()).isEqualTo(ZoneOffset.ofHours(8)); // 断言时区偏移正确
        assertThat(event.metadata().traceId()).isEqualTo("trace-001"); // 断言追踪 ID 正确
        assertThat(event.metadata().producer()).isEqualTo("ygh-order-service"); // 断言生产者正确
        assertThat(event.metadata().businessKey()).isEqualTo("order-20260717-001"); //断言业务键正确
        assertThat(event.payload().orderId()).isEqualTo("9007199254740993"); // 断言负载数据正确
        assertThat(event).isInstanceOf(VersionedDomainEvent.class); // 断言其实现类类型正确
    }

    @Test   // 修复：规范方法名 + 补充 event 变量定义 + 调整修改顺序（先创建事件再改源Map，验证防御性拷贝）
    void payloadIsDefensivelyCopiedAndCannotBeMutated() {   // 测试负载是否被防御性转移且无法被外部修改
        var source = new LinkedHashMap<String, Object>(); //创建一个可变的源 Map
        source.put("orderId", "order-1");   // 放入包含该 Map 的事件

        // 【新增】基于源 Map 创建事件（触发防御性拷贝）
        DomainEvent<Map<String, Object>> event = eventWith(source);

        // 【调整顺序】创建事件后再修改原始源 Map，验证事件内部不受影响
        source.put("orderId", "tampered");

        assertThat(event.payload()).containsEntry("orderId", "order-1"); // 断言事件内的负载主体未受外部源 Map 修改的影响
        assertThatThrownBy(() -> event.payload().put("extra", true)) // 断言如果尝试直接修改事件内的负载
                .isInstanceOf(UnsupportedOperationException.class); // 应该抛出"不支持操作"的异常
    }

    @Test // 标记为测试方法
    void envelopeRejectsIncompleteOrUnversionedEvents() { // 测试外壳是否拒绝不完整或未定义版本的事件
        assertThatThrownBy(() -> metadata("", 1, OCCURRED_AT, "trace-1")) // 断言事件类型为空时会报错
                .isInstanceOf(IllegalArgumentException.class); // 抛出非法参数异常

        assertThatThrownBy(() -> metadata("ORDER_CREATED", 0, OCCURRED_AT, "trace-1")) // 断言版本小于1时会报错
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> metadata("ORDER_CREATED", 1, null, "trace-1")) // 断言发生时间为空时会抛异常
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> metadata("ORDER_CREATED", 1, OCCURRED_AT, " ")) // 断言追踪 ID 为空白时会报错
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test // 标记为测试方法
    void envelopeRejectsPoisonMetadataBeforeTransport() { // 测试外壳是否在传输验证前拒绝包含恶意/畸形元数据事件
        assertThatThrownBy(() -> new EventMetadata(
                "x".repeat(129), "ORDER_CREATED", 1, OCCURRED_AT,
                "trace-1", "order-service", "order-1")) // 事件ID超过 128 位限制
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new EventMetadata( // 尝试在 traceId 中注入换行符
                "event-1", "ORDER_CREATED", 1, OCCURRED_AT,
                "trace\nforged", "order-service", "order-1")) // 非法字符注入
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new EventMetadata( // 尝试在生产者名称中使用大写字母（正则限制为小写）
                "event-1", "ORDER_CREATE", 1, OCCURRED_AT,
                "trace-1", "order-service", "order-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test // 标记为测试方法
    void nestedMapAndListPayloadIsDeeplyImmutable() { // 测试嵌套 Map 和 List 负载是否为深度不可变
        var mutableItem = new LinkedHashMap<String, Object>(); // 创建可变明细项
        mutableItem.put("skuId", "sku-1");
        var mutableItems = new ArrayList<Map<String, Object>>(); // 创建可变列表
        mutableItems.add(mutableItem);
        var payload = new LinkedHashMap<String, Object>(); // 创建可变负载
        payload.put("items", mutableItems);

        var event = eventWith(payload); // 构造事件
        mutableItem.put("skuId", "tampered"); // 修改原始项
        mutableItems.add(Map.of("skuId", "sku-2")); // 向原始列表添加项

        @SuppressWarnings("unchecked")  // 忽略警告
        var storedItems = (List<Map<String, Object>>) event.payload().get("items"); // 从事件中提取存储的列表

        assertThat(storedItems).containsExactly(Map.of("skuId", "sku-1")); // 断言列表内容未被外部修改影响

        assertThatThrownBy(() -> storedItems.add(Map.of("skuId", "sku-3"))) // 断言尝试向内部列表添加数据会报错
                .isInstanceOf(UnsupportedOperationException.class);

        assertThatThrownBy(() -> storedItems.getFirst().put("skuId", "tampered-again")) // 断言尝试修改内部 Map 的元素会报错
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test // 标记为测试方法
    void linkedHashMapPayloadRetainsItsDeclaredMapTypeWithoutClassCastFailure() { // 测试保持声明的 Map 类型不发生强制转换失败
        var payload = new LinkedHashMap<String, Object>(); // 创建有序 Map
        payload.put("orderId", "order-1");

        DomainEvent<Map<String, Object>> event =    // 创建基于 Map 的事件
                VersionedDomainEvent.ofMap(
                        metadata("ORDER_CREATED", 1, OCCURRED_AT, "tace-1"), payload);

        assertThat(event.payload()).containsEntry("orderId", "order-1");    // 断言数据内容正常
        assertThat(event.payload()).isInstanceOf(Map.class);    // 断言类型为 Map
    }

    @Test
    void onlyTypedDtoAndMapFactoriesArePublicConstructionEntrypoint() {    // 测试仅强类型 DTO 和 Map 工厂方法是公开的构造入口
        var publicStaticFactoryNames = Arrays.stream(VersionedDomainEvent.class.getDeclaredMethods()) // 检查声明的方法
                .filter(method -> Modifier.isPublic(method.getModifiers())) // 过滤出公开方法
                .filter(method -> Modifier.isStatic(method.getModifiers())) // 过滤出静态方法
                .map(method -> method.getName()) // 获取方法名
                .toList(); // 转为列表

        assertThat(publicStaticFactoryNames).containsExactlyInAnyOrder("of", "ofMap");  // 断言包含方法：of 和 ofMap 两个工厂方法

        // 修复：noneMatch 返回 boolean，必须加 .isTrue() 完成断言
        assertThat(Arrays.stream(VersionedDomainEvent.class.getDeclaredConstructors())  // 检查声明的构造函数
                .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())))   // 断言没有任何构造函数是公开的
                .isTrue();
    }

    // 辅助方法：快捷创建 Map 事件
    private static DomainEvent<Map<String, Object>> eventWith(Map<String, Object> payload) {    // 辅助方法：快捷创建 Map 事件
        return VersionedDomainEvent.ofMap(
                metadata("ORDER_CREATED", 1, OCCURRED_AT, "trace-1"), payload);
    }

    private static EventMetadata metadata(
            String eventType, int eventVersion, OffsetDateTime occurredAt, String traceId) {
        return new EventMetadata(
                "evt-1", eventType, eventVersion, occurredAt, traceId, "ygh-order-service", "order-1");
    }

    private record OrderCreatedPayload(String orderId) implements ImmutableEventPayload {   // 定义测试用负载记录类
    }
}
