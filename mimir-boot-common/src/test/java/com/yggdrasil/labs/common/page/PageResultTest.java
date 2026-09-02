package com.yggdrasil.labs.common.page;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageResultTest {

    private static final List<String> SINGLE_ENTRY_DATA = List.of("a");

    @Test
    void of_computes_totalPages_hasNext_hasPrevious() {
        PageResult<Integer> pr = PageResult.of(List.of(1, 2, 3), 23L, 2L, 10L);
        assertEquals(3L, pr.getTotalPages());
        assertTrue(pr.getHasNext());
        assertTrue(pr.getHasPrevious());
    }

    @Test
    void validated_constructor_and_factories_reject_invalid_numeric_values() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PageResult<>(SINGLE_ENTRY_DATA, null, 1L, 10L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PageResult.of(SINGLE_ENTRY_DATA, 1L, null, 10L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PageResult.of(SINGLE_ENTRY_DATA, 1L, 1L, null)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new PageResult<>(SINGLE_ENTRY_DATA, -1L, 1L, 10L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PageResult.of(SINGLE_ENTRY_DATA, 1L, 0L, 10L)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> PageResult.empty(1L, 0L))
        );
    }

    @Test
    void empty_builds_empty_list_and_derived_fields() {
        PageResult<Serializable> pr = PageResult.empty(1L, 10L);
        assertNotNull(pr.getData());
        assertEquals(0, pr.getData().size());
        assertEquals(0L, pr.getTotalCount());
        assertEquals(0L, pr.getTotalPages());
        assertFalse(pr.getHasNext());
        assertFalse(pr.getHasPrevious());
    }

    @Test
    void empty_from_request_uses_request_values() {
        PageRequest req = PageRequest.of(3L, 5L);
        PageResult<Integer> pr = PageResult.empty(req);
        assertEquals(3L, pr.getPageIndex());
        assertEquals(5L, pr.getPageSize());
    }

    @Test
    void calculatesMaxTotalPagesWithoutOverflow() {
        PageResult<Integer> result = PageResult.of(List.of(), Long.MAX_VALUE, 1L, 1000L);

        assertEquals(9_223_372_036_854_776L, result.getTotalPages());
        assertTrue(result.getHasNext());
        assertFalse(result.getHasPrevious());
    }
}
