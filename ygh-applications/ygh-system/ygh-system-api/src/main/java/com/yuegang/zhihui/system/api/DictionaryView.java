package com.yuegang.zhihui.system.api;

import java.util.List;

/**
 * 业务侧字典视图（精简版，用于业务展示）
 */
public record DictionaryView(String code, String name, List<Item> items) { // 定义字典视图：仅包含编码、名称和项列表
    /**
     * 内部记录类：精简字典项
     */
    public record Item(String key, String value, int sortOrder) { // 字典项：键、值、排序权重
    }
}
