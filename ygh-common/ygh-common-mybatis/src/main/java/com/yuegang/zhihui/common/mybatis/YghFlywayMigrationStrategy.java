package com.yuegang.zhihui.common.mybatis;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.InfoOutput;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
/**
 * 最终迁移入口： 在委派给 Flyway 执行迁移前，验证有效配置，解析出的资源以及已应用的历史
 * */
public class YghFlywayMigrationStrategy implements FlywayMigrationStrategy { // 拦截 Spring 自动执行的迁移

    private final FlywayMigrationPolicy migrationPolicy; // 命名与内容策略
    private final FlywayConfigurationGuard configurationGuard; // 配置安全守卫
    private final FlywayHistoryValidator historyValidator; // 历史完整性校验

    public YghFlywayMigrationStrategy(
            FlywayMigrationPolicy migrationPolicy,
            FlywayConfigurationGuard configurationGuard,
            FlywayHistoryValidator historyValidator
    ){
        this.migrationPolicy = Objects.requireNonNull(migrationPolicy,"migrationPolicy cannot be null");
        this.configurationGuard = Objects.requireNonNull(configurationGuard,"configurationGuard cannot be null");
        this.historyValidator = Objects.requireNonNull(historyValidator,"historyValidator cannot be null");
    }

    @Override
    public void migrate(Flyway flyway){ // 核心迁移逻辑拦截
        Objects.requireNonNull(flyway,"flyway cannot be null");
        configurationGuard.validateOrThrow(flyway.getConfiguration()); // 1.校验配置是否安全

        var info = flyway.info().getInfoResult(); // 获取 flyway 解析出的所有迁移信息
        List<String> allResources = new ArrayList<>(); // 全部资源
        List<String> notAppliedResources =  new ArrayList<>(); // 待执行的新脚本
        long highestAppliedVersion = 0L; //数据库已应用最高版本

        for (InfoOutput migration : info.migrations){ // 迭代所有脚本
            if (!isSqlMigration(migration)){ // 强制要求只能使用 SQL 迁移，禁止 Java 迁移（防止隐式漏洞）
                throw new MigrationPolicyException(MigrationViolationCode.UNSUPPORTED_MIGRATION_TYPE, "only SQL migrations are supported");
            }
            if (isRepeatable(migration)){ // 再次检查并拦截 R__ 脚本
                throw new MigrationPolicyException(MigrationViolationCode.REPEATABLE_SCRIPT_FORBIDDEN, "repeatable migration forbidden");
            }
            if (isUndo(migration)){
                throw new MigrationPolicyException(MigrationViolationCode.UNDO_SCRIPT_FORBIDDEN, "undo migration if forbidden");
            }
            if (migration.filepath != null && migration.filepath.endsWith(".sql")){ // 收集 SQL 资源
                String policyPath = configurationGuard.toPolicyResourcePath(flyway.getConfiguration(), migration.filepath); // 转换为策略资源路径
                allResources.add(policyPath);
                if (migration.installedOnUTC == null){ // 如果尚未安装，加入待执行列表
                    notAppliedResources.add(policyPath);
                }
            }
            String resolvedVersion = resolvedVersion(migration);
            if (resolvedVersion != null && isApplied(migration)){ // 计算历史最高版本号
                highestAppliedVersion = Math.max(highestAppliedVersion,parseAppliedVersion(resolvedVersion));
            }
        }
        migrationPolicy.validate(allResources).throwIfInvalid(); // 2. 执行静态规范验证
        for (InfoOutput migration : info.migrations){ // 3. 检查解析用的把呢不能是否低于历史版本
            String resolvedVersion = resolvedVersion(migration);
            if (resolvedVersion != null && !isApplied(migration)
                    && parseAppliedVersion(resolvedVersion) <= highestAppliedVersion){
                throw new MigrationPolicyException(MigrationViolationCode.OUT_OF_ORDER_VERSION, "resolved migration version must be greater than applied  history");
            }
        }
        migrationPolicy.validateNewMigrations(notAppliedResources, highestAppliedVersion).throwIfInvalid(); // 4. 验证新脚本是否合法
        flyway.migrate(); // 5. 正式执行 Flyway 迁移
        historyValidator.validateOrThrow(flyway); // 6. 迁移后再次校验历史一致性
    }

    // --- 内部辅助判断方法 ---
    private static boolean isRepeatable(InfoOutput migration){ // 判断是否为重复脚本
        boolean repeatableCategory = migration.category != null &&
                migration.category.toLowerCase().contains("repeatable");
        boolean sqlWithoutVersion = resolvedVersion(migration) == null &&
                migration.filepath != null && migration.filepath.toLowerCase().endsWith(".sql");
        return repeatableCategory || sqlWithoutVersion;
    }

    private static boolean isUndo(InfoOutput migration){ // 判断是否为 Undo 脚本
        if (migration.filepath == null) return false;
        String normalized = migration.filepath.replace('\\','/');
        int separator =  normalized.lastIndexOf('/');
        String fileName = separator >= 0 ? normalized.substring(separator + 1) : normalized;
        return fileName.startsWith("U");

    }

    private static boolean isSqlMigration(InfoOutput migration) { // 判断是否为 SQL 类型
        return migration.type != null && migration.type.toLowerCase().contains("sql");
    }

    private static long parseAppliedVersion(String version){ // 解析版本号字符串为 Long
        try { return Long.parseLong(version); }
        catch (NumberFormatException exception){ throw  new MigrationPolicyException(MigrationViolationCode.INVALID_NAME,"applied migration uses unsupported version format"); }
    }

    private static String resolvedVersion(InfoOutput migration) { // 获取版本对应的版本号
        if (migration.type != null && !migration.rawVersion.isBlank()) return migration.rawVersion;
        return migration.version == null || migration.version.isBlank() ? migration.rawVersion : migration.version;

    }

    public static boolean isApplied(InfoOutput migration) { // 判断版本号是否执行过
        if (migration.installedOnUTC != null && !migration.installedOnUTC.isBlank()) return true;
        return migration.state != null &&
                migration.state.toLowerCase().contains("success");
    }
}
