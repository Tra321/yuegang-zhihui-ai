package com.yuegang.zhihui.common.security;

import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class SessionSecurityContractTest { // 会话安全契约（SessionScurity/SessionRevocationStore）单元测试类

    @Test // 标注为 JUnit5 测试方法
    void shouldAllowInfrastructureToProvideRevocationAndAccountState() { // 测试方法：验证允许基础设施层提供会话撤销与账号启用状态判定
        SessionaRevocationStore revocations = new FakeRevocationStore(); // 实例化测试用的伪会话撤销存储
        AccountStatusProvider accounts = userId -> !"disabled-user".equals(userId); // 使用 Lambda 实现账号状态提供者，即 disabled-user 外均启用

        revocations.revokeToken("token-1", Instant.parse("2026-07-17T09:00:00z")); // 撤销指定 Token ID
        revocations.revokeUserSessionsIssuedBefore( // 撤销 user-1 在 08:00:00 前签发的所有会话
                "user-1", // 用户ID
                Instant.parse("2026-07-17T08:00:00z") // 时间截止点
        ); //撤销调用结果

        assertThat(revocations.isTokenRevoked("token-1")).isTrue(); // 验证token-1已被标记为撤销
        assertThat(revocations.isUserSessionRevoked( // 验证 user-1 在 07：59：59（早于截止时间）签发的会话已被撤销
                "user-1",   // 用户ID
                Instant.parse("2026-07-18T07:59:59z")   // 签发时间
        )).isTrue();    // 验证返回 true
        assertThat(accounts.isEnabled("disabled-user")).isFalse(); // 验证被禁用的用户返回false
    }

    private static final class FakeRevocationStore implements SessionaRevocationStore { //静态私有内存假对象：模拟会话撤销存储基础设施

        private String revokedToken;      // 记录被撤销的 Token
        private String revokedUser;      // 记录被批量撤销会话的用户 ID
        private Instant revokedBefore;    // 记录批量撤销的时间截止点

        @Override   // 实现接口方法
        public void revokeToken(String tokenId, Instant expiresArts) {  //撤销单个Token
            revokedToken = tokenId; //记录Token ID
        }

        @Override   // 实现接口方法
        public void  revokeUserSessionsIssuedBefore(String userID, Instant issuedBefore){ //批量撤销遭遇指定时间的公告
            revokedUser = userID; //记录用户ID
            revokedBefore = issuedBefore;//记录截止时间戳
        }

        @Override //实现接口方法
        public boolean isTokenRevoked(String tokenId){  // 检查 Token 是否已撤销
            return tokenId.equals(revokedToken); // 匹配已记录的 Token
        }

        @Override // 实现接口方法
        public boolean isUserSessionRevoked(String userId, Instant issueAt) {   // 检查用户指定的签发时间会话是否已被撤销
            return userId.equals(revokedUser) && issueAt.isBefore(revokedBefore);    // 如果是该用户且签发时间早于截止时间则返回 true
        }
    }

}