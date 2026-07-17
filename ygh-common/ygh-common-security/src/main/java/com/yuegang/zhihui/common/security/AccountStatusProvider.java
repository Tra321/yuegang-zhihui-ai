package com.yuegang.zhihui.common.security;

@FunctionalInterface // 标识这是一个函数式接口，仅包含一个抽象方法
public interface AccountStatusProvider { // 定义账户状态供应接口，用于判断账号是否可用
    boolean isEnabled(String userId); // 声明检查用户ID是否启用的抽象方法


}
