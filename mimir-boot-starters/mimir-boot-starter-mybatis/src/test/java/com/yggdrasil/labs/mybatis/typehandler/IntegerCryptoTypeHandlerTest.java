package com.yggdrasil.labs.mybatis.typehandler;

import com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider;
import com.yggdrasil.labs.mybatis.crypto.CryptoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integer 加密 TypeHandler 测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class IntegerCryptoTypeHandlerTest {

    private IntegerCryptoTypeHandler handler;
    private CryptoKeyProvider keyProvider;
    private String testKey;

    @BeforeEach
    void setUp() {
        testKey = CryptoUtils.generateKey();
        keyProvider = () -> testKey;
        handler = new IntegerCryptoTypeHandler(keyProvider);
    }

    @Test
    void testSetNonNullParameter() throws Exception {
        java.sql.PreparedStatement ps = mock(java.sql.PreparedStatement.class);
        Integer value = 12345;

        handler.setNonNullParameter(ps, 1, value, null);

        verify(ps).setString(eq(1), anyString());
    }

    @Test
    void testGetNullableResult() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        String encrypted = CryptoUtils.encrypt("12345", testKey);
        
        when(rs.getString("column_name")).thenReturn(encrypted);

        Integer result = handler.getNullableResult(rs, "column_name");

        assertEquals(12345, result);
    }

    @Test
    void testGetNullableResultWithNull() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("column_name")).thenReturn(null);

        Integer result = handler.getNullableResult(rs, "column_name");

        assertNull(result);
    }

    @Test
    void testToStringWithNull() {
        String result = handler.toString(null);
        assertEquals("", result);
    }

    @Test
    void testToStringWithValue() {
        String result = handler.toString(12345);
        assertEquals("12345", result);
    }

    @Test
    void testFromString() {
        Integer result = handler.fromString("12345");
        assertEquals(12345, result);
    }

    @Test
    void testFromStringWithNull() {
        Integer result = handler.fromString(null);
        assertNull(result);
    }

    @Test
    void testFromStringWithEmpty() {
        Integer result = handler.fromString("");
        assertNull(result);
    }

    @Test
    void testRoundTrip() throws Exception {
        Integer originalValue = 999999;
        
        // 加密存储
        String encrypted = CryptoUtils.encrypt(String.valueOf(originalValue), testKey);
        
        // 解密读取
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("column_name")).thenReturn(encrypted);
        
        Integer decrypted = handler.getNullableResult(rs, "column_name");
        assertEquals(originalValue, decrypted);
    }

    @Test
    void testGetNullableResultFromCallableStatement() throws Exception {
        java.sql.CallableStatement cs = mock(java.sql.CallableStatement.class);
        String encrypted = CryptoUtils.encrypt("12345", testKey);
        
        when(cs.getString(1)).thenReturn(encrypted);

        Integer result = handler.getNullableResult(cs, 1);

        assertEquals(12345, result);
    }
}

