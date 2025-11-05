package com.yggdrasil.labs.mybatis.util;

import com.yggdrasil.labs.common.constant.CommonConstants;
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

    static class User {
        @SensitiveField(strategy = SensitiveField.MaskStrategy.PHONE)
        String phone;

        @SensitiveField(strategy = SensitiveField.MaskStrategy.ID_CARD)
        String idCard;

        @SensitiveField(strategy = SensitiveField.MaskStrategy.BANK_CARD)
        String bank;

        @SensitiveField(strategy = SensitiveField.MaskStrategy.EMAIL)
        String email;

        @SensitiveField(strategy = SensitiveField.MaskStrategy.CUSTOM, replacement = "***")
        String token;

        String name; // 非敏感字段

        User(String phone, String idCard, String bank, String email, String token, String name) {
            this.phone = phone;
            this.idCard = idCard;
            this.bank = bank;
            this.email = email;
            this.token = token;
            this.name = name;
        }
    }

    @Test
    void mask_object_fields_with_annotations_and_nested_map() {
        User user = new User("13800138000", "110105199001010015", "6222021234567890", "user@example.com", "abcd", "Alice");
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("user", user);
        wrapper.put("extra", "keep");

        Object masked = SqlLogMaskUtils.maskParams(wrapper);
        assertTrue(masked instanceof Map);

        Map<?, ?> m = (Map<?, ?>) masked;
        assertEquals("keep", m.get("extra"));

        // Map 下按 key 对应字段名进行匹配脱敏
        Map<?, ?> maskedUser = (Map<?, ?>) m.get("user");
        assertEquals("138****8000", maskedUser.get("phone"));
        assertEquals("110105********0015", maskedUser.get("idCard"));
        assertEquals("6222****7890", maskedUser.get("bank"));
        assertEquals("u****@example.com", maskedUser.get("email"));
        assertEquals("***", maskedUser.get("token"));
        assertEquals("Alice", maskedUser.get("name"));
    }

    @Test
    void mask_map_with_nulls_and_primitives_passthrough() {
        Map<String, Object> map = new HashMap<>();
        map.put("num", 123);
        map.put("str", "text");
        map.put("nil", null);

        Object out = SqlLogMaskUtils.maskParams(map);
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals(123, m.get("num"));
        assertEquals("text", m.get("str"));
        assertNull(m.get("nil"));
    }

    @Test
    void mask_value_strategy_all_defaults_to_masked() {
        class Holder {
            @SensitiveField
            String secret = "x";
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals(CommonConstants.MASKED, m.get("secret"));
    }
}
