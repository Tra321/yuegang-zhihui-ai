package com.yuegang.zhihui.gateway;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.util.Set;

/** 安装静态的 "失败优先" 网关流控规则，动态规则持久化则另行配置。 */
@Configuration(proxyBeanMethods = false)
class GatewaySentinelConfiguration {

    @Bean
    Set<GatewayFlowRule> gatewaySentinelRules(
            @Value("${ygh.gateway.sentinel.auth-qps:20}") double authQps,
            @Value("${ygh.gateway.sentinel.auth-burst:5}") int authBurst,
            @Value("${ygh.gateway.sentinel.service-qps:100}") double serviceQps,
            @Value("${ygh.gateway.sentinel.service-burst:10}") int serviceBurst) {
        // 创建规则集合
        Set<GatewayFlowRule> rules = GatewaySentinelRuleSet.create(authQps, authBurst, serviceQps, serviceBurst);
        // 加载到Sentinel 规则管理器中
        GatewayRuleManager.loadRules(rules);
        return rules;
    }

    // 注册网关过滤器，优先级设为 5
    @Bean
    SentinelGatewayFilter sentinelGatewayFilter(Set<GatewayFlowRule> gatewaySentinelRules) {
        return new SentinelGatewayFilter(Ordered.HIGHEST_PRECEDENCE + 5);
    }
}
