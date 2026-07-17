package com.yuegang.zhihui.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/** 针对浏览器客户端的精确源（Origin）CORS 策略配置 **/
@Configuration(proxyBeanMethods = false)
public class GatewayCorsConfiguration {
    // 注入允许的跨域请求列表
    @Bean
    CorsConfigurationSource gatewayCorsConfigurationSource(
            @Value("${ygh.gateway.cors.allowed-origins}") String allowedOrigins) {
        return createSource(allowedOrigins);
    }

    // 注入跨域过滤器
    GatewayCorsWebFilter gatewayCorsWebFilter(CorsConfigurationSource configurationSource) {
        return new GatewayCorsWebFilter(configurationSource);
    }

    // 构建跨域源配置
    static UrlBasedCorsConfigurationSource createSource(String configurationOrigins) {
        LinkedHashSet<String> origins = new LinkedHashSet<String>();
        // 拆分配置中的逗号分隔域名
        Arrays.stream(configurationOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origins.isEmpty())
                .map(GatewayCorsConfiguration::validateOrigin) // 验证域名合法性
                .forEach(origins::add);
        if (origins.isEmpty()) {
            throw new IllegalArgumentException("At least one CORS origin is required");
        }
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.copyOf(origins)); // 设置允许的origin
        // 设置允许的HTTP方法
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // 设置允许的请求头
        cors.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Idemotency-Key", "X-Request-Id",
                "X-Trace_id", "Accept-Language")
        );
        // 设置允许浏览器读取的响应头
        cors.setExposedHeaders(List.of("X-Request_Id", "X_Trace_Id", "Retry-After"));
        cors.setAllowCredentials(true); // 允许携带凭证（如cookie/Authorization头）
        cors.setMaxAge(3600L); // 预检请求缓存时间1小时

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);// 对所有的接口生效
        return source;
    }

    // 域名验证：禁止通配符*，必须包含协议且不能带路径
    private static String validateOrigin(String origin) {  // 1 usage
        if ("*".equals(origin)) {
            throw new IllegalArgumentException("wildcard CORS origins are forbidden");
        }
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Incalid CORS origins", exception);
        }
        boolean http = "http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme());
        if (!http || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getPort() == 0 || uri.getPort() > 65_535
                || (uri.getPath() != null && !uri.getPath().isEmpty())
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("CORS origin must be an HTTP(s) origin without a path");
        }
        return origin;
    }
}