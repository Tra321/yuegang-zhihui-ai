package com.yuegang.zhihui.common.security;

@FunctionalInterface // 标识函数式接口
public interface ResourceOwnershipChecker<I> { // 定义资源所有权检查的泛型扩展接口

    boolean isOwner(CurrentUserPrincipal principal, I resourceId); // 定义判断用户主体是否拥有特定资源 ID 的核心逻辑接口
}