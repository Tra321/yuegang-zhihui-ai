package com.yuegang.zhihui.gateway;

import org.springframework.core.Ordered;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.CorsWebFilter;

final class GatewayCorsWebFilter extends CorsWebFilter implements Ordered {

    GatewayCorsWebFilter(CorsConfigurationSource configurationSource) {
        super(configurationSource);
    }

    @Override
    public int getOrder() {
        // 优先级为12，紧跟在追踪ID过滤之后
        return Ordered.HIGHEST_PRECEDENCE + 12;
    }
}
