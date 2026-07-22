package com.yuegang.zhihui.common.mybatis;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuegang.zhihui.common.core.PageRequest;
import com.yuegang.zhihui.common.core.PageResponse;

import java.util.Objects;
import java.util.function.Function;

/**
 * 在持久层分页类型与稳定的外部页面契约之间进行转换。 */
public class MybatisPageAdapter { // 解耦 MyBatis Plus 与业务 API

    private MybatisPageAdapter(){ } // 静态工具类，禁止实例化

    public static <T> Page<T> toMybatisPage(PageRequest request){ // 将自定义 PageRequest 转为 MP 的 Page 对象
        Objects.requireNonNull(request, "request cannot be null");
        return new Page<>(request.pageNo(),request.pageSize(),true); // 设置当前页、页大小、并开启总数统计

    }

    public static <S, T>PageResponse<T> toPageResponse(IPage<S> page, Function<? super S, T> mapper){ // 将 MP 的 IPage 转为业务响应 PageResponse
        Objects.requireNonNull(page, "page cannot be null");
        Objects.requireNonNull(mapper, "mapper cannot be null");
        Objects.requireNonNull(page.getRecords(), "page record must not be null");

        int pageNo = Math.toIntExact(page.getCurrent()); // 转换页码
        int pageSize = Math.toIntExact(page.getSize()); // 转换页大小
        var request = new PageRequest(pageNo,pageSize); // 构造请求上下文
        var records = page.getRecords().stream().map(mapper).toList(); // 转换传入的 mapper 转换数据 （如 Entity -> DTO）
        return PageResponse.of(records,request,page.getTotal()); // 组装响应对象
    }
}
