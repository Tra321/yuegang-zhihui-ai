package com.yuegang.zhihui.common.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ValueContractTest { // 定义值对象契约测试类

    @Test
        // 标记为测试方法
    void externalIdKeepsValuesBeyondJavaScriptSafeIntegerAsText() { // 测试外部 ID 是否将超过 JS 安全整数范围的值保存为文本
        var id = ExternalId.of("9007199254740993"); // 传入一个大整数（超过 2^53-1）

        assertThat(id.value()).isEqualTo("9007199254740993"); // 断言内部存储的数据正确
        assertThat(id.toString()).isEqualTo("9007199254740993"); // 断言转为字符串后的表现正确
    }

    @Test // 标记为测试方法
    void externalIdRejectMissingOrSurroundedWhitespace() { // 测试外部 ID 是否拒绝缺失值或带有后空格的输入
        assertThatThrownBy(() -> ExternalId.of(null)) // 拒绝 null
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExternalId.of(" ")) // 拒绝纯空格
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExternalId.of(" user-1 ")) // 拒绝带前后空格的字符串
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test // 标记为测试方法
    void moneyUsesExactBigDecimalScaleAndTheOnlySupportedCurrency() { // 测试金额是否使用精确的 Scale（小数位）和唯一支持的货币
        var money = Money.cny(new BigDecimal("99.80")); // 创建 99.80 人民币对象

        assertThat(money.amount()).isEqualByComparingTo("99.80"); // 使用比值断言（忽略末尾 0 差异，虽然在此处 Scale 强制为 2）
        assertThat(money.amount().scale()).isEqualTo(Money.SCALE); // 断言精度等于 2
        assertThat(money.currency()).isEqualTo(CurrencyCode.CNY); // 断言货币为人民币
        assertThat(money.currency().code()).isEqualTo("CNY"); // 断言代码字符串正确
        assertThat(CurrencyCode.values()).containsExactly(CurrencyCode.CNY); // 断言目前系统仅支持人民币一种货币
    }

    @Test // 标记为测试方法
    void moneyNeverRoundsOrChangesScaleImplicitly() { // 测试金额对象不进行隐式舍入或改变精度
        assertThatThrownBy(() -> Money.cny(new BigDecimal("99.8"))) // 拒绝小数位不足 2 位的输入
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale");
        assertThatThrownBy(() -> Money.cny(null)) // 拒绝空值
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test // 标记为测试方法
    void enumCodeAreExplicitStableAndIndependentFromDisplayText() { // 测试枚举编码是否显式、稳定且独立于展示文本
        assertThat(CurrencyCode.CNY.code()).isEqualTo("CNY"); // 代码应为 CNY
        assertThat(CurrencyCode.CNY.displayName()).isEqualTo("人民币"); // 显式文本为中文
        assertThat(CurrencyCode.CNY.code()).isNotEqualTo(CurrencyCode.CNY.displayName()); // 确保代码和显示文本没有耦合
    }
}
