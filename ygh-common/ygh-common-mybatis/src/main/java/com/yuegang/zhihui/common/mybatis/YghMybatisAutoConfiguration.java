package com.yuegang.zhihui.common.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.yuegang.zhihui.common.core.PageRequest;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.time.Clock;
import java.util.List;

/** 共享的 Mybatis-Plus 审计和受限分页基础设施自动配置类。 */
@AutoConfiguration // 标记为 Spring Boot 自动配置类
public class YghMybatisAutoConfiguration {

    @Bean // 注册审计时钟 Bean
    @ConditionalOnMissingBean // 如果用户没定义，则使用默认时钟
    public Clock auditClock() { return Clock.systemUTC(); }

    @Bean // 注册审计填充处理器
    @ConditionalOnMissingBean(MetaObjectHandler.class) // 如果没定义 MP 填充器，则使用本项目定制的
    public AuditMetaObjectHandler auditMetaObjectHandler(AuditorProvider auditorProvider, Clock auditClock) {
        return new AuditMetaObjectHandler(auditorProvider, auditClock);
    }

    @Bean // 注册 MyBatis Plus 拦截器
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        var pagination = new PaginationInnerInterceptor(); // 初始化分页拦截器
        pagination.setMaxLimit((long) PageRequest.MAX_PAGE_SIZE); // 核心：强制限制单次查询上限（如 100 条），防止 DOM
        pagination.setOverflow(false); // 禁止分页溢出处理

        var interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(pagination); // 添加内部分页拦截器
        return interceptor;
    }

    @Bean // 启动后检查，确保系统中只存在受限的分页配置，严禁开发者通过覆盖配置解除安全限制
    public SmartInitializingSingleton mybatisPaginationGuard(List<MybatisPlusInterceptor> interceptors) {
        return () -> {
            if (interceptors.size() != 1) { // 强制要求只能有一个 MP 拦截器
                throw new IllegalArgumentException("exactly one MybatisPlusInterceptor is required");
            }
            var paginationInterceptors = interceptors.getFirst().getInterceptors().stream()
                    .filter(PaginationInnerInterceptor.class::isInstance)
                    .map(PaginationInnerInterceptor.class::cast)
                    .toList();
            // 校验分页配置是否符合 PageRequest.MAX_SIZE 规定的上限
            boolean boundedPagination = paginationInterceptors.size() == 1
                    && paginationInterceptors.getFirst().getMaxLimit() != null
                    && paginationInterceptors.getFirst().getMaxLimit() >= 1
                    && paginationInterceptors.getFirst().getMaxLimit() <= PageRequest.MAX_PAGE_SIZE
                    && !paginationInterceptors.getFirst().isOverflow();

            if (!boundedPagination) {
                throw new IllegalArgumentException("MybatisPlusInterceptor must include pagination limited to " + PageRequest.MAX_PAGE_SIZE);
            }
        };
    }

    @Bean // 注册 Flyway 安全检测器
    @Order(Ordered.LOWEST_PRECEDENCE) // 保证最低优先级，从而覆盖第三方或 Spring 的默认设置
    public FlywayConfigurationCustomizer yghFlywaySafetyCustomizer() { return new YghFlywaySafetyCustomizer(); }

    @Bean // 注册迁移策略政策 Bean
    public FlywayMigrationPolicy flywayMigrationPolicy() { return new FlywayMigrationPolicy(); }

    @Bean // 注册配置守卫 Bean
    public FlywayConfigurationGuard flywayConfigurationGuard() { return new FlywayConfigurationGuard(); }

    @Bean // 注册历史校验 Bean
    public FlywayHistoryValidator flywayHistoryValidator() { return new FlywayHistoryValidator(); }

    @Bean // 注册核心迁移策略入口
    @Primary // 标记为首选策略，替换 Spring Boot 默认的迁移流程
    public FlywayMigrationStrategy yghFlywayMigrationStrategy(
            FlywayMigrationPolicy migrationPolicy,
            FlywayConfigurationGuard configurationGuard,
            FlywayHistoryValidator historyValidator
    ){
        return new YghFlywayMigrationStrategy(migrationPolicy, configurationGuard, historyValidator);
    }
}
