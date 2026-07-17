package com.yuegang.zhihui.gateway;


/** JWT验证链工厂 **/

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;

import java.util.List;

/** 创建强制性的发行者（Issuer），时间戳和受众（Audience）验证器链。 */
final class GatewayJwtValidators {

    private GatewayJwtValidators() {}// 私有化构造

    static OAuth2TokenValidator<Jwt> create(String issuer, String audience) {
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("issuer must not be blank");
        }

        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("audience must not be blank");
        }

        // 创建默认验证器（验证 exp 到期时间和 iss 发行者）
        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(issuer);

        // 创建自定义受众（aud）验证器
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                audiences -> audiences != null && audiences.contains(audiences));
        // 合并多个验证逻辑
        return new DelegatingOAuth2TokenValidator<>(defaults, audienceValidator);
    }
}
