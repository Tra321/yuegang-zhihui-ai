package com.yuegang.zhihui.gateway;

// 受信任客户端IP过滤

import com.yuegang.zhihui.common.security.InternalRequestSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

/**
 *  将边缘节点观测到的真实 IP 签名放入 Header， 替换掉所有不可信的伪造 IP 头。
 */
@Component
@ConditionalOnProperty(prefix = "ygh.internal-request", name = "enabled", havingValue = "true", matchIfMissing = true)
final class TrustedClientIpFilter implements GlobalFilter, Ordered {
    private final InternalRequestSignature signatures;   // 内部签名工具
    private final Clock clock;   // 系统时钟

    TrustedClientIpFilter(@Value("${ygh.internal-request.hmac-base64}") String encodedSecret) {
        this(encodedSecret, Clock.systemUTC());
    }

    TrustedClientIpFilter(String encodedSecret, Clock clock) {
        this.clock = clock;
        byte[] secret;
        try {
            secret = Base64.getDecoder().decode(encodedSecret);     // 解码 HMAC 密钥
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("YGH_INTERNAL_REQUEST_HMAC_BASE64 is malformed", malformed);
        }
        try {
            this.signatures = new InternalRequestSignature(secret, clock, Duration.ofSeconds(30));   // 签名有效期 30 秒
        }finally {
            Arrays.fill(secret, (byte) 0); // 擦除敏感内存
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null) {
            return Mono.error(new IllegalStateException("client remote address is unavailable"));
        }
        String clientIp = stripScope(remote.getAddress().getHostAddress());     // 获取真实物体 IP
        String traceId = exchange.getRequest().getHeaders().getFirst(GatewayHeaders.TRACE_ID);
        String requestId = exchange.getRequest().getHeaders().getFirst(GatewayHeaders.REQUEST_ID);
        Instant timestamp = clock.instant();

        // 构造待签名的元数据包： 包含IP， 追踪ID， 请求方法和路径
        var metadata = new InternalRequestSignature.Metadata(
                clientIp, traceId, requestId, exchange.getRequest().getMethod().name(),
                exchange.getRequest().getPath().pathWithinApplication().value(),timestamp);
        String signature = signatures.sign(metadata);   // 生成 HMAC 签名

        var request = exchange.getRequest().mutate().headers(headers -> {
            headers.remove(GatewayHeaders.CLIENT_IP);
            headers.remove(GatewayHeaders.CLIENT_IP_TIMESTAMP);
            headers.remove(GatewayHeaders.CLIENT_IP_SIGNATURE);
            headers.set(GatewayHeaders.CLIENT_IP, clientIp);
            headers.set(GatewayHeaders.CLIENT_IP_TIMESTAMP, Long.toString(timestamp.toEpochMilli()));
            headers.set(GatewayHeaders.CLIENT_IP_SIGNATURE, signature);
        }).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 25;
    }

    private static String stripScope(String address) {
        int scope = address.indexOf('%');   // 过滤 IPv6 的作用域标识符
        return scope < 0 ? address : address.substring(0,scope);
    }
}
