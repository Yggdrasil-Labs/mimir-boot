package com.yggdrasil.labs.common.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

    @Test
    void fromCode_returns_matching_enum_or_system_error() {
        assertEquals(ErrorCode.SUCCESS, ErrorCode.fromCode("00000"));
        assertEquals(ErrorCode.FAIL, ErrorCode.fromCode("00001"));
        assertEquals(ErrorCode.SYSTEM_ERROR, ErrorCode.fromCode("not-exist"));
        assertEquals(ErrorCode.SYSTEM_ERROR, ErrorCode.fromCode(null));
    }

    @Test
    void fromCodeOrNull_returns_only_known_error_codes() {
        assertEquals(ErrorCode.SUCCESS, ErrorCode.fromCodeOrNull("00000"));
        assertEquals(ErrorCode.SYSTEM_ERROR, ErrorCode.fromCodeOrNull("10000"));
        assertNull(ErrorCode.fromCodeOrNull("not-exist"));
        assertNull(ErrorCode.fromCodeOrNull(null));
    }
}

