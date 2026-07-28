package com.yuegang.zhihui.system.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 更新系统参数配置请求 DTO
 */
public record UpdateSystemSettingRequest(@NotBlank @Size(max = 2000) String value, // 配置值：非空且最长2000字符
                                         @NotBlank @Pattern(regexp = "STRING|INTEGER|BOOLEAN|JSON") String valueType,// 类型：必须属于定义的枚举值
                                         boolean secret, @PositiveOrZero long version) { // 是否敏感加密、版本号
}

