package com.yuegang.zhihui.system.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 保存/更新字典类型（分类）请求 DTO
 */
public record SaveDictionaryTypeRequest(@NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}") String code,// 编码：非空且符合全大写蛇形正则
                                        @NotBlank @Size(max = 100) String name, boolean enabled, // 名称：非空且限100位，启用状态
                                        @PositiveOrZero long version) { // 版本号校验：非负数
}

