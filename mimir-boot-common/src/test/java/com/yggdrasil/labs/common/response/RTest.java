package com.yggdrasil.labs.common.response;

import com.yggdrasil.labs.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.*;

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
}


