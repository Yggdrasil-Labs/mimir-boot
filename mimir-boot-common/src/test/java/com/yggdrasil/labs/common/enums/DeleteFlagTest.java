package com.yggdrasil.labs.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeleteFlagTest {

    @Test
    void fromCode_matches_or_defaults_not_deleted() {
        assertEquals(DeleteFlag.NOT_DELETED, DeleteFlag.fromCode(0));
        assertEquals(DeleteFlag.DELETED, DeleteFlag.fromCode(1));
        assertEquals(DeleteFlag.NOT_DELETED, DeleteFlag.fromCode(999));
        assertEquals(DeleteFlag.NOT_DELETED, DeleteFlag.fromCode(null));
    }

    @Test
    void isDeleted_isNotDeleted_checks() {
        assertTrue(DeleteFlag.isDeleted(1));
        assertFalse(DeleteFlag.isDeleted(0));
        assertTrue(DeleteFlag.isNotDeleted(0));
        assertFalse(DeleteFlag.isNotDeleted(1));
        assertFalse(DeleteFlag.isDeleted(999));
        assertFalse(DeleteFlag.isNotDeleted(999));
    }

    @Test
    void fromCodeOrNull_returns_only_known_flags() {
        assertEquals(DeleteFlag.NOT_DELETED, DeleteFlag.fromCodeOrNull(0));
        assertEquals(DeleteFlag.DELETED, DeleteFlag.fromCodeOrNull(1));
        assertNull(DeleteFlag.fromCodeOrNull(999));
        assertNull(DeleteFlag.fromCodeOrNull(null));
    }
}

