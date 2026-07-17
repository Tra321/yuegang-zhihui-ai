package com.yuegang.zhihui.gateway;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.awt.*;
import java.util.Set;
import java.util.regex.Pattern;

/** 拒绝大容量请求体，并防止在未声明的了路径上使用 Multipart 表单上传 **/
@SuppressWarnings("NullableProblems")
public class GatewayRequestGuardFilter implements WebFilter, Ordered {

    static final long MAX_CONFIGURABLE_BYTES = 100L * 1024 *1024; // 100MB上限
    private static final Pattern SAFE_UPLOAD_BYTES = Pattern.compile("api/v1/[A-Za-z0-9/_-]+");

    private final long requestMaxBytes;
    private final long uploadMaxBytes;
    private final Set<String> uploadPaths;
    private final GatewaySecurityErrorWriter errorWriter;

    GatewayRequestGuardFilter(long requestMaxBytes,long uploadMaxBytes, Set<String> uploadPaths, GatewaySecurityErrorWriter errorWriter) {
        this.requestMaxBytes = requestMaxBytes;
        this.uploadMaxBytes = uploadMaxBytes;
        this.uploadPaths = uploadPaths;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 判断是否为Multipart上传格式
        boolean multipart = exchange.getRequest().getHeaders().getContentType() != null && MediaType.MULTIPART_FORM_DATA.isCompatibleWith(exchange.getRequest().getHeaders().getContentType());

        String path = exchange.getRequest().getPath().pathWithinApplication().value();

        boolean allowedUpload = multipart && HttpMethod.POST.equals(exchange.getRequest().getMethod()) && uploadPaths.contains(path);

        if (multipart && !allowedUpload) {
            return errorWriter.uploadPathRejected(exchange);
        }

        // 读取 Content Length 进行初步判断
        long contentLength = exchange.getRequest().getHeaders().getContentLength();
        if (allowedUpload && contentLength < 0) {
            return errorWriter.lengthRequired(exchange); // 上传接口禁止不传 Length
        }

        long limit = allowedUpload ? uploadMaxBytes : requestMaxBytes;
        if (contentLength > limit) {
            return errorWriter.payloadTooLarge(exchange); // 报文过大
        }

        // 如果带有明确长度的常规请求，直接放行
        if (contentLength >= 0) {
            return chain.filter(exchange);
        }

        // 如果是 Chunked 传输（长度未知），需要流式读取并计算，超过 limit 则抛出异常
        return DataBufferUtils.join(exchange.getRequest().getBody(), Math.toIntExact(limit))
                .singleOptional()
                .flatMap( buffer -> replay(exchange, chain, buffer.orElse(null)))
                .onErrorResume(DataBufferLimitException.class, ignored -> errorWriter.payloadTooLarge(exchange));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 15;
    }

    // 辅助方法：重新封装 Request，使下游能再次读取 Body 字节流
    private static Mono<Void> replay(ServerWebExchange exchange, WebFilterChain chain, DataBuffer bufferBody) {
        var guardeRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public Flux<DataBuffer> getBody() {
                if (bufferBody == null) return Flux.empty();
                return Flux.defer(() -> Flux.just(DataBufferUtils.retain(bufferBody)));
            }
        };
        Mono<Void> result = Mono.defer(() -> chain.filter(exchange.mutate().request(guardeRequest).build()));
        if (bufferBody == null) return result;
        // 在逻辑处理完后释放缓冲区内存
        return result.doFinally(ignored -> DataBufferUtils.release(bufferBody));
    }
}
