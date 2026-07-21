package com.yuegang.zhihui.common.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** 使用单键原子 Lua 操作实现的分布式锁底层操作指令集。 */
public class SpringDataRedisLockCommands implements RedisLockCommands{

    static final RedisScript<Long> RELEASE_IF_OWNER = new DefaultRedisScript<>( // 定义释放锁脚本
            "if redis.call('get', KEY[1]) == ARGV[1] then "
                    + "return redis.call('del', KEY[1]) else return 0 end", // 语义：如果值匹配则删除，否则返回0（原子操作）
            Long.class); // 返回类型为 Long

    static final RedisScript<Long> RENEW_IF_OWNER = new DefaultRedisScript<>( // 定义续约锁脚本
            "if redis.call('get', KEY[1]) == ARGV[1] then "
                + "return redis.call('expire', KEY[1], ARGV[2]) else return 0 end", // 语义：如果值匹配则跟新毫秒级过期时间
            Long.class); // 返回类型为Long

    private final StringRedisTemplate redis; // 声明 redis 模板

    public SpringDataRedisLockCommands(StringRedisTemplate redis) { // 构造函数
        this.redis = Objects.requireNonNull(redis, "redis must be null"); // 校验并注入
    }

    @Override // 加锁命令
    public boolean setIfAbsent(String key, String owner, Duration lease) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, owner, lease)); // 调用 Redis 的 SET NX EX 命令
    }

    @Override // 释放锁命令
    public boolean releaseIfOwner(String key, String owner) {
        Long result = redis.execute(RELEASE_IF_OWNER, List.of(key), owner); // 执行上述删除 Lua 脚本
        return Long.valueOf(1L).equals(result); // 如果是 1 则代表删除成功
    }

    @Override // 续约命令
    public boolean renewIfOwner(String key, String owner, Duration lease) {
        Long result = redis.execute(
                RENEW_IF_OWNER, List.of(key), owner, Long.toString(lease.toMillis()));  //  传入 Key ， Owner 和新续约的毫秒数
        return Long.valueOf(1L).equals(result); // 成功返回 true
    }
}
