package com.yuegang.zhihui.common.core;

import java.util.List;
import java.util.Objects;

/**
 * 在 {@link ApiResponse} 内部返回的稳定的分页数据负载。
 */
public record PageResponse<T>(
        List<T> records,        // 当前页的数据列表
        int pageNo,             // 当前页码
        int pageSize,           // 每页记录
        long total,             // 总记录数
        long pages) {           // 总页数

    public PageResponse {    // 构造逻辑
        records = List.copyOf(Objects.requireNonNull(records, "records must not be null")); // 强制要求记录非空并转为不可变列表
        if (pageNo < 1) {    // 校验页码
            throw new IllegalArgumentException("pageNo must be at least 1"); // 报错
        }
        if (pageSize < 1 || pageSize > PageRequest.MAX_PAGE_SIZE) {    // 校验页容量
            throw new IllegalArgumentException("pageSize is outside the supported range"); // 报错
        }
        if (total < 0 || pages < 0) {    // 校验总记录数
            throw new IllegalArgumentException("total and pages must not be negative"); // 报错
        }
    }

    public static <T> PageResponse<T> of(List<T> records, PageRequest request, long total) {  // 静态转换工厂
        Objects.requireNonNull(request, "request must not be null"); // 校验分页请求非空总页数算法
        var pages = total == 0 ? 0 : ((total - 1) / request.pageSize() + 1); // 自动计算总页数
        return new PageResponse<>(records, request.pageNo(), request.pageSize(), total, pages); // 封装并返回
    }
}