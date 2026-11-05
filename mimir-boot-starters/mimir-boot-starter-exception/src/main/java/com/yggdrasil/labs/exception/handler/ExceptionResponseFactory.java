package com.yggdrasil.labs.exception.handler;

/**
 * 异常响应工厂接口
 *
 * <p>负责将异常处理结果转换为响应体对象。接入方可自定义实现以适配不同架构（如 COLA 5）。
 *
 * @author Yggdrasil Labs
 * @since 2.1.0
 */
@FunctionalInterface
public interface ExceptionResponseFactory {

    /**
     * 构建异常响应体
     *
     * <p>只负责响应体，每次异常处理至多调用一次。返回 null 表示主动无响应体；返回 {@code ResponseEntity} 时仅提取体和合法端到端头， HTTP
     * 状态与异常同名协议头由处理器确定。无实体状态不调用工厂；工厂抛出 Exception 时处理器直接降级为 R，data 为 null。 内容协商及实际写出仍交给原生
     * MVC，其失败不属于工厂降级。
     *
     * @param code 错误码
     * @param message 错误信息
     * @param data 附加数据（如校验错误列表），可为 null
     * @return 普通响应体、null 或携带响应体及可接受头的 {@code ResponseEntity}
     */
    Object createResponse(String code, String message, Object data);
}
