package com.yuegang.zhihui.common.mq;

public final class RetryableMessageException extends MessageHandlingException { // 可重试的消息异常
    public RetryableMessageException(String failureCode) { // 构造方法
        super(failureCode); // 抛出此异常表示目前临时错误（如第三方 API 超时），MQ 应当继续按退避算法重投
    }
}