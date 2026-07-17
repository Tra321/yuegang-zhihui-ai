package com.yuegang.zhihui.gateway;

/** 仅限内部信任的交换属性键：这些值绝对禁止直接从客户端 Header 中获取 **/
interface GatewaySecurityAttributes {
    String TRACE_ID = GatewaySecurityAttributes.class.getName() + ".traceId";
    String REQUEST_ID = GatewaySecurityAttributes.class.getName() + ".requestId";
    String AUTHENTICATION_PRINCIPAL = GatewaySecurityAttributes.class.getName() + ".principal"; // 已认证的主体对象
}
