package com.yggdrasil.labs.common.exception;

/**
 * 框架统一异常接口
 *
 * 提供错误码与错误信息访问，便于全局处理器统一处理。
 */
public interface IException {

    /**
     * 错误码
     */
    String getCode();

    /**
     * 错误信息
     */
    String getMessage();
}


