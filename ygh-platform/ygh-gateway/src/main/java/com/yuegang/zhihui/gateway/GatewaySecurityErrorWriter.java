package com.yuegang.zhihui.gateway;

import com.yuegang.zhihui.common.core.ApiResponse;
import com.yuegang.zhihui.common.core.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/** 使用通用的响应外壳写入无凭据的安全失败响应。 */
@Component
final class GatewaySecurityErrorWriter {

    private final ObjectMapper objectMapper; // Jackson 序列化工具

    GatewaySecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // 401 认证失败
    Mono<Void> unauthenticated(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED); // ErrorCode这个类来自与ygh-common
    }

    // 403 权限不足
    Mono<Void> accessDenied(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.FORBIDDEN, ErrorCode.PERMISSION_DENIED); // ErrorCode这个类来自与ygh-common
    }

    // 429 触发限流
    Mono<Void> rateLimited(ServerWebExchange exchange) {
        if (!exchange.getResponse().isCommitted()) {
            exchange.getResponse().getHeaders().set("Retry-After", "1"); // 提示1秒后重试
        }
        return write(exchange, HttpStatus.TOO_MANY_REQUESTS, ErrorCode.RATE_LIMITED); // ErrorCode 这个类来自与ygh-common
    }

    // 503 依赖不可用（如果服务未找到）
    Mono<Void> dependencyUnavailable(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.DEPENDENCY_UNAVAILABLE); // ErrorCode 这个类来自与ygh-common
    }

    // 413 报文过大
    Mono<Void> payloadTooLarge(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.PAYLOAD_TOO_LARGE, ErrorCode.VALIDATION_ERROR); // ErrorCode 这个类来自与ygh-common
    }

    // 403 上传路径被拒绝
    Mono<Void> uploadPathRejected(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.FORBIDDEN, ErrorCode.PERMISSION_DENIED); // ErrorCode 这个类来自与ygh-common
    }

    // 411 缺失长度
    Mono<Void> lengthRequired(ServerWebExchange exchange) {
        return write(exchange, HttpStatus.LENGTH_REQUIRED, ErrorCode.VALIDATION_ERROR); // ErrorCode 这个类来自与ygh-common
    }

    // 内部通用写入逻辑
    private Mono<Void> write(ServerWebExchange exchange, HttpStatus status, ErrorCode errorCode) {
        if (exchange.getResponse().isCommitted()) return Mono.empty();

        // 获取当前请求的 TraceId
        String traceId = exchange.getAttributeOrDefault(GatewaySecurityAttributes.TRACE_ID,  "unavailable");
        // 构造标准返回体并序列化
        byte[] body = objectMapper.writeValueAsBytes(ApiResponse.failure(errorCode, null, traceId)); // ApiResponse 是来自common子模块的

        exchange.getResponse().setStatusCode(status); // 设置HTTP状态码
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON); // 设置JSON格式
        exchange.getResponse().getHeaders().setContentLength(body.length);

// 写入缓冲区并返回
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }
}
