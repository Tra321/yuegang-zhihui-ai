package com.yuegang.zhihui.system.api;

import java.util.Set;

/**
 * 权限快照对象
 */
public record AuthoritySnapshot(String userId, Set<String> roles, Set<String> permissions, long version) {// 定义权限快照记录类：包含用户ID、角色集、权限集及版本
    public AuthoritySnapshot { // 紧凑构造函数，用于初始化数据校验或转换 no usages
        roles = Set.copyOf(roles); // 将传入的角色集合转换为不可变副本
        permissions = Set.copyOf(permissions); // 将传入的权限集合转换为不可变副本
    } // 构造逻辑结束
}
