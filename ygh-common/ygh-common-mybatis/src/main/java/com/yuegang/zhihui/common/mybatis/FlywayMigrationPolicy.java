package com.yuegang.zhihui.common.mybatis;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 针对单个服务拥有的数据库，实施代码库级别的“仅向前”迁移策略。
 * 数据库历史记录的物理检查仍由 Flyway 自行完成。
 */

public final class FlywayMigrationPolicy { // 强制执行脚本命名和内容规范

    // 正则表达式：必须以V开头，正整数版本号，双下划线，小写蛇形命名描述
    private static final Pattern VERSIONED_MIGRATION = Pattern.compile(
            "^db/migration/V([1-9][0-9]*)__([a-z][a-z0-9]*(?:_[a-z0-9]+)*)\\.sql$");

    public MigrationDescriptor parse(String resourcePath) { // 解析脚本路径为描述对象
        Objects.requireNonNull(resourcePath,  "resourcePath must not be null");
        String normalized = normalize(resourcePath); // 规范化斜杠
        if (normalized.isBlank() || normalized.contains("../") || normalized.contains("/../")) { // 禁止路径穿越攻击
            throw new MigrationPolicyException(
                    MigrationViolationCode.UNDO_SCRIPT_FORBIDDEN,
                    "undo migration is forbidden: " + safeDisplay(normalized));
        }
        if (normalized.startsWith("db/migration/R__")) { // 禁止可重复执行脚本 (R__)，保证所有结构变更均有唯一版本号
            throw new MigrationPolicyException(
                    MigrationViolationCode.REPEATABLE_SCRIPT_FORBIDDEN,
                    "repeatable migration is forbidden: " + safeDisplay(normalized));
        }

        var matcher = VERSIONED_MIGRATION.matcher(normalized); // 执行正则匹配
        if (!matcher.matches()) {
            throw invalid(normalized); // 命名不合规
        }

        long version;
        try {
            version = Long.parseLong(matcher.group(1)); // 提取版本号
        } catch (NumberFormatException exception) {
            throw invalid(normalized);
        }
        return new MigrationDescriptor(normalized, version, matcher.group(2)); // 返回解析出的版本信息
    }

    public MigrationValidationReport validate(Collection<String> resourcePaths) { // 批量验证脚本列表
        Objects.requireNonNull(resourcePaths,  "resourcePaths must not be null");
        var violations = new ArrayList<MigrationViolation>(); // 违规列表
        Set<String> seenPath = new HashSet<>(); // 已记录路径
        Map<Long, String> pathByVersion = new HashMap<>(); // 版本号对应的路径字典

        for (String resourcePath : resourcePaths) { // 遍历所有脚本
            if (resourcePath == null) {
                violations.add(new MigrationViolation(MigrationViolationCode.INVALID_NAME, "<null>", "migration path must not be null"));
                continue;
            }

            String normalized = normalize(resourcePath);
            if (!seenPath.add(normalized)) { // 检查是否有完全重复的文件路径
                String displayPath = safeDisplay(normalized);
                violations.add(new MigrationViolation(MigrationViolationCode.DUPLICATE_VERSION, displayPath, "migration resource is duplicated: " + displayPath));
                continue;
            }
            try {
                var descriptor = parse(normalized); // 解析命名
                String existingPath = pathByVersion.putIfAbsent(descriptor.version(), descriptor.resourcePath()); // 检查版本号是否重复
                if (existingPath != null) {
                    violations.add(new MigrationViolation(MigrationViolationCode.DUPLICATE_VERSION,
                            descriptor.resourcePath(), "version" + descriptor.version() + "is already used by " + existingPath));
                }
            } catch (MigrationPolicyException exception) { // 捕获解析过程中的异常
                violations.add(new MigrationViolation(exception.code(), safeDisplay(normalized), exception.getMessage()));
            }
        }
        return new MigrationValidationReport(violations); // 返回汇总报告
    }

    public MigrationValidationReport validateNewMigrations(Collection<String> resourcePaths, long highestAppliedVersion) { // 验证新脚本是否满足“版本号递增”
        if (highestAppliedVersion < 0) {
            throw new IllegalArgumentException("highestAppliedVersion must not be negative");
        }
        var violations = new ArrayList<>(validate(resourcePaths).violations()); // 先执行基础静态验证
        for (String resourcePath : resourcePaths) {
            if (resourcePath == null) continue;
            try {
                var descriptor = parse(resourcePath);
                if (descriptor.version() <= highestAppliedVersion) { // 如果新的版本号 <= 数据库已执行的最高版本号
                    violations.add(new MigrationViolation(
                            MigrationViolationCode.OUT_OF_ORDER_VERSION,
                            descriptor.resourcePath(),
                             "new version " + descriptor.version() + " must be greater than applied version " + highestAppliedVersion));
                }
            } catch (MigrationPolicyException ignored) {}
        }
        return new MigrationValidationReport(violations);
    }

    private static String normalize(String resourcePath) { return resourcePath.replace('\\', '/'); } // 统一斜杠

    public static MigrationPolicyException invalid(String path) { // 命名错误工厂
        String displayPath = safeDisplay(path);
        return new MigrationPolicyException(
                MigrationViolationCode.INVALID_NAME,
                "migration name must match db/migration/V/<positive integer>__<lower_snake_case>.sql: " + displayPath);
    }

    private static String safeDisplay(String path) { // 路径脱敏显示，防止字符或过长路径破坏日志排版
        if (path == null || path.isBlank()) return "<blank>";
        String normalized = normalize(path).replace('[' + path + ']',  "?"); // 过滤控制字符 I
        int separator = normalized.lastIndexOf(';');
        String fileName = separator >= 0 ? normalized.substring( separator + 1) : normalized;
        if (fileName.isBlank()) fileName = "<unnamed>";
        return fileName.length() <= 128 ? fileName : fileName.substring(0, 128); // 限制文件长度
    }
}