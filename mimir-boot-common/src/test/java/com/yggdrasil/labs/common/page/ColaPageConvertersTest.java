package com.yggdrasil.labs.common.page;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.alibaba.cola.dto.PageResponse;

class ColaPageConvertersTest {

    @Test
    void converts_metadata_without_swapping_index_and_size() {
        PageResult<Integer> source = PageResult.of(Arrays.asList(1, null, 2), 20L, 2L, 5L);

        PageResponse<Integer> response = ColaPageConverters.toCola(source);

        assertTrue(response.isSuccess());
        assertNull(response.getErrCode());
        assertNull(response.getErrMessage());
        assertEquals(20, response.getTotalCount());
        assertEquals(2, response.getPageIndex());
        assertEquals(5, response.getPageSize());
        assertEquals(Arrays.asList(1, null, 2), response.getData());
    }

    @Test
    void mapping_skips_null_elements_and_keeps_null_results_in_order() {
        AtomicInteger calls = new AtomicInteger();
        PageResult<Integer> source = PageResult.of(Arrays.asList(1, null, 2), 3L, 1L, 10L);

        PageResponse<StringBuilder> response =
                ColaPageConverters.toCola(
                        source,
                        value -> {
                            calls.incrementAndGet();
                            return value == 2 ? null : new StringBuilder("v" + value);
                        });

        assertEquals(2, calls.get());
        assertEquals(3, response.getData().size());
        assertEquals("v1", response.getData().get(0).toString());
        assertNull(response.getData().get(1));
        assertNull(response.getData().get(2));
    }

    @Test
    void default_conversion_equals_identity_mapping_and_isolates_list() {
        List<Integer> data = new ArrayList<>(List.of(1, 2));
        PageResult<Integer> source = PageResult.of(data, 2L, 1L, 10L);

        PageResponse<Integer> byDefault = ColaPageConverters.toCola(source);
        PageResponse<Integer> byIdentity = ColaPageConverters.toCola(source, Function.identity());

        assertEquals(byIdentity.getData(), byDefault.getData());
        assertEquals(byIdentity.getTotalCount(), byDefault.getTotalCount());
        assertNotSame(data, byDefault.getData());
        byDefault.getData().add(3);
        data.remove(0);
        assertEquals(List.of(1, 2, 3), byDefault.getData());
        assertEquals(List.of(2), source.getData());
    }

    @Test
    void null_or_empty_data_reads_as_empty_list() {
        PageResult<Integer> nullData = new PageResult<>();
        nullData.setTotalCount(0L);
        nullData.setPageIndex(1L);
        nullData.setPageSize(1L);

        assertTrue(ColaPageConverters.toCola(nullData).getData().isEmpty());
        assertTrue(
                ColaPageConverters.toCola(PageResult.<Integer>empty(1L, 1L)).getData().isEmpty());
    }

    @Test
    void accepts_int_boundaries_and_page_beyond_last() {
        PageResult<Integer> max = page(2147483647L, 2147483647L, 2147483647L);
        PageResponse<Integer> response = ColaPageConverters.toCola(max);

        assertEquals(Integer.MAX_VALUE, response.getTotalCount());
        assertEquals(Integer.MAX_VALUE, response.getPageIndex());
        assertEquals(Integer.MAX_VALUE, response.getPageSize());

        PageResponse<Integer> beyond = ColaPageConverters.toCola(page(0L, 9L, 1L));
        assertEquals(0, beyond.getTotalCount());
        assertEquals(9, beyond.getPageIndex());
        assertEquals(1, beyond.getPageSize());
    }

    @Test
    void invalid_metadata_is_rejected_before_any_mapping() {
        List<PageResult<Integer>> invalid =
                List.of(
                        page(null, 1L, 1L),
                        page(1L, null, 1L),
                        page(1L, 1L, null),
                        page(-1L, 1L, 1L),
                        page(1L, 0L, 1L),
                        page(1L, -1L, 1L),
                        page(1L, 1L, 0L),
                        page(1L, 1L, -5L),
                        page(2147483648L, 1L, 1L),
                        page(1L, 2147483648L, 1L),
                        page(1L, 1L, Long.MAX_VALUE));
        AtomicInteger calls = new AtomicInteger();

        for (PageResult<Integer> source : invalid) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ColaPageConverters.toCola(source, v -> calls.incrementAndGet()));
            assertThrows(IllegalArgumentException.class, () -> ColaPageConverters.toCola(source));
        }
        assertEquals(0, calls.get());
    }

    @Test
    void null_source_or_mapper_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> ColaPageConverters.toCola(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> ColaPageConverters.toCola(null, Function.identity()));
        assertThrows(
                IllegalArgumentException.class,
                () -> ColaPageConverters.toCola(PageResult.<Integer>empty(1L, 1L), null));
    }

    @Test
    void mapper_exception_propagates_same_instance_and_stops() {
        IllegalStateException boom = new IllegalStateException("boom");
        List<Integer> seen = new ArrayList<>();
        PageResult<Integer> source = PageResult.of(List.of(1, 2, 3), 3L, 1L, 10L);

        IllegalStateException thrown =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                ColaPageConverters.toCola(
                                        source,
                                        value -> {
                                            seen.add(value);
                                            if (value == 2) {
                                                throw boom;
                                            }
                                            return value;
                                        }));

        assertSame(boom, thrown);
        assertEquals(List.of(1, 2), seen);
    }

    private static PageResult<Integer> page(Long totalCount, Long pageIndex, Long pageSize) {
        PageResult<Integer> result = new PageResult<>();
        result.setData(new ArrayList<>(List.of(1)));
        result.setTotalCount(totalCount);
        result.setPageIndex(pageIndex);
        result.setPageSize(pageSize);
        return result;
    }
}
