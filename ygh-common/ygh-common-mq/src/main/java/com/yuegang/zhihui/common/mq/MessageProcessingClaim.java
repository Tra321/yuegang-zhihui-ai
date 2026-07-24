package com.yuegang.zhihui.common.mq;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.regex.Pattern;

/** 包含持有者信息的认证凭证，用于防止已超时的消费者线程误写其他节点的尝试结果。 */
public record MessageProcessingClaim( // 凭证记录类
                                      String consumerGroup,         // 组 ID
                                      String eventId,                // 消息唯一 ID
                                      @JsonIgnore String owner      // 持有者令牌 (JSON序列化时忽略以防泄露)
) {

    private static final Pattern GROUP = Pattern.compile("^[a-z0-9][a-z0-9-](0,63)");
    // 组名正则规则
    private static final Pattern OWNER = Pattern.compile("^[A-Za-z0-9_-]{32,128}$");
    // 令牌正则规则

    public MessageProcessingClaim { // 紧凑构造函数逻辑
        if (consumerGroup == null || !GROUP.matcher(consumerGroup).matches()) { // 校验组
            throw new IllegalArgumentException("consumerGroup is malformed"); // 报错
        }
        if (eventId == null || eventId.isBlank() || eventId.length() > 128) { // 校验消息ID
            throw new IllegalArgumentException("eventId is malformed"); // 报错
        }
        if (owner == null || !OWNER.matcher(owner).matches()) { // 验证持有者身份令牌
            throw new IllegalArgumentException("claim owner is malformed"); // 报错
        }
    }

    @Override
    public String toString() { // 重写 toString 实现脱敏
        return "MessageProcessingClaim[consumerGroup=" + consumerGroup
                + ", eventId = " + eventId + ", owner=[REDACTED]]"; // 在日志中隐藏真实令牌内容
    }
}