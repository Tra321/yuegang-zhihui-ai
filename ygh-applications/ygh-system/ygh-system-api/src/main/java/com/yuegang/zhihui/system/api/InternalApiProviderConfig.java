package com.yuegang.zhihui.system.api;

/**
 * 内部使用的 AI 提供商完整配置对象
 */
public record InternalApiProviderConfig( // 定义内部 AI 配置记录类
                                         String provider,    // 字段：供应商标识
                                         String baseUrl,    // 字段：基础地质
                                         String chatModel,    // 字段：对话模型
                                         String embeddingModel,    // 字段：向量模型
                                         boolean webSearchEnabled, // 字段：是否启用联网搜索
                                         String apiKey,    // 字段：明文 API 密钥
                                         long version) {    // 字段：版本号
    /**
     * 检查配置是否已完成（所有必填项是否均已填写）
     */
    public boolean configured() {    // 定义逻辑方法
        return apiKey != null && !apiKey.isBlank() // 密钥不能为空且非空白
                && chatModel != null && !chatModel.isBlank() // 对话模型不能为空且非空白
                && embeddingModel != null && !embeddingModel.isBlank(); // 向量模型不能为空且非空白
    }
}
