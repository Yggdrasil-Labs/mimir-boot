package com.yggdrasil.labs.exception.handler;

import com.yggdrasil.labs.common.response.R;

import java.io.Serializable;

/**
 * 默认异常响应工厂实现
 *
 * <p>返回 {@link R} 统一响应格式，作为框架默认行为。</p>
 *
 * @author Yggdrasil Labs
 * @since 2.1.0
 */
public class DefaultExceptionResponseFactory implements ExceptionResponseFactory {

    @Override
    public Object createResponse(String code, String message, Object data) {
        if (data instanceof Serializable s) {
            return new R<>(code, message, s);
        }
        return R.fail(code, message);
    }
}
