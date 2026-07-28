package com.yuegang.zhihui.common.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/** 为导入了 common-web 的服务提供的 Servlet 请求日志记录自动配置。 */
@AutoConfiguration // 标识为自动配置类
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET) // 仅在 Servlet Web 环境下加载
public class RequestLoggingAutoConfiguration { // 类定义

    @Bean // 注入 Bean n no usages
    @ConditionalOnMissingBean(RequestLogSink.class) // 如果容器中没有日志接收器
    RequestLogSink requestLogSink() { // 则提供一个
        return new StructuredRequestLogSink(); // 默认使用结构化日志输出
    }

    @Bean // 注入 Bean no usages
    @ConditionalOnMissingBean(GlobalExceptionHandler.class) // 如果容器中没有异常处理器，
    GlobalExceptionHandler globalExceptionHandler() { // 则提供一个
        return new GlobalExceptionHandler(); // 返回实例
    }

    @Bean // 注入 Bean no usages
    @ConditionalOnMissingBean(RequestLoggingFilter.class) // 缺少日志过滤器时注入
    RequestLoggingFilter requestLoggingFilter(RequestLogSink sink) { // 传入刚才定义得Sink
        return new RequestLoggingFilter(sink); // 建构过滤器
    }

    @Bean // 注入 Bean no usages
    FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration( // 注册过滤器逻辑
                               RequestLoggingFilter filter // 自动装配过滤器
    ) { // 开始
        var registration = new FilterRegistrationBean<>(filter); // 封装
        registration.setName("yghRequestLoggingFilter"); // 设置过滤器名称
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10); // 设置极高优先级
        registration.setEnabled(true); // 启用
        registration.addUrlPatterns("/**"); // 全链路匹配
        return registration; // 返回
    }

    @Bean // 注入 Bean no usages
    @ConditionalOnMissingBean(AuditLoggingFilter.class) // 缺失审计过滤器时注入
    AuditLoggingFilter auditLoggingFilter() { // 定义
        return new AuditLoggingFilter(); // 实例
    }

    @Bean // 注入 Bean no usages
    FilterRegistrationBean<AuditLoggingFilter> auditLoggingFilterRegistration( // 注册审计
                           AuditLoggingFilter filter // 装配
    ) { // 开始
        var registration = new FilterRegistrationBean<>(filter); // 封装
        registration.setName("yghAuditLoggingFilter"); // 名称
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20); // 优先级略低于请求日志
        registration.setEnabled(true); // 启用
        registration.addUrlPatterns("/api/*", "/internal/*"); // 仅匹配 API 与内部接口
        return registration; // 返回
    }
}
