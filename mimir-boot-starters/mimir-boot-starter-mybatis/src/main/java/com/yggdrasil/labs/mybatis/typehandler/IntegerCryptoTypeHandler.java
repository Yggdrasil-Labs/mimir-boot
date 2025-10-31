package com.yggdrasil.labs.mybatis.typehandler;

import com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider;
import org.apache.ibatis.type.MappedTypes;

/**
 * Integer 字段加解密 TypeHandler。
 */
@MappedTypes(Integer.class)
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

