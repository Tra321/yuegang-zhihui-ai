package com.yuegang.zhihui.common.security;

import java.time.Instant;
import java.util.Objects;

public record SecurityAuditEvent(  // 定义安全审计事件记录类，用于记录合规性日志
        Instant occurredAt, // 时间发生时间戳
        String userId, // 触发该操作的用户 ID
        String action, // 操作名称（如：更新权限）
        String requiredPermission, // 该操作所需的权限码
        SecurityDecision decision, // 决策结果（允许或拒绝）
        String reason, // 详细原因说明
        String traceId // 关联的请求链路追踪 ID
) {
    public SecurityAuditEvent {
        Objects.requireNonNull(occurredAt,"occurredAt must not be null"); // 发生时间不能为空
        requiredText(userId, "userId"); // 用户 ID 不能为空且文本
        requiredText(action, "action"); // 操作动作不能为空且文本
        requiredText(requiredPermission, "requiredPermission");
        Objects.requireNonNull(decision, "decision must not be null");
        requiredText(reason, "reason"); // 原因描述不能为空
        requiredText(traceId, "traceId"); // 追踪 ID 不能为空
    }

    private static void requiredText(String value, String fieldName) { // 定义文本非空辅助
        if (value == null || value.isBlank()) { // 如果字符串为 null 或者为字符串
            throw new IllegalArgumentException(fieldName + " must not be blank"); // 抛出字段必填异常
        }
    }
}