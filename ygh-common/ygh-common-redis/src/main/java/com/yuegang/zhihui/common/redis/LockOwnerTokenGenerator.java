package com.yuegang.zhihui.common.redis;

//生成不可预测的，每次获取时唯一的持有者令牌。
@FunctionalInterface    // 标识这是一个函数式接口
public interface LockOwnerTokenGenerator {
    String generate();  // 声明生成令牌字符串的方法

}
