package com.yuegang.zhihui.common.core;

import java.util.*;

/** 领域事件契约的默认类型安全实现。 */
public final class VersionedDomainEvent<T> implements DomainEvent<T> { // 实现领域事件接口

    private final EventMetadata metadata;   // 持有元数据
    private final T payload;                // 持有负载

    private VersionedDomainEvent(EventMetadata metadata, T payload) { // 私有构造函数
        if (metadata == null) { // 校验元数据
            throw new IllegalArgumentException("metadata must not be null"); // 报错
        }
        if (payload == null) { // 校验负载
            throw new IllegalArgumentException("payload must not be null"); // 报错
        }
        this.metadata = metadata; // 赋值元数据
        this.payload = payload; // 赋值负载
    }

    public static <T extends ImmutableEventPayload> VersionedDomainEvent<T> of( // 强类型工厂方法
            EventMetadata metadata,  // 传入头
            T payload // 传入不可变负载对象
    ) {
        return new VersionedDomainEvent<>(metadata, payload); //返回新的实例
    }

    public static VersionedDomainEvent<Map<String, Object>> ofMap( // 面向动态Map的工厂
                                                                   EventMetadata metadata, // 传入头
                                                                   Map<String, Object> payload // 传入Map格式负载
    ) {
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null"); // 报错
        }
        return new VersionedDomainEvent<>(metadata, immutableMap(payload)); // 递归转换为深度不可变的Map后返回
    }

    @Override
    public EventMetadata metadata() {
        return null;
    }

    @Override
    public T playLoad() {
        return null;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) { // 深度递归不可变的Map转换
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>(source.size()); // 创建有序Map副本
        source.forEach((key, value) -> copy.put(key, immutableNestedValue(value))); // 遍历并递归处理子项
        return Collections.unmodifiableMap(copy); // 返回只读视图
    }

    private static Object immutableNestedValue(Object value) { // 递归处理嵌套集合的工具方法
        if (value instanceof Map<?, ?> map) { // 判断是否为Map
            LinkedHashMap<Object, Object> copy = new LinkedHashMap<>(map.size()); // 递归转换
            map.forEach((key, item) -> copy.put(key, immutableNestedValue(item))); //递归调用
            return Collections.unmodifiableMap(copy); // 返回
        }
        if (value instanceof List<?> list) { // 如果值是List
            List<Object> copy = new ArrayList<>(list.size()); // 递归转换
            list.forEach(item -> copy.add(immutableNestedValue(item))); // 循环添加递归值
            return Collections.unmodifiableList(copy); // 返回
        }
        if (value instanceof Set<?> set) { // 如果值是Set
            LinkedHashSet<Object> copy = new LinkedHashSet<>(set.size()); // 递归转换
            set.forEach(item -> copy.add(immutableNestedValue(item))); // 循环添加递归值
            return Collections.unmodifiableSet(copy); // 返回
        }
            if (value != null && value.getClass().isArray()) { // 显式禁止在 Map 负载中使用原生数组
                throw new IllegalArgumentException("arrays are not supported in map event payloads"); // 数组有序化兼容性差，报错
            }
        return value; // 基础类型直接返回
    }
}