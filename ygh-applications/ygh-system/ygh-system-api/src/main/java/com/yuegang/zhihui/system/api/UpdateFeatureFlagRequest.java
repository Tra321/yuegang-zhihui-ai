package com.yuegang.zhihui.system.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 更新功能开关请求 DTO
 */
public record UpdateFeatureFlagRequest(boolean enabled, // 启用状态、放量比例(限制0-100) 规则JSON
                                       @Min(0) @Max(100) int rolloutPercent,
                                       String rulesJson,
                                       long version) { // 版本号
}

