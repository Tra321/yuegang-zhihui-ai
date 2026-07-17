package com.yuegang.zhihui.gateway;

/** 定义仅在网关内部和下游微服务间共享的边缘请求头常量 */
interface GatewayHeaders {
    String TRACE_ID = "X-Trance_Id"; // 全局追踪ID
    String REQUEST_ID = "X-Request-Id"; // 单次请求ID
    String USER_ID = "X-YGH-User_Id"; // 解析后的用户ID
    String ROLES = "X-YGH-Roles"; // 用户角色

    String PERMISSIONS="X-YGH-Permissions"; // 用户权限点
    String CLIENT_IP = "X-YGH-Client-IP"; // 真实客户端IP
    String CLIENT_IP_TIMESTAMP = "X-YGH-Client-IP-Timestamp"; // 客户端IP时间戳
    String CLIENT_IP_SIGNATURE = "X-YGH-Client-IP-Signature"; // 客户端IP签名
    String USER_CONTEXT_TIMESTAMP = "X-YGH-User-Context-Timestamp"; // 用户上下文时间戳
    String USER_CONTEXT_SIGNATURE = "X-YGH-User-Context-Signature"; // 用户上下文签名
}
