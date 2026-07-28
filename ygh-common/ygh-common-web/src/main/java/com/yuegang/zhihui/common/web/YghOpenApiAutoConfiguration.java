package com.yuegang.zhihui.common.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;


/** 为每个 Servlet 业务服务提供的共享 OpenAPI (Swagger) 组件配置。 */
@AutoConfiguration // 标记自动配置类
@ConditionalOnClass({OpenAPI.class, OpenApiCustomizer.class}) // 仅在项目中包含 Swagger 相关包时才加载
public class YghOpenApiAutoConfiguration { // 定义类

    public static final String BEARER_AUTH = "bearerAuth"; // 定义认证方案名称常量
    public static final String REQUEST_ID_PARAMETER = "X-Request-Id"; // 定义请求 ID 参数键名

    @Bean // 注入 Bean
    @ConditionalOnMissingBean(name = "yghOpenApiCustomizer") // 如果用户没有自定义文档定制器
    OpenApiCustomizer yghOpenApiCustomizer() { // 定义 Bean
        return this::customize; // 引用当前类的自定义逻辑方法
    }

    private void customize(OpenAPI openAPI) { // 定义文档增强逻辑
        Components components = openAPI.getComponents(); // 获取现有的组件清单
        if (components == null) { // 若清单为空
            components = new Components(); // 初始化组件容器
            openAPI.setComponents(components); // 重新挂载
        }

        // 添加安全方案，配置 Swagger 页面支持发送 JWT Bearer 类型的认证头
        components.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP) // HTTP 类型
                .scheme("bearer") // bearer 架构
                .bearerFormat("JWT")); // 格式说明: JWT

        // 注册本项目标准的数据模型定义，方便前端生成 SDK
        components.addSchemas("ApiResponse", apiResponseSchema());
        components.addSchemas("FieldValidationError", fieldValidationErrorSchema()); // 注册字段校验错误类型
        components.addSchemas("ValidationErrorResponse", validationErrorResponseSchema()); // 注册校验错误详细响应模型

        // 预定义通用的业务错误响应模板，减少各个业务服务重复定义
        addErrorResponse(components, "ValidationError", "参数校验失败", "ValidationErrorResponse");
        addErrorResponse(components, "unauthorized", "未登录或登录已失效", "ApiResponse");
        addErrorResponse(components, "Forbidden", "无权执行该操作", "ApiResponse");
        addErrorResponse(components, "NotFound", "资源不存在", "ApiResponse");
        addErrorResponse(components, "Conflict", "业务状态冲突", "ApiResponse");
        addErrorResponse(components, "RateLimited", "请求过于频繁", "ApiResponse");
        addErrorResponse(components, "DependencyUnavailable", "依赖服务暂时不可用", "ApiResponse");
        addErrorResponse(components, "InternalError", "系统内部错误", "ApiResponse");

        // 为限流响应添加特殊的头字段描述: Retry-After（告知客户端多久后重试）
        components.getResponses().get("RateLimited").addHeaderObject(
                "Retry-After",
                new Header()
                        .description("客户端重试前至少等待的秒数") // 说明
                        .schema(new StringSchema().pattern("[1-9][0-9]*"))); // 限制为正整数正则
        // 在文档界面全局添加 X-Request-ID 请求头输入框
        components.addParameters(REQUEST_ID_PARAMETER, new Parameter()
                .name(TraceIdResolver.REQUEST_ID_HEADER) // 使用 Trace 工具类中定义的标准头名
                .in("header") // 参数位于请求头
                .required(false) // 非强制，确实则后端自动生成
                .description("客户端请求标识: 缺失或不安全时由服务生成") // 说明
                .schema(new StringSchema() // 字符串模式
                            .pattern("[A-Za-z0-9._-]{1,128}") // 合法字符正则限制
                            .maxLength(128))); // 长度限制
    }

    // 以下方法用于定义详细的 JSON 结构模型 (Schema)，供 Swagger 生成 UI 展示。
    private Schema<?> apiResponseSchema() { // 定义统一响应的结构
        Schema<?> schema = new ObjectSchema() // 定义为对象
                .addProperty("code", new StringSchema().description("稳定业务错误码")) // 定义属性 code
                .addProperty("message", new StringSchema().description("可安全展示的消息")) // 定义属性 message
                .addProperty("data", new Schema<>().description("业务数据：失败时通常为空")) // 定义属性 data
                .addProperty("traceId", new StringSchema().description("链路追踪标识")) // 定义属性 traceId
                .addProperty("timestamp", new StringSchema().format("date-time")); // 定义属性 timestamp
        return require(schema, "code", "message", "data", "traceId", "timestamp"); // 设置所有字段为必填
    }

    private Schema<?> fieldValidationErrorSchema() { // 定义校验错误的单个条目模型 no usages
        Schema<?> schema = new ObjectSchema() // 定义对象
                .addProperty("field", new StringSchema()) // 报错字段名
                .addProperty("message", new StringSchema()) // 报错消息内容
                .addProperty("rejectedValue", new Schema<>()
                        .description("始终为空，避免敏感输入泄露")); // 记录：出于安全，不回复错误值
        return require(schema, "field", "message", "rejectedValue"); // 设置必填
    }

    private Schema<?> validationErrorResponseSchema() { // 定义包含多个字段报错的列表模型 no usages
        Schema<?> schema = new ObjectSchema() // 定义对象
                .addProperty("code", new StringSchema()) // 业务码
                .addProperty("message", new StringSchema()) // 提示
                .addProperty("data", new ArraySchema().items( // 定义 data 为数组类型
                        new Schema<>().$ref("#/components/schemas/FieldValidationError"))) // 数组内元素引用上面的条目模型
                .addProperty("traceId", new StringSchema()) // 追踪 ID
                .addProperty("timestamp", new StringSchema().format("date-time")); // 时间戳
        return require(schema, "code", "message", "data", "traceId", "timestamp"); // 必填
    }

    private void addErrorResponse(
            // 辅助方法，快速添加 HTTP 响应模板
            Components components, // 组件类
            String name, // 模板名称
            String description, // 描述
            String schemaName // 关联的数据结构名
    ) { // 开始
        var mediaType = new io.swagger.v3.oas.models.media.MediaType() // 定义媒体类型: JSON
                .schema(new Schema<>().$ref("#/components/schema/" + schemaName)); // 引用结构类型
        components.addResponses(name, new ApiResponse() // 向文档库注册一种响应类型
                .description(description) // 设置文字描述
                .content(new io.swagger.v3.oas.models.media.Content() // 设置返回内容
                        .addMediaType("application/json", mediaType))); // 绑定 JSON 映射
    }

    private Schema<?> require(Schema<?> schema, String... propertyNames) {
        for (String propertyName : propertyNames) { // 循环
            schema.addRequiredItem(propertyName); // 加入必需清单
        }
        return schema; // 返回模式对象
    }
}
