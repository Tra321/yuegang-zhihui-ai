package com.yuegang.zhihui.common.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InternalServiceSignatureTest { // 微服务间隔用签名（InternalServiceSignature） 单元测试类
    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");    // 定义基准时间
    private static final byte[] SECRET = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII); //定义32字节密钥
    private final InternalServiceSignature signatures = new InternalServiceSignature(
            SECRET, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30));  // 绑定密钥、固定时钟与30秒容忍时间

    @Test   // 标注为JUnit5 测试方法
    void acceptOnlyUntamperedFreshLowerHexSignatures() {   // 测试方法：只接受为篡改、未过期的64位小写十六进制签名
        var metadata = new InternalServiceSignature.Metadata(
                "ygh-order-service", "POST","/internal/v1/inventory/reserve", NOW); // 填充服务名、方法、路径及时间
        String signature = signatures.sign(metadata);    // 计算签名
        assertThat(signature).matches("[0-9a-f]{64}");  // 验证导出的签名符合64位小写十六进制正则
        assertThat(signatures.verify(metadata, signature)).isTrue();    // 验证正确签名通过
        assertThat(signatures.verify(metadata, null)).isFalse();    // 验证空签名被拒绝
        assertThat(signatures.verify(metadata, "invalid")).isFalse();   // 验证格式异常的签名被拒绝
        assertThat(signatures.verify(new InternalServiceSignature.Metadata( // 验证篡改请求方法（POST改为GET）后
                metadata.service(), "GET", metadata.path(), NOW), signature)).isFalse();    // 签名校验失败
        var stale = new InternalServiceSignature.Metadata(  // 构造 31 秒前的过期元数据
                metadata.service(), metadata.method(), metadata.path(), NOW.minusSeconds(31));  // 时间戳超期
    }

    @Test   // 标注为 JUnit5 测试任务
    void rejectsWeakSecretsAndNullInfrastructure() {    // 测试方法： 拒绝弱密钥（小于32字节） 以及空基础设置组件
        assertThatThrownBy(() -> new InternalServiceSignature(new byte[31],Clock.systemUTC(), Duration.ZERO))   // 传入31字节的过短密钥
                .isInstanceOf(IllegalArgumentException.class);  // 断言抛出参数给发异常
        assertThatThrownBy(() -> new InternalServiceSignature(null, Clock.systemUTC(),Duration.ZERO))   // 传入 null 密钥
                .isInstanceOf(IllegalArgumentException.class);  // 断言抛出参数给发异常
        assertThatThrownBy(() -> new InternalServiceSignature(SECRET, null, Duration.ZERO)) // 传入 null 时钟
                .isInstanceOf(NullPointerException.class);  // 断言抛出空指针异常
        assertThatThrownBy(() -> new InternalServiceSignature(SECRET, Clock.systemUTC(), null)) // 传入 null 容忍时长
                .isInstanceOf(NullPointerException.class);  // 断言抛出参数非法异常
    }

    @Test   // 标注为 JUnit 5 测试方法
    void metadataRejectsUnsafeCanonicalFields() {  // 测试方法：元数据构造时拒绝不合法或不安全的规范字段
        assertThatThrownBy(() -> new InternalServiceSignature.Metadata(null, "GET", "/x", NOW))     // 服务名为null
                .isInstanceOf(IllegalArgumentException.class);  // 抛出异常
        assertThatThrownBy(() -> new InternalServiceSignature.Metadata("x", "GET", "/x", NOW))  // 服务器名为大写非法字符
                .isInstanceOf(IllegalArgumentException.class);  // 抛出异常
        assertThatThrownBy(() -> new InternalServiceSignature.Metadata("ygh-service", "null", "/x", NOW))   // HTTP方法为空
                .isInstanceOf(IllegalArgumentException.class);  // 抛出异常
        assertThatThrownBy(() -> new InternalServiceSignature.Metadata("ygh-service", "GET", "null", NOW))   // 路径为 null
                .isInstanceOf(IllegalArgumentException.class);  // 抛出异常
        assertThatThrownBy(() -> new InternalServiceSignature.Metadata("ygh-service", "GET", "relative", NOW))   // 路径未以斜杠开头
                .isInstanceOf(IllegalArgumentException.class);  // 抛出异常
        assertThatThrownBy(() -> new InternalServiceSignature.Metadata("ygh-service", "GET", "/x", null))   // 时间戳为null
                .isInstanceOf(NullPointerException.class);  // 抛出空指针异常
    }
}
