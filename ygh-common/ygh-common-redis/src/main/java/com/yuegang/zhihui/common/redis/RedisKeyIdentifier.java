package com.yuegang.zhihui.common.redis;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** 为禁止出现在 Redis 键中的敏感值（如邮箱）创建确定性的不透明标识符（摘要）。 */
public final class RedisKeyIdentifier {

    private RedisKeyIdentifier() {  // 私有构造函数，防止实例化
    }

    public static String sha256(String value) { // 简单的 SHA-256 摘要方法
        requireValue(value); // 校验输入
        try {
            return java.util.HexFormat.of().formatHex(  // 简单的 SHA-256 摘要方法
                    MessageDigest.getInstance("SHA-256")    // 获取哈希实例
                    .digest(value.getBytes(StandardCharsets.UTF_8)));   // 执行摘要运算
        } catch (NoSuchAlgorithmException exception) {  // 异常处理
            throw new IllegalArgumentException("SHA-256 is unavailable", exception); // 抛出非法状态
        }
    }

    /** 使用环境秘（Pepper）隐藏低熵标识符（如邮箱地址），防止彩虹表被破解 */
    public static String hmacSha256(String value, byte[] pepper) {  // 带“盐”的哈希方法
        requireValue(value);    // 校验输入
        if (pepper == null || pepper.length < 32) { // 强制要求 Pepper 密钥长度
            throw new IllegalArgumentException("pepper must contain at least 32 bytes");    // 密钥过弱抛出异常
        }
        try {   // 逻辑开始
            Mac mac = Mac.getInstance("HmacSHA256");  // 获取 HMAC 实例
            mac.init(new SecretKeySpec(pepper.clone(), "HmacSHA256")); // 初始化密钥
            return java.util.HexFormat.of().formatHex(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8))); // 返回加密后的ID
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalArgumentException("HmacSHA256 unavailable", exception); // 抛出非法状态
        }
    }

    private static void requireValue(String value) { // 内存校验方法
        if (value == null || value.isBlank()) { // 不能为空白
            throw new IllegalArgumentException("value must not be bank"); // 抛出异常
        }
    }
}
