package com.yuegang.zhihui.common.mq;

public final class MessageInfrastructureException extends RuntimeException { // 基础设施（如果数据库挂了）引发的不可控异常
    public MessageInfrastructureException(String message, Throwable cause) { // 带原因的构造
        super(message, cause);
    }
}
