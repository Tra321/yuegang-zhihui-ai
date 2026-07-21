package com.yuegang.zhihui.common.core;


import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Arrays;
/* 货币代码 */
/** 当前虚拟钱包版本支持的货币 */

public enum CurrencyCode implements StableCodeEnum { // 实现稳定代码接口
    CNY("CNY","人民币"); // 定义人民币常量

    private final String code; // 货币缩写代码
    private final String displayName; // 货币展示名称

    CurrencyCode(String code, String displayName) { // 私有构造函数
        this.code = code; // 初始化代码
        this.displayName = displayName; // 初始化代码
    }

    @Override // 重写接口方法
    public String code() { // 获取编码
        return code; // 返回代码值
    }

    @Override // 重写接口方法
    public String displayName() { // 获取名称
        return displayName; // 返回展示名称
    }

    @JsonCreator // 用于 Jackson 反序列化
    public static CurrencyCode fromCode(String code) { // 根据字符串代码匹配表示
        return Arrays.stream(values()) // 遍历所有枚举值
                .filter(value -> value.code.equals(code)) // 匹配代码一致的项目
                .findFirst() // 获取第一个结果
                .orElseThrow(() -> new IllegalArgumentException("unsupported currency code: " + code)); // 未匹配则抛出参数异常
    }
}