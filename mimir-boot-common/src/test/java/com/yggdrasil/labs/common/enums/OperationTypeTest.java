package com.yggdrasil.labs.common.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationTypeTest {

    @Test
    void fromCode_matches_or_defaults_other() {
        assertEquals(OperationType.SELECT, OperationType.fromCode("SELECT"));
        assertEquals(OperationType.INSERT, OperationType.fromCode("INSERT"));
        assertEquals(OperationType.OTHER, OperationType.fromCode("UNKNOWN"));
        assertEquals(OperationType.OTHER, OperationType.fromCode(null));
    }
}


