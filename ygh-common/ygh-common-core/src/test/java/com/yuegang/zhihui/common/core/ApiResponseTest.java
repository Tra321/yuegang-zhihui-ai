package com.yuegang.zhihui.common.core;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class ApiResponseTest { // 定义 API 响应测试类

    @Test // 标记为测试方法
    void successResponseCarriesStableEnvelopeFields() { // 测试成功响应是否携带指定的外壳字段
        var before = OffsetDateTime.now(); // 记录执行前的时间点

        var response = ApiResponse.success("payload", "trace-001"); // 创建一个成功的响应对象

        assertThat(response.code()).isEqualTo(ErrorCode.SUCCESS.code()); // 断言状态码为SUCCESS 对应的代码
        assertThat(response.message()).isEqualTo("payload"); // 断言返回的数据内容正确
        assertThat(response.traceId()).isEqualTo("trace-001"); // 断言追踪 ID 正确
        assertThat(response.timestamp()).isAfterOrEqualTo(before); // 断言响应时间戳在操作时间之后或相等
    }

    @Test   // 标记为测试方法
    void failureResponseDoesNotRequireBusinessData() { // 测试失效响应不需要业务数据
        var response = ApiResponse.failure(ErrorCode.VALIDATION_ERROR, "参数错误", "trace-002"); // 创建失败响应

        assertThat(response.code()).isEqualTo("VALIDATION_ERROR"); // 断言状态码为参数校验失败
        assertThat(response.message()).isEqualTo("参数错误"); // 断言提示消息为传入的自定义消息
        assertThat(response.data()).isNull(); // 断言失败响应的数据负载应为 null
        assertThat(response.traceId()).isEqualTo("trace-002"); // 断言追踪 ID 正确
    }
}