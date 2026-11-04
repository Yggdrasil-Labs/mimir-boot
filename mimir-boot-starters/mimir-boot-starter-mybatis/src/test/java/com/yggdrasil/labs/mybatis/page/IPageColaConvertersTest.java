package com.yggdrasil.labs.mybatis.page;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.alibaba.cola.dto.PageResponse;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

class IPageColaConvertersTest {

    @Test
    void maps_without_serializable_bounds_and_preserves_null_positions() {
        List<SourceRecord> records =
                new ArrayList<>(Arrays.asList(new SourceRecord(1), null, new SourceRecord(2)));
        Page<SourceRecord> source = page(records, 10, 2, 3);
        AtomicInteger calls = new AtomicInteger();

        PageResponse<OutputRecord> response =
                IPageColaConverters.toCola(
                        source,
                        value -> {
                            calls.incrementAndGet();
                            return new OutputRecord("v" + value.value());
                        });

        assertTrue(response.isSuccess());
        assertNull(response.getErrCode());
        assertNull(response.getErrMessage());
        assertEquals(10, response.getTotalCount());
        assertEquals(2, response.getPageIndex());
        assertEquals(3, response.getPageSize());
        assertEquals("v1", response.getData().get(0).value());
        assertNull(response.getData().get(1));
        assertEquals("v2", response.getData().get(2).value());
        assertEquals(2, calls.get());
        assertNotSame(records, response.getData());
    }

    @Test
    void mapper_null_result_is_kept_and_metadata_boundaries_are_enforced() {
        PageResponse<String> nullMapped =
                IPageColaConverters.toCola(page(Arrays.asList(1, null, 2), 0, 1, 1), value -> null);
        assertEquals(Arrays.asList(null, null, null), nullMapped.getData());

        List<IPage<Integer>> invalid =
                List.of(
                        invalidPage(-1, 1, 1),
                        invalidPage(1, 0, 1),
                        invalidPage(1, -1, 1),
                        invalidPage(1, 1, 0),
                        invalidPage(1, 1, -1),
                        invalidPage(Integer.MAX_VALUE + 1L, 1, 1),
                        invalidPage(1, Integer.MAX_VALUE + 1L, 1),
                        invalidPage(1, 1, Integer.MAX_VALUE + 1L));
        AtomicInteger calls = new AtomicInteger();
        for (IPage<Integer> source : invalid) {
            IllegalArgumentException error =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    IPageColaConverters.toCola(
                                            source, value -> calls.incrementAndGet()));
            assertTrue(
                    error.getMessage().contains("total")
                            || error.getMessage().contains("current")
                            || error.getMessage().contains("size"));
            verify(source, never()).getRecords();
        }
        assertEquals(0, calls.get());
    }

    @Test
    void identity_conversion_copies_records_and_null_records_become_empty() {
        Object value = new Object();
        List<Object> records = new ArrayList<>(List.of(value));
        Page<Object> source = page(records, 1, 1, 1);
        PageResponse<Object> response = IPageColaConverters.toCola(source);
        assertNotSame(records, response.getData());
        assertSame(value, response.getData().get(0));
        response.getData().add(new Object());
        assertEquals(1, source.getRecords().size());

        assertTrue(IPageColaConverters.toCola(page(null, 0, 1, 1)).getData().isEmpty());
    }

    @Test
    void validates_metadata_before_mapping_and_propagates_mapper_exception() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        IPageColaConverters.toCola(
                                page(List.of(1), Integer.MAX_VALUE + 1L, 1, 1),
                                value -> calls.incrementAndGet()));
        assertEquals(0, calls.get());
        PageResponse<Integer> max =
                IPageColaConverters.toCola(
                        page(List.of(1), Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, max.getPageIndex());
        assertEquals(Integer.MAX_VALUE, max.getPageSize());

        IllegalStateException failure = new IllegalStateException("mapper");
        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                IPageColaConverters.toCola(
                                        page(List.of(1), 1, 1, 1),
                                        value -> {
                                            throw failure;
                                        })));
    }

    @Test
    void null_source_or_mapper_is_rejected() {
        assertThrows(IllegalArgumentException.class, () -> IPageColaConverters.toCola(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> IPageColaConverters.toCola(page(List.of(), 0, 1, 1), null));
    }

    private static <T> Page<T> page(List<T> records, long total, long current, long size) {
        Page<T> page = new Page<>(current, size, total);
        page.setRecords(records);
        return page;
    }

    private record SourceRecord(int value) {}

    private record OutputRecord(String value) {}

    @SuppressWarnings("unchecked")
    private static IPage<Integer> invalidPage(long total, long current, long size) {
        IPage<Integer> page = mock(IPage.class);
        when(page.getTotal()).thenReturn(total);
        when(page.getCurrent()).thenReturn(current);
        when(page.getSize()).thenReturn(size);
        return page;
    }
}
