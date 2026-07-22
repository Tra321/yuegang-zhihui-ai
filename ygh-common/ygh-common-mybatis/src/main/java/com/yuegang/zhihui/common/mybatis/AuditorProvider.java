package com.yuegang.zhihui.common.mybatis;

/** 提供持久化审计字段中记录的稳定外部标识符。 */
@FunctionalInterface // 标识为函数式接口
public interface AuditorProvider {

    String SYSTEM_AUDITOR = "SYSTEM"; // 系统默认审计人常量

    String currentAuditor(); // 获取当前审计人 ID 的方法（如从 Security 上下文获取）

    static AuditorProvider system() { // 静态工厂，返回系统级审计人
        return () -> SYSTEM_AUDITOR;
    }
}