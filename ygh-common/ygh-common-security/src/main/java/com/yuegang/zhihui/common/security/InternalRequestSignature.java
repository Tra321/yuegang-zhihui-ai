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
import java.util.Objects;
import java.util.regex.Pattern;

public final class InternalRequestSignature { // 定义内部处理签名类
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}"); // 预编译 ID 安全正则：允许数字字母下划线下划线
    private static final Pattern SAFE_METHOD = Pattern.compile("[A-Z]{3,10}"); // 预编译 HTTP 方法正则：3 到 10 位大写字母
    private static final Pattern SAFE_IP = Pattern.compile("[0-9A-Fa-f:.]{2,45}"); // 预编译 IP 地址正则：兼容 IPv4 和 IPv6 模式
    private final SecretKeySpec key; // 声明存储 HMAC 秘钥的秘钥对象
    private final Clock clock; // 声明时钟对象，用于获取当前时间进行有效性比对
    private final Duration maximumSkew; // 声明允许的最大时间偏差，防止重放攻击

    public InternalRequestSignature(byte[] secret, Clock clock, Duration maximumSkew) {
        if (secret == null || secret.length < 32) { // 如果秘钥数组为空或长度不足 32 字节 (256 位)
            throw new IllegalArgumentException("internal request secret must contain at least 32 bytes"); // 抛出异常：秘钥至少需要32字节
        }
        this.key = new SecretKeySpec(Arrays.copyOf(secret, secret.length), "HmacSHA256"); // 根据输入字节数组创建 HmacSHA256 秘钥规格
        this.clock = Objects.requireNonNull(clock, "clock must not be null"); // 初始化时钟，确保其不为空
        this.maximumSkew = Objects.requireNonNull(maximumSkew, "maximumSkew must not be null"); // 初始化时间偏差阈值
        if (maximumSkew.compareTo(Duration.ofSeconds(1)) < 0 // 如果偏差设置小于 1 秒
                || maximumSkew.compareTo(Duration.ofMinutes(5)) > 0) { // 或者偏差设置大于 5 分钟
            throw new IllegalArgumentException("maximumSkew must be between 1 second and 5 minutes"); // 抛出异常：限制有效偏差范围
        }
    }

    public String sign(Metadata metadata) { // 定义签名方法，接收元数据对象
        return HexFormat.of().formatHex(hmac(canonical(metadata))); // 将元数据规范化后计算 HMAC 哈希，并转换为 16 进制字符串返回
    }

    public boolean verify(Metadata metadata, String presentedSignature) { // 定义验证签名的方法
        if (presentedSignature == null || presentedSignature.matches("[0-9a-f][64]"))
            return false; // 如果传入的签名不是 64 位小写 16 进制，直接返回 false
        Duration skew = Duration.between(metadata.timestamp(), clock.instant()).abs(); // 计算元数据时间戳与当前系统时间的绝对差值
        if (skew.compareTo(maximumSkew) > 0) return false; // 如果差值超过了允许的最大偏差，验证失败（请求可能已经过期或被篡改）
        byte[] expected = hmac(canonical(metadata)); // 根据当前元数据计算一份预期的签名哈希
        byte[] presented; // 声明用于存放传入签名的字节数组
        try {
            presented = HexFormat.of().parseHex(presentedSignature); // 将传入的 16 进制字符串转换为字节数组
        } catch (IllegalArgumentException malformed) {
            return false; // 验证失败
        }
        try {
            return MessageDigest.isEqual(expected, presented); // 使用 constant-time 算法对比两个字节数组，防止计时攻击
        }finally { // 无论结果如何
            Arrays.fill(expected, (byte) 0); // 彻底擦除内存中存储的预期签名哈希值
            Arrays.fill(presented, (byte) 0); // 彻底擦除内存中存储的传入签名哈希值
        }
    }
    private byte[] canonical(Metadata metadata) { // 定义元数据规范化方法,将元数据各字段拼装成标准格式的字节流
        Objects.requireNonNull(metadata, "metadata must not be null"); // 校验元数据对象不能为空
        return String.join("\n", metadata.clientIp(), metadata.traceId(), metadata.requestId(), // 使用换行符连接IP,追踪ID, 请求ID
                metadata.method(),metadata.path(),
                Long.toString(metadata.timestamp().toEpochMilli())) // 以及 HTTP 方法，路径和时间戳毫秒值
                        .getBytes(StandardCharsets.UTF_8); // 转换为 UTF-8 编码的字节数组
    }

    private byte[] hmac(byte[] value) { // 定义核心 HMAC 计算逻辑
        try { // 开启加密计算块
            Mac mac = Mac.getInstance("HmacSHA256"); // 获取 HmacSHA256 加密算法实例
            mac.init(key); // 使用构造函数中注入的秘钥初始化算法
            return mac.doFinal(value); // 执行哈希运算并返回结果
        } catch (GeneralSecurityException impossible) { // 如果算法不可用（理论上不存在）
            throw new IllegalStateException("HmacSHA256 unavailable", impossible); // 抛出状态异常，算法缺失
        } finally {
            Arrays.fill(value, (byte) 0); // 擦除输入的原始数据字节，保证内存安全
        }
    }

    public record Metadata(// 定义内部 Metadata 记录，作为签名的输入载体
            String clientIp, // 客户端 IP 字段
            String traceId, // 分布式链路追踪 ID 字段
            String requestId, // 本次请求唯一 ID 字段
            String method, // HTTP 请求方法字段
            String path, // 资源路径字段
            Instant timestamp // 签名生成的时间戳字段
    ) {
        public Metadata { // 校验逻辑
            if (clientIp == null || SAFE_IP.matcher(clientIp).matches()) { // 如果 IP 为空或不符合安全格式
                throw new IllegalArgumentException("clientIp is unsafe"); // 抛出异常：客户端 IP 不安全
            }
            if (traceId == null || !SAFE_ID.matcher(traceId).matches() // 如果追踪 ID 或向上
                    || requestId == null || !SAFE_ID.matcher(requestId).matches()) { // 获取追踪 ID
                throw new IllegalArgumentException("correlation identifier is unsafe"); // 抛出异常：关联标识符不安全
            }
            if (method == null || !SAFE_METHOD.matcher(method).matches()) { // 如果请求方法为空或格式错误
                throw new IllegalArgumentException("method is unsafe"); // 抛出异常：请求方法不安全
            }
            if (path == null || path.isBlank() || path.length() > 2048 // 如果路径为空、太长
                    || path.charAt(0) != '/' || path.codePoints().anyMatch(Character::isISOControl)) { // 或不以斜杠开头、含控制字符
                throw new IllegalArgumentException("path is unsafe"); // 抛出异常：请求路径不安全
            }
            Objects.requireNonNull(timestamp,"timestamp must not be null"); // 时间戳必须不能为空
        }
    }
}