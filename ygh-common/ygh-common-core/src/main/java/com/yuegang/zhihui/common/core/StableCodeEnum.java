package com.yuegang.zhihui.common.core;

import com.fasterxml.jackson.annotation.JsonValue;

/** 枚举类的，其外部表现值独立于 Java 的常量名称。 */
public interface StableCodeEnum { // 接口开始

    @JsonValue // 标记序列化时使用该方法的返回值
    String code(); // 获取对外输出的编码字符串

    String displayName(); // 获取对人类友好的展示名称
}