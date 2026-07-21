package com.yuegang.zhihui.common.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PageRequestTest { // 定义分页请求测试页

    @Test
        // 标记为测试方法
    void defaultsArePageOneAndTwenTyRows() { // 测试默认分页参数是否为第 1 页且每页 20 行
        assertThat(PageRequest.defaults()).isEqualTo(new PageRequest(1, 20)); // 断言默认对象符合规则
    }

    @Test // 标记为测试方法
    void rejectPageSizeAboveOneHundred() { // 测试是否拒绝每页大小超过 100 的请求
        assertThatThrownBy(() -> new PageRequest(1, 101)) // 尝试设置 101 行
                .isInstanceOf(IllegalArgumentException.class) // 应该抛出异常
                .hasMessageContaining("pageSize"); // 异常消息应该包含字段名
    }

    @Test // 标记为测试方法
    void calculateZeroBaseOffset() { // 测试计算以 0 为起始索引的数据库偏移量
        assertThat(new PageRequest(3, 20).offset()).isEqualTo(40L); // 第 3 页，每页 20，偏移量 (3-1)*20 = 40
    }
}
