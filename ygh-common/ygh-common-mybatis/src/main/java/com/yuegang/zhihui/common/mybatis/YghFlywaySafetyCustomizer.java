package com.yuegang.zhihui.common.mybatis;

import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;

/** 为每个业务服务强制执行“仅向前，快速失败”的 Flyway 设置。 */
public final class YghFlywaySafetyCustomizer implements FlywayConfigurationCustomizer { // 配置自动注入

    @Override
    public void customize(FluentConfiguration configuration) {
        configuration
                .validateMigrationNaming(true) // 强制启用命名校验
                .validateOnMigrate(true) // 强制迁移前校验历史
                .cleanDisabled(true) // 强制禁用 clean 危险动作
                .outOfOrder(false) // 强制禁用乱序执行
                .baselineOnMigrate(false) // 禁用自动基线
                .ignoreMigrationPatterns(new String[0]); // 禁止忽略任何版本
    }
}
