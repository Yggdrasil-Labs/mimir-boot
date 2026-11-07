package com.yggdrasil.labs.mybatis.typehandler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.sql.PreparedStatement;

import org.mockito.ArgumentCaptor;

/** 复用数值处理器对默认旧格式与显式 v2 写入的断言。 */
final class CryptoTypeHandlerSupport {

    private CryptoTypeHandlerSupport() {}

    static void assertLegacyAndV2Ciphertext(PreparedStatement statement) throws Exception {
        ArgumentCaptor<String> v1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> v2 = ArgumentCaptor.forClass(String.class);
        verify(statement).setString(eq(1), v1.capture());
        verify(statement).setString(eq(2), v2.capture());
        assertFalse(v1.getValue().startsWith("v2:"));
        assertTrue(v2.getValue().startsWith("v2:"));
    }
}
