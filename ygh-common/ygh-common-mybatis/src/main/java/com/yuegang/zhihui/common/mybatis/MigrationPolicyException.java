package com.yuegang.zhihui.common.mybatis;

import java.util.Objects;

/**
 * 快速失败的策略迁移错误，绝不包含数据库异常，保证日志安全。 */
public final class MigrationPolicyException extends RuntimeException {
    private final MigrationViolationCode code;  // 违规代码

    public MigrationPolicyException(MigrationViolationCode code, String message) {  // 构造函数
        super(message);  // 传递消息给父类
        this.code = Objects.requireNonNull(code, "code must not be null");  // 强制非空代码
    }

    public MigrationViolationCode code() { return code; }  // 获取代码
}