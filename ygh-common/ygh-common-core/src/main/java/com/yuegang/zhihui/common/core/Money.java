package com.yuegang.zhihui.common.core;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
/** 精确的非负金额值，带符号的账本变动应使用单独的领域类型。 */
public record Money( // 货币记录
        @JsonFormat(shape = JsonFormat.Shape.STRING)BigDecimal amount, // 强制将BigDecimal序列化为字符的防止精度丢失
        CurrencyCode currency // 对应的货币类型（如CNY）
) {

    public static final int SCALE = 2; // 全局强制统一两位小数

    public Money{ // 业务规则校验
        if (amount == null) { //金额不能为空
            throw new IllegalArgumentException("amount cannot be null"); // 报错
        }
        if (amount.scale() != SCALE) { // 小数位数必须严格等于2
            throw new IllegalArgumentException("amount scale must be exactly " + SCALE); // 报错
        }
        if (amount.signum() < 0){ // 金额不能为负数
            throw new IllegalArgumentException("amount cannot be negative"); // 报错
        }
        if (currency == null) {
            throw new IllegalArgumentException("currencyCode cannot be null"); // 报错
        }
    }
    public static Money cny(BigDecimal amount) { // 创建人民币金额便捷方法
        return new Money(amount, CurrencyCode.CNY); // 返回新实例
    }
}
