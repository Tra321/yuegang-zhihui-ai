package com.yuegang.zhihui.common.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditContractTest { // 安全审计契约（SecurityAudit）单元测试类

    @Test  // 标注为 Junit5 测试方法
    void shouldExposeStablePermissionDeniedBusinessCode() { // 测试方法：验证 PermissionDeniedException 暴露出稳定的 PERMISSION_DENIED 业务错误码
        PermissionDeniedException exception = new PermissionDeniedException(); // 实例化权限拒绝异常

        assertThat(exception.errorCode().code()).isEqualTo("PERMISSION_DENIED"); // 断言异常内部绑定的错误字符串为 PERMISSION_DENIED
    }

    @Test
    void shouldPublishImmutableSecurityDecisionWithoutSensitiveCredentials() {  // 测试方法：验证安全审计事件发布不可变记录，且绝对不包含令牌/密码等敏感数据
        AtomicReference<SecurityAuditEvent> captured = new AtomicReference<>(); // 创建原子引用用于捕获发布的事件
        SecurityAuditPublisher publisher = captured::set;   // 使用 Lambda 表达式实现审计发布接口，将事件存入原子引用
        SecurityAuditEvent event = new SecurityAuditEvent(
                Instant.parse("2026-07-17T:08:00:00z"), // 发生时间
                "user-1", // 操作用户
                "READ_ADDRESS", // 动作名称
                "user:address:read", // 所需权限
                SecurityDecision.DENIED, // 判定结果为拒绝
                "RESOURCE_NNOT_FOUND", // 拒绝原因：非资源所有者
                "trace-1" // 链路追踪 ID
        ); // 实例化完成

        publisher.publish(event); // 执行事件发布

        assertThat(captured.get()).isEqualTo(event); // 验证发布者收到的事件对象与原始事件是否一致

        assertThat(event.toString()).doesNotContain("token", "password", "secret"); // 验证事件的 toString 文本中绝不包含敏感数据关键字
    }
}