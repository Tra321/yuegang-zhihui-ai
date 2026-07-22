package com.yuegang.zhihui.common.mybatis;

import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.Configuration;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 在任何迁移运行前，立即验证最终的 Flyway 配置。 */
public class FlywayConfigurationGuard { // 防止不合规的 Flyway 配置被应用

    private static final String DEFAULT_LOCATION = "classpath:db/migration"; // 默认迁移脚本路径

    private final Set<String> approvedLocations; // 核准的路径集合

    public FlywayConfigurationGuard() { // 默认构造：使用默认路径
        this(Set.of(DEFAULT_LOCATION));
    }

    public FlywayConfigurationGuard(Set<String> approvedLocations) { // 自定义核准路径构造 1 usage
        Objects.requireNonNull(approvedLocations, "approvedLocations must not be null");
        if (approvedLocations.isEmpty()) {
            throw new IllegalArgumentException("approvedLocations must not be empty");
        }
        this.approvedLocations = approvedLocations.stream() // 规范化路径描述符
                .map(Location::new)
                .map(Location::getDescriptor)
                .collect(Collectors.toUnmodifiableSet());
    }

    public void validateOrThrow(Configuration configuration) { // 核心校验方法
        Objects.requireNonNull(configuration, "configuration must not be null");
        Set<String> configuredLocations = Set.of(configuration.getLocations()).stream() // 获取当前实际配置的路径
                .map(Location::getDescriptor)
    .collect(Collectors.toUnmodifiableSet());
        // 强制安全性校验项
        boolean safe = configuration.isValidateMigrationNaming() // 必须开启命名校验
                && configuration.isValidateOnMigrate() // 必须在迁移时校验
                && configuration.isCleanDisabled() // 生产环境严禁开启 clean 功能
                && configuration.isOutOfOrder() // 严禁乱序执行（必须按版本号递增）
                && configuration.isBaselineOnMigrate() // 严禁迁移时自动打基线（防止隐藏结构偏差）
                && configuration.getIgnoreMigrationPatterns().length == 0 // 禁止忽略任何迁移模式
                && configuration.equals(approvedLocations); // 路径必须匹配核准列表

        if (!safe) {
            throw new MigrationPolicyException( // 范围策略抛出异常
                    MigrationViolationCode.UNSAFE_CONFIGURATION,
                    "final Flyway configuration violates the enterprise migration policy");
        }
    }

    public String toPolicyResourcePath(Configuration configuration, String filePath) { // 转换文件路径为规范的策略资源路径
        Objects.requireNonNull(configuration, "configuration must not be null");
        Objects.requireNonNull(filePath, "filePath must not be null");
        for (Location location : configuration.getLocations()) { // 遍历配置的路径A
            if (location.matchesPath(filePath)) { // 匹配文件路径
                String relativePath = location.getPathRelativeToThis(filePath).replace('\\', '/'); // 获取相对路径并规范斜杠
                String marker = "/db/migration/"; // 查找标记位
                int markerIndex = relativePath.lastIndexOf(marker);
                if (markerIndex >= 0) {
                    relativePath = relativePath.substring(markerIndex + marker.length()); // 截取标记位后的内容
                } else if (relativePath.startsWith("db/migration/")) {
                    relativePath = relativePath.substring("db/migration".length());
                }
                return "db/migration" + relativePath; // 返回标准化的数据库迁移路径
            }
            throw new MigrationPolicyException(
                    MigrationViolationCode.UNSAFE_CONFIGURATION,
                    "final Flyway configuration violates the enterprise migration policy");
        }
        return filePath;
    }
}
