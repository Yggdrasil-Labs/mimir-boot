package com.yggdrasil.labs.log.converter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 敏感数据模式测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class SensitiveDataPatternTest {

    @Test
    void testPasswordPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.PASSWORD;
        assertEquals("password", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("password"));
    }

    @Test
    void testTokenPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.TOKEN;
        assertEquals("token", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("token"));
    }

    @Test
    void testSecretPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.SECRET;
        assertEquals("secret", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("secret"));
    }

    @Test
    void testApiKeyPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.API_KEY;
        assertEquals("api_key", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("apikey"));
    }

    @Test
    void testAccountPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.ACCOUNT;
        assertEquals("account", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("account"));
    }

    @Test
    void testIdCardPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.ID_CARD;
        assertEquals("id_card", pattern.getName());
        assertNotNull(pattern.getPattern());
    }

    @Test
    void testPhonePattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.PHONE;
        assertEquals("phone", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("phone"));
    }

    @Test
    void testBankCardPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.BANK_CARD;
        assertEquals("bank_card", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("bankcard"));
    }

    @Test
    void testEmailPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.EMAIL;
        assertEquals("email", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("email"));
    }

    @Test
    void testNamePattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.NAME;
        assertEquals("name", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("name"));
    }

    @Test
    void testIdCardNumberPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.ID_CARD_NUMBER;
        assertEquals("id_card_number", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("\\d"));
    }

    @Test
    void testPhoneNumberPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.PHONE_NUMBER;
        assertEquals("phone_number", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().startsWith("1"));
    }

    @Test
    void testBankCardNumberPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.BANK_CARD_NUMBER;
        assertEquals("bank_card_number", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("\\d"));
    }

    @Test
    void testEmailAddressPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.EMAIL_ADDRESS;
        assertEquals("email_address", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("@"));
    }

    @Test
    void testFromNameExisting() {
        SensitiveDataPattern pattern = SensitiveDataPattern.fromName("password");
        assertNotNull(pattern);
        assertEquals(SensitiveDataPattern.PASSWORD, pattern);
    }

    @Test
    void testFromNameCaseInsensitive() {
        SensitiveDataPattern pattern = SensitiveDataPattern.fromName("PASSWORD");
        assertNotNull(pattern);
        assertEquals(SensitiveDataPattern.PASSWORD, pattern);
    }

    @Test
    void testFromNameNonExistent() {
        SensitiveDataPattern pattern = SensitiveDataPattern.fromName("non_existent");
        assertNull(pattern);
    }

    @Test
    void testFromNameNull() {
        SensitiveDataPattern pattern = SensitiveDataPattern.fromName(null);
        assertNull(pattern);
    }

    @Test
    void testAllPatternsHaveNameAndPattern() {
        for (SensitiveDataPattern pattern : SensitiveDataPattern.values()) {
            assertNotNull(pattern.getName());
            assertNotNull(pattern.getPattern());
            assertFalse(pattern.getName().isEmpty());
            assertFalse(pattern.getPattern().isEmpty());
        }
    }

    @Test
    void testAllPatternsUnique() {
        SensitiveDataPattern[] patterns = SensitiveDataPattern.values();
        for (int i = 0; i < patterns.length; i++) {
            for (int j = i + 1; j < patterns.length; j++) {
                assertNotEquals(patterns[i].getName(), patterns[j].getName(),
                        "Duplicate pattern name found: " + patterns[i].getName());
            }
        }
    }

    @Test
    void testValuesCount() {
        // 验证所有的枚举值都被定义了
        SensitiveDataPattern[] values = SensitiveDataPattern.values();
        // 实际有 14 个枚举值
        assertTrue(values.length >= 10, "至少应该有10个预置模式");
    }
}

