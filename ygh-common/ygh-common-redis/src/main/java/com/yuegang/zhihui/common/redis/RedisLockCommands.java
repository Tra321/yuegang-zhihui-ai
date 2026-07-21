package com.yuegang.zhihui.common.redis;

import java.time.Duration;

/** 持有者校验型分布式锁所需的原子 Redis 原语操作集合。 */
public interface RedisLockCommands { // 接口定义开始

    boolean setIfAbsent(String key, String owner, Duration lease); // 原子地尝试设置 key，仅当不存在时生效（加锁）

    boolean releaseIfOwner(String key, String owner); // 原子地校验 value 并删除 key（释放锁）

    boolean renewIfOwner(String key, String owner, Duration lease); // 原子地校验 value 并延长过期时间（续约）
}
