package com.yuegang.zhihui.common.security;

import com.yuegang.zhihui.common.core.BusinessException;
import com.yuegang.zhihui.common.core.ErrorCode;

public final class PermissionDeniedException extends BusinessException { // 定义权限拒绝异常类，继承自业务异常基类
    public PermissionDeniedException() { // 无参构造函数
        super(ErrorCode.PERMISSION_DENIED);
    }

    public PermissionDeniedException(String message) { // 带自定义消息的构造函数
        super(ErrorCode.PERMISSION_DENIED, message); // 传入标准错误码及具体错误描述
    }
}