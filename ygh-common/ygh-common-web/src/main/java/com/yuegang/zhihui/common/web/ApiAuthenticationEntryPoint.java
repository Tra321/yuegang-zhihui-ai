package com.yuegang.zhihui.common.web;

import com.yuegang.zhihui.common.core.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/** 针对未通过身份验证的 Servlet 安全请求提供的标准 JSON 响应入口点。 */
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint { // 定义入口类

    @Override // 重写标记
    public void commence( // 当检测到匿名访问受保护资源时调用的方法
                          HttpServletRequest request, // 请求参数
                          HttpServletResponse response, // 响应参数
                          AuthenticationException authException // 异常信息
    ) throws IOException, ServletException { // 异常声明
        // 调用响应写入器，返回 401 状态码和"未认证"业务码
        try {
            SecurityApiResponseWriter.write(request, response, 401, ErrorCode.UNAUTHENTICATED);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
