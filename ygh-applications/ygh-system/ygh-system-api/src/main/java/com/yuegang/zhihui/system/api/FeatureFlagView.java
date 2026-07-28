package com.yuegang.zhihui.system.api;

/**
 * 特性开关（功能开关）视图对象
 */
public record FeatureFlagView(String key, boolean enabled, int rolloutPercent, String rulesJson, long version) {// //包含开关键、是否启用、回复放量比例(0-100)、规则JSON字符串、版本号
}
