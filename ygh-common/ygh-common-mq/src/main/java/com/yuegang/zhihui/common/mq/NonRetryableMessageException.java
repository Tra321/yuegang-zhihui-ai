package com.yuegang.zhihui.common.mq;

public final class NonRetryableMessageException extends MessageHandlingException { // 不可挽回的消息异常
    public NonRetryableMessageException(String failureCode) { // 构造方法
        super(failureCode); // 一旦抛出此类异常，消息将直接传入死信，不会尝试下次投递（节省资源）
    }
}