package com.yuegang.zhihui.common.security;

import java.lang.annotation.*;

@Documented // 生成 JavaDoc 时包含该注解
@Target({ElementType.TYPE, ElementType.METHOD}) // 允许该注解在类和方法上
@Retention(RetentionPolicy.RUNTIME) // 运行时保留，以便 AOP 逻辑可以检测该注解进行权限拦截
public @interface RequiresPermission { // 定义权限声明注解

    String value(); // 强制要求在使用注解时提供权限代码字符串（如 "user:delete"）
}