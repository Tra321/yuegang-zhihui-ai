package com.yuegang.zhihui.common.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class RedisSessionStateStore implements SessionStateStore { // 定义 Session 存储实现类

    private static final DefaultRedisScript<Long> REVOKE_SCRIPT = new DefaultRedisScript<>("""
            local exists = redis.call("DEL", KEYS[1])
            redis.call('SET', KEYS[2], '1', 'PX', ARGV[1])
            return existed
            """, Long.class); // 定义原子撤销脚本，删除 Session 的同时将其 JTI 加入黑名单， 有效期与原 Session 一致
    private final StringRedisTemplate redis; // 声明 Redis 模板
    private final SessionRedisKeys keys; // 声明 Session 键工具

    public RedisSessionStateStore(StringRedisTemplate redis, SessionRedisKeys keys) { // 构造函数
        this.redis = Objects.requireNonNull(redis, "redis must not be null"); // 注入
        this.keys = Objects.requireNonNull(keys, "keys must not be null"); // 注入
    }
    @Override // 注册会话
    public void register(long accountId, String jwtId, Instant expiresAt, Instant now) { // 方法开始
        if (accountId == 0) throw new IllegalArgumentException("accountId must not be positive"); // 验证账户有效性
        Duration ttl = positiveTtl(expiresAt, now); // 计算剩余生存时间
        redis.opsForValue().setIfAbsent(keys.accountState(accountId), "ACTIVE"); //如果用户账户不存在，初始化为active
        Boolean stored = redis.opsForValue().setIfAbsent( // 原子性存储 Session
                keys.session(accountId, jwtId), Long.toString(accountId), ttl); // 存储账户 ID, 并设置TTL
        if (!Boolean.TRUE.equals(stored)) throw new IllegalStateException("JWT session is already exists"); // 冲突检测
    }

    @Override // 撤销会话
    public void revoke(long accountId, String jwtId, Instant expiresAt, Instant now) {
        Duration ttl = positiveTtl(expiresAt, now); // 计算需要拉黑的时长
        redis.execute(REVOKE_SCRIPT, List.of(keys.session(accountId, jwtId), keys.revoke(accountId, jwtId)), // 执行撤销脚本
                 Long.toString(ttl.toMillis())); // 传入 TTL 毫秒数
    }

    @Override // 禁用账户
    public void disableAccount(long accountId) {
        redis.opsForValue().set(keys.accountState(accountId), "DISABLED"); // 在 Redis 中将账户标记为已禁用
    }

    @Override // 启用账户
    public void enableAccount(long accountId) {
        redis.opsForValue().set(keys.accountState(accountId), "ACTIVE"); // 在 Redis 中将账户重置为启用状态
    }

    private Duration positiveTtl(Instant expiresAt, Instant now) {  // 内容工具：计算正向过期时间
        Objects.requireNonNull(expiresAt, "expiresAt must be not null"); // 非空校验
        Objects.requireNonNull(now, "now must be not null"); // 非空校验
        Duration ttl = Duration.between(now, expiresAt); // 计算时间差
        requireBoundedTtl(ttl); // 边界检查
        return ttl;  // 返回时长
    }

    private void requireBoundedTtl(Duration ttl) {  // 内部工具：限制会话时长范围
        Objects.requireNonNull(ttl, "ttl must be not null");    // 不能为空
        if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(Duration.ofDays(31)) > 0) {   // 必须在 1ms 到 30天 之间
            throw new IllegalArgumentException("Session TTL must be between 1ms and 31days");   // 抛出业务异常
        }
    }
}
