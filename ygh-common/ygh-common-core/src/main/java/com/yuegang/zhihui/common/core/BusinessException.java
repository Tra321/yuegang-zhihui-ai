package com.yuegang.zhihui.common.core;

import java.util.Objects;

/**
 * 预期的任务失败，可被翻译为稳定的外部错误码
 */
public class BusinessException extends RuntimeException { // 继承运行时异常类
    private final ErrorCode errorCode; // 内部持有的错误码枚举

    public BusinessException(ErrorCode errorCode) { // 仅传入错误码的构造函数
        this(errorCode, errorCode.defaultMessage()); // 调用全参构造，使用默认信息
    }

    public BusinessException(ErrorCode errorCode, String message) { // 传入错误码和自定义消息
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null"); // 强制要求错误码非空
    }

    public ErrorCode errorCode() { // 获取错误码的方法 no usages
        return errorCode; // 返回持有的错误码
    }
}