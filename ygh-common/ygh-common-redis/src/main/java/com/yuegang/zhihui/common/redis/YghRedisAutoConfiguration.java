package com.yuegang.zhihui.common.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 共享的 Redis 键构造、TTL 策略及持有者安全型分布式短锁的基础设施自动配置类。 */
@AutoConfiguration(after = DataRedisAutoConfiguration.class) // 在 Spring Data Redis 配置之后加载
public class YghRedisAutoConfiguration { // 类点=定义开始

    @Bean // 注册 Bean
    @ConditionalOnMissingBean // 仅当容器中没有此类型的 Bean 时生效
    public RedisKeyBuilder redisKeyBuilder() { // 注册键构造器
        return new RedisKeyBuilder(); // 返回实例
    }

    @Bean // 注册 Bean
    @ConditionalOnMissingBean // 防止键重复定义
    public TtlJitterPolicy ttlJitterPolicy() { // 注册过期时间抖动策略
        return new TtlJitterPolicy(); // 返回默认 10% 抖动策略
    }

    @Bean // 注册 Bean
    @ConditionalOnBean(StringRedisTemplate.class) // 仅当纯在阻塞时 Redis 模板时注册
    @ConditionalOnMissingBean // 默认实现
    public RedisLockCommands redisLockCommands(StringRedisTemplate redis) { // 注册锁原子命令执行器
        return new SpringDataRedisLockCommands(redis); //返回基于 Spring Data 的实现
    }

    @Bean // 注册 Bean
    @ConditionalOnBean(StringRedisTemplate.class) // 依赖基础的 Redis 设施
    @ConditionalOnMissingBean  // 默认实现
    public LockOwnerTokenGenerator lockOwnerTokenGenerator() { // 注册锁所有者令牌生成器
        return new SecureLockOwnerTokenGenerator(); // 返回安全的令牌生成器
    }

    @Bean // 注册 Bean
    @ConditionalOnBean({StringRedisTemplate.class, RedisLockCommands.class}) // 必须具备上述依赖
    @ConditionalOnMissingBean // 默认实现
    public RedisDistributedLock redisDistributedLock( // 注册分布式锁核心门面
            RedisLockCommands commands, // 注入指令集
            RedisKeyBuilder keys, // 注入键工具
            LockOwnerTokenGenerator ownerTokens // 注入令牌生成器
    ) {
        return new RedisDistributedLock(commands, keys, ownerTokens); // 返回封装好的分布式锁对象
    }

    @Bean // 注册 Bean
    @ConditionalOnMissingBean // 默认实现
    public SessionRedisKeys sessionRedisKeys( // 注册 Session 专用键生成器
            RedisKeyBuilder keys, // 依赖基础键工具
            @Value("${ygh.redis.session.keys.prefix:session}") String environment) { // 注入当前系统环境配置，默认为dev
        return new SessionRedisKeys(keys, environment);
    }

    @Bean // 注册 Bean
    @ConditionalOnBean(StringRedisTemplate.class) // 仅在非响应式环境注册存储器
    @ConditionalOnMissingBean // 默认实现
    public RedisSessionStateStore redisSessionStateStore(StringRedisTemplate redis, SessionRedisKeys keys) { // 注册 Session 状态仓库
        return new RedisSessionStateStore(redis, keys); //返回实例
    }

    @Bean // 注册 Bean
    @ConditionalOnBean(ReactiveStringRedisTemplate.class) // 仅在响应式 WebFlux 环境注册校验器
    @ConditionalOnMissingBean // 默认实现
    public ReactiveRedisSessionValidator reactiveRedisSessionValidator( // 注册响应式会话校验器
            ReactiveStringRedisTemplate redis, SessionRedisKeys keys) { // 注入响应式模板
        return new ReactiveRedisSessionValidator(redis, keys); // 返回实例
    }
}
