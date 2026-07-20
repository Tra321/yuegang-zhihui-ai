package com.yuegang.zhihui.common.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalUserContextSignatureTest { // 网关传递用户上下文签名（InternalUserContextSignature）单元测试类
    private static final Instant NOW = Instant.parse("2026-07-17T00:00:00Z"); // 定义固定时间戳 4 usages
    @Test void bindsIdentityAuthoritiesAndRequestMetadata() { // 测试：绑定用户身份、权限列表和请求元数据进行签名校验
        var signatures = new InternalUserContextSignature(new byte[32], Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30)); // 初始化签名器
        var metadata = new InternalUserContextSignature.Metadata("42", List.of("CUSTOMER"), List.of("user:read"), // 构造包含用户ID、角色、权限的完整上下文
                "trace-123456", "request-123456", "GET", "/api/v1/users/:me", NOW);  // 补充请求头元数据
        String signed = signatures.sign(metadata); // 生成签名
        assertThat(signatures.verify(metadata, signed)).isTrue(); // 验证未修改的上下文签名成功
        var tampered = new InternalUserContextSignature.Metadata("43", List.of("ADMIN"), List.of("user:write"), // 构造篡改了用户ID、角色权限的新元数据
                "trace-123456", "request-123456", "GET", "/api/v1/users/:me", NOW); // 使用相同请求标识
        assertThat(signatures.verify(tampered, signed)).isFalse(); // 验证已篡改的元数据校验失败
    }

    @Test void rejectsExpiredOrUnsafeMetadata() { // 测试：拒绝已过期或格式不安全的上下文元数据
        var signatures = new InternalUserContextSignature(new byte[32], Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30)); // 初始化签名器
        var expired = new InternalUserContextSignature.Metadata("42", List.of(), List.of(), "trace-123456", // 构造31秒前的过期元数据
                "request-123456", "GET", "/api/v1/users/me", NOW.minusSeconds(31)); // 时间超期
        assertThat(signatures.verify(expired, "0".repeat(64))).isFalse(); // 验证过期数据配合伪造签名失败
        assertThatThrownBy(() -> new InternalUserContextSignature.Metadata("42", List.of("bad role"), List.of(), // 传入包含空格的不安全的角色名称
                "trace-123456", "request-123456", "GET", "/api/v1/users/me", NOW)) // 触发正则校验
                .isInstanceOf(IllegalArgumentException.class); // 抛出非法参数异常
        assertThat(signatures.verify(expired, null)).isFalse(); // 验证签名返回 false
        assertThat(signatures.verify(expired, "not-a-signature")).isFalse(); // 验证非签名格式返回 false
    }

    @Test void validatesConstructorBoundsAndCanonicalizesAuthorities() { // 测试: 校验构造参数边界，并对角色权限列表进行排序与去重规范化
        assertThatThrownBy(() -> new InternalUserContextSignature(new byte[31], Clock.systemUTC(), Duration.ofSeconds(30))) // 密钥过短(31字节)
                .isInstanceOf(IllegalArgumentException.class); // 抛出异常
        assertThatThrownBy(() -> new InternalUserContextSignature(new byte[32], Clock.systemUTC(), Duration.ofSeconds(999))) // 容忍时间不小于1秒
                .isInstanceOf(IllegalArgumentException.class); // 抛出异常
        var signatures = new InternalUserContextSignature(new byte[32], Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(30)); // 实例化签名工具
        var metadata = new InternalUserContextSignature.Metadata("42", List.of("USER", "ADMIN", "USER"), // 传入重复且未排序的角色列表
                List.of("user:write", "user:read"), "trace-123456", "request-123456", // 传入权限列表
                "PUT", "/api/v1/users/me", NOW); // 填充元数据
        assertThat(metadata.roles()).containsExactly("ADMIN", "USER"); // 验证角色列表被自动去重并按字典序排序为 ["ADMIN", "USER"]
        assertThat(signatures.verify(metadata, signatures.sign(metadata))).isTrue(); // 验证规范化后的元数据正常签署与校验
        assertThatThrownBy(() -> new InternalUserContextSignature.Metadata("bad user", List.of(), List.of(), // 用户ID包含空格不安全
                "trace-123456", "request-123456", "GET", "/", NOW)).isInstanceOf(IllegalArgumentException.class); // 输出异常
        assertThatThrownBy(() -> new InternalUserContextSignature.Metadata("42", List.of(), List.of(), // TraceId包含空格不安全
                "bad trace", "request-123456", "GET", "/", NOW)).isInstanceOf(IllegalArgumentException.class); // 抛出异常
        assertThatThrownBy(() -> new InternalUserContextSignature.Metadata("42", List.of(), List.of(), // HTTP小写不安全
                "trace-123456", "request-123456", "get", "/", NOW)).isInstanceOf(IllegalArgumentException.class); // 抛出异常
        assertThatThrownBy(() -> new InternalUserContextSignature.Metadata("42", List.of(), List.of(), // 相对路径非斜杠开头不安全
                "trace-123456", "request-123456", "GET", "relative", NOW)).isInstanceOf(IllegalArgumentException.class);// 抛出异常
    }
}
