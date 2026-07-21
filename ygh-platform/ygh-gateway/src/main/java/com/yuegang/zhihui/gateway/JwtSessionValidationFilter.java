package com.yuegang.zhihui.gateway;

// 黑名单/会话校验

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

//TODO 未完善
/** 如果启用，则对 JWT 进行 Redis 会话有效性验证（支持主动踢人、强制下线）。 */
@Component
@ConditionalOnProperty(prefix = "ygh.security.session-validation", name = "enabled", havingValue = "true")
final class JwtSessionValidationFilter implements GlobalFilter, Ordered {
    private final ReactiveSessionValidator sessions;     // 依赖公共模块的 redis 校验器
    private final GatewaySecurityErrorWriter errors;

    JwtSessionValidationFilter(ReactiveSessionValidator sessions, GatewaySecurityErrorWriter errors) {
        this.sessions = sessions;
        this.errors = errors;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(auth -> auth != null && auth.isAuthenticated())
                .map(auth -> auth.getPrincipal())
                .filter(Jwt.class::isInstance)
                .cast(Jwt.class)
                //  只有已登录用户才走验证逻辑，未登录用户跳过此过滤器（交由安全配置拦截）
                .flatMap(jwt -> validate(jwt, exchange, chain).thenReturn(true))
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange).thenReturn(false)))
                .then();
    }

    private Mono<Void> validate(Jwt jwt, ServerWebExchange exchange, GatewayFilterChain chain) {
        long accountId;
        try {
            // 获取JWT 中的 account_id声明
            accountId = Long.parseLong(jwt.getClaimAsString("account_id"));
        } catch (RuntimeException invalidClaim) {
            return errors.unauthenticated(exchange);    // 解析失败认为无效
        }
        // 调用分布式 Redis 查找该账号的 JTI 是否在黑名单或已失效
        return sessions.valid(accountId, jwt.getId())
                .map(valid -> valid ? SessionCheck.ACTIVE : SessionCheck.REJECTED)
                .onErrorReturn(SessionCheck.UNAVAILABLE)
                .flatMap(check -> switch (check) {
                    case ACTIVE -> chain.filter(exchange);      // 会话正常，放行
                    case REJECTED -> errors.unauthenticated(exchange);      // 会话失效，拦截
                    case UNAVAILABLE -> errors.dependencyUnavailable(exchange);     // Redis 挂了，报错
                });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 15;
    }
    private enum SessionCheck {
        ACTIVE,
        REJECTED,
        UNAVAILABLE
    }
}