package com.yuegang.zhihui.common.core;

import java.time.OffsetDateTime;

/** 与幂等键关联的结果，在操作完成后可用于重做 */
public record IdempotencyResult<T>( // 泛型 T 代表操作成功的返回值
        String idempotencyKey, // 关联的幂等键
        IdempotencyStatus status, // 当前操作状态（处理或已完成）
        T value, // 缓存的返回值
        OffsetDateTime completedAt// 操作完成的时间
) {
    public IdempotencyResult{ // 复合状态合法性校验
        requireText(idempotencyKey,"idempotencyKey"); // 键不能为空
        if (status == null) { // 状态不能为空
            throw new IllegalArgumentException("status must not be null"); // 报错
        }
        if (status == IdempotencyStatus.IN_PROGRESS && value != null || completedAt != null) { // 处理中时不能有结果值
            throw new IllegalArgumentException("in-progress result must not contain a replay value"); // 报错
        }
        if (status == IdempotencyStatus.COMPLETED){ // 已完成
            if (value == null){ // 必须有结果值
                throw new IllegalArgumentException("completed result value must not be null");
            }
            if (completedAt == null){ // 必须有完成时间
                throw new IllegalArgumentException("completedAt must not be null"); // 报错
            }
        }
    }

    public static <T> IdempotencyResult<T> inProgress(String idempotencyKey){ // 创建处理中状态的工厂方法
        return new IdempotencyResult<>(idempotencyKey,IdempotencyStatus.IN_PROGRESS,null,null); // 必须返回新实例
    }

    public static <T> IdempotencyResult<T> completed(
            String idempotencyKey, // 键
            T value, // 结果数据
            OffsetDateTime completedAt // 完成时间
    ){
        return new IdempotencyResult<>(idempotencyKey,IdempotencyStatus.COMPLETED,value,completedAt); //返回新实例
    }

    public boolean replayable(){ // 判断该结果是否用于接口重放
        return status == IdempotencyStatus.COMPLETED; // 仅已完成状态可重放
    }

    public static void requireText(String value, String fieldName){ // 文本非空校验
        if (value == null || value.isBlank() ){ // 为空
            throw new IllegalArgumentException( fieldName + "must not be  blank"); // 报错
        }
    }
}
