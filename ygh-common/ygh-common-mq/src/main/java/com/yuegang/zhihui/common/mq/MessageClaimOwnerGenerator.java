package com.yuegang.zhihui.common.mq;

@FunctionalInterface   // 函数式编程
public interface MessageClaimOwnerGenerator {  // 租约持有者随机标识生成器接口
    String generate();  // 返回一个不可预测的随机令牌字符串
}
