package com.yuegang.zhihui.common.redis;

import reactor.core.publisher.Mono;

@FunctionalInterface    // 标识为函数式接口
public interface ReactiveSessionValidator { // 定义响应式会话校验规范
    Mono<Boolean> valid(long accountId, String jwtId);  // 声明异步校验方法，返回布尔值类型你的 Mono
}
