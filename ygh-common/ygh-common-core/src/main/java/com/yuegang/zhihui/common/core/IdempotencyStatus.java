package com.yuegang.zhihui.common.core;

/** 唯一幂等持久化存储的状态 */
public enum IdempotencyStatus { // 幂等状态结果
    IN_PROGRESS, // 正在执行中，拦截新的重复请求
    COMPLETED // 已执行成功， 后续重复请求将直接返回结果
}
