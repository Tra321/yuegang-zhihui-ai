package com.yuegang.zhihui.common.core;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class IdempotencyModelTest {     // 定义幂等模型测试类

    private static final OffsetDateTime REQUESTED_AT = // 定义请求发生的基准时间
            OffsetDateTime.of(2026,7,17,15,0,0,0, ZoneOffset.ofHours(8));   // 设为2026-7-17

    @Test   // 标记为测试方法
    void requestContextIdentifiesOneCallerOperationAndRequestBody() {   // 创建一个幂等请求上下文
        var context = new IdempotencyRequestContext(    // 创建一个幂等请求上下文
                "idem-wallet-pay-001",  // 幂等键
                "WALLET_PAY",   // 操作类型：钱包支付
                "user-9007199254740993",    // 发起人用户 ID
                "sha256:0123456789abcdef",    // 请求内容摘要指纹
                REQUESTED_AT);  // 时间

        assertThat(context.idempotencyKey()).isEqualTo("idem-wallet-pay-001");// 断言键正确
        assertThat(context.operation()).isEqualTo("WALLET_PAY");// 断言操作名正确
        assertThat(context.subjectId()).isEqualTo("user-9007199254740993");// 断言主体 ID 正确
        assertThat(context.requestFingerprint()).isEqualTo("sha256:0123456789abcdef");  // 断言指纹正确
        assertThat(context.requestedAt()).isEqualTo(REQUESTED_AT);  // 断言时间正确
        assertThat(context.requestedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(8));  // 断言时区偏移正确
    }

    @Test   // 标记为测试方法
    void requestContextRejectsMissingIdentityOrFingerprint() {  // 测试请求上下文是否拒绝缺失身份信息或指纹的构造
        assertThatThrownBy(() -> new IdempotencyRequestContext( // 尝试传入空格作为幂等键
                " ","WALLET_PAY", "user-1", "sha256:abc", REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class);  // 应该报错
        assertThatThrownBy(() -> new IdempotencyRequestContext( // 尝试传入空格作为请求指纹
                "idem-1", "WALLET_PAY", "user-1", " ", REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyRequestContext( // 尝试传入空的时间戳
                "idem-1", "WALLET_PAY", "user-1", "sha256:abc", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test   // 标记为测试方法
    void completeResultCanBeReplaydWithTheOriginalBusinessValue() { // 测试已完成的结果是否可以使用原始业务值进行重放
        var completeAt = REQUESTED_AT.plusSeconds(1);   // 模拟在请求 1 秒后完成
        var result = IdempotencyResult.completed(   // 创建一个已经完成的幂等结果
                "idem-wallet-pay-001", "wallet-flow-001", completeAt);  // 绑定结果值（流水号）
        assertThat(result.idempotencyKey()).isEqualTo("idem-wallet-pay-001");   // 断言键一致
        assertThat(result.status()).isEqualTo(IdempotencyStatus.COMPLETED);  // 断言状态为已完成
        assertThat(result.completedAt()).isEqualTo(completeAt); // 断言完成时间正确
        assertThat(result.replayable()).isTrue();   // 断言结果标记为可重放
    }

    @Test   // 标记为测试方法
    void inProgressResultCannotPretendToBeReplayable() {    // 测试”处理中“的结果不假装为可重放状态
        var result = IdempotencyResult.inProgress("idem-wallet-pay-001"); // 创建处理中的结果

        assertThat(result.status()).isEqualTo(IdempotencyStatus.IN_PROGRESS);  // 断言状态为处理中
        assertThat(result.completedAt()).isNull();  // 断言处理中时完成时间为空
        assertThat(result.replayable()).isFalse();   // 断言结果标记为不可重放
    }

    @Test   // 标记为测试方法
    void completedResultRequireKeyValueAndOffsetTimestamp() {   // 测试已经完成的结果是否强制要求键、值和带偏移的时间戳
        assertThatThrownBy(() -> IdempotencyResult.completed(" ", "flow-1", REQUESTED_AT))  // 空格键报错
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyResult.completed("idem-1", null, REQUESTED_AT)) // 空结果值报错
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IdempotencyResult.completed("idem-1", "flow-1", null)) // 空时间报错
                .isInstanceOf(IllegalArgumentException.class);
    }
}
