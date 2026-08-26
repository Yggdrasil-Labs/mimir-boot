package com.yggdrasil.labs.mybatis.typehandler;

import com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider;

/**
 * Long 字段加解密 TypeHandler。
 *
 * <p>注意：此 TypeHandler 不会自动注册为全局 Long 类型处理器，
 * 需要在字段上显式使用 {@code @TableField(typeHandler = LongCryptoTypeHandler.class)} 才会生效。
 */
public class LongCryptoTypeHandler extends AbstractCryptoTypeHandler<Long> {

    public LongCryptoTypeHandler(CryptoKeyProvider keyProvider) {
        super(keyProvider);
    }

    public LongCryptoTypeHandler(CryptoKeyProvider keyProvider, String cryptoContext) {
        super(keyProvider, cryptoContext);
    }

    public LongCryptoTypeHandler(CryptoKeyProvider keyProvider, String cryptoContext, boolean cryptoV2WriteEnabled) {
        super(keyProvider, cryptoContext, cryptoV2WriteEnabled);
    }

    @Override
    protected String toString(Long value) {
        return value == null ? "" : String.valueOf(value);
    }

    @Override
    protected Long fromString(String value) {
        return value == null || value.isEmpty() ? null : Long.parseLong(value);
    }
}
