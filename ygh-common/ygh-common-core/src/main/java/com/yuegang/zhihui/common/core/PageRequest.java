package com.yuegang.zhihui.common.core;

/**
 * 以 1 为起始索引的 API 分页请求，带有一个强制性的上限。
 */
public record PageRequest(int pageNo, int pageSize) { // 分页记录

    public static final int DEFAULT_PAGE_NO = 1; // 默认页码：1
    public static final int DEFAULT_PAGE_SIZE = 20; // 默认每页大小：20
    public static final int MAX_PAGE_SIZE = 100; // 每页最大允许拉取 100 条

    public PageRequest { // 参数约束校验
        if (pageNo < 1) { // 页码必须从 1 开始
            throw new IllegalArgumentException("pageNo must be at least 1");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) { // 每页大小范围校验
            throw new IllegalArgumentException("pageSize must be between 1 and" + MAX_PAGE_SIZE); // 报错
        }
    }

    public static PageRequest defaults() { // 获取默认分页配置 no usages
        return new PageRequest(DEFAULT_PAGE_NO, DEFAULT_PAGE_SIZE); // 返回 1, 20
    }

    public long offset() { // 计算数据库查询所需的偏移量
        return Math.multiplyExact((long) pageNo - 1L, pageSize); // 公式：（pageNo - 1） * pageSize
    }
}
