package com.yuegang.zhihui.system.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增或更新权限点请求 DTO
 */
public record UpsertPermissionRequest(@NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9:_\\-]{0,127}") String code,// 权限编码：非空且符合通用编码正则
                                      @NotBlank @Size(max = 100) String name, // 权限显示名称：非空且限100位
                                      @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}") String resourceType,// 资源类型：非空且全大写蛇形正则
                                      boolean enabled) { // 是否启用标识
}

