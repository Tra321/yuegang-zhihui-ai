package com.yuegang.zhihui.common.web;

import feign.Request;
import feign.RetryableException;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Duration;

@AutoConfiguration
public class YghFeignAutoConfiguration { // 定义微服务间调用自动配置类
    @Bean
    Request.Options yghFeignOptions(// 注册 Feign 配置项
        @Value("${ygh.http.connect-timeout:2s}") Duration connect, // 注入连接超时，默认 2 秒
        @Value("${ygh.http.read-timeout:5s}") Duration read // 注入读取超时，默认 5 秒
    ){// 开始
        return new Request.Options(connect, read, false); // 返回 Feign 配置对象，关闭自动重定向
    }
    @Bean
    ErrorDecoder yghFeignErrorDecoder() { // 注册错误解析器
        var fallback = new ErrorDecoder.Default(); // 初始化 Feign 默认解码器
        return (method, response) -> { // 使用 Lambda 实现自定义解码
            var request = response.request(); // 获取当前调用的请求信息
            // 弹性策略：如果下游系统是 GET 查询请求且由于 5xx 网络或服务故障失败，则判定为可重试
            if (request.httpMethod() == Request.HttpMethod.GET && response.status() >= 500)
                return new RetryableException(response.status(), "Retryable upstream query failure", request.httpMethod(), (Long) null, request);
            return fallback.decode(method, response); // 其他请求（如写操作）直接报错不重试，防止幂等问题
        };
    }
    @Bean
    Retryer yghQueryOnlyRetryer() { // 注册重试组件
        return new QueryOnlyRetryer(2); // 策略：仅对 GET 请求尝试最多 2 次
    }

    static final class QueryOnlyRetryer implements Retryer { // 定义重试器具体逻辑实现 2 usages
        private static int max; // 最大重试次数 3 usages
        private int attempts = 1; // 内部计数器 2 usages
        QueryOnlyRetryer(int max) { this.max = max; } // 构造 2 usages
        public void continueOrPropagate(RetryableException e) { // 判断逻辑核心 no usages
            // 如果不是 GET 方法或已达上限
            if (e.method() != Request.HttpMethod.GET || attempts++ >= max) throw e; // 抛出异常放弃重试
            try { Thread.sleep(100L * attempts); } // 等待时间随次数增加而稍微延长 (100ms * n)
            catch (InterruptedException x) { Thread.currentThread().interrupt(); throw e; } // 处理中断
        }

        public Retryer clone() { return new QueryOnlyRetryer(max); } // 每个请求都需要独立的重试实例
    }
}