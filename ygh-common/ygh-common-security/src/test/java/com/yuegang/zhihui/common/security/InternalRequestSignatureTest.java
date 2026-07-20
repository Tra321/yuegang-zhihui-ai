package com.yuegang.zhihui.common.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InternalRequestSignatureTest { // 网关到内部服务请求签名（InternalRequestSignatureTest） 单元测试类
    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z");   // 定义测试用固定基准时间
    private static final InternalRequestSignature signatures = new InternalRequestSignature(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII), // 存入32字节安全密钥
            Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30));  // 绑定固定UTC时间，设置最大允许时间偏差为30 秒

    @Test   // 标注为 JUnit5 测试方法
    void verifiesOnlyUntamperedMetadataInsideTheTimeWindow() { // 测试方法： 验证仅在有效时间窗口内且未被篡改的元数据课通过签名校验
        var metadata = metadata(NOW);   // 生成当前时间的合法数据
        String signature = signatures.sign(metadata);   // 计算生成签名字符串

        assertThat(signatures.verify(metadata, signature)).isTrue();    // 验证未篡改的元数据与对应签名校验成功
        assertThat(signatures.verify(new InternalRequestSignature.Metadata( // 验证篡改了 IP 地址（从 192.0.2.0 改为192.0.2.9) 后的元数据
                "192.0.2.9", metadata.traceId(), metadata.requestId(), metadata.method(),   // 传入新的 IP
                metadata.path(), metadata.timestamp()), signature)).isFalse();  // 验证防篡改校验返回False
        assertThat(signatures.verify(metadata(NOW.minusSeconds(31)),    // 创建时间戳超出时间窗口（过去31秒）的元数据
                signatures.sign(metadata(NOW.minusSeconds(31))))).isFalse();    // 验证即使签名正确，也因过期被拒绝
        assertThat(signatures.verify(metadata, "not-a-signature")).isFalse();   // 验证格式错误的非法签名返回 false
    }

    private static InternalRequestSignature.Metadata metadata(Instant timestamp) {  // 辅助静态方法，构造测试用元数据
        return new InternalRequestSignature.Metadata(
                "192.0.2.8", "trace-1", "request-1", "POST", "/api/v1/auth/login", timestamp);  // 填充 IP、TraceId、RequestId、Method、Path 和时间戳
    }
}
