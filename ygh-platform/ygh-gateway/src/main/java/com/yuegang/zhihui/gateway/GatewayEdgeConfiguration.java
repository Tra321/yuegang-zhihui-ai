package com.yuegang.zhihui.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/** 绑定受限制的请求策略，不向外暴露可变的配置状态。 **/
@Configuration(proxyBeanMethods = false)
public class GatewayEdgeConfiguration {

    GatewayRequestGuardFilter gatewayRequestGuardFilter(
            @Value("${ygh.gateway.request.max-size:2MB}") DataSize requestMaxSize, // 默认普通请求2M
            @Value("${ygh-gateway.request.upload-max-size:50MB}") DataSize uploadMaxSize, // 默认上传50M
            @Value("${ygh-gateway.request.upload-path}") String uploadPaths, // 逗号默认路径
            GatewaySecurityErrorWriter errorWriter) {
        // 初始化请求防护过滤器
        return new GatewayRequestGuardFilter(
                requestMaxSize.toBytes(), uploadMaxSize.toBytes(), parsePaths(uploadPaths), errorWriter);
    }

    // 辅助工具：解析逗号分隔的路径字符串为Set
    static Set<String> parsePaths(String configuredPaths) {
        var paths = new LinkedHashSet<String>();
        Arrays.stream(configuredPaths.split(","))
                .map(String::trim)
                .filter(String::isEmpty)
                .forEach(paths::add);
        return Set.copyOf(paths);
    }
}