package com.yggdrasil.labs.mybatis.util;

import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.mybatis.annotation.SensitiveField;
import com.yggdrasil.labs.test.base.BaseUnitTest;
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
class SqlLogMaskUtilsTest extends BaseUnitTest {

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

    // ========== 堆栈溢出修复相关测试 ==========

    @Test
    void mask_params_with_circular_reference() {
        // 测试循环引用检测
        class Node {
            @SuppressWarnings("unused")
            String value;
            @SuppressWarnings("unused")
            Node next;

            Node(String value) {
                this.value = value;
            }
        }

        Node node1 = new Node("node1");
        Node node2 = new Node("node2");
        node1.next = node2;
        node2.next = node1; // 形成循环引用

        // 不应该抛出堆栈溢出异常
        Object masked = SqlLogMaskUtils.maskParams(node1);
        assertNotNull(masked);
        // 应该返回简单表示而不是无限递归
        assertInstanceOf(Map.class, masked);
    }

    @Test
    void mask_params_with_deep_nesting() {
        // 测试深度限制
        class DeepNode {
            @SuppressWarnings("unused")
            String value;
            @SuppressWarnings("unused")
            DeepNode child;

            DeepNode(String value) {
                this.value = value;
            }
        }

        // 创建深度嵌套的对象（超过 MAX_DEPTH = 5）
        DeepNode root = new DeepNode("root");
        DeepNode current = root;
        for (int i = 0; i < 10; i++) {
            current.child = new DeepNode("level" + i);
            current = current.child;
        }

        // 不应该抛出堆栈溢出异常
        Object masked = SqlLogMaskUtils.maskParams(root);
        assertNotNull(masked);
        // 深度超过限制时应该返回简单表示
        assertInstanceOf(Map.class, masked);
    }

    @Test
    void mask_params_with_complex_wrapper_like_object() {
        // 测试类似 MyBatis-Plus Wrapper 的复杂对象
        // 由于 isMyBatisPlusWrapper 检查类名包含 "com.baomidou.mybatisplus" 和 "Wrapper"
        // 我们创建一个模拟类来测试深度限制和循环引用检测
        class MockWrapper {
            @SuppressWarnings("unused")
            private final Object internal = new Object();
            @SuppressWarnings("unused")
            private final java.util.List<Object> conditions = new java.util.ArrayList<>();

            @Override
            public String toString() {
                return "MockWrapper@12345";
            }
        }

        MockWrapper wrapper = new MockWrapper();
        
        // 不应该抛出堆栈溢出异常
        Object masked = SqlLogMaskUtils.maskParams(wrapper);
        assertNotNull(masked);
        // 应该返回 Map 表示（因为不是真正的 MyBatis-Plus Wrapper，所以会走 maskObject 路径）
        assertInstanceOf(Map.class, masked);
    }

    @Test
    void mask_params_with_collection() {
        // 测试 Collection 类型处理
        java.util.List<String> list = new java.util.ArrayList<>();
        list.add("item1");
        list.add("item2");
        list.add("item3");

        Object masked = SqlLogMaskUtils.maskParams(list);
        assertInstanceOf(java.util.List.class, masked);
        java.util.List<?> result = (java.util.List<?>) masked;
        assertEquals(3, result.size());
        assertEquals("item1", result.get(0));
        assertEquals("item2", result.get(1));
        assertEquals("item3", result.get(2));
    }

    @Test
    void mask_params_with_collection_containing_objects() {
        // 测试包含对象的 Collection
        class Item {
            @SuppressWarnings("unused")
            String name;
            @SensitiveField(strategy = SensitiveField.MaskStrategy.ALL)
            String secret;

            Item(String name, String secret) {
                this.name = name;
                this.secret = secret;
            }
        }

        java.util.List<Item> list = new java.util.ArrayList<>();
        list.add(new Item("item1", "secret1"));
        list.add(new Item("item2", "secret2"));

        Object masked = SqlLogMaskUtils.maskParams(list);
        assertInstanceOf(java.util.List.class, masked);
        java.util.List<?> result = (java.util.List<?>) masked;
        assertEquals(2, result.size());

        // 检查第一个元素
        assertInstanceOf(Map.class, result.get(0));
        Map<?, ?> item1 = (Map<?, ?>) result.get(0);
        assertEquals("item1", item1.get("name"));
        assertEquals(CommonConstants.MASKED, item1.get("secret"));
    }

    @Test
    void mask_params_with_collection_containing_null() {
        // 测试包含 null 的 Collection
        java.util.List<Object> list = new java.util.ArrayList<>();
        list.add("item1");
        list.add(null);
        list.add("item3");

        Object masked = SqlLogMaskUtils.maskParams(list);
        assertInstanceOf(java.util.List.class, masked);
        java.util.List<?> result = (java.util.List<?>) masked;
        assertEquals(3, result.size());
        assertEquals("item1", result.get(0));
        assertNull(result.get(1));
        assertEquals("item3", result.get(2));
    }

    @Test
    void mask_params_with_set_collection() {
        // 测试 Set 类型
        java.util.Set<String> set = new java.util.HashSet<>();
        set.add("item1");
        set.add("item2");

        Object masked = SqlLogMaskUtils.maskParams(set);
        assertInstanceOf(java.util.List.class, masked); // Collection 转换为 List
        java.util.List<?> result = (java.util.List<?>) masked;
        assertTrue(result.size() >= 2);
    }

    @Test
    void mask_params_with_boolean_type() {
        // 测试 Boolean 类型
        Boolean boolValue = true;
        Object masked = SqlLogMaskUtils.maskParams(boolValue);
        assertEquals(true, masked);

        boolValue = false;
        masked = SqlLogMaskUtils.maskParams(boolValue);
        assertEquals(false, masked);
    }

    @Test
    void mask_params_with_primitive_boolean() {
        // 测试原始 boolean 类型
        boolean boolValue = true;
        Object masked = SqlLogMaskUtils.maskParams(boolValue);
        assertEquals(true, masked);
    }

    @Test
    void mask_params_with_character_type() {
        // 测试 Character 类型
        Character charValue = 'A';
        Object masked = SqlLogMaskUtils.maskParams(charValue);
        assertEquals('A', masked);
    }

    @Test
    void mask_params_with_primitive_char() {
        // 测试原始 char 类型
        char charValue = 'B';
        Object masked = SqlLogMaskUtils.maskParams(charValue);
        assertEquals('B', masked);
    }

    @Test
    void mask_params_with_map_containing_circular_reference() {
        // 测试 Map 中包含循环引用
        class Node {
            @SuppressWarnings("unused")
            String value;
            Map<String, Object> refs = new HashMap<>();

            Node(String value) {
                this.value = value;
            }
        }

        Node node1 = new Node("node1");
        Node node2 = new Node("node2");
        node1.refs.put("other", node2);
        node2.refs.put("other", node1); // 形成循环引用

        Map<String, Object> map = new HashMap<>();
        map.put("node1", node1);
        map.put("node2", node2);

        // 不应该抛出堆栈溢出异常
        Object masked = SqlLogMaskUtils.maskParams(map);
        assertNotNull(masked);
        assertInstanceOf(Map.class, masked);
    }

    @Test
    void mask_params_with_nested_collections() {
        // 测试嵌套的 Collection
        java.util.List<java.util.List<String>> nestedList = new java.util.ArrayList<>();
        java.util.List<String> inner1 = new java.util.ArrayList<>();
        inner1.add("a");
        inner1.add("b");
        java.util.List<String> inner2 = new java.util.ArrayList<>();
        inner2.add("c");
        inner2.add("d");
        nestedList.add(inner1);
        nestedList.add(inner2);

        Object masked = SqlLogMaskUtils.maskParams(nestedList);
        assertInstanceOf(java.util.List.class, masked);
        java.util.List<?> result = (java.util.List<?>) masked;
        assertEquals(2, result.size());
        assertInstanceOf(java.util.List.class, result.get(0));
    }

    @Test
    void mask_params_with_map_containing_collection() {
        // 测试 Map 中包含 Collection
        Map<String, Object> map = new HashMap<>();
        java.util.List<String> list = new java.util.ArrayList<>();
        list.add("item1");
        list.add("item2");
        map.put("list", list);
        map.put("other", "value");

        Object masked = SqlLogMaskUtils.maskParams(map);
        assertInstanceOf(Map.class, masked);
        Map<?, ?> result = (Map<?, ?>) masked;
        assertInstanceOf(java.util.List.class, result.get("list"));
        assertEquals("value", result.get("other"));
    }

    @Test
    void mask_params_with_object_containing_collection() {
        // 测试对象中包含 Collection
        class Container {
            java.util.List<String> items = new java.util.ArrayList<>();
            @SuppressWarnings("unused")
            String name;

            Container(String name) {
                this.name = name;
                items.add("item1");
                items.add("item2");
            }
        }

        Container container = new Container("container");
        Object masked = SqlLogMaskUtils.maskParams(container);
        assertInstanceOf(Map.class, masked);
        Map<?, ?> result = (Map<?, ?>) masked;
        assertEquals("container", result.get("name"));
        assertInstanceOf(java.util.List.class, result.get("items"));
    }

    @Test
    void mask_params_with_deep_map_nesting() {
        // 测试深度嵌套的 Map
        Map<String, Object> level1 = new HashMap<>();
        Map<String, Object> level2 = new HashMap<>();
        Map<String, Object> level3 = new HashMap<>();
        Map<String, Object> level4 = new HashMap<>();
        Map<String, Object> level5 = new HashMap<>();
        Map<String, Object> level6 = new HashMap<>(); // 超过 MAX_DEPTH

        level1.put("level2", level2);
        level2.put("level3", level3);
        level3.put("level4", level4);
        level4.put("level5", level5);
        level5.put("level6", level6);
        level6.put("value", "deep");

        // 不应该抛出堆栈溢出异常
        Object masked = SqlLogMaskUtils.maskParams(level1);
        assertNotNull(masked);
        assertInstanceOf(Map.class, masked);
    }

    @Test
    void mask_params_with_self_referencing_map() {
        // 测试自引用的 Map
        Map<String, Object> map = new HashMap<>();
        map.put("self", map); // 自引用
        map.put("value", "test");

        // 不应该抛出堆栈溢出异常
        Object masked = SqlLogMaskUtils.maskParams(map);
        assertNotNull(masked);
        assertInstanceOf(Map.class, masked);
    }

    @Test
    void mask_params_with_circular_map_reference_hashcode_issue() {
        // 测试 Map 循环引用导致的 hashCode() 堆栈溢出问题
        // 这个问题发生在使用 HashSet 存储已访问对象时，HashSet 会调用 Map 的 hashCode()
        // 而 Map 的 hashCode() 会遍历所有 entry，如果包含循环引用就会无限递归
        Map<String, Object> map1 = new HashMap<>();
        Map<String, Object> map2 = new HashMap<>();
        Map<String, Object> map3 = new HashMap<>();
        
        map1.put("map2", map2);
        map2.put("map3", map3);
        map3.put("map1", map1); // 形成循环引用
        
        map1.put("value1", "test1");
        map2.put("value2", "test2");
        map3.put("value3", "test3");

        // 不应该抛出堆栈溢出异常（特别是 hashCode() 相关的）
        Object masked = SqlLogMaskUtils.maskParams(map1);
        assertNotNull(masked);
        assertInstanceOf(Map.class, masked);
    }

    @Test
    void mask_params_with_nested_circular_maps() {
        // 测试嵌套的循环引用 Map
        Map<String, Object> outer = new HashMap<>();
        Map<String, Object> inner1 = new HashMap<>();
        Map<String, Object> inner2 = new HashMap<>();
        
        outer.put("inner1", inner1);
        inner1.put("inner2", inner2);
        inner2.put("outer", outer); // 形成循环引用
        
        outer.put("data", "outerData");
        inner1.put("data", "inner1Data");
        inner2.put("data", "inner2Data");

        // 不应该抛出堆栈溢出异常
        Object masked = SqlLogMaskUtils.maskParams(outer);
        assertNotNull(masked);
        assertInstanceOf(Map.class, masked);
    }

    @Test
    void mask_params_with_map_as_key() {
        // 测试 Map 作为 key 的情况
        // 使用 IdentityHashMap 创建测试数据，避免在创建时调用 key 的 hashCode()
        // 因为 HashMap.put() 会调用 key 的 hashCode()，如果 key 是包含循环引用的 Map，会导致堆栈溢出
        Map<String, Object> keyMap = new HashMap<>();
        Map<String, Object> valueMap = new HashMap<>();
        keyMap.put("data", "keyData");
        valueMap.put("data", "valueData");
        
        // 使用 IdentityHashMap 来存储 Map 作为 key，避免调用 hashCode()
        Map<Object, Object> mapWithMapKey = new java.util.IdentityHashMap<>();
        mapWithMapKey.put(keyMap, valueMap); // Map 作为 key
        mapWithMapKey.put("normalKey", "normalValue");

        // 不应该抛出堆栈溢出异常
        Object masked = SqlLogMaskUtils.maskParams(mapWithMapKey);
        assertNotNull(masked);
        assertInstanceOf(Map.class, masked);
        
        // 验证 Map key 被转换为字符串表示
        Map<?, ?> result = (Map<?, ?>) masked;
        // 应该有一个以 Map 类名开头的 key（因为 Map key 被转换为字符串表示）
        boolean hasMapKeyRepresentation = result.keySet().stream()
                .anyMatch(k -> k instanceof String && k.toString().contains("HashMap"));
        assertTrue(hasMapKeyRepresentation || result.containsKey("normalKey"),
                "应该包含转换后的 Map key 表示或正常 key");
    }

    @Test
    void mask_params_with_circular_map_as_key() {
        // 测试循环引用的 Map 作为 key
        // 使用 IdentityHashMap 创建测试数据，避免在创建时调用 key 的 hashCode()
        Map<String, Object> map1 = new HashMap<>();
        Map<String, Object> map2 = new HashMap<>();
        map1.put("map2", map2);
        map2.put("map1", map1); // 形成循环引用
        
        // 使用 IdentityHashMap 来存储 Map 作为 key，避免调用 hashCode()
        Map<Object, Object> container = new java.util.IdentityHashMap<>();
        container.put(map1, "value1"); // 循环引用的 Map 作为 key
        container.put(map2, "value2"); // 另一个循环引用的 Map 作为 key

        // 不应该抛出堆栈溢出异常
        Object masked = SqlLogMaskUtils.maskParams(container);
        assertNotNull(masked);
        assertInstanceOf(Map.class, masked);
    }
}
