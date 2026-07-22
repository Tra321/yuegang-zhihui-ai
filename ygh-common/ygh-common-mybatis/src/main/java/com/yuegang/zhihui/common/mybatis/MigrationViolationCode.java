package com.yuegang.zhihui.common.mybatis;
/** 由代码库和数据库迁移守卫发出的稳定违规代码 */
public enum MigrationViolationCode {
    INVALID_NAME,                       // 命名不合规
    UNDO_SCRIPT_FORBIDDEN,              // 禁止 Undo 脚本
    REPEATABLE_SCRIPT_FORBIDDEN,        // 禁止重复执行脚本
    DUPLICATE_VERSION,                  // 版本号重复
    DUPLICATE_RESOURCE,                 // 文件资源重复
    OUT_OF_ORDER_VERSION,               // 版本号乱序（不递增）
    UNSAFE_CONFIGURATION,               // 配置不安全（如允许Clean）
    UNSUPPORTED_MIGRATION_TYPE,         // 不支持的迁移类型
    HISTORY_VALIDATION_FAILED           // 历史验证失败
}
