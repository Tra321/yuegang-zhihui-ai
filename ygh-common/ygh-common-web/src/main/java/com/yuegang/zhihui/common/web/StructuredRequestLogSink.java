package com.yuegang.zhihui.common.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** 针对清洗后的请求事件的默认结构化应用程序日志接收器。 */
public class StructuredRequestLogSink implements RequestLogSink { // 定义最终类实现接口

    private static final Log LOGGER = LogFactory.getLog("ygh-request"); // 锁定 ygh.request 日志类别

    @Override // 标记实现
    public void accept(RequestLogEvent event) { // 执行日志写入逻辑
        // 将请求事件的所有关键信息拼装成一整行，方便后续类似 ELK 的系统提取索引
        LOGGER.info("request_completed" // 标记该条日志含义为请求完成
                + "trace_Id=" + event.traceId() // 写入追踪 ID
                + "requestId=" + event.requestId() // 写入请求 ID
                + "method=" + event.method() // 写入动作名
                + "path=" + event.path() // 写入 URI
                + "status=" + event.status() // 写入最终 HTTP 状态
                + "durationMs=" + event.durationMs() // 写入耗时
                + "headers=" + event.headers()); // 写入 UA 头等信息
    }
}
