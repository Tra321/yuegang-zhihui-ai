package com.yuegang.zhihui.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
// JWT 桥接过滤器
/** 将认证通过后资源服务器 JWT 桥接到受信任的网关属性（Gateway Attributes）中 */
@Component
final class JwtPrincipalBridgeFilter implements GlobalFilter, Ordered {

    private final JwtPrincipalMapper principalMapper;

    JwtPrincipalBridgeFilter(JwtPrincipalMapper principalMapper) {
        this.principalMapper = principalMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 从响应式安全上下文中获取认证信息
        return ReactiveSecurityContextHolder.getContext()
            .map(context -> context.getAuthentication())
            .filter(auth -> auth !=null && auth.isAuthenticated())
            .map(auth -> auth.getPrincipal())
            .filter(Jwt.class::isInstance)
            .cast(Jwt.class)
            // 如果存在合法JWT,解析并存入应用属性
            .doOnNext(jwt -> exchange.getAttributes().put(
                    GatewaySecurityAttributes.AUTHENTICATION_PRINCIPAL,
                    principalMapper.map(jwt)))
            .then(Mono.defer(() -> chain.filter(exchange)));
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
