package com.yuegang.zhihui.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 解析请求追踪标识符，不创建第二个追踪系统，复用现在的链路 ID。
 */

public final class TraceIdResolver { // 定义解析器类

    public static final String TRACE_ID_ATTRIBUTE = "traceId"; // 定义属性存储键
    public static final String REQUEST_ID_ATTRIBUTE = "requestId"; // 定义请求 ID 存储器
    public static final String TRACE_ID_HEADER = "X-Trace-Id"; // 定义 Trace 头字段名
    public static final String REQUEST_ID_HEADER = "X-Request_Id"; // 定义 Request 头字段名
    public static final String UNAVAILABLE = "unavailable"; // 定义无法获取时候的常量

    private TraceIdResolver() { // 私有化构造
    }

    public static String resolve(HttpServletRequest request) { // 解析逻辑入口
        var attribute = request.getAttribute(TRACE_ID_ATTRIBUTE); // 尝试从 Request 属性中解析（过滤器已放入）
        if (attribute instanceof String traceId && !traceId.isBlank()) { // 如果存在且不为空
            return traceId; // 直接返回链路 ID
        }
        var requestId = request.getHeader(REQUEST_ID_HEADER); // 返回其其次从 Header 中读取请求 ID
        return requestId == null || requestId.isBlank() ? UNAVAILABLE : requestId; // 若暂无则返回"不可用"字符串
    }
}
