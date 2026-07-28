package com.yuegang.zhihui.system.api;

import java.time.OffsetDateTime;

/**
 * AI 供应商匹配视图对象（用于前端展示）
 */
public record AiProviderConfigView(// 定义共有的记录类（Record）：AI供应商配置视图
        String provider, // 供应商标识名称（如：DOUBAO_ARK）
        String baseUrl, // API 基础请求地址
        String chatModel, // 对话模型名称/端点 ID
        String embeddingModel, // 向量化模型名称/端点 ID
        boolean webSearchEnabled, // 是否启用联网搜索功能
        boolean apiKeyConfigured, // 系统中是否已配置 API 秘钥
        String apiKeyMasked, // 已脱敏显示的 API 秘钥（如：sk-****xxxx）
        long version, // 配置版本号（用于乐观锁校验）
        OffsetDateTime updatedAt) { // 最后一次更新的时间
}
