package com.yggdrasil.labs.common.page;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageResultTest {

    @Test
    void of_computes_totalPages_hasNext_hasPrevious() {
        PageResult<Integer> pr = PageResult.of(List.of(1, 2, 3), 23L, 2L, 10L);
        assertEquals(3L, pr.getTotalPages());
        assertTrue(pr.getHasNext());
        assertTrue(pr.getHasPrevious());
    }

    @Test
    void totalPages_zero_when_pageSize_zero() {
        PageResult<String> pr = PageResult.of(List.of("a"), 10L, 1L, 0L);
        assertEquals(0L, pr.getTotalPages());
        assertFalse(pr.getHasNext());
        assertFalse(pr.getHasPrevious());
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
}


