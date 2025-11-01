package com.yggdrasil.labs.mybatis.util;

import com.yggdrasil.labs.mybatis.annotation.SensitiveField;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL 日志脱敏工具测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class SqlLogMaskUtilsTest {

    // 测试类
    static class TestUser {
        @SensitiveField(strategy = SensitiveField.MaskStrategy.PHONE)
        private String phone;

        @SensitiveField(strategy = SensitiveField.MaskStrategy.ID_CARD)
        private String idCard;

        @SensitiveField(strategy = SensitiveField.MaskStrategy.BANK_CARD)
        private String bankCard;

        @SensitiveField(strategy = SensitiveField.MaskStrategy.EMAIL)
        private String email;

        @SensitiveField(strategy = SensitiveField.MaskStrategy.ALL)
        private String password;

        @SensitiveField(strategy = SensitiveField.MaskStrategy.CUSTOM, replacement = "***MASKED***")
        private String customField;

        private String normalField;

        public TestUser(String phone, String idCard, String bankCard, String email, 
                       String password, String customField, String normalField) {
            this.phone = phone;
            this.idCard = idCard;
            this.bankCard = bankCard;
            this.email = email;
            this.password = password;
            this.customField = customField;
            this.normalField = normalField;
        }
    }

    @Test
    void testMaskParamsNull() {
        Object result = SqlLogMaskUtils.maskParams(null);
        assertNull(result);
    }

    @Test
    void testMaskParamsPrimitive() {
        assertEquals(123, SqlLogMaskUtils.maskParams(123));
        assertEquals("test", SqlLogMaskUtils.maskParams("test"));
        assertEquals(123L, SqlLogMaskUtils.maskParams(123L));
        assertEquals(3.14, SqlLogMaskUtils.maskParams(3.14));
    }

    @Test
    void testMaskParamsString() {
        String result = (String) SqlLogMaskUtils.maskParams("hello");
        assertEquals("hello", result);
    }

    @Test
    void testMaskParamsMap() {
        Map<String, Object> params = new HashMap<>();
        params.put("id", 123);
        params.put("name", "test");

        Object result = SqlLogMaskUtils.maskParams(params);
        assertNotNull(result);
        assertTrue(result instanceof Map);
    }

    @Test
    void testMaskParamsObject() {
        TestUser user = new TestUser(
            "13812345678",
            "110101199001011234",
            "6222021234567890",
            "user@example.com",
            "password123",
            "customValue",
            "normalValue"
        );

        Object result = SqlLogMaskUtils.maskParams(user);
        assertNotNull(result);
        assertTrue(result instanceof Map);
    }

    @Test
    void testMaskParamsObjectWithPhone() {
        TestUser user = new TestUser(
            "13812345678", null, null, null, null, null, null
        );

        Object result = SqlLogMaskUtils.maskParams(user);
        assertNotNull(result);
        assertTrue(result instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        Object phone = map.get("phone");
        assertNotNull(phone);
        // 手机号脱敏：保留前3位后4位
        assertTrue(phone.toString().startsWith("138"));
        assertTrue(phone.toString().contains("5678"));
    }

    @Test
    void testMaskParamsObjectWithIdCard() {
        TestUser user = new TestUser(
            null, "110101199001011234", null, null, null, null, null
        );

        Object result = SqlLogMaskUtils.maskParams(user);
        assertNotNull(result);
        assertTrue(result instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        Object idCard = map.get("idCard");
        assertNotNull(idCard);
        // 身份证脱敏：保留前6位后4位
        assertTrue(idCard.toString().startsWith("110101"));
        assertTrue(idCard.toString().endsWith("1234"));
    }

    @Test
    void testMaskParamsObjectWithEmail() {
        TestUser user = new TestUser(
            null, null, null, "user@example.com", null, null, null
        );

        Object result = SqlLogMaskUtils.maskParams(user);
        assertNotNull(result);
        assertTrue(result instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        Object email = map.get("email");
        assertNotNull(email);
        // 邮箱脱敏：保留首字符和@后的部分
        assertTrue(email.toString().startsWith("u"));
        assertTrue(email.toString().contains("@example.com"));
    }

    @Test
    void testMaskParamsObjectWithPassword() {
        TestUser user = new TestUser(
            null, null, null, null, "password123", null, null
        );

        Object result = SqlLogMaskUtils.maskParams(user);
        assertNotNull(result);
        assertTrue(result instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        Object password = map.get("password");
        assertEquals("******", password);
    }

    @Test
    void testMaskParamsObjectWithCustomField() {
        TestUser user = new TestUser(
            null, null, null, null, null, "customValue", null
        );

        Object result = SqlLogMaskUtils.maskParams(user);
        assertNotNull(result);
        assertTrue(result instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        Object customField = map.get("customField");
        assertEquals("***MASKED***", customField);
    }

    @Test
    void testMaskParamsObjectWithNormalField() {
        TestUser user = new TestUser(
            null, null, null, null, null, null, "normalValue"
        );

        Object result = SqlLogMaskUtils.maskParams(user);
        assertNotNull(result);
        assertTrue(result instanceof Map);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) result;
        Object normalField = map.get("normalField");
        assertEquals("normalValue", normalField);
    }

    @Test
    void testMaskParamsMapWithNullValues() {
        Map<String, Object> params = new HashMap<>();
        params.put("id", 123);
        params.put("name", null);

        Object result = SqlLogMaskUtils.maskParams(params);
        assertNotNull(result);
        assertTrue(result instanceof Map);
    }
}

