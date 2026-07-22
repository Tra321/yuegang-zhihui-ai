package com.yuegang.zhihui.common.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** 自动填充不可变的创建审计数据并刷新修改审计数据。 */

public final class AuditMetaObjectHandler implements MetaObjectHandler { // 实现 Mybatis-plus 自动填充接口

    private static final String CREATED_BY = "createdBy"; // 字段常量：创建人
    private static final String CREATED_AT = "createdAt"; // 字段常量：创建时间
    private static final String UPDATED_BY = "updatedBy"; // 字段常量：更新人
    private static final String UPDATED_AT = "updatedAt"; // 字段常量：更新时间

    private final AuditorProvider auditorProvider; // 注入审计人获取器
    private final Clock clock; // 注入时钟（方便测试模拟时间）

    public AuditMetaObjectHandler(AuditorProvider auditorProvider, Clock clock) { // 构造函数
        this.auditorProvider = Objects.requireNonNull(auditorProvider, "auditorProvider must not be null"); // 校验非空
        this.clock = Objects.requireNonNull(clock, "clock must not be null"); // 校验
    }

    @Override
    public void insertFill(MetaObject metaObject) { // 插入数据时填充逻辑
        Objects.requireNonNull(metaObject, "metaObject must not be null"); // 校验元对象
        if (!hasAnyAuditProperty(metaObject)) { // 如果对象不包含任何审计属性（如未继承 AuditableEntity，直接返回）
            return;
        }

        String auditor = resolveAuditor(); // 获取当前操作人
        Instant now = clock.instant(); // 获取当前时间戳
        setIfPresent(metaObject, CREATED_BY, auditor); // 填充创建人
        setIfPresent(metaObject, CREATED_AT, now); // 填充创建时间
        setIfPresent(metaObject, UPDATED_BY, auditor); // 填充更新人（初始与创建人一致）
        setIfPresent(metaObject, UPDATED_AT, now); // 填充更新时间
    }

    @Override
    public void updateFill(MetaObject metaObject) { // 更新数据时填充逻辑
        Objects.requireNonNull(metaObject, "metaObject must not be null");
        if (!hasAnyAuditProperty(metaObject)) {
            return;
        }

        String auditor = resolveAuditor(); // 获取当前操作人
        Instant now = clock.instant(); // 获取当前时间戳
        setIfPresent(metaObject, UPDATED_BY, auditor); // 刷新更新人（初始与创建人一致）
        setIfPresent(metaObject, UPDATED_AT, now); // 刷新更新时间
    }

    private String resolveAuditor() { // 解析当前操作人 ID
        String auditor = auditorProvider.currentAuditor(); // 从提供者获取
        if (auditor == null || auditor.isBlank()) { // 禁止空白操作人
            throw new IllegalArgumentException("auditor must not be null");
        }
        return auditor;
    }

    private static boolean hasAnyAuditProperty(MetaObject metaObject) { // 检查对象是否存在 setter 方法，判断是否需要填充
        return metaObject.hasSetter(CREATED_BY)
                || metaObject.hasSetter(CREATED_AT)
                || metaObject.hasSetter(UPDATED_BY)
                || metaObject.hasSetter(UPDATED_AT);
    }

    private static void setIfPresent(MetaObject metaObject, String property, Object value) { // 安全填充方法
        if (metaObject.hasSetter(property)) { // 仅当字段存在 Setter 时才赋值
            metaObject.setValue(property, value);
        }
    }
}

