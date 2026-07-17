package com.yuegang.zhihui.gateway;

/** 全局异常转换**/

import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/** 将网关基础设施级别的失败转换为标注你的公共错误契约 JSON **/
public class GatewayFailureWebExceptionHandler implements WebExceptionHandler, Ordered {

    private final GatewaySecurityErrorWriter errorWriter;

    GatewayFailureWebExceptionHandler(GatewaySecurityErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
        // 如果是 Sentinel 限流异常
        if (BlockException.isBlockException(error)) {
            return errorWriter.rateLimited(exchange);
        }

        // 如果是 404 服务未找到异常
        if (error instanceof NotFoundException) {
            return errorWriter.dependencyUnavailable(exchange);
        }

        return Mono.error(error);  // 其他异常向上抛出
    }

    @Override
    public int getOrder() {
        // 优先级设为 -2，确保在默认异常处理之前执行
        return -2;
    }


}
