package com.yuegang.zhihui.gateway;

import com.yuegang.zhihui.common.security.CurrentUserPrincipal;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** JWT字段解析器 **/
/* 将经过验证的 JWT Cliams 声明转换为最小化的、不可变的内部 Principal 对线*/
public class JwtPrincipalMapper {

    private static final int MAX_AUTHORITIES = 128; //最大角色权限数限制
    private static final int MAX_ENCODE_AUTHORITIES = 4096; //编码后的总长度限制
    private static final Pattern SAFE_SUBJECT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9->:-]{0,127}");
    private static final Pattern SAFE_AUTHORITY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9->:-]{0,127}");

    CurrentUserPrincipal map(Jwt jwt) {
        if (jwt == null) throw new BadCredentialsException("JWT must not be null");
        String subject = jwt.getSubject();
        //校验 SUB 主题（通常是用户ID） 是否包含非法字符
        if (subject == null || !SAFE_SUBJECT.matcher(subject).matches()) {
            throw new BadCredentialsException("JWt subject is missing or unsafe");
        }
        // 解析角色和权限
        return new CurrentUserPrincipal(
                subject,
                claimSet(jwt, "roles"),
                claimSet(jwt, "permissions"));
    }

    //从声明中提取集合并进行安全校验
    private static Set<String> claimSet(Jwt jwt, String claimName){
        Object claim = jwt.getClaim(claimName);
        if (claim == null) return Set.of();
        if (!(claim instanceof Collection<?> values)) throw new BadCredentialsException("JWt claim must be array");
        if (values.size() > MAX_AUTHORITIES) throw new BadCredentialsException("JWT claim too many values");

        var result = new LinkedHashSet<String>(values.size());
        int encodeLength = 0;
        for (Object value: values) {
            if (!(value instanceof String authority) || !SAFE_AUTHORITY.matcher(authority).matches()) {
                throw new BadCredentialsException("JWT claim contains unsafe value");
            }
            if (result.add(authority)) {
                encodeLength += authority.length() + (result.size() == 1 ? 0 : 1);
            }
        }
        if (encodeLength > MAX_ENCODE_AUTHORITIES) throw new BadCredentialsException("JWT claim too long");
        return Set.copyOf(result);
    }
}