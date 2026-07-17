package com.yuegang.zhihui.common.core;

/**
 * 外部 API 响应外完成的稳定跨服务错误代码码
 * */

public enum ErrorCode { // 定义错误码枚举
    SUCCESS("SUCCESS","操作成功"), // 成功状态
    VALIDATION_ERROR("VALIDATION_ERROR","参数校验失败"), // 分角色验证错误
    UNAUTHENTICATED("UNAUTHENTICATED","未登录或登录已失效"), // 身份验证错误
    PERMISSION_DENIED("PERMISSION_DENIED","无权执行该操作"), // 权限不足错误
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND","资源不存在"), // 404
    BUSINESS_CONFLICT("BUSINESS_CONFLICT","业务状态冲突"), // 业务逻辑冲突
    RATE_LIMITED("RATE_LIMITED","请求过于频繁"), // 限流触发
    DEPENDENCY_UNAVAILABLE("DEPENDENCY_UNAVAILABLE","依赖服务不可用"), // 依赖服务不可用 no usages
    INTERNAL_ERROR("INTERNAL_ERROR",    "系统内部错误"); // 兜底 500

    private final String code; // 状态字符串代码
    private final String defaultMessage; // 默认中文提示

    ErrorCode(String code, String defaultMessage) { // 构造函数
        this.code = code; // 赋值代码
        this.defaultMessage = defaultMessage; // 赋值消息
    }

    public String code() { // 获取错误码
        return code; // 返回代码内容
    }

    public String defaultMessage() { // 获取默认消息
        return defaultMessage;  // 返回消息内容
    }
}