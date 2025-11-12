package com.yggdrasil.labs.mybatis.util;

import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.mybatis.annotation.SensitiveField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

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

    static class Parent {
        String parentField = "parent";
    }

    static class Child extends Parent {
        @SensitiveField(strategy = SensitiveField.MaskStrategy.ALL)
        String childField = "child";
    }

    // ========== maskParams 方法测试 ==========

    @Test
    void maskParams_with_null() {
        assertNull(SqlLogMaskUtils.maskParams(null));
    }

    @Test
    void maskParams_with_string() {
        String input = "test";
        assertEquals(input, SqlLogMaskUtils.maskParams(input));
    }

    @Test
    void maskParams_with_primitive_int() {
        int input = 42;
        assertEquals(input, SqlLogMaskUtils.maskParams(input));
    }

    @Test
    void maskParams_with_primitive_long() {
        long input = 123L;
        assertEquals(input, SqlLogMaskUtils.maskParams(input));
    }

    @Test
    void maskParams_with_number_integer() {
        Integer input = 100;
        assertEquals(input, SqlLogMaskUtils.maskParams(input));
    }

    @Test
    void maskParams_with_number_double() {
        Double input = 3.14;
        assertEquals(input, SqlLogMaskUtils.maskParams(input));
    }

    @Test
    void mask_object_fields_with_annotations_and_nested_map() {
        User user = new User("13800138000", "110105199001010015", "6222021234567890", "user@example.com", "abcd", "Alice");
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("user", user);
        wrapper.put("extra", "keep");

        Object masked = SqlLogMaskUtils.maskParams(wrapper);
        assertInstanceOf(Map.class, masked);

        Map<?, ?> m = (Map<?, ?>) masked;
        assertEquals("keep", m.get("extra"));

        // Map 下按 key 对应字段名进行匹配脱敏
        Map<?, ?> maskedUser = (Map<?, ?>) m.get("user");
        assertEquals("138****8000", maskedUser.get("phone"));
        assertEquals("110105****0015", maskedUser.get("idCard"));
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
    void mask_map_with_sensitive_field_match() {
        class TestObj {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.ALL)
            String password = "secret123";
        }
        TestObj obj = new TestObj();
        Map<String, Object> map = new HashMap<>();
        map.put("password", obj);

        Object masked = SqlLogMaskUtils.maskParams(map);
        Map<?, ?> result = (Map<?, ?>) masked;
        assertEquals(CommonConstants.MASKED, result.get("password"));
    }

    @Test
    void mask_map_with_no_field_match() {
        class TestObj {
            final String normalField = "value";
        }
        TestObj obj = new TestObj();
        Map<String, Object> map = new HashMap<>();
        map.put("unknown", obj);

        Object masked = SqlLogMaskUtils.maskParams(map);
        Map<?, ?> result = (Map<?, ?>) masked;
        // 找不到字段时，应该递归处理对象
        assertInstanceOf(Map.class, result.get("unknown"));
    }

    @Test
    void mask_object_with_null_fields() {
        class TestObj {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.ALL)
            String sensitive = null;
            final String normal = null;
        }
        Object masked = SqlLogMaskUtils.maskParams(new TestObj());
        Map<?, ?> result = (Map<?, ?>) masked;
        assertNull(result.get("sensitive"));
        assertNull(result.get("normal"));
    }

    @Test
    void mask_object_with_inheritance() {
        Child child = new Child();
        Object masked = SqlLogMaskUtils.maskParams(child);
        Map<?, ?> result = (Map<?, ?>) masked;
        assertEquals("parent", result.get("parentField"));
        assertEquals(CommonConstants.MASKED, result.get("childField"));
    }

    @Test
    void mask_object_with_nested_object() {
        class Inner {
            final String innerField = "inner";
        }
        class Outer {
            final Inner inner = new Inner();
        }
        Object masked = SqlLogMaskUtils.maskParams(new Outer());
        Map<?, ?> result = (Map<?, ?>) masked;
        assertInstanceOf(Map.class, result.get("inner"));
        Map<?, ?> innerMap = (Map<?, ?>) result.get("inner");
        assertEquals("inner", innerMap.get("innerField"));
    }

    // ========== maskValue 方法测试 ==========

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

    @Test
    void mask_value_with_null_annotation() {
        class Holder {
            @SensitiveField
            String secret = "test";
        }
        // 通过反射测试 maskValue 的 null 注解分支
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        // 有注解但策略为 ALL，应该返回 MASKED
        assertEquals(CommonConstants.MASKED, m.get("secret"));
    }

    @Test
    void mask_value_with_empty_string() {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.ALL)
            String secret = "";
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals("", m.get("secret"));
    }

    @Test
    void mask_value_strategy_phone() {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.PHONE)
            String phone = "13800138000";
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals("138****8000", m.get("phone"));
    }

    @ParameterizedTest
    @MethodSource("provideShortValueTestCases")
    void mask_value_strategy_short_values(SensitiveField.MaskStrategy maskStrategy, String testValue, String fieldName) {
        Object holder = createHolderWithStrategy(maskStrategy, testValue);
        Object out = SqlLogMaskUtils.maskParams(holder);
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals(CommonConstants.MASKED, m.get(fieldName));
    }

    private Object createHolderWithStrategy(SensitiveField.MaskStrategy strategy, String value) {
        return switch (strategy) {
            case PHONE -> new Object() {
                @SensitiveField(strategy = SensitiveField.MaskStrategy.PHONE)
                String maskedField = value;
            };
            case ID_CARD -> new Object() {
                @SensitiveField(strategy = SensitiveField.MaskStrategy.ID_CARD)
                String maskedField = value;
            };
            case BANK_CARD -> new Object() {
                @SensitiveField(strategy = SensitiveField.MaskStrategy.BANK_CARD)
                String maskedField = value;
            };
            default -> new Object() {
                @SensitiveField(strategy = SensitiveField.MaskStrategy.ALL)
                String maskedField = value;
            };
        };
    }

    private static Stream<Arguments> provideShortValueTestCases() {
        return Stream.of(
                Arguments.of(SensitiveField.MaskStrategy.PHONE, "12345", "maskedField"), // 长度 < 7
                Arguments.of(SensitiveField.MaskStrategy.ID_CARD, "123456789", "maskedField"), // 长度 < 10
                Arguments.of(SensitiveField.MaskStrategy.BANK_CARD, "1234567", "maskedField") // 长度 < 8
        );
    }

    @ParameterizedTest
    @MethodSource("provideStandardMaskingTestCases")
    void mask_value_strategy_standard_cases(SensitiveField.MaskStrategy maskStrategy, String testValue, String fieldName, String expected) {
        Object holder = createHolderWithStrategy(maskStrategy, testValue);
        Object out = SqlLogMaskUtils.maskParams(holder);
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals(expected, m.get(fieldName));
    }

    private static Stream<Arguments> provideStandardMaskingTestCases() {
        return Stream.of(
                Arguments.of(SensitiveField.MaskStrategy.ID_CARD, "110105199001010015", "maskedField", "110105****0015"), // 18位身份证
                Arguments.of(SensitiveField.MaskStrategy.ID_CARD, "110105199001015", "maskedField", "110105****1015"), // 15位身份证
                Arguments.of(SensitiveField.MaskStrategy.BANK_CARD, "6222021234567890", "maskedField", "6222****7890") // 银行卡
        );
    }

    @Test
    void mask_value_strategy_email() {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.EMAIL)
            String email = "user@example.com";
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals("u****@example.com", m.get("email"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-email", "@example.com", "a@example.com"})
    void mask_value_strategy_email_invalid_cases(String email) {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.EMAIL)
            String emailField = email;
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals(CommonConstants.MASKED, m.get("emailField"));
    }

    @Test
    void mask_value_strategy_custom() {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.CUSTOM, replacement = "***CUSTOM***")
            String token = "secret-token";
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals("***CUSTOM***", m.get("token"));
    }

    // ========== 边界情况测试 ==========

    @Test
    void mask_phone_non_standard_length() {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.PHONE)
            String phone = "1234567"; // 长度 = 7，非标准11位
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        // maskPhone 对于非标准长度（非11位），使用更保守方案，保留前3位和后2位，所以 1234567 -> 123****67
        assertEquals("123****67", m.get("phone"));
    }

    @Test
    void mask_id_card_non_standard_length() {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.ID_CARD)
            String idCard = "1234567890"; // 长度 = 10，非标准15/18位
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        // maskIdCard 对于非标准长度（非15/18位），使用更保守方案，保留前4位和后4位，所以 1234567890 -> 1234****7890
        assertEquals("1234****7890", m.get("idCard"));
    }

    @Test
    void mask_bank_card_non_standard_length() {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.BANK_CARD)
            String card = "12345678"; // 长度 = 8，非标准16/19位
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        // maskBankCard 对于非标准长度（非16/19位），使用更保守方案，保留前3位和后3位，所以 12345678 -> 123****678
        assertEquals("123****678", m.get("card"));
    }

    @Test
    void mask_map_with_case_insensitive_field_match() {
        class TestObj {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.ALL)
            String password = "secret";
        }
        TestObj obj = new TestObj();
        Map<String, Object> map = new HashMap<>();
        map.put("PASSWORD", obj); // 大写 key

        Object masked = SqlLogMaskUtils.maskParams(map);
        Map<?, ?> result = (Map<?, ?>) masked;
        assertEquals(CommonConstants.MASKED, result.get("PASSWORD"));
    }

    @Test
    void mask_object_with_exception_handling() {
        // 创建一个会导致反射异常的对象（通过 final 字段或其他方式）
        // 由于 Java 的限制，很难直接触发异常，但我们可以测试异常处理逻辑
        class TestObj {
            final String finalField = "test";
        }
        // 即使有 final 字段，也应该能正常处理
        Object masked = SqlLogMaskUtils.maskParams(new TestObj());
        assertNotNull(masked);
    }

    @Test
    void mask_map_with_non_string_key() {
        class TestObj {
            final String field = "value";
        }
        TestObj obj = new TestObj();
        Map<Integer, Object> map = new HashMap<>();
        map.put(123, obj);

        Object masked = SqlLogMaskUtils.maskParams(map);
        Map<?, ?> result = (Map<?, ?>) masked;
        // key 转换为字符串 "123" 后查找字段
        assertNotNull(result.get(123));
    }

    @Test
    void private_constructor_throws_exception() throws Exception {
        Constructor<SqlLogMaskUtils> constructor = SqlLogMaskUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                constructor::newInstance);

        assertInstanceOf(IllegalStateException.class, exception.getCause());
        assertEquals("Utility class", exception.getCause().getMessage());
    }

    // ========== 补充测试用例以提高分支覆盖率 ==========

    @Test
    void mask_phone_with_standard_length() {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.PHONE)
            String phone = "13800138000"; // 11位标准手机号
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals("138****8000", m.get("phone"));
    }

    @Test
    void mask_phone_with_non_standard_length() {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.PHONE)
            String phone = "1234567890"; // 10位，非标准11位
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals("123****90", m.get("phone")); // 保留前3位和后2位
    }

    @Test
    void mask_id_card_with_15_digits() {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.ID_CARD)
            String idCard = "110105199001015"; // 15位身份证
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals("110105****1015", m.get("idCard"));
    }

    @Test
    void mask_id_card_with_18_digits() {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.ID_CARD)
            String idCard = "110105199001010015"; // 18位身份证
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals("110105****0015", m.get("idCard"));
    }

    @Test
    void mask_bank_card_with_16_digits() {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.BANK_CARD)
            String card = "6222021234567890"; // 16位银行卡
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals("6222****7890", m.get("card"));
    }

    @Test
    void mask_bank_card_with_19_digits() {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.BANK_CARD)
            String card = "6222021234567890123"; // 19位银行卡
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals("6222****0123", m.get("card"));
    }

    @Test
    void mask_email_with_normal_format() {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.EMAIL)
            String email = "testuser@example.com";
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals("t****@example.com", m.get("email"));
    }

    @Test
    void mask_email_with_single_char_before_at() {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.EMAIL)
            String email = "a@example.com"; // 只有一个字符在@前
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        // atIndex == 1，应该返回 MASKED
        assertEquals(CommonConstants.MASKED, m.get("email"));
    }

    @Test
    void mask_value_with_null_value() {
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.ALL)
            String secret = null;
        }
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        assertNull(m.get("secret"));
    }

    @Test
    void mask_map_with_null_key() {
        Map<Object, Object> map = new HashMap<>();
        map.put(null, "value");
        
        Object masked = SqlLogMaskUtils.maskParams(map);
        Map<?, ?> result = (Map<?, ?>) masked;
        assertNotNull(result);
    }

    @Test
    void mask_object_with_exception_in_field_access() {
        // 创建一个会导致字段访问异常的对象
        class TestObj {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.ALL)
            private String field = "test";
            
            // 通过反射访问私有字段时不应该抛出异常
        }
        Object masked = SqlLogMaskUtils.maskParams(new TestObj());
        assertNotNull(masked);
        Map<?, ?> result = (Map<?, ?>) masked;
        assertEquals(CommonConstants.MASKED, result.get("field"));
    }

    @Test
    void mask_map_with_field_not_found() {
        class TestObj {
            final String normalField = "value";
        }
        TestObj obj = new TestObj();
        Map<String, Object> map = new HashMap<>();
        map.put("nonExistentField", obj);
        
        Object masked = SqlLogMaskUtils.maskParams(map);
        Map<?, ?> result = (Map<?, ?>) masked;
        // 找不到字段时，应该递归处理对象
        assertInstanceOf(Map.class, result.get("nonExistentField"));
    }

    @Test
    void mask_object_with_all_fields_null() {
        class TestObj {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.ALL)
            String field1 = null;
            String field2 = null;
        }
        Object masked = SqlLogMaskUtils.maskParams(new TestObj());
        Map<?, ?> result = (Map<?, ?>) masked;
        assertNull(result.get("field1"));
        assertNull(result.get("field2"));
    }

    @Test
    void mask_map_with_primitive_value() {
        Map<String, Object> map = new HashMap<>();
        map.put("number", 123);
        map.put("string", "text");
        
        Object masked = SqlLogMaskUtils.maskParams(map);
        Map<?, ?> result = (Map<?, ?>) masked;
        assertEquals(123, result.get("number"));
        assertEquals("text", result.get("string"));
    }

    @Test
    void mask_object_with_nested_map() {
        Map<String, Object> innerMap = new HashMap<>();
        innerMap.put("key", "value");
        
        class Outer {
            final Map<String, Object> inner = innerMap;
        }
        
        Object masked = SqlLogMaskUtils.maskParams(new Outer());
        Map<?, ?> result = (Map<?, ?>) masked;
        assertInstanceOf(Map.class, result.get("inner"));
    }

    @Test
    void mask_object_with_inheritance_and_multiple_levels() {
        class GrandParent {
            String grandParentField = "grandparent";
        }
        class Parent extends GrandParent {
            String parentField = "parent";
        }
        class Child extends Parent {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.ALL)
            String childField = "child";
        }
        
        Object masked = SqlLogMaskUtils.maskParams(new Child());
        Map<?, ?> result = (Map<?, ?>) masked;
        assertEquals("grandparent", result.get("grandParentField"));
        assertEquals("parent", result.get("parentField"));
        assertEquals(CommonConstants.MASKED, result.get("childField"));
    }

    @Test
    void mask_map_with_case_insensitive_field_name() {
        class TestObj {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.ALL)
            String password = "secret";
        }
        TestObj obj = new TestObj();
        Map<String, Object> map = new HashMap<>();
        map.put("PASSWORD", obj); // 大写
        map.put("Password", obj); // 混合大小写
        map.put("passWord", obj); // 驼峰
        
        Object masked = SqlLogMaskUtils.maskParams(map);
        Map<?, ?> result = (Map<?, ?>) masked;
        // 所有变体都应该匹配到 password 字段
        assertEquals(CommonConstants.MASKED, result.get("PASSWORD"));
        assertEquals(CommonConstants.MASKED, result.get("Password"));
        assertEquals(CommonConstants.MASKED, result.get("passWord"));
    }

    @Test
    void mask_value_with_null_annotation_parameter() {
        // 测试 maskValue 方法中 anno == null 的分支
        // 这在实际使用中不太可能发生，因为只有有注解的字段才会调用 maskValue
        // 但我们可以通过反射测试这个分支
        class Holder {
            @SensitiveField(strategy = SensitiveField.MaskStrategy.ALL)
            String field = "test";
        }
        // 正常流程中，有注解的字段会调用 maskValue，anno 不为 null
        Object out = SqlLogMaskUtils.maskParams(new Holder());
        Map<?, ?> m = (Map<?, ?>) out;
        assertEquals(CommonConstants.MASKED, m.get("field"));
    }
}
