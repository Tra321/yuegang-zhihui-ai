package com.yuegang.zhihui.common.security;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class PermissionContractTest { // 权限注解 (RequiresPermission) 与所有权契约单元测试类

    @Test
        // 标注为 Junit5 测试方法
    void shouldExposePermissionAnnotationAtRuntime() throws NoSuchMethodException { // 测试方法：验证权限注解在运行时可通过反射获取
        Method method = ProtectedOperations.class.getDeclaredMethod("reviewKnowledge"); // 反射获取受保护的测试方法

        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class); // 提取注解实例

        assertThat(annotation).isNotNull(); // 验证注解不为空 (RetentionPolicy 为 RUNTIME 生效)
        assertThat(annotation.value()).isEqualTo("knowledge:document:review"); // 验证注解配置的权限点字符串准确无误
    }


    @Test // 标注为 Junit5 测试方法
    void shouldUseDomainOwnershipCheckerWithoutGrantingImplicitAdminByPass() { // 测试方法：使用领域所有权检查器，且不向未明确授权的 ADMIN 角色赋予隐式越权许可
        ResourceAccessGuard guard = new ResourceAccessGuard(); // 实例化资源访问守卫
        CurrentUserPrincipal principal = new CurrentUserPrincipal("review-1", Set.of("ADMIN"), Set.of()); // 构建仅有 ADMIN 角色但没有任何权限点的用户
        ResourceOwnershipChecker<String> checker = ( CurrentUserPrincipal currentUser, String documentId) -> // 定义模拟的资源所有权逻辑
                currentUser.userId().equals("owner-of-" + documentId); // 规则：用户ID必须为"owner-of-" + 文档ID
        assertThatCode(() -> guard.requireOwnerOrPermission(
                new CurrentUserPrincipal("owner-of-doc-1", Set.of(), Set.of()), // 拥有者用户
                "doc-1", // 资源ID
                checker, // 检查器
                "knowledge:document:manage:any" // 跨所有者特权点
        )).doesNotThrowAnyException(); // 拥有者直接放行，不抛出任何异常

        org.junit.jupiter.api.Assertions.assertThrows(// 验证非拥有者访问
                com.yuegang.zhihui.common.core.BusinessException.class, // 预期抛出 BusinessException（具体为权限拒绝）
                () -> guard.requireOwnerOrPermission(
                        principal, // 仅拥有 ADMIN 角色但非拥有者且无显式跨所有者权限的用户
                        "doc-1", // 资源ID
                        checker, // 检查器
                        "knowledge:document:manage:any" // 所需跨所有者权限点
                )
        );
    }

    private static final class ProtectedOperations { // 内部私有静态类：用于测试反射读取注解 1 usage
        @RequiresPermission("knowledge:document:review") // 标注测试用权限点 1 usage
        private void reviewKnowledge() { // 模拟受保护的方法
        }
    }
}
