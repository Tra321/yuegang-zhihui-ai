package com.yuegang.zhihui.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/** 发出不可变的，不含请求体的变更审计记录。凭据和个人敏感负载绝不会被读取。 */
public class AuditLoggingFilter extends OncePerRequestFilter { // 定义审计日志类
    private static final Log LOG = LogFactory.getLog("ygh.audit" ); // 定义静态日志常量，使用 ygh.audit 命名空间
    private static final Pattern ID = Pattern.compile("[0-9]{1,20}" ); // 预编译数字 ID 正则表达式，限制 20 位

    @Override // 标记重写
    protected boolean shouldNotFilter(HttpServletRequest r) { // 定义哪些请求不需要进行审计
        return switch (r.getMethod()) { // 使用增强型 switch 判断 HTTP 方法
            case "POST", "PUT", "PATCH", "DELETE" -> false; // 如果是变更类操作，则“不跳过”（执行过滤）
            default -> true; // 其他（如 GET, OPTIONS）则“跳过”（不执行过滤）
        };
    }

    @Override   // 标记速写
    protected void doFilterInternal(
            HttpServletRequest r, //请求参数
            HttpServletResponse p, //响应参数
            FilterChain c) //过滤链
            throws ServletException, IOException {
        boolean failed=false;   // 定义失败标记位
        try{    // 开启 try 块
            c.doFilter(r, p);   // 将请求传递给过滤链中的下一个元素（如业务 Controller）
        }catch (ServletException | IOException | RuntimeException e) {  // 捕获可能抛出的异常
            failed = true;  // 发生异常，标记为失败
            throw e;    // 重新抛出异常
        }finally {  // 开启最终处理块，无论成功失败都执行
            String user = r.getHeader("X-YGH-User-Id"); // 从自定义请求头中尝试提取用户ID
            if (user == null || !ID.matcher(user).matches()) user = "service-or-anonymous"; // 校验ID，若为空或非法则标记为系统或匿名
            String trace = TraceIdResolver.resolve(r);  // 获取本次请求的 Trace ID（追踪 ID）
            // 打印一行结构化的审计日志，包含用户、方法、路径、状态码和追踪ID
            LOG.info("business_mutation usersId=" + user + " method=" + r.getMethod() + " path=" + r.getRequestURI() + "status=" + (failed ? 500 : p.getStatus()) + " traceId=" + trace);

        }
    }
}
