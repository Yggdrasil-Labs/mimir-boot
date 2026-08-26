package com.yggdrasil.labs.mybatis.typehandler;

import com.yggdrasil.labs.common.exception.ErrorCode;
import com.yggdrasil.labs.common.exception.SystemException;
import com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider;
import com.yggdrasil.labs.mybatis.crypto.CryptoUtils;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.util.StringUtils;

/**
 * 通用加解密 TypeHandler 基类。
 *
 * <p>注意：示例实现基于对称密钥加解密，仅用于通用场景演示，实际生产应
 * 依据安全规范选择更安全的算法/模式并做好密钥管理。</p>
 */
public abstract class AbstractCryptoTypeHandler<T> extends BaseTypeHandler<T> {

    private final CryptoKeyProvider keyProvider;
    private final String cryptoContext;
    private final boolean cryptoV2WriteEnabled;

    protected AbstractCryptoTypeHandler(CryptoKeyProvider keyProvider) {
        this(keyProvider, null, false);
    }

    protected AbstractCryptoTypeHandler(CryptoKeyProvider keyProvider, String cryptoContext) {
        this(keyProvider, cryptoContext, false);
    }

    protected AbstractCryptoTypeHandler(
            CryptoKeyProvider keyProvider, String cryptoContext, boolean cryptoV2WriteEnabled) {
        if (cryptoV2WriteEnabled && !StringUtils.hasText(cryptoContext)) {
            throw new IllegalStateException("启用 MyBatis v2 密文写入时必须配置 mimir.boot.mybatis.crypto-context");
        }
        this.keyProvider = keyProvider;
        this.cryptoContext = cryptoContext;
        this.cryptoV2WriteEnabled = cryptoV2WriteEnabled;
    }

    protected abstract String toString(T value);

    protected abstract T fromString(String value);

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException {
        String plaintext = toString(parameter);
        String encrypted = cryptoV2WriteEnabled
                ? CryptoUtils.encrypt(plaintext, keyProvider.getKey(), cryptoContext)
                : CryptoUtils.encrypt(plaintext, keyProvider.getKey());
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
            String decrypted = encrypted.startsWith("v2:")
                    ? CryptoUtils.decrypt(encrypted, keyProvider.getKey(), cryptoContext)
                    : CryptoUtils.decrypt(encrypted, keyProvider.getKey());
            return fromString(decrypted);
        } catch (Exception e) {
            throw new SystemException(ErrorCode.SYSTEM_ERROR, e);
        }
    }
}
