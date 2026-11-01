package com.yggdrasil.labs.common.page;

import static org.junit.jupiter.api.Assertions.*;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

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
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> new PageResult<>(SINGLE_ENTRY_DATA, null, 1L, 10L)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> PageResult.of(SINGLE_ENTRY_DATA, 1L, null, 10L)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> PageResult.of(SINGLE_ENTRY_DATA, 1L, 1L, null)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> new PageResult<>(SINGLE_ENTRY_DATA, -1L, 1L, 10L)),
                () ->
                        assertThrows(
                                IllegalArgumentException.class,
                                () -> PageResult.of(SINGLE_ENTRY_DATA, 1L, 0L, 10L)),
                () -> assertThrows(IllegalArgumentException.class, () -> PageResult.empty(1L, 0L)));
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

    @Test
    void map_copies_all_metadata_as_is_and_converts_in_order() {
        PageResult<Integer> source = PageResult.of(new ArrayList<>(List.of(1, 2)), 20L, 2L, 5L);
        source.setTotalPages(99L);

        PageResult<String> mapped = source.map(String::valueOf);

        assertEquals(List.of("1", "2"), mapped.getData());
        assertEquals(20L, mapped.getTotalCount());
        assertEquals(2L, mapped.getPageIndex());
        assertEquals(5L, mapped.getPageSize());
        assertEquals(99L, mapped.getTotalPages());
        assertEquals(source.getHasNext(), mapped.getHasNext());
        assertEquals(source.getHasPrevious(), mapped.getHasPrevious());
    }

    @Test
    void map_keeps_null_and_inconsistent_metadata() {
        PageResult<Integer> source = new PageResult<>();
        source.setData(new ArrayList<>(List.of(1)));
        source.setTotalCount(-3L);
        source.setPageIndex(null);
        source.setPageSize(0L);
        source.setTotalPages(7L);
        source.setHasNext(null);
        source.setHasPrevious(true);

        PageResult<Integer> mapped = source.map(Function.identity());

        assertEquals(-3L, mapped.getTotalCount());
        assertNull(mapped.getPageIndex());
        assertEquals(0L, mapped.getPageSize());
        assertEquals(7L, mapped.getTotalPages());
        assertNull(mapped.getHasNext());
        assertTrue(mapped.getHasPrevious());
    }

    @Test
    void map_returns_new_result_and_independent_mutable_list() {
        List<Integer> data = new ArrayList<>(List.of(1, 2));
        PageResult<Integer> source = PageResult.of(data, 2L, 1L, 10L);

        PageResult<Integer> mapped = source.map(Function.identity());

        assertNotSame(source, mapped);
        assertNotSame(data, mapped.getData());
        mapped.getData().add(3);
        data.remove(0);
        assertEquals(List.of(1, 2, 3), mapped.getData());
        assertEquals(List.of(2), source.getData());
    }

    @Test
    void map_skips_null_elements_and_keeps_null_results() {
        AtomicInteger calls = new AtomicInteger();
        PageResult<Integer> source = PageResult.of(Arrays.asList(1, null, 2), 3L, 1L, 10L);

        PageResult<String> mapped =
                source.map(
                        value -> {
                            calls.incrementAndGet();
                            return value == 2 ? null : String.valueOf(value);
                        });

        assertEquals(Arrays.asList("1", null, null), mapped.getData());
        assertEquals(2, calls.get());
    }

    @Test
    void map_keeps_null_list_and_returns_new_mutable_empty_list() {
        PageResult<Integer> nullData = new PageResult<>();
        assertNull(nullData.map(String::valueOf).getData());

        List<Integer> empty = new ArrayList<>();
        PageResult<String> mapped = PageResult.of(empty, 0L, 1L, 10L).map(String::valueOf);
        assertNotSame(empty, mapped.getData());
        assertTrue(mapped.getData().isEmpty());
        mapped.getData().add("x");
        assertTrue(empty.isEmpty());
    }

    @Test
    void map_rejects_null_mapper_even_for_empty_or_null_data() {
        assertThrows(IllegalArgumentException.class, () -> PageResult.empty(1L, 10L).map(null));
        assertThrows(IllegalArgumentException.class, () -> new PageResult<Integer>().map(null));
    }

    @Test
    void map_propagates_same_exception_and_stops_processing() {
        IllegalStateException boom = new IllegalStateException("boom");
        List<Integer> data = new ArrayList<>(List.of(1, 2, 3));
        PageResult<Integer> source = PageResult.of(data, 3L, 1L, 10L);
        List<Integer> seen = new ArrayList<>();

        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                source.map(
                                        value -> {
                                            seen.add(value);
                                            if (value == 2) {
                                                throw boom;
                                            }
                                            return value;
                                        }));

        assertSame(boom, thrown);
        assertEquals(List.of(1, 2), seen);
        assertEquals(List.of(1, 2, 3), source.getData());
    }
}
