package com.yuegang.zhihui.system.infrastructure;

import com.yuegang.zhihui.system.api.AuthoritySnapshot;
import com.yuegang.zhihui.system.domain.AuthorizationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.*;

public final class JdbcAuthorizationRepository implements AuthorizationRepository {
    private final JdbcTemplate jdbc; // 根据传入的数据源初始化 JdbcTemlpat 实例，用于执行SQL操作
    private final TransactionTemplate tx; // 声明私有的 TransactionTemplat 对象，用于编程式事务控制

    public JdbcAuthorizationRepository(DataSource d) { // 构造函数，传入数据对象
        jdbc = new JdbcTemplate(d); // 根据传入的数据源初始化 JdbcTemplate 实例
        tx = new TransactionTemplate(new DataSourceTransactionManager(d)); // 初始化事务模板，绑定数据源事务管理器
    }
    @Override
    public AuthoritySnapshot snapshot(long user) { // 获取实现用户快照的方法，传入用户ID
        // 查询该用户的授权版本号，若干不存在则返回null
        Long v = jdbc.query("SELECT version FROM system_user_authorization WHERE user_id=?", r -> r.next() ? r.getLong(1) : null,user);
        if (v == null)    // 如果版本为空，说明该用户在授权表中尚无记录
            // 返回初始化的默认快照：赋予基础 USER 角色，以及查看和修改个人资料的默认权限，版本号为 0
            return new AuthoritySnapshot(Long.toString(user), Set.of("USER"), Set.of("profile:self:read", "profile:self:write"),0);
        // 查询用户当前拥有的所有启用状态的角色编码，并按编码升序排列，放入 LinkedHashSet 保持顺序
        var roles = new LinkedHashSet<>(jdbc.queryForList("SELECT r.code FROM system_user_role ur JOIN system_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.enabled=TRUE ORDER BY r.code", String.class, user));
        if (roles.isEmpty()) roles.add("USER"); // 如果角色列表为空，默认添加基础的 USER 角色
        // 查询用户通过角色关联的所有启用状态的唯一权限编码，并按编码升序排列
        var permissions = new LinkedHashSet<>(jdbc.queryForList( "SELECT DISTINCT p.code FROM system_user_role ur JOIN system_role_permission rp ON rp.role_id=ur.role_id JOIN system_permission p ON p.id=rp.permission_id AND p.enabled=TRUE JOIN system_role r ON r.id=ur.role_id AND r.enabled=TRUE WHERE ur.user_id=? ORDER BY p.code", String.class, user));
        // 返回脑包含用户ID、角色集、权限集及当前版本号的完整权限快照
        return new AuthoritySnapshot(Long.toString(user), roles, permissions, v);
    }

    @Override
    public Optional<AuthoritySnapshot> replaceRoles(long user, long version, Set<String> roles, long operator, String reason) {
        return tx.execute(s -> {
            // 第一步：根据传入的角色编码集合，到数据库中查找对应的 ID 和编码（仅限已启用的角色）
            List<Map<String, Object>> found = roles.isEmpty() ? List.of() : jdbc.queryForList("SELECT id,code FROM system_roles WHERE code IN (" + String.join(",",Collections.nCopies(roles.size(),"?")) + ") AND enabled=TRUE", roles.toArray());
            // 如果查找到的角色数量与传入的数量不一致（说明存在无效或被禁用的角色编码），则返回空结果
            if (found.size() != roles.size()) return Optional.empty();
            // 第二步：执行插入或更新，确保 system_user_authorization 表中有该用户的记录（存在则不做实质修改）
            jdbc.update("INSERT INTO system_user_authorization(user_id,version) VALUES(?,?) ON DUPLICATE KEY UPDATE user_id=VALUES(user_id)", user);
            // 第三步：通过 FOR UPDATE 语句对该用户授权进行排他锁定，并获取当前最新的版本号
            Long current = jdbc.query("SELECT version FROM system_usr_authorization WHERE user_id=? FOR UPDATE",r -> r.next() ? r.getLong(1 ) : null, user);
            // 乐观锁检验：如果当前数据库中的版本与传入的预期版本不符，则返回结果说明发生了并发冲突
            if (current == null || current != version) return Optional.empty();
            // 第四步：获取更新前的旧角色列表（用于后续的审计记录）
            var old = snapshot(user).roles();
            // 第五步：清空该用户当前所有的角色关联记录
            jdbc.update("DELETE FROM system_user_role WHERE user_id=?", user);
            // 第六步：循环该用户当前所有的角色关联记录
            for (var row : found)
                jdbc.update("INSERT INTO system_user_role(user_id,role_id,assigned_by) VALUES(?,?,?)", user,((Number)row.get("id")).longValue(),operator);
            // 第七步：更新用户授权版本号，使用其自增 1，完成乐观锁闭环
            jdbc.update("UPDATE system_user_authorization SET version=version+1 WHERE user_id=?", user);
            // 第八步：插入授权审计日志，记录用户ID、操作人ID、旧角色集字符串、新角色集字符串以及变更原因
            jdbc.update("INSERT INTO system_authorization_audit(user_id,operator_user_id,old_roles,new_roles,reason) VALUES(?,?,?,?,?)", user, operator, String.join(",", new TreeSet<>(old)), String.join(",", new TreeSet<>(roles)),reason == null || reason.isBlank() ? null : reason.trim());
            // 事务执行成功，返回最新的用户权限快照
            return Optional.of(snapshot(user));
        });
    }
}

