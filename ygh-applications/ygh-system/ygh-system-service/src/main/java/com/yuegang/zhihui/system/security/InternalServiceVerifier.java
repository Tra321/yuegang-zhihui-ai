package com.yuegang.zhihui.system.security;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;
import com.yuegang.zhihui.common.security.InternalServiceSignature;
import jakarta.servlet.http.HttpServletRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// 该类用于验证内部服务之间调用的合法性，防止非信任节点伪造服务请求。
public final class InternalServiceVerifier { // 定义最终类：内部服务校验器
    private final InternalServiceSignature signature; // 声明内部服务签名的操作对象

    private InternalServiceVerifier(byte[] s, Clock c) { // 构造函数，传入密钥字节数组和时钟对象
        // 初始化签名工具，设置允许的时间偏差为 30 秒，防止由于网络延迟或时钟不同步导致的校验失败
        signature = new InternalServiceSignature(s, c, Duration.ofSeconds(30));
    }

    public void verify(HttpServletRequest r, String expected) { // 核心方法：验证请求是否来自预期的服务
        try { // 开启异常捕获块
            String service = h(r,"X-YGH-Service"); // 从请求头中获取服务名称标识
            if (!expected.equals(service)) throw fail(); // 如果实际服务名与预期服务名不符，退出验证失败异常
            // 获取请求头中的时间戳并解析为 Instant 瞬时对象
            Instant t = Instant.ofEpochMilli(Long.parseLong(h(r,"X-YGH-Service-Timestamp")));
            // 构造签名元数据：包含服务名、请求方法 (GET/POST 等)、请求路径以及时间戳
            var m = new InternalServerSignature.Metadata(service, r.getMethod(), r.getRequestURI(), t);
            // 调用签名工具验证请求头中的签名 (X-YGH-Service-Signature) 是否有效
            if (!signature.verify(m, h(r,"X-YGH-Service-Signature"))) throw fail();
        }catch (BusinessException e) { // 如果捕获到其他运行期异常
            throw e; // 直接向上抛出
        }catch (RuntimeException e) { // 如果捕获到其他运行，异常
            throw fail();
        }
    }

    private static String h(HttpServletRequest r, String n) { // 私有化辅助方法，获取必填的请求头
        String v = r.getHeader(n); // 根据名称获取头信息
        if (v==null || v.isEmpty()) throw fail(); // 如果头信息不存在或为空，则判定为验证失败
        return v; // 返回头信息内容
    }

    private static BusinessException fail() {
        return new BusinessException(ErrorCode.UNAUTHENTICATED);
    }
}
