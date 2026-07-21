package com.yuegang.zhihui.common.redis;

import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** 短生存期的 Redis 锁，其释放和持续操作始终原子地比较持有者令牌。 */
public final class RedisDistributedLock{
    public static final Duration MIN_LEASE = Duration.ofMinutes(1); //最小租约时间为1秒
    public static final Duration MAX_LEASE = Duration.ofSeconds(5);

    private final RedisLockCommands commands;  //原子命令执行器
    private final RedisKeyBuilder keys;
    private final LockOwnerTokenGenerator ownerTokens;

    public RedisDistributedLock(    // 构造函数开始
                                    RedisLockCommands commands, // 注入底层命令
                                    RedisKeyBuilder keys,   // 注入键构造器
                                    LockOwnerTokenGenerator ownerTokens   // 注入持有者令牌生成器
    ){
        this.commands = Objects.requireNonNull(commands,"commands must not be null");   // 校验并赋值
        this.keys = Objects.requireNonNull(keys,"keys must not be null");// 校验并赋值
        this.ownerTokens = Objects.requireNonNull(ownerTokens,"ownerTokens must not be null");// 校验并赋值
    }

    public Optional<RedisLockHandle> tryAcquire(String key, Duration lease) { // 尝试获取锁的方法
        requireCannoicalKey(key);   // 校验key 是否负荷系统规范
        requireLease(lease);   // 校验租约时间是否在合法范围内
        String owner = ownerTokens.generate();  // 生成本次请求唯一持有者标识
        var handle = new RedisLockHandle(key, owner, lease);    // 构造锁句柄
        return commands.setIfAbsent(key,owner,lease) ? Optional.of(handle) : Optional.empty();  // 执行 SET NX 操作，成功则返回句柄，失败为空
    }

    public boolean release(RedisLockHandle handle) { // 释放锁的方法
        Objects.requireNonNull(handle,"handle must not be null"); // 句柄不能为空
        requireCannoicalKey(handle.key()); // 校验 key 规范
        return commands.releaseIfOwner(handle.key(), handle.owner());   // 仅当 value 匹配持有者才进行删除，防止误删他人的锁
    }

    public boolean renew(RedisLockHandle handle, Duration lease) {
        Objects.requireNonNull(handle,"handle must not be null"); // 句柄不能为空
        requireCannoicalKey(handle.key());
        requireLease(lease);
        return commands.renewIfOwner(handle.key(), handle.owner(), lease);
    }

    static void requireLease(Duration lease) { // 静态工具：租约范围校验
        Objects.requireNonNull(lease,"lease must not be null"); // 不能为空
        if (lease.compareTo(MIN_LEASE) < 0 || lease.compareTo(MAX_LEASE) > 0) { // 判断是否在 1s 到 5min 之间
            throw new IllegalArgumentException("lease must be between 1 second and 5 minutes"); // 不合法抛出异常
        }
    }

    private void requireCannoicalKey(String key) { // 私有方法：强制要求key使用系统规范的格式
        if (!keys.isCanonical(key)) { // 如果不符合 ygh:env:service..格式
            throw new IllegalArgumentException("lock key must the canonical ygh namespace");    // 抛出安全限制异常
        }
    }
}
