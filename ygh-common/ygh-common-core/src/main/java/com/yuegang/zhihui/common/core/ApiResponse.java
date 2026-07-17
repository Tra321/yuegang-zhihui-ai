package com.yuegang.zhihui.common.core;

import java.time.OffsetDateTime;
import java.util.Objects;

public record ApiResponse<T>(  // 使用Java Record定义响应时间
                               String code,   // 状态业务码
                               String message,    // 提示信息
                               T data,    // 泛型数据负载
                               String traceId,    // 请求链路追踪 ID
                               OffsetDateTime timestamp) {    // 响应生成的时间戳
    public ApiResponse { // 紧凑构造函数，用于参数校验
        Objects.requireNonNull(code, "code must not be null");    // 状态码不能为空
        Objects.requireNonNull(message, "message must not be null");    // 消息不能为空
        Objects.requireNonNull(traceId, "traceId must not be null");    // 消息不能为空
        Objects.requireNonNull(timestamp, "timestamp must not be null");    // 时间戳不能为空
    }

    public static <T> ApiResponse<T> success(T data, String traceId) {    // 静态快捷成功方法
        return new ApiResponse<>(    // 创建成功的响应对象
                ErrorCode.SUCCESS.code(),    // 使用预定义的成功状态码
                ErrorCode.SUCCESS.defaultMessage(),    // 使用默认成功消息
                data,    // 传入的数据
                traceId,    // 传入的追踪 ID
                OffsetDateTime.now()    // 记录当系统时间
        );
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, String message, String traceId) { // 简单失败方法
        return failure(errorCode, message,  null, traceId); // 调用全参数失败方法，数据传空
    }

    public static <T> ApiResponse<T> failure( // 完整参数失败方法
            ErrorCode errorCode, // 错误枚举对象
            String message, // 自定义错误消息
            T data, // 相关的错误细节数据
            String traceId // 追踪 ID
    ) {
        Objects.requireNonNull(errorCode,  "errorCode must not be null"); // 错误码对象不能为空
        var resolvedMessage = message == null || message.isBlank() // 如果自定义消息为空
                ? errorCode.defaultMessage() // 则使用错误码
                : message; // 否则使用自定义消息
        return new ApiResponse<>(errorCode.code(), resolvedMessage, data, traceId, OffsetDateTime.now()); // 返回错误响应
    }
}