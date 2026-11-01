package com.yggdrasil.labs.common.response;

import static org.junit.jupiter.api.Assertions.*;

import java.io.Serializable;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yggdrasil.labs.common.exception.ErrorCode;
import com.yggdrasil.labs.common.exception.ErrorDefinition;

class RTest {

    @Test
    void success_without_data_sets_code_message_and_timestamp() {
        R<Serializable> r = R.success();
        assertEquals(ErrorCode.SUCCESS.getCode(), r.getCode());
        assertEquals(ErrorCode.SUCCESS.getMessage(), r.getMessage());
        assertNull(r.getData());
        assertNotNull(r.getTimestamp());
        assertTrue(r.isSuccess());
        assertFalse(r.isFail());
    }

    @Test
    void success_with_data_sets_payload() {
        R<String> r = R.success("payload");
        assertEquals(ErrorCode.SUCCESS.getCode(), r.getCode());
        assertEquals("payload", r.getData());
        assertTrue(r.isSuccess());
    }

    @Test
    void success_with_custom_message() {
        R<Integer> r = R.success("ok-msg", 123);
        assertEquals(ErrorCode.SUCCESS.getCode(), r.getCode());
        assertEquals("ok-msg", r.getMessage());
        assertEquals(123, r.getData());
    }

    @Test
    void fail_with_message_uses_default_fail_code() {
        R<Serializable> r = R.fail("bad");
        assertEquals(ErrorCode.FAIL.getCode(), r.getCode());
        assertEquals("bad", r.getMessage());
        assertNull(r.getData());
        assertTrue(r.isFail());
    }

    @Test
    void fail_with_custom_code_and_message() {
        R<Serializable> r = R.fail("E100", "oops");
        assertEquals("E100", r.getCode());
        assertEquals("oops", r.getMessage());
        assertNull(r.getData());
        assertTrue(r.isFail());
    }

    @Test
    void constructor_sets_timestamp_and_fields() {
        long before = System.currentTimeMillis();
        R<String> r = new R<>("C", "M", "D");
        long after = System.currentTimeMillis();
        assertEquals("C", r.getCode());
        assertEquals("M", r.getMessage());
        assertEquals("D", r.getData());
        assertNotNull(r.getTimestamp());
        assertTrue(r.getTimestamp() >= before && r.getTimestamp() <= after);
    }

    @Test
    void json_serialization_preserves_response_fields() throws Exception {
        R<String> r = R.success("payload");

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(r));

        assertEquals(ErrorCode.SUCCESS.getCode(), json.get("code").asText());
        assertEquals(ErrorCode.SUCCESS.getMessage(), json.get("message").asText());
        assertEquals("payload", json.get("data").asText());
        assertEquals(r.getTimestamp(), json.get("timestamp").asLong());
        assertTrue(json.get("traceId").isNull());
        assertTrue(json.get("success").asBoolean());
        assertFalse(json.get("fail").asBoolean());
        assertEquals(7, json.size());
    }

    @Test
    void failCode_uses_definition_code_and_default_or_override_message() {
        R<Serializable> byDefault = R.failCode(definition("ORDER_NOT_FOUND", "订单不存在"));
        R<Serializable> overridden =
                R.failCode(definition("ORDER_NOT_FOUND", "订单不存在"), "订单 123 不存在 {} %s");
        R<Serializable> nullOverride = R.failCode(definition("ORDER_NOT_FOUND", "订单不存在"), null);

        assertEquals("ORDER_NOT_FOUND", byDefault.getCode());
        assertEquals("订单不存在", byDefault.getMessage());
        assertNull(byDefault.getData());
        assertNotNull(byDefault.getTimestamp());
        assertFalse(byDefault.isSuccess());
        assertTrue(byDefault.isFail());
        assertEquals("订单 123 不存在 {} %s", overridden.getMessage());
        assertEquals("订单不存在", nullOverride.getMessage());
    }

    @Test
    void failCode_allows_null_default_and_keeps_blank_override() {
        assertNull(R.failCode(definition("E1", null)).getMessage());
        assertNull(R.failCode(definition("E1", null), null).getMessage());
        assertEquals("", R.failCode(definition("E1", "d"), "").getMessage());
        assertEquals("  ", R.failCode(definition("E1", "d"), "  ").getMessage());
    }

    @Test
    void failCode_keeps_non_blank_code_untrimmed() {
        R<Serializable> r = R.failCode(definition(" 00000 ", "m"));
        assertEquals(" 00000 ", r.getCode());
        assertFalse(r.isSuccess());
        assertEquals("20001", R.failCode(ErrorCode.DATA_NOT_FOUND).getCode());
    }

    @Test
    void failCode_rejects_invalid_definition() {
        assertThrows(IllegalArgumentException.class, () -> R.failCode(null));
        assertThrows(IllegalArgumentException.class, () -> R.failCode(null, "m"));
        assertThrows(IllegalArgumentException.class, () -> R.failCode(ErrorCode.SUCCESS));
        for (String code : new String[] {null, "", " \t", "00000"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> R.failCode(definition(code, "m"), "x"),
                    "code=" + code);
        }
    }

    @Test
    void legacy_fail_keeps_lenient_semantics() {
        R<Serializable> nullMessage = R.fail(null);
        R<Serializable> successCode = R.fail(ErrorCode.SUCCESS.getCode(), "legacy");

        assertEquals(ErrorCode.FAIL.getCode(), nullMessage.getCode());
        assertNull(nullMessage.getMessage());
        assertTrue(successCode.isSuccess());
    }

    private static ErrorDefinition definition(String code, String message) {
        return new ErrorDefinition() {
            @Override
            public String getCode() {
                return code;
            }

            @Override
            public String getMessage() {
                return message;
            }
        };
    }
}
