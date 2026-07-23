package com.yuegang.zhihui.common.mq;

public enum MessageClaimStatus { // 认领操作的三种可能结果枚举
    CLAIMED, // 成功认领：你是当前唯一的合法执行者
    DUPLICATE, // 重复消息：该消息之前已由别人处理成功
    IN_PROGRESS // 正在进行：有人正在处理，且租约尚未过期
}
