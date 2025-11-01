package com.yggdrasil.labs.mybatis.typehandler;

import com.yggdrasil.labs.mybatis.crypto.CryptoKeyProvider;
import com.yggdrasil.labs.mybatis.crypto.CryptoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Long 加密 TypeHandler 测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class LongCryptoTypeHandlerTest {

    private LongCryptoTypeHandler handler;
    private CryptoKeyProvider keyProvider;
    private String testKey;

    @BeforeEach
    void setUp() {
        testKey = CryptoUtils.generateKey();
        keyProvider = () -> testKey;
        handler = new LongCryptoTypeHandler(keyProvider);
    }

    @Test
    void testSetNonNullParameter() throws Exception {
        java.sql.PreparedStatement ps = mock(java.sql.PreparedStatement.class);
        Long value = 12345L;

        handler.setNonNullParameter(ps, 1, value, null);

        verify(ps).setString(eq(1), anyString());
    }

    @Test
    void testGetNullableResult() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        String encrypted = CryptoUtils.encrypt("12345", testKey);
        
        when(rs.getString("column_name")).thenReturn(encrypted);

        Long result = handler.getNullableResult(rs, "column_name");

        assertEquals(12345L, result);
    }

    @Test
    void testGetNullableResultWithNull() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("column_name")).thenReturn(null);

        Long result = handler.getNullableResult(rs, "column_name");

        assertNull(result);
    }

    @Test
    void testToStringWithNull() {
        String result = handler.toString(null);
        assertEquals("", result);
    }

    @Test
    void testToStringWithValue() {
        String result = handler.toString(12345L);
        assertEquals("12345", result);
    }

    @Test
    void testFromString() {
        Long result = handler.fromString("12345");
        assertEquals(12345L, result);
    }

    @Test
    void testFromStringWithNull() {
        Long result = handler.fromString(null);
        assertNull(result);
    }

    @Test
    void testFromStringWithEmpty() {
        Long result = handler.fromString("");
        assertNull(result);
    }

    @Test
    void testRoundTrip() throws Exception {
        Long originalValue = 999999L;
        
        // 加密存储
        String encrypted = CryptoUtils.encrypt(String.valueOf(originalValue), testKey);
        
        // 解密读取
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("column_name")).thenReturn(encrypted);
        
        Long decrypted = handler.getNullableResult(rs, "column_name");
        assertEquals(originalValue, decrypted);
    }
}

