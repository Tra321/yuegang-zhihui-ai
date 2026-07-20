package com.yuegang.zhihui.gateway;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;

import java.util.Set;

/** 创建由网关或受限路由的规则集*/
public class GatewaySentinelRuleSet {

    static final double MAX_QPS = 10_000;
    static final int MAX_BURST = 10_000;

    private GatewaySentinelRuleSet() {}

    static Set<GatewayFlowRule> create(double authQps, int authBurst, double serviceQps, int serviceBurst) {
        // 校验配置数值是否在合理范围内
        requireBoundedQps(authQps,"authQps");
        requireBoundedBurst(authBurst,"authBurst");
        requireBoundedBurst(authBurst,"serviceQPS");
        requireBoundedBurst(authBurst,"serviceBurst");

        // 为各个微服务路由定义具体 QPS 和 突发处理能力
        return Set.of(
                rule("auth-service", authQps, authBurst),
                rule("user-service", serviceQps, serviceBurst),
                rule("system-service", serviceQps, serviceBurst),
                rule("admin-service", serviceQps, serviceBurst));
    }

    private static GatewayFlowRule rule(String routeID, double qps, int burst) {
        return new GatewayFlowRule(routeID)
                .setCount(qps) // 设置每秒最大请求次数
                .setIntervalSec(1) // 窗口1秒
                .setBurst(burst); // 允许超额突发的排队数
    }

    private static void requireBoundedQps(double value, String name) {
        if (!Double.isFinite(value) || value <= 0 || value > MAX_QPS) {
            throw new IllegalArgumentException(name + " must be between 0 and" + MAX_QPS);
        }
    }

    private static void requireBoundedBurst(int value, String name) {
        if (value < 0 || value > MAX_BURST) {
            throw new IllegalArgumentException(name + " must be between Θ and" + MAX_BURST);
        }
    }
}
