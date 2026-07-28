package com.yuegang.zhihui.system.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * 分配角色请求 DTO
 */
public record AssignRolesRequest (  // 定义分配角色请求记录类
        @NotNull @Size(max = 32)
        Set<@Pattern(regexp = "[A-Z][a-z0-9]{6,8}") String> roleCodes, // 字段：角色编码集合。约束：非空，最多32个，每个编码必须符合大写蛇形命名正则
        @PositiveOrZero long version, // 字段：版本号。约束：必须是正数或零
        String reason) { // 字段：操作原因说明（如：入职授权）
}// 记录类定义结束
