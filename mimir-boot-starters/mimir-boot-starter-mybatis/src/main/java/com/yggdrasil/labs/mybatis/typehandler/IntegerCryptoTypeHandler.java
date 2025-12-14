package com.yggdrasil.labs.mybatis.typehandler;

import com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider;

/**
 * Integer 字段加解密 TypeHandler。
 *
 * <p>注意：此 TypeHandler 不会自动注册为全局 Integer 类型处理器，
 * 需要在字段上显式使用 {@code @TableField(typeHandler = IntegerCryptoTypeHandler.class)} 才会生效。
 */
public class IntegerCryptoTypeHandler extends AbstractCryptoTypeHandler<Integer> {

    public IntegerCryptoTypeHandler(CryptoKeyProvider keyProvider) {
        super(keyProvider);
    }

    @Override
    protected String toString(Integer value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    protected Integer fromString(String value) {
        return value == null || value.isEmpty() ? null : Integer.parseInt(value);
    }
}

