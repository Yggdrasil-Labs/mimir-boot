package com.yggdrasil.labs.mybatis.typehandler;

import com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider;
import org.apache.ibatis.type.MappedTypes;

/**
 * 字符串字段加解密 TypeHandler。
 */
@MappedTypes(String.class)
public class StringCryptoTypeHandler extends AbstractCryptoTypeHandler<String> {

    public StringCryptoTypeHandler(CryptoKeyProvider keyProvider) {
        super(keyProvider);
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

