package com.yuegang.zhihui.common.web;

import java.util.LinkedHashMap;
import java.util.Map;

/** 经过清洗的请求元数据。查询字符串、请求体和凭据被排除在外。 */
public record RequestLogEvent( // 定义日志事件 Record
                               String traceId,
                               String requestId,
                               String method,
                               String path,
                               int status,
                               long durationMs,
                               Map<String, String> headers
) { // 类体

    public RequestLogEvent { // 构造逻辑
        requireText(traceId, "traceId"); // 校验 TraceId
        requireText(requestId, "requestId"); // 校验 RequestId
        requireText(method, "method"); // 校验方法
        requireText(path, "path"); // 校验路径
        if (status < 100 || status > 599) { // 检查状态码有效性
            throw new IllegalArgumentException("status must be a valid HTTP status"); // 报错
        }
        if (durationMs < 0) { // 耗时不能为负
            throw new IllegalArgumentException("durationMs must not be negative"); // 报错
        }
        if (headers == null) { // 头部 Map 不能为空
            throw new IllegalArgumentException("headers must not be null"); // 报错
        }
        headers = Map.copyOf(new LinkedHashMap<>((headers))); // 执行深度不可变拷贝
    }

    private static void requireText(String value, String fieldName) { // 静态工具：必填文本校验
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank"); // 报错
        }
    }
}
