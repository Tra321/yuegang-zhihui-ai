package com.yuegang.zhihui.common.mq;

import java.time.Instant;
import java.util.regex.Pattern;

/** 经过清洗，可审计的终端消费失败记录，不包含异常堆栈或原始负载数据。 */
public record DeadLetterRecord( // 使用 record 定义死信数据结构
                                String eventId,    // 事件唯一标识
                                String eventType,    // 事件类型
                                int eventVersion,    // 事件版本
                                String customerGroup, // 消费者组名
                                String businessKey, // 业务主键（如订单号）
                                String traceId,    // 链路追踪 ID
                                int deliveryAttempt, // 已投递尝试失败次数
                                String failureCode,   // 错误状态码
                                Instant failedAt    // 失败时间
) {
    private static final Pattern EVENT_TYPE = Pattern.compile("[A-Z][A-Z0-9_]*"); // 定义事件类型正则：大写字母蛇形命名
    private static final Pattern GROUP = Pattern.compile("[a-z0-9][a-z0-9]{0,63}"); // 定义消费者组正则，小写字母数字横线
    private static final Pattern FAILURE_CODE = Pattern.compile("[A-Z][A-Z0-9_]{2,63}"); // 定义错误码正则

    public DeadLetterRecord { // 紧凑构造函数，执行入参校验
        requireText(eventId, "eventId", 128); // 验证事件 ID 格式和长度
        if (eventType == null || !EVENT_TYPE.matcher(eventType).matches()) { // 校验事件
            throw new IllegalArgumentException("eventType is malformed"); // 格式错误抛出异常
        }
        if (eventVersion < 1) { // 校验版本号
            throw new IllegalArgumentException("eventVersion must be at least 1"); // 否则报错
        }
        if (customerGroup == null || !GROUP.matcher(customerGroup).matches()) { // 校验消费者组格式
            throw new IllegalArgumentException("customerGroup is malformed"); // 格式错误报错
        }
        requireText(businessKey, "businessKey", 128); // 校验业务主键非空及长度
        requireText(traceId, "traceId", 128); // 校验链路 ID 非空及长度
        if (deliveryAttempt < 1) { // 校验投递次数必须大于等于 1
            throw new IllegalArgumentException("deliveryAttempt must be at least 1"); // 否则报错
        }
        if (failureCode == null || !FAILURE_CODE.matcher(failureCode).matches()) { // 校验错误码格式
            throw new IllegalArgumentException("failureCode must be a stable uppercase code"); // 否则报错
        }
        if (failedAt == null) { // 校验失败时间不能为空
            throw new IllegalArgumentException("failedAt must not be null"); // 否则报错
        }
    }

    private static void requireText(String value, String name, int maxLength) { // 文本非空且长度检查工具方法
        if (value == null || value.isBlank() || value.length() > maxLength) { // 判定条件
            throw new IllegalArgumentException(name + " is malformed"); // 校验失败报错
        }
    }

}
