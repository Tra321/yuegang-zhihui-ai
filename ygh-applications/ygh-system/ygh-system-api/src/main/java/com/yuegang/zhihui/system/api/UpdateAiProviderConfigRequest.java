package com.yuegang.zhihui.system.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 更新 AI 供应商配置请求 DTO
 */
public record UpdateAiProviderConfigRequest( // 定义记录类
                                             @NotBlank @Pattern(regexp = "DOUBAO_ARK") String provider,// 供应商：非空且目前固定为豆包方舟
                                             @NotBlank @Size(max = 500) String baseUrl, // 基础地址：非空且最多500个字符
                                             @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{3,200}") String chatModel,// 对话模型：非空且满足安全标识符正则
                                             @NotBlank @Pattern(regexp = "[A-Za-z0-9._:-]{3,200}") String embeddingModel,// 向量模型：非空且满足安全标识符正则
                                             boolean webSearchEnabled, // 字段：是否启用联网搜索
                                             @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) @Size(max = 4096) String apiKey,//API密钥：长度限4096，标记为只写属性（不返回给前端）
                                             long version) { // 字段：用于乐观锁控制版本号
}

