package com.yuegang.zhihui.gateway;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/*
 *建立相关的关联标识符，并在身份验证执行前移除所有客户端提供的内部身份头，防止Header注入攻击
 */
@Component //声明为Spring组件
public class CorrelationIdFilter implements WebFilter, Ordered { // 实现WebFilter接口和Ordered接口

    // 定义安全的ID正则表达式，只允许字母数字和特定的分隔符
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{7,63}");
    // 内部ID生成逻辑供应者
    private final Supplier<String> idGenerator;

    // 默认构造：生成去掉横线的UUID作为ID
    CorrelationIdFilter() {
        this(() -> UUID.randomUUID().toString().replace("-", ""));
    }
    // 允许注入自定义生成逻辑的构造函数
    CorrelationIdFilter(Supplier<String> idGenerator) {
        this.idGenerator = Objects.requireNonNull(idGenerator,"idGenerator must not be null");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 从Header获取或生成TraceId(追踪ID)
        String traceId = resolveId(exchange.getRequest().getHeaders().getFirst(GatewayHeaders.TRACE_ID));
        // 从Header获取生成RequestId(请求唯一ID)
        String requestId = resolveId(exchange.getRequest().getHeaders().getFirst(GatewayHeaders.REQUEST_ID));

        //对原始请求进行装饰和修饰
        var request = exchange.getRequest().mutate().headers(headers -> {
            headers.set(GatewayHeaders.TRACE_ID, traceId); // 设置标准追踪ID
            headers.set(GatewayHeaders.REQUEST_ID, requestId);//设置标准化请求ID
            headers.remove(GatewayHeaders.USER_ID); //行移除外部传入的内部用户ID
            headers.remove(GatewayHeaders.ROLES); //行移除外部传入的权限角色
            headers.remove(GatewayHeaders.PERMISSIONS); //行移除外部传入的权限点
            headers.remove(GatewayHeaders.USER_CONTEXT_TIMESTAMP); //移除时间戳防重放头
            headers.remove(GatewayHeaders.USER_CONTEXT_SIGNATURE); //移除签名头
        }).build();

        // 构造新的交换对象
        var correlated = exchange.mutate().request(request).build();
        // 将ID存入交换对象属性，方便后续逻辑获取
        correlated.getAttributes().put(GatewaySecurityAttributes.TRACE_ID, traceId);
        correlated.getAttributes().put(GatewaySecurityAttributes.REQUEST_ID, requestId);
        // 在响应头中写入ID，方便前端定位后端日志
        correlated.getResponse().getHeaders().set(GatewayHeaders.TRACE_ID, traceId);
        correlated.getResponse().getHeaders().set(GatewayHeaders.REQUEST_ID, requestId);

        // 在响应提交前回调：确保响应头包含这些标识
        correlated.getResponse().beforeCommit(() -> {
            correlated.getResponse().getHeaders().set(GatewayHeaders.TRACE_ID, traceId);
            correlated.getResponse().getHeaders().set(GatewayHeaders.REQUEST_ID, requestId);
            return Mono.empty();
        });

        // 将ID写入Reactor上下文，供响应式链路使用
        return chain.filter(correlated).contextWrite(context -> context
                .put(GatewaySecurityAttributes.TRACE_ID, traceId)
                .put(GatewaySecurityAttributes.REQUEST_ID, requestId));
    }

    @Override
    public int getOrder() {
        // 设置极高优先级，排在所有过滤器最前面
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
    // 校验传入的ID是否合法，如果不合法则生成新的
     private String resolveId(String candidate){
        if (candidate != null && SAFE_ID.matcher(candidate).matches()) {
        return candidate;
        }
        String generated = idGenerator.get();
        if (generated == null || !SAFE_ID.matcher(generated).matches()) {
            throw new IllegalStateException("Correlation ID generator returned an unsafe value");
        }
         return generated;
     }
}