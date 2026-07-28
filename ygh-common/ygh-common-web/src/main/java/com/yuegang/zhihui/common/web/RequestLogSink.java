package com.yuegang.zhihui.common.web;

/** 接收经过清洗的结构化请求完成事件。 */
@FunctionalInterface    // 标识这是一个函数式接口
public interface RequestLogSink { // 定义接口

    void accept(RequestLogEvent event); // 声明单一接收方法，传入日志事件对象
}