package com.yuegang.zhihui.gateway;

import com.yuegang.zhihui.common.security.CurrentUserPrincipal;
import com.yuegang.zhihui.common.security.InternalUserContextSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

//受信任用户上下文传播
/**
 * 仅从服务器认证后的交换属性中提取身份信息， 并签名传播到下游服务。
 */
@Component  // 标记该类为 Spring 组件，使其能被自动扫描并注册到容器中
public class TrustedUserContextFilter implements GlobalFilter, Ordered {    // 定义最终类，实现全局过滤器接口和排序接口
    private static final int MAX_AUTHORITIES = 128;     // 定义单个请求允许携带的最大权限/角色数量
    private static final int MAX_AUTHORITY_HEADER_LENGTH = 4096;    // 定义权限相关请求头的最大字符长度限制
    private static final Pattern SAFE_USER_TO = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");   // 预编译用户 ID 的安全正则模式
    private static final Pattern SAFE_AUTHORITY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}");     // 预编译权限/角色名称的安全正则模式
    private final InternalUserContextSignature signatures;  // 声明内部用户上下文签名工具，用于防篡改
    private final Clock clock;  // 声明时钟对象，用于生成签名时的时间戳

    // 默认构造函数： 主要用于开发环境或无配置时的降级处理
    TrustedUserContextFilter() { this(Base64.getEncoder().encodeToString(new byte[32]), Clock.systemUTC()); }

    // 生产环境使用的构造函数，通过Spring 注入配置文件中的 HMAC 密钥
    @Autowired      // 标记该构造函数由 Spring 自动注入依赖
    TrustedUserContextFilter(@Value("${ygh.internal-request.hmac-base64}") String encodedSecret) {
        this(encodedSecret, Clock.systemUTC()); // 调用本类的三参数构造函数
    }

    // 核心逻辑构造函数，初始化签名器和时钟
    TrustedUserContextFilter(String encodedSecret, Clock clock) {
        byte[] secret; // 定义字节数组用户存储解码后的密钥
        try { secret = Base64.getDecoder().decode(encodedSecret); } // 尝试对 Base64 编码的密钥进行解码
        catch (IllegalArgumentException malformed) { throw new IllegalStateException("internal HMAC secret is malformed", malformed); } // 解码失败后抛出状态异常
        try{ this.signatures = new InternalUserContextSignature(secret, clock, Duration.ofSeconds(30));} // 初始化签名工具，设置 30 秒的时间容量
        finally { Arrays.fill(secret, (byte) 0);}   // 关键安全操作：清空内存中的密钥字节数组，防止被内存快照泄露
        this.clock = clock;     // 初始化时钟
    }

    @Override   // 实现全局过滤器的核心过滤方法
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 从属性中获取刚才 JwtPrincipalBridgeFilter 塞进去的 principal 对象
        // 从当前请求属性中获取已通过认证的用户主体信息（由前置过滤器存入）
        CurrentUserPrincipal principal = exchange.getAttribute(GatewaySecurityAttributes.AUTHENTICATION_PRINCIPAL);

        // 如果主体不为空，校验并获取用户 ID， 否则为 null
        String userId = principal == null ? null : validatedUsreId(principal.userId());
        // 如果主体不为空，将角色集合编码为逗号分隔的字符串，否则为空字符串
        String roles = principal == null ? "" : encodedAuthorities(principal.roles(),"roles");
        // 如果主体不为空，将权限集合编码为逗号分隔的字符串：否则为空字符串
        String permissions = principal == null ? "" : encodedAuthorities(principal.permissions(),"permissions");

        Instant timestamp = clock.instant();    // 获取当前瞬时时间戳用于签名

        //关键点：对用户ID、角色、权限、请求ID、路径进行统一哈希签名，下游服务会重新校验此签名
        String signature = principal == null ? null : signatures.sign(new InternalUserContextSignature.Metadata(
                userId, split(roles), split(permissions),   // 传入身份信息
                exchange.getRequest().getHeaders().getFirst(GatewayHeaders.TRACE_ID),   // 关联 TraceId
                exchange.getRequest().getHeaders().getFirst(GatewayHeaders.REQUEST_ID), // 关联 RequestId
                exchange.getRequest().getMethod().name(),                               // 关联请求方法
                exchange.getRequest().getPath().pathWithinApplication().value(), timestamp));   // 关联请求路径和时间戳

        // 开始转换请求头（ Mutation）， 确保信息安全传播
        var request = exchange.getRequest().mutate().headers(headers -> {
            // 关键安全动作：移除所有由客户端发来的潜在伪造身份请求头（零信任策略）
            headers.remove(GatewayHeaders.USER_ID);
            headers.remove(GatewayHeaders.ROLES);
            headers.remove(GatewayHeaders.PERMISSIONS);
            headers.remove(GatewayHeaders.USER_CONTEXT_TIMESTAMP);
            headers.remove(GatewayHeaders.USER_CONTEXT_SIGNATURE);

            // 如果用户已登录，注入经过网关计算并加签的“可信身份头”
            if (principal != null) {
                headers.set(GatewayHeaders.USER_ID, userId);    // 注入可信用户 ID
                if (!roles.isEmpty()) {
                    headers.set(GatewayHeaders.ROLES, roles);   // 注入角色列表
                }
                if (!permissions.isEmpty()) {
                    headers.set(GatewayHeaders.PERMISSIONS,permissions);    // 注入权限列表
                }
                headers.set(GatewayHeaders.USER_CONTEXT_TIMESTAMP, Long.toString(timestamp.toEpochMilli()));    // 注入生成签名时的时间戳
                headers.set(GatewayHeaders.USER_CONTEXT_SIGNATURE, signature);      // 注入 HMAC 签名，下游服务将用其验算
            }
        }).build(); // 构建新的请求对象

        // 将修改后的请求继续传递给过滤链中的下一个节点
        return chain.filter(exchange.mutate().request(request).build());
    }

    // 辅助方法: 将逗号分隔的字符串拆分为列表
    private static List<String> split(String encoded) {
        return encoded.isEmpty() ? List.of() : List.of(encoded.split(",", -1)); // 为空返回空列表，否则执行拆分
    }

    @Override   // 定义过滤器在链中的执行顺序
    public int getOrder() {
        // 设置为最高优先级 + 38，确保在身份认证之后、请求发送到下游之前执行
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }

    // 校验用户 ID 的安全性， 防止注入非法字符
    private static String validatedUsreId(String userId) {
        if (!SAFE_USER_TO.matcher(userId).matches()) {    // 使用正则校验
            throw new IllegalArgumentException("trusted userId contains unsafe characters"); // 校验失败抛出异常
        }
        return userId;  // 返回安全的 ID
    }

    // 将权限/角色集合编码为排序后的安全字符串
    private static String encodedAuthorities(Set<String> authorities, String fieldName) {
        if (authorities.size() > MAX_AUTHORITIES) { // 检查数量是否超限
            throw new IllegalArgumentException(fieldName + "exceeds authority count limit");    // 超限报错
        }
        String encoded = authorities.stream()   // 开启流处理
                .peek(value -> {    // 检查每一个元素的安全性
                    if (!SAFE_AUTHORITY.matcher(value).matches()) {
                        throw new IllegalArgumentException(fieldName + "contains an unsafe authority"); // 元素非法报错
                    }
                })
                .sorted()   // 字母序排序，确保生成规范化（Cananical) 的字符串以供签名
                .collect(Collectors.joining(","));  // 使用逗号连接
        if (encoded.length() > MAX_AUTHORITY_HEADER_LENGTH) {   // 检查总长度是否超限
            throw new IllegalArgumentException(fieldName + "exceeds header length limit");  // 超长报错
        }
        return encoded; // 返回编码后的字符串
    }
}
