package com.yuegang.zhihui.common.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public final class InternalUserContextSignature { // 定义包含用户上下文（身份信息）的签名校验
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}"); // 安全ID模式：增加了冒号的支持（用于 URN 格式）
    private static final Pattern SAFE_AUTHORITY = Pattern.compile("[A-Za-z][A-Za-z0-9:_-]{0,127}"); // 权限名正则：字母开头，允许特定字符
    private final SecretKeySpec key; // 秘钥规格对象
    private final Clock clock; // 系统时钟
    private final Duration maximumSkew; // 最大时间窗口偏差

    public InternalUserContextSignature(byte[] secret, Clock clock, Duration maximumSkew) {
        if (secret == null || secret.length < 32) throw new IllegalArgumentException("identity secret must contain at least 32 bytes"); // 秘钥长度必须满足
        this.key = new SecretKeySpec(Arrays.copyOf(secret, secret.length), "HmacSHA256"); // 初始化算法规范
        this.clock = Objects.requireNonNull(clock); // 初始化时钟
        this.maximumSkew = Objects.requireNonNull(maximumSkew); // 初始化偏差设定
        if (maximumSkew.compareTo(Duration.ofSeconds(1)) < 0 || maximumSkew.compareTo(Duration.ofMinutes(5)) > 0) { //偏差范围合法性检查
            throw new IllegalArgumentException("maximum must be between 1 and 5 minutes");
        }
    }

    public String sign(Metadata metadata) {return HexFormat.of().formatHex(hmac(canonical(metadata))); // 执行规范化、哈希、及16进制转换
    }

    public boolean verify(Metadata metadata, String signature) { // 验证方法
        if (signature == null || !signature.matches("[0-9a-f]{64}")) return false; // 签名格式不正确返回失败
        if (Duration.between(metadata.timestamp(),clock.instant()).abs().compareTo(maximumSkew) > 0) return false; // 时间窗口验证失败返回false
        byte[] expected = hmac(canonical(metadata)); // 计算本地预期哈希
        byte[] actual; // 声明实际哈希字节
        try { actual = HexFormat.of().parseHex(signature); } // 解析传入签名
        catch (IllegalArgumentException malformed) { return false; } // 解析异常返回失败
        try { return MessageDigest.isEqual(expected, actual);} // 常数时间哈希比对
        finally { Arrays.fill(expected, (byte) 0); Arrays.fill(actual, (byte) 0); } // 擦除敏感数据内存
    }

    private byte[] canonical(Metadata value) { // 定义上下文专用的规范化拼接
        return String.join("\n", value.userId(), String.join(",", value.roles()),
                String.join(",", value.permissions()), value.traceId(),value.requestId(),
                value.method(), value.path(),
                Long.toString(value.timestamp().toEpochMilli())
        ).getBytes(StandardCharsets.UTF_8); // 转换为字节流
    }

    private byte[] hmac(byte[] value) { // HMAC 核心算法封装 2 usages
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(key); return mac.doFinal(value); } // 获取实例并计算
        catch (GeneralSecurityException impossible) { throw new IllegalArgumentException(impossible); } // 异常处理
        finally { Arrays.fill(value, (byte) 0); } // 内存安全擦除
    }

    public record Metadata(String userId, List<String> roles, List<String> permissions, // 身份上下文元数据定义
                           String traceId, String requestId, String method, String path, Instant timestamp) { // 各种id及路径
        public Metadata { // 构造逻辑 no usages
            requireSafe(userId, SAFE_ID, "userId"); // 校验用户 ID 安全性
            roles = normalized(roles, "roles"); // 规范化角色列表（排序去重）
            permissions = normalized(permissions, "permissions"); // 规范化权限列表
            requireSafe(traceId, SAFE_ID, "traceId"); // 校验链路 ID 安全性
            requireSafe(requestId, SAFE_ID, "requestId"); // 校验请求 ID 安全性
            if (method == null || !method.matches("[A-Z]{3,10}")) throw new IllegalArgumentException("method is unsafe"); // 校验方法安全性
            if (path == null || path.isBlank() || path.length() > 2048 || path.charAt(0) != '/') { // 检验路径长度及根据路径起始
                throw new IllegalArgumentException("path is unsafe"); // 路径不合法
            }
            Objects.requireNonNull(timestamp); // 时间戳不能为空
        }

        private static List<String> normalized(List<String> values, String name) { // 定义列表规范化静态方法
            Objects.requireNonNull(values, name);
            if (values.size() > 128) throw new IllegalArgumentException(name + " exceeds limit"); // 单个请求角色/权限不能超过 128 个
            var copy = values.stream().peek(v -> requireSafe(v, SAFE_AUTHORITY, name)).sorted().distinct().toList(); // 流式处理：校验、排序、去重
            if (String.join(",", copy).length() > 4096) throw new IllegalArgumentException(name + " exceeds length limit"); // Header 长度限制
            return copy;
        }

        private static void requireSafe(String value, Pattern pattern, String name) { // 安全校验工具方法
            if (value == null || !pattern.matcher(value).matches()) throw new IllegalArgumentException(name + " is unsafe");
        }
    }

}
