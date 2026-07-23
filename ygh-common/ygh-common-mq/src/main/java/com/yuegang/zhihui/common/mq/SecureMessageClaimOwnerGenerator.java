package com.yuegang.zhihui.common.mq;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/** 创建具有 192 位加密随机行的单词识别所有标识符。 */
public class SecureMessageClaimOwnerGenerator implements MessageClaimOwnerGenerator { // 实现生成器

    private final SecureRandom random; // 随机源

    public SecureMessageClaimOwnerGenerator() { // 默认构造
        this(new SecureRandom()); // 使用强随机算法（如 Linux 的 /dev/random）
    }

    SecureMessageClaimOwnerGenerator(SecureRandom random) { // 注入构造
        this.random = Objects.requireNonNull(random, "random must not be null"); // 校验
    }

    @Override
    public String generate() { // 执行生成
        byte[] bytes = new byte[24]; // 分配24个字节内存
        random.nextBytes(bytes); // 填充随机熵值
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); // 返回 URL 安全的 Base64 字符串
    }
}
