package com.yuegang.zhihui.gateway;



import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
/** 响应式安全核心配置*/
/** 网关全局 API 路由的“闭合式失败”响应式安全配置*/
@Configuration(proxyBeanMethods = false)
@EnableWebFluxSecurity // =启用响应式安全检查
public class GatewaySecurityConfiguration {

    //配置基于公钥集的JWT 解码器
    @Bean
    ReactiveJwtDecoder gatewayJwtDecoder(
            @Value("${ygh.security.jwt.issuer}") String issuer,
            @Value("${ygh.security.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${ygh.security.jwt.audience}") String audience
    ) {
        var decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(GatewayJwtValidators.create(issuer, audience));
        return decoder;
    }

    //配置转换器：将 JWT 的 Claims （声明） 转换为Spring Security 的权限对象
    @Bean
    Converter<Jwt, Mono<AbstractAuthenticationToken>> gatewayJwtAuthenticationConverter(
            JwtPrincipalMapper principalMapper
    ){
        return jwt -> {
            var principal = principalMapper.map(jwt);
            var authorities = new LinkedHashSet<SimpleGrantedAuthority>();
            //转换权限点： 加前缀 ROLE_
            principal.roles().stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .forEach(authorities::add);
            //转换权限点：加前缀 PERM_
            principal.permissions().stream()
                    .map(permission -> new SimpleGrantedAuthority("PERM_" + permission))
                    .forEach(authorities::add);
            return Mono.just(new JwtAuthenticationToken(jwt, authorities, principal.userId()));
        };
    }

    //核心安全过滤器：配置谁能访问哪些接口
    @Bean
    SecurityWebFilterChain gatewaySecurityWebFilterChain(
            ServerHttpSecurity http,
            GatewaySecurityErrorWriter errorWriter,
            Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter
    ) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable) //禁用 CSRF
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable) //禁用Basic 认证
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable) //禁用表单登录
                .logout(ServerHttpSecurity.LogoutSpec::disable) //禁用登出
                //设置为无状态服务，不存储对话
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .authorizeExchange(exchange -> exchange
                        //1.旅行健康检查，文档和Swagger路径
                        .pathMatchers("/actuator/health/**","/v3/api-docs/**","/swagger-ui/**","/livez","/readyz").permitAll()
                        //2.旅行认证相关的注册、登录、刷新令牌接口
                        .pathMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh","/api/v1/auth/password-rset/**").permitAll()
                        //3.旅行图形验证码
                        .pathMatchers(HttpMethod.GET,"/api/v1/auth/captcha").permitAll()
                        //4.旅行只读性的公共业务接口（商品、类目、指示检索等）
                        .pathMatchers(HttpMethod.GET,"/api/v1/products/**","/api/v1/product-categories","/api/v1/product-brands","/api/v1/knowledge/dpcument/**","/api/v1/knowledge/search").permitAll()
                        //5.限制：仅限员工和管理员访问的业务
                        .pathMatchers("/api/v1/organization/**","/api/v1/training/**").hasAnyRole("EMPLOYEE","ADMIN")
                        //6.限制：仅限超级管理员访问的后台管理接口
                        .pathMatchers("/api/v1/auth/admin/**","/api/v1/admin/**","/api/v1/system/**","/api/v1/roles/**","/api/v1/permissions/**").hasRole("ADMIN")
                        //7.兜底： 其余所有 /api/v1/** 请求必须经过认证
                        .pathMatchers("/api/v1/**").authenticated()
                        //8.绝对防御：任何未在上面列出的请求全部拒绝
                        .anyExchange().denyAll())
                        //异常处理逻辑（返回统一的 401/403 JSON）
                        .exceptionHandling(errors -> errors
                                .authenticationEntryPoint((exchange, ignored) -> errorWriter.unauthenticated(exchange))
                                .accessDeniedHandler((exchange, ignored) -> errorWriter.accessDenied(exchange)))
                                .oauth2ResourceServer(resourceServer -> resourceServer
                                        .authenticationEntryPoint((exchange, ignored) -> errorWriter.unauthenticated(exchange))
                                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                                .build();
    }
}
