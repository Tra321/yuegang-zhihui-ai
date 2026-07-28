package com.yuegang.zhihui.common.web;

/** 经过清洗的字段校验详情，可安全地提供给外部 API 响应使用。 */
public record FieldValidationError(String field, String message, Object rejectedValue) { // 定义 Record 数据结构

    public FieldValidationError { // 紧凑构造函数，用于参数验证
        if (field == null || field.isBlank()) { // 检查字段名称是否为空
            throw new IllegalArgumentException("field must not be blank"); // 抛出异常
        }
        if (message == null || message.isBlank()) { // 检查错误消息是否为空
            throw new IllegalArgumentException("message must not be null");
        }
        // 安全清洗：被拒绝的原始值可能包含密码，令牌或个人地址等敏感信息
        // 强制将其设为 null，防止敏感数据通过校验报错信息泄露给外部。
        rejectedValue = null;
    }

    public static FieldValidationError sanitized(String field, String message) { // 静态工厂方法
        return new FieldValidationError(field, message, null); // 返回一个已清洗的错误对象
    }
}
