package com.yggdrasil.labs.mybatis.typehandler;

import com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider;
import com.yggdrasil.labs.mybatis.crypto.CryptoUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 通用加解密 TypeHandler 基类。
 *
 * <p>注意：示例实现基于对称密钥加解密，仅用于通用场景演示，实际生产应
 * 依据安全规范选择更安全的算法/模式并做好密钥管理。</p>
 */
@MappedJdbcTypes(JdbcType.VARCHAR)
public abstract class AbstractCryptoTypeHandler<T> extends BaseTypeHandler<T> {

    private final CryptoKeyProvider keyProvider;

    protected AbstractCryptoTypeHandler(CryptoKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    protected abstract String toString(T value);

    protected abstract T fromString(String value);

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        String plaintext = toString(parameter);
        String encrypted = CryptoUtils.encrypt(plaintext, keyProvider.getKey());
        ps.setString(i, encrypted);
    }

    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String encrypted = rs.getString(columnName);
        return decryptAndParse(encrypted);
    }

    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String encrypted = rs.getString(columnIndex);
        return decryptAndParse(encrypted);
    }

    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String encrypted = cs.getString(columnIndex);
        return decryptAndParse(encrypted);
    }

    private T decryptAndParse(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) {
            return null;
        }
        try {
            String decrypted = CryptoUtils.decrypt(encrypted, keyProvider.getKey());
            return fromString(decrypted);
        } catch (Exception e) {
            throw new RuntimeException("Decrypt failed for column value", e);
        }
    }
}

