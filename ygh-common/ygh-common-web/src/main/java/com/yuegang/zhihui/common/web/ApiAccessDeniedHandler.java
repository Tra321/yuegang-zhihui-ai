package com.yuegang.zhihui.common.web;

import com.yuegang.zhihui.common.core.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** 针对被拒绝的 Servlet 安全请求提供的标准。 */
public class ApiAccessDeniedHandler implements AccessDeniedHandler { // 定义最终类实现权限拒绝接口

    @Override // 标记重写父类方法
    public void handle(  // 实现处理权限拒绝的核心方法
                         HttpServletRequest request, // 传入请求对象
                         HttpServletResponse response, // 传入响应对象
                         AccessDeniedException accessDeniedException // 传入具体的拒绝异常
    ) throws IOException, ServletException { // 声明方法可能抛出的异常
        try {
            SecurityApiResponseWriter.write(request, response, 403, ErrorCode.PERMISSION_DENIED);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
