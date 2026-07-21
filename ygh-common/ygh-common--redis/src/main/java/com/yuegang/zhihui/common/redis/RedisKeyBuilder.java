package com.yuegang.zhihui.common.redis;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 构造由特定环境和服务拥有的抗冲突 Redis 键
 */
public class RedisKeyBuilder {  // 类开始定义
    private static final Pattern NAMESPACE_SEGMENT =
            Pattern.compile("[a-z0-9][a-z0-9-]{0,31}"); // 定义命名空间段正则，小写字母数字开头，允许中划线，长度32以内
    private static final Pattern IDENTIFIER =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    public String bulid(String environment,String service,String business,String identifier){  // 核心构造方法
        requireNamespaceSegment(environment,"environment"); // 校验环境段规范
        requireNamespaceSegment(service,"service"); // 校验服务段规范
        requireNamespaceSegment(business,"business");   // 校验业务段规范
        requireIdentifier(identifier); // 校验唯一标识符规范
        return "ygh:" + environment + ":" + service + ":" + business + ":" + identifier;    // 拼接为 ygh:env:svc:biz:id 格式
    }

    public boolean isCanonical(String key){ // 判断一个 key 是否符合系统预设规范
        if (key == null || key.length() > 235) { // 长度超过限制返回 false
            return false;
        }

        var segments = key.split(":",-1); // 按冒号拆分段
        return segments.length == 5 // 必须且只能有 5 段
                && "ygh".equals(segments[0]) // 第一段必须是 ygh
                && NAMESPACE_SEGMENT.matcher(segments[1]).matches() // 校验环境段
                && NAMESPACE_SEGMENT.matcher(segments[2]).matches() // 校验服务段
                && NAMESPACE_SEGMENT.matcher(segments[3]).matches() // 校验业务段
                && IDENTIFIER.matcher(segments[4]).matches();   // 校验标识符段
    }

    private static void requireNamespaceSegment(String segment,String name) {   // 辅助方法：强制段校验
        Objects.requireNonNull(segment,name + " must not be null"); // 段内容不能为空
        if (!NAMESPACE_SEGMENT.matcher(segment).matches()) { // 正则匹配校验
            throw new IllegalArgumentException(name + " must match " +
                    NAMESPACE_SEGMENT.pattern()); // 格式错误抛出异常
        }
    }

    private static void requireIdentifier(String identifier) { // 辅助方法，强制标识符校验
        Objects.requireNonNull(identifier,"identifier must not be null");   // 不能为空
        if (!IDENTIFIER.matcher(identifier).matches()) { // 正则匹配校验
            throw new IllegalArgumentException("identifier must match " + IDENTIFIER.pattern());
        }
    }
}
