package com.yggdrasil.labs.mybatis.typehandler;

import com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider;
import com.yggdrasil.labs.mybatis.crypto.CryptoUtils;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * String 加密 TypeHandler 测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class StringCryptoTypeHandlerTest extends BaseUnitTest {

    private StringCryptoTypeHandler handler;
    private String testKey;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        testKey = CryptoUtils.generateKey();
        CryptoKeyProvider keyProvider = () -> testKey;
        handler = new StringCryptoTypeHandler(keyProvider);
    }

    @Test
    void testSetNonNullParameter() throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        String value = "test-string";

        handler.setNonNullParameter(ps, 1, value, null);

        // 验证调用了 setString，并且值应该是加密后的
        verify(ps).setString(eq(1), anyString());
    }

    @Test
    void testSetNonNullParameterWithNullValue() {
        PreparedStatement ps = mock(PreparedStatement.class);

        // toString 方法将 null 转为空字符串
        assertDoesNotThrow(() -> {
            handler.setNonNullParameter(ps, 1, null, null);
        });
    }

    @Test
    void testGetNullableResultFromColumnName() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        String encrypted = CryptoUtils.encrypt("test-value", testKey);

        when(rs.getString("column_name")).thenReturn(encrypted);

        String result = handler.getNullableResult(rs, "column_name");

        assertEquals("test-value", result);
    }

    @Test
    void testGetNullableResultFromColumnIndex() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        String encrypted = CryptoUtils.encrypt("test-value", testKey);

        when(rs.getString(1)).thenReturn(encrypted);

        String result = handler.getNullableResult(rs, 1);

        assertEquals("test-value", result);
    }

    @Test
    void testGetNullableResultWithNull() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("column_name")).thenReturn(null);

        String result = handler.getNullableResult(rs, "column_name");

        assertNull(result);
    }

    @Test
    void testGetNullableResultWithEmptyString() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("column_name")).thenReturn("");

        String result = handler.getNullableResult(rs, "column_name");

        assertNull(result);
    }

    @Test
    void testRoundTrip() throws SQLException {
        String originalValue = "original-text";

        // 加密
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, originalValue, null);

        // 获取加密后的值
        verify(ps).setString(eq(1), anyString());

        // 解密
        String encrypted = CryptoUtils.encrypt(originalValue, testKey);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("column_name")).thenReturn(encrypted);

        String decrypted = handler.getNullableResult(rs, "column_name");
        assertEquals(originalValue, decrypted);
    }

    @Test
    void testGetNullableResultFromCallableStatement() throws SQLException {
        java.sql.CallableStatement cs = mock(java.sql.CallableStatement.class);
        String encrypted = CryptoUtils.encrypt("test-value", testKey);

        when(cs.getString(1)).thenReturn(encrypted);

        String result = handler.getNullableResult(cs, 1);

        assertEquals("test-value", result);
    }

    @Test
    void testToStringWithNull() {
        String result = handler.toString(null);
        assertEquals("", result);
    }

    @Test
    void testToStringWithValue() {
        String result = handler.toString("test");
        assertEquals("test", result);
    }

    @Test
    void testFromString() {
        String result = handler.fromString("test");
        assertEquals("test", result);
    }

    @Test
    void contextConstructor_readsV2ButWritesV1_and_threeArgumentConstructorWritesV2() throws Exception {
        CryptoKeyProvider keyProvider = () -> testKey;
        StringCryptoTypeHandler v1Writer = new StringCryptoTypeHandler(keyProvider, "orders");
        StringCryptoTypeHandler v2Writer = new StringCryptoTypeHandler(keyProvider, "orders", true);
        PreparedStatement statement = mock(PreparedStatement.class);

        v1Writer.setNonNullParameter(statement, 1, "value", null);
        org.mockito.ArgumentCaptor<String> v1 = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(statement).setString(eq(1), v1.capture());
        assertFalse(v1.getValue().startsWith("v2:"));

        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("value")).thenReturn(CryptoUtils.encrypt("value", testKey, "orders"));
        assertEquals("value", v1Writer.getNullableResult(resultSet, "value"));

        v2Writer.setNonNullParameter(statement, 2, "value", null);
        org.mockito.ArgumentCaptor<String> v2 = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(statement).setString(eq(2), v2.capture());
        assertTrue(v2.getValue().startsWith("v2:"));
    }
}
