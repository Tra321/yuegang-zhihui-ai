package com.yuegang.zhihui.common.security;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;

public final class ResourceAccessGuard { // 定义资源访问守卫类，执行业务颗粒度的权限控制

    public void requireOwnerOrPermission( // 检查是否拥有者是否拥有权限
                                          CurrentUserPrincipal principal, // 当前操作的用户主体
                                          String ownerUserId, // 被访问资源所属的用户 ID
                                          String crossOwnerPermission // 允许跨主体访问所需的指定权限码
    ) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED); // 抛出未认证异常
        }

        boolean owner = ownerUserId != null && ownerUserId.equals(principal.userId()); // 逻辑判断：资源所属 ID 是否匹配当前用户 ID
        boolean explicitlyAllowed = principal.hasPermission(crossOwnerPermission); // 逻辑判断：用户是否拥有管理权限的跨域权限
        if (!owner && !explicitlyAllowed) { // 如果既不是管理员，也没有跨资源权限
            throw new PermissionDeniedException(); // 抛出权限拒绝异常
        }
    }

    public <I> void requireOwnerOrPermission(  // 方法：基于拥有者检查器接口的泛型版本
                                               CurrentUserPrincipal principal, // 认证主体
                                               I resourceId, // 资源的泛型 ID
                                               ResourceOwnershipChecker<I> ownershipChecker, // 外部传入的拥有者逻辑判定接口
                                               String crossOwnerPermission // 跨资源权限码
    ) {
        if (principal == null) { // 登录校验
            throw new BusinessException(ErrorCode.UNAUTHENTICATED); // 拦截未登录
        }
        if (ownershipChecker == null) { // 检查器不能为空
            throw new IllegalArgumentException("ownershipChecker must not be null"); // 参数错误报告
        }
        boolean owner = ownershipChecker.isOwner(principal, resourceId); // 调用业务逻辑：询问检查器该用户是否拥有该资源
        boolean explicitlyAllowed = principal.hasPermission(crossOwnerPermission); // 检查跨资源管理权限
        if (!owner && !explicitlyAllowed) { // 拦截条件：非管理员且无授权
            throw new PermissionDeniedException(); // 抛出拒绝异常
        }
    }

    public <I> void requireOwnerOrPermission(
            CurrentUserPrincipal principal,
            I resourcesId,
            ResourcesOwnershipChecker<I> ownershipChecker, // 外部传入的拥有者逻辑做判断结构
            String crossOwnerPermission // 跨资源权限码
    ) {
        if (principal == null) { // 登录校验
            throw new BusinessException(ErrorCode.UNAUTHENTICATED); // 拦截未登录
        }
        if (ownershipChecker == null) { // 筛选器不能为空
            throw new IllegalArgumentException("ownershipChecker must not be null"); // 参数错误报告
        }
        boolean owner = ownershipChecker.isOwner(principal, resourcesId); // 检查跨资源管理权限
        if (!owner && !explicitlyallowed) { // 拦截条件：非客户且我特权
            throw new PermissionDeniedException("ownershipChecker must not be null"); // 参数错误
        }
    }
}