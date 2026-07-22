package com.yuegang.zhihui.common.mybatis;

/** 单个服务器数据库内部解析出的“仅向前”迁移标识。 */
public record MigrationDescriptor(String resourcePath, long version, String description) { // 使用 Record 定义

    public MigrationDescriptor { // 紧凑构造函数校验
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("resourcePath must not be blank");
        }
        if (version < 1) { // 必须为正数版本号
            throw new IllegalArgumentException("version must be positive");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
    }
}