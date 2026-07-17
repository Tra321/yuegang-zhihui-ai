package com.yuegang.zhihui.common.core;

import java.time.OffsetDateTime;

/**单个调用者操作及其规范请求具体的身份标识*/
public record IdempotencyRequestContext( // 定义幂等请求载体
        String idempotencyKey,     // 幂等键（由客户端生成）
        String operation,          // 业务操作名称
        String subjectId,          // 请求主题ID（如用户ID）
        String requestFingerprint, // 请求参数的哈希指纹(防篡改)
        OffsetDateTime requestedAt // 请求发起时间戳
) {
    public IdempotencyRequestContext{ // 校验非空
        requireText(idempotencyKey,"idempotencyKey"); // 校验幂等键
        requireText(operation,"operation"); // 校验操作名
        requireText(subjectId,"subjectId"); // 校验主体 ID
        requireText(requestFingerprint,"requestFingerprint"); //校验指纹
        if (requestedAt == null) {
            throw new IllegalArgumentException("requestedAt must not be null"); // 抛出异常
        }
    }

    private static void requireText(String value,String fieldName) { // 文本非空检查工具
        if (value == null || value.isBlank()) { // 若为空白
            throw new IllegalArgumentException(fieldName + "must not be blank"); // 抛出异常
        }
    }
}
