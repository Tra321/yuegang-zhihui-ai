package com.yuegang.zhihui.common.mq;

@FunctionalInterface // 函数式接口标识
public interface MessageBusinessOperation { // 业务操作契约，定义了必须在幂等事务内执行的代码逻辑

    void execute() throws Exception; // 执行方法：允许抛出异常以触发事务回滚
}