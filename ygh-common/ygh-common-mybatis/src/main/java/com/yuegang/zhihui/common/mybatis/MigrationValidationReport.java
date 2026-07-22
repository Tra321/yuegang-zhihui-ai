package com.yuegang.zhihui.common.mybatis;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 不可变的，排序稳定的报告，适合作为 CI/CD 输出。 */
public record MigrationValidationReport(List<MigrationViolation> violations) { // 包含违规清单

    private static final Comparator<MigrationViolation> ORDER = // 排序规则：先代码后路径
            Comparator.comparing(MigrationViolation::code)
                    .thenComparing(MigrationViolation::resourcePath);

    public MigrationValidationReport { // 构造函数
        Objects.requireNonNull(violations,  "violations must not be null");
        violations = violations.stream().sorted(ORDER).toList(); // 执行排序并转为不可变列表
    }

    public boolean valid() { return violations.isEmpty(); } // 是否合法（无违规）

    public void throwIfInvalid() { // 如果不合法，抛出包含首个错误信息的异常
        if (!valid()) {
            var first = violations.getFirst();
            throw new MigrationPolicyException(
                    first.code(),
                    "migration policy failed with " + violations.size() + " violation(s): "
                            + first.message());
        }
    }
}