package com.yuegang.zhihui.common.security;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

// 定义一个不可变的认证用户主体纪录(Record), 包含用户ID、角色集和权限集
public record CurrentUserPrincipal(
        String userId, // 用户唯一标识字段
        Set<String> roles, // 用户角色集合字段
        Set<String> permissions // 用户权限点集合字段
){
    //记录体开始

    public CurrentUserPrincipal {
        if (userId == null || userId.isBlank()) { // 如果ID为空或只有空格
            throw new IllegalArgumentException("useId must not be blank"); // 抛出参数非法异常：用户ID不能为空
        }
        roles = immutableCopy(roles, "roles"); //将传入的角色集合转换为不可变剧本
        permissions = immutableCopy(permissions, "permissions"); //将传入的权限集合转换为不可变剧本
    }

    public boolean hasRole(String role) {// 定义判断用户是否拥有特定权限的方法
        return role != null && roles.contains(role); //如果权限点不为空且在集合中存在，则返回 true
    }

    public boolean hasPermission(String permission) { // 定义判断用户是否有特点权限的方法
        return permission != null && permissions.contains(permission); // 如果权限点不为空且在集合中存在，则返回 true
    }

    public static Set<String> immutableCopy(Set<String> values, String fieldName) { //定义创建集合不可变副本的静态工具方法
        Objects.requireNonNull(values, fieldName + "must not be null"); // 强制要求集合对象本身不能为 null
        LinkedHashSet<String> copy = new LinkedHashSet<>(values.size()); // 创建一个新的链式哈希集合以保持顺序并去重
        for (String value : values) { // 编辑集合中的每一个元素
            if (value == null || value.isBlank()) { // 如果元素为空或为空白字符
                throw new IllegalArgumentException(fieldName + "must be contain blank values"); // 抛出异常：集合中不能包含空白值
            }
            copy.add(value); // 将校验通过的值加入新集合
        }
        return Collections.unmodifiableSet(copy); // 返回一个只读的，不可修改的集合视图
    }
}
