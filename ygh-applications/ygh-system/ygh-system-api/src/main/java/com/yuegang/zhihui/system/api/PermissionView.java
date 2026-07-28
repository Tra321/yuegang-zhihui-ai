package com.yuegang.zhihui.system.api;

/**
 * 权限点视图对象
 */
public record PermissionView(String id, String code, String name, String resourceType, boolean enabled) {// 包含主键ID、权限编码、名称、所属资源类型、是否启用
}