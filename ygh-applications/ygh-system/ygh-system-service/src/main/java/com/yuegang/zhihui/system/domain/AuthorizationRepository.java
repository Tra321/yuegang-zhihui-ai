package com.yuegang.zhihui.system.domain;

import com.yuegang.zhihui.system.api.AuthoritySnapshot;

import java.util.Optional;

/**
 * 授权仓储接口
 */
public interface AuthorizationRepository { // 定义一个名为 AuthorizationRepository 的公共接口，作为授权数据的持久化抽象类
    /**
     * 获取用户权限快照
     */
    AuthoritySnapshot snapshot(long userId); // 声明 snapshot 方法，根据用户唯一标识（userId）查询并返回该用户角色与权限快照对象

    /**
     * 替换用户角色
     */
    Optional<AuthoritySnapshot> replaceRoles(long userId, long expectedVersion, java.util.Set<String> roles, long operator, String reason);
    // 声明 replaceRoles 方法，用于更新用户的角色关联。
    // 参数说明：
    // userId: 目标用户 ID;
    // expectedVersion: 预期的版本号（用于数据库乐观锁校验，防止并发冲突）；
    // roles: 新的角色编码字符串集合；
    // operator: 当前执行该操作的操作员 ID;
    // reason: 执行此次角色变更的原因（用于审计）
    // 返回值：使用 Optional 包装的更新后的权限快照对象，若版本冲突或更新失败则返回 Optional。empty()
}
