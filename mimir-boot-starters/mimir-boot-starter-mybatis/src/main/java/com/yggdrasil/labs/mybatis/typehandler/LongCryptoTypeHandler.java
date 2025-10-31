package com.yggdrasil.labs.mybatis.typehandler;

import com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider;
import org.apache.ibatis.type.MappedTypes;

/**
 * Long 字段加解密 TypeHandler。
 */
@MappedTypes(Long.class)
public class LongCryptoTypeHandler extends AbstractCryptoTypeHandler<Long> {

    public LongCryptoTypeHandler(CryptoKeyProvider keyProvider) {
        super(keyProvider);
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

