package com.yggdrasil.labs.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderDirectionTest {

    @Test
    void fromCode_is_case_insensitive_and_defaults_to_asc() {
        assertEquals(OrderDirection.ASC, OrderDirection.fromCode("ASC"));
        assertEquals(OrderDirection.DESC, OrderDirection.fromCode("desc"));
        assertEquals(OrderDirection.ASC, OrderDirection.fromCode("unknown"));
        assertEquals(OrderDirection.ASC, OrderDirection.fromCode(null));
    }

    @Test
    void isAsc_isDesc_are_case_insensitive() {
        assertTrue(OrderDirection.isAsc("asc"));
        assertTrue(OrderDirection.isDesc("DESC"));
        assertFalse(OrderDirection.isAsc("zzz"));
        assertFalse(OrderDirection.isDesc("zzz"));
    }
}


