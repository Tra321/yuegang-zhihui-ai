package com.yuegang.zhihui.system.api;

import java.util.List;

/**
 * 数据字典管理范围（用于后台管理界面）
 */
public record DictionaryAdminView(String code, String name, boolean enabled, long version, List<Item> items) {// 定义字典管理视图：包含字典编码、名称、状态、版本和明细项列表
    /**
     * 内部记录类：字典项明细
     */
    public record Item(String key, String value, int sortOrder, boolean enabled, long version) { // 定义字典项详情：键、值、排行权重、启用状态、版本
    }
}
