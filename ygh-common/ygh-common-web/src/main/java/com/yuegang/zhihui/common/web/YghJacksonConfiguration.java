package com.yuegang.zhihui.common.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/** HTTP 和消息适配器公用的共享 Jackson3 配置。 */
@Configuration(proxyBeanMethods = false)  // 设置配置类，关闭代理
public class YghJacksonConfiguration { // 定义类

    @Bean
        // 注入 Bean
    JsonMapperBuilderCustomizer yghJsonMapperBuilderCustomizer() {  // 定义定制器逻辑
        return YghJacksonConfiguration::customize;  // 引用内部静态定制方法
    }

    /** 未尚未迁移至 Jackson 3 的领域和 MQ 适配器提供兼容性映射器。 */
    @Bean // 注入 Bean
    @ConditionalOnMissingBean(com.fasterxml.jackson.databind.ObjectMapper.class) // 如果 Spring 容器还没定义老版 Mapper
    com.fasterxml.jackson.databind.ObjectMapper legacyObjectMapper() { // 定义方法
        return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(); // 创建并自动注册 Java 时间等级块
    }

    public static JsonMapper createMapper() { // 提供的内部工具类（如 SecurityWrite）使用的静态创建方法
        JsonMapper.Builder builder = JsonMapper.builder(); // 开启构建器
        customize(builder); // 应用项目全局定制规则
        return builder.build(); // 生成最终映射器对象
    }

    private static void customize(JsonMapper.Builder builder) {  // 全局一致性配置逻辑
        // 关键点：禁用“根据上下文时区调整日期”
        // 确保数据库存入的 ISO 格式时间与前端显示的一致，防止因多网关节点时区不一致导致的时间偏差。
        builder.disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }
}
