package com.yuegang.zhihui.common.mybatis;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;

import java.time.Instant;

/**
 * 用于记录创建和修改审计数据的持久化操作基类型。
 * 标识符保存为字符串，因为认证主体可能来自非数字身份提供者。
 *此处不强制要求版本控制和逻辑删除，因为这些属于业务表的决策。
 * */
public class AuditableEntity { // 定义抽象类，供业务实体继承
    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER) // 插入时填充，更新时永远不修改此字段
    private String createBy; // 创建人 ID
    @TableField(fill = FieldFill.INSERT, updateStrategy = FieldStrategy.NEVER) // 插入时填充，更新时永不修改
    private Instant createdAt;  // 创建时间(使用 Instant 保证精度）

    @TableField(fill = FieldFill.INSERT_UPDATE) // 插入和更新执行自动填充
    private String updateBy;    // 最后修改人 ID

    @TableField(fill = FieldFill.INSERT_UPDATE) // 插入和更新时均自动填充
    private Instant updatedAt; // 最后修改时间

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}