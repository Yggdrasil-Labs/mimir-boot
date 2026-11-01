package com.yggdrasil.labs.common.exception;

/**
 * 错误定义
 *
 * <p>描述可复用的错误码与默认消息，通常由错误枚举实现，作为 {@code R.failCode}、{@code BizException.fromError}、 {@code
 * SystemException.fromError} 及批量失败项的输入。
 *
 * <p>与 {@link IException} 签名相同但语义不同：后者表示已发生的异常实例，其消息可能已被覆盖，二者不可互换。
 *
 * @author Yggdrasil Labs
 * @since 2.4.0
 */
public interface ErrorDefinition {

    /** 错误码 */
    String getCode();

    /** 默认错误信息，允许为 null */
    String getMessage();
}
