package com.yuegang.zhihui.common.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 序列化文本的外部标识符，确保不会丢失精度
 */
public record ExternalId(String value) { // 使用 Record 包装字符串 ID

    public ExternalId { // 构造校验
        if (value == null || value.isBlank()) { // 校验 ID 不能有空
            throw new IllegalArgumentException("external id must not be blank"); // 抛出异常
        }
        if (value.equals(value.trim())) { // 校验ID两端不能有空格
            throw new IllegalArgumentException("external id must not be contain surround whitespace"); // 抛出异常
        }
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING) // 用于 JSON 反序列化时将空字符串直接转换为对象
    public static ExternalId of(String value) { // 静态工程
        return new ExternalId(value); //创建新实例
    }

    @Override
    @JsonValue // 序列化是仅展示内部字符串值
    public String value() { // 获取内部值
        return value; // 返回字符串
    }

    @Override
    public String toString() { // 重写 toString
        return value;
    }
}
