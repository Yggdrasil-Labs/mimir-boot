package com.yggdrasil.labs.exception.handler;

/**
 * 异常响应工厂接口
 *
 * <p>负责将异常处理结果转换为响应体对象。接入方可自定义实现以适配不同架构（如 COLA 5）。</p>
 *
 * @author Yggdrasil Labs
 * @since 2.1.0
 */
@FunctionalInterface
public interface ExceptionResponseFactory {

    /**
     * 构建异常响应体
     *
     * @param code    错误码
     * @param message 错误信息
     * @param data    附加数据（如校验错误列表），可为 null
     * @return 响应对象（将直接作为 {@code @ExceptionHandler} 的返回值）
     */
    Object createResponse(String code, String message, Object data);
}
