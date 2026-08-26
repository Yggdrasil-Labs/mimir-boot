package com.yggdrasil.labs.mybatis.typehandler;

import com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider;

/**
 * 字符串字段加解密 TypeHandler。
 *
 * <p>注意：此 TypeHandler 不会自动注册为全局 String 类型处理器，
 * 需要在字段上显式使用 {@code @TableField(typeHandler = StringCryptoTypeHandler.class)} 才会生效。
 */
public class StringCryptoTypeHandler extends AbstractCryptoTypeHandler<String> {

    public StringCryptoTypeHandler(CryptoKeyProvider keyProvider) {
        super(keyProvider);
    }

    public StringCryptoTypeHandler(CryptoKeyProvider keyProvider, String cryptoContext) {
        super(keyProvider, cryptoContext);
    }

    public StringCryptoTypeHandler(CryptoKeyProvider keyProvider, String cryptoContext, boolean cryptoV2WriteEnabled) {
        super(keyProvider, cryptoContext, cryptoV2WriteEnabled);
    }

    @Override
    protected String toString(String value) {
        return value == null ? "" : value;
    }

    @Override
    protected String fromString(String value) {
        return value;
    }
}
