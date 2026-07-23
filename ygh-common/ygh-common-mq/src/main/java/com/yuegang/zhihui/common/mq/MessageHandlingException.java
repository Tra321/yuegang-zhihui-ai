package com.yuegang.zhihui.common.mq;

import java.util.regex.Pattern;

public abstract class MessageHandlingException extends RuntimeException{
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,63}"); // 错误码正则要求
    private final String failureCode; // 内部错误识别码

    protected MessageHandlingException(String failureCode) { // 构造方法开始
        super(validate(failureCode)); // 校验后传给父类消息
        this.failureCode = failureCode; // 赋值
    }

    public final String failureCode() { return failureCode; } // 获取稳定码方法
    private static String validate(String code) { // 校验静态方法
        if (code == null || !CODE.matcher(code).matches()) { // 正则匹配
            throw new IllegalArgumentException("failureCode must not be a stable uppercase code"); // 抛错
        }
        return code; // 返回合法的码
    }
}
