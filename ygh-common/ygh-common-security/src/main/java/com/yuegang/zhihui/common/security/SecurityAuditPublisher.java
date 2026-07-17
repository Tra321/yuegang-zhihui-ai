package com.yuegang.zhihui.common.security;

@FunctionalInterface  // 表示函数式接口
public interface SecurityAuditPublisher {  // 定义安全审计事件发布窗口，解释具体发布逻辑（如 MQ 或 HTTP）
    void publish(SecurityAuditEvent event);  // 定义发布审计事件的抽象方法
}