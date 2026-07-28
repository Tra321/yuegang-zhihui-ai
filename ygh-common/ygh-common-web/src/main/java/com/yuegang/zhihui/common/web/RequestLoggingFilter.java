package com.yuegang.zhihui.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** 记录安全的请求元数据，不读取查询参数或请求体。 */
public class RequestLoggingFilter extends OncePerRequestFilter { // 最终类定义

    private static final String REDACTED = "[REDACTED]"; // 敏感信息占位符
    private static final int MAX_CORRELATION_ID_LENGTH = 128; // 最大标识符长度
    private static final int MAX_USER_AGENT_LENGTH = 256; // 最大 UA 长度
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]+"); // 安全ID正则
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}"); // 控制字符正则
    private static final Log LOGGER = LogFactory.getLog(RequestLoggingFilter.class); // 定义记录器

    private final RequestLogSink sink; // 日志输出点接口

    public RequestLoggingFilter(RequestLogSink sink) { // 构造注入
        if (sink == null) { // 列空
            throw new IllegalArgumentException("sink must not be null"); // 报错
        }
        this.sink = sink; // 赋值
    }

    @Override // 标记重写
    protected void doFilterInternal(
            HttpServletRequest request, // 请求
            HttpServletResponse response, // 响应
            FilterChain filterChain // 链
    ) throws ServletException, IOException { // 异常声明
        long startedAt = System.nanoTime(); // 纳秒精度起始时间
        String traceId = resolveOrCreateTraceId(request); // 解析或创建 TraceId
        String requestId = resolveOrCreateRequestId(request); // 解析或创建 RequestId
        request.setAttribute(TraceIdResolver.TRACE_ID_ATTRIBUTE, traceId); // 将 ID 绑定到请求上下文
        request.setAttribute(TraceIdResolver.REQUEST_ID_ATTRIBUTE, requestId); // 同上
        response.setHeader(TraceIdResolver.TRACE_ID_HEADER, traceId); // 在响应头返回 TraceId
        response.setHeader(TraceIdResolver.REQUEST_ID_HEADER, requestId); // 在响应头返回 RequestId
        MDC.put("traceId", traceId); // 向 MDC 写入 TraceId，后续 Log 日志都会自动带上
        MDC.put("requestId", requestId); // 向 MDC 写入 RequestId

        boolean failed = false; // 错误标记
        try {
            filterChain.doFilter(request, response); // 执行下一个过滤器或 Controller
        } catch (ServletException | IOException | RuntimeException exception) { // 捕获
            failed = true; // 失败
            throw exception; // 抛出
        } finally { // 开启结束块
            long durationMs = Math.max(0, (System.nanoTime() - startedAt) / 1_000_000); // 计算执行毫秒数
            int status = failed ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR : response.getStatus(); // 计算最终状态码
            publishSafely(new RequestLogEvent( // 构造日志事件并发布
                    traceId, requestId, request.getMethod(), request.getRequestURI(), // 填入元数据
                    status, durationMs, safeHeaders(request))); // 填入耗时和脱敏后遗失信息
            MDC.remove("requestId"); // 清理 MDC
            MDC.remove("traceId"); // 清理 MDC
        }
    }

    private String resolveOrCreateTraceId(HttpServletRequest request) { // 获取 ID 逻辑
        Object attribute = request.getAttribute(TraceIdResolver.TRACE_ID_ATTRIBUTE); // 先看 Request 属性
        if (attribute instanceof String value && isSafeIdentifier(value)) return value; // 若合法则返回
        String header = request.getHeader(TraceIdResolver.TRACE_ID_HEADER); // 再看请求头
        return isSafeIdentifier(header) ? header : newIdentifier(); // 头合法则用头，否则生成全新的
    }

    private String resolveOrCreateRequestId(HttpServletRequest request) { // 获取请求 ID 逻辑
        Object attribute = request.getAttribute(TraceIdResolver.REQUEST_ID_ATTRIBUTE); // 看属性
        if (attribute instanceof String value && !value.isEmpty()) { // 解析
            return isSafeIdentifier(value) ? value : newIdentifier(); // 合法用，否则造新
        }
        String header = request.getHeader(TraceIdResolver.REQUEST_ID_HEADER); // 看请求头
        return isSafeIdentifier(header) ? header : newIdentifier(); // 合法用，否则造新
    }

    private Map<String, String> safeHeaders(HttpServletRequest request) { // 请求头过滤
        String userAgent = request.getHeader("User-Agent"); // 获取浏览器 UA
        if (userAgent == null || userAgent.isEmpty()) { // 为空
            return Map.of(); // 返回空 Map
        }
        LinkedHashMap<String, String> headers = new LinkedHashMap<>(); // 创建有序容器
        headers.put("user-Agent", sanitizeUserAgent(userAgent)); // 脱敏并限制长度后放入
        return headers; // 返回
    }

    private String sanitizeUserAgent(String userAgent) { // UA 字符串清理逻辑 1 usage
        String withoutControlCharacters = CONTROL_CHARACTERS.matcher(userAgent).replaceAll(""); // 去除控制字符（防注入）
        String normalized = withoutControlCharacters.toLowerCase(Locale.ROOT); // 转小写
        if (normalized.contains("token") // 包含敏感关键字检查
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("cookie")
                || normalized.contains("authorization")) { // 命中
            return REDACTED; // 强制脱敏
        }
        return withoutControlCharacters.substring( // 拦截处理，防止日志过长
                0, Math.min(withoutControlCharacters.length(), MAX_USER_AGENT_LENGTH)); // 返回
    }

    private boolean isSafeIdentifier(String value) { // ID 格式校验
        return value != null // 不为空
                && !value.isBlank() // 不为空白
                && !"unavailable".equals(value) // 不是保留关键字
                && value.length() <= MAX_CORRELATION_ID_LENGTH // 长度不超限
                && SAFE_CORRELATION_ID.matcher(value).matches(); // 正则匹配
    }

    private void publishSafely(RequestLogEvent event) { // 安全发布，不影响业务请求
        try {
            sink.accept(event); // 调用接收器输出日志
        } catch (RuntimeException exception) { // 捕获异常，防止日志系统故障拖累业务
            LOGGER.warn("Request log sink failed, type=" + exception.getClass().getName()); // 打印警告
        }
    }

    private String newIdentifier() { // 生成随机 ID
        return UUID.randomUUID().toString().replace("-", ""); // 去掉横线中的 UUID
    }
}
