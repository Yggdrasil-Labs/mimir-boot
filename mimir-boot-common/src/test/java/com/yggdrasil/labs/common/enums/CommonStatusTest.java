package com.yggdrasil.labs.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommonStatusTest {

    @Test
    void fromCode_matches_or_defaults_disabled() {
        assertEquals(CommonStatus.ENABLED, CommonStatus.fromCode(1));
        assertEquals(CommonStatus.DISABLED, CommonStatus.fromCode(0));
        assertEquals(CommonStatus.DISABLED, CommonStatus.fromCode(999));
        assertEquals(CommonStatus.DISABLED, CommonStatus.fromCode(null));
    }

    @Test
    void isEnabled_isDisabled_checks() {
        assertTrue(CommonStatus.isEnabled(1));
        assertFalse(CommonStatus.isEnabled(0));
        assertTrue(CommonStatus.isDisabled(0));
        assertFalse(CommonStatus.isDisabled(1));
    }
}


