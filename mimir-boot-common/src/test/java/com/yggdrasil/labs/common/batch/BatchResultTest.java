package com.yggdrasil.labs.common.batch;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.yggdrasil.labs.common.exception.ErrorCode;
import com.yggdrasil.labs.common.exception.ErrorDefinition;

class BatchResultTest {

    private static final ErrorDefinition NOT_FOUND = definition("ORDER_NOT_FOUND", "订单不存在");

    @Test
    void partial_success_derives_counts_and_keeps_order() {
        List<BatchItemResult<String>> items =
                List.of(
                        BatchItemResult.success(0, "a", "ok"),
                        BatchItemResult.failure(1, "b", NOT_FOUND),
                        BatchItemResult.success(2, null, null));

        BatchResult<String> batch = BatchResult.of(items);

        assertEquals(3, batch.getTotalCount());
        assertEquals(2, batch.getSuccessCount());
        assertEquals(1, batch.getFailureCount());
        assertEquals(BatchStatus.PARTIAL_SUCCESS, batch.getStatus());
        assertEquals(items, batch.getItems());
    }

    @Test
    void empty_all_success_and_all_failed_status() {
        BatchResult<String> empty = BatchResult.of(List.of());
        assertEquals(BatchStatus.EMPTY, empty.getStatus());
        assertEquals(0, empty.getTotalCount());
        assertEquals(0, empty.getSuccessCount());
        assertEquals(0, empty.getFailureCount());
        assertNotNull(empty.getItems());
        assertTrue(empty.getItems().isEmpty());

        assertEquals(
                BatchStatus.ALL_SUCCESS,
                BatchResult.of(List.of(BatchItemResult.<String>success(0, null, "x"))).getStatus());
        BatchResult<String> failed =
                BatchResult.of(
                        List.of(
                                BatchItemResult.<String>failure(0, null, NOT_FOUND),
                                BatchItemResult.<String>failure(null, "k", NOT_FOUND, "覆盖")));
        assertEquals(BatchStatus.ALL_FAILED, failed.getStatus());
        assertEquals(2, failed.getFailureCount());
        assertEquals(0, failed.getSuccessCount());
    }

    @Test
    void success_and_failure_fields_are_mutually_exclusive() {
        BatchItemResult<String> success = BatchItemResult.success(0, "a", null);
        assertTrue(success.isSuccess());
        assertNull(success.getData());
        assertNull(success.getCode());
        assertNull(success.getMessage());

        BatchItemResult<String> failure = BatchItemResult.failure(1, "b", NOT_FOUND, "订单 b 不存在");
        assertFalse(failure.isSuccess());
        assertNull(failure.getData());
        assertEquals("ORDER_NOT_FOUND", failure.getCode());
        assertEquals("订单 b 不存在", failure.getMessage());
        assertEquals("订单不存在", BatchItemResult.failure(1, "b", NOT_FOUND).getMessage());
    }

    @Test
    void failure_follows_error_definition_semantics() {
        BatchItemResult<String> nullMessages =
                BatchItemResult.failure(0, null, definition("E1", null), null);
        assertNull(nullMessages.getMessage());
        assertEquals("  ", BatchItemResult.failure(0, null, NOT_FOUND, "  ").getMessage());
        assertEquals(
                " 00000 ", BatchItemResult.failure(0, null, definition(" 00000 ", "m")).getCode());

        assertThrows(IllegalArgumentException.class, () -> BatchItemResult.failure(0, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> BatchItemResult.failure(0, null, ErrorCode.SUCCESS));
        for (String code : new String[] {null, "", " \t", "00000"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BatchItemResult.failure(0, null, definition(code, "m")),
                    "code=" + code);
        }
    }

    @Test
    void association_by_index_or_input_id() {
        BatchItemResult<String> byId = BatchItemResult.success(null, "order-1", "x");
        assertNull(byId.getIndex());
        assertEquals("order-1", byId.getInputId());

        BatchItemResult<String> byIndexBlankId = BatchItemResult.success(0, "  ", "x");
        assertEquals("  ", byIndexBlankId.getInputId());

        assertThrows(IllegalArgumentException.class, () -> BatchItemResult.success(-1, "a", "x"));
        assertThrows(
                IllegalArgumentException.class, () -> BatchItemResult.success(null, null, "x"));
        assertThrows(IllegalArgumentException.class, () -> BatchItemResult.success(null, " ", "x"));
        assertThrows(
                IllegalArgumentException.class, () -> BatchItemResult.failure(-2, "a", NOT_FOUND));
        assertThrows(
                IllegalArgumentException.class, () -> BatchItemResult.failure(null, "", NOT_FOUND));
    }

    @Test
    void keeps_completion_order_gaps_and_duplicate_ids() {
        List<BatchItemResult<String>> items =
                List.of(
                        BatchItemResult.success(5, "dup", "a"),
                        BatchItemResult.success(1, "dup", "b"),
                        BatchItemResult.failure(1, "dup", NOT_FOUND),
                        BatchItemResult.success(null, "z", "c"));

        BatchResult<String> batch = BatchResult.of(items);

        assertEquals(items, batch.getItems());
        assertEquals(4, batch.getTotalCount());
        assertEquals(3, batch.getSuccessCount());
    }

    @Test
    void rejects_null_list_or_null_item() {
        assertThrows(IllegalArgumentException.class, () -> BatchResult.of(null));
        List<BatchItemResult<String>> withNull =
                Arrays.asList(BatchItemResult.success(0, null, "a"), null);
        assertThrows(IllegalArgumentException.class, () -> BatchResult.of(withNull));
    }

    @Test
    void snapshot_is_isolated_and_unmodifiable() {
        List<BatchItemResult<String>> items = new ArrayList<>();
        items.add(BatchItemResult.success(0, null, "a"));
        BatchResult<String> batch = BatchResult.of(items);

        items.add(BatchItemResult.failure(1, null, NOT_FOUND));
        items.set(0, BatchItemResult.failure(0, null, NOT_FOUND));

        assertEquals(1, batch.getTotalCount());
        assertEquals(BatchStatus.ALL_SUCCESS, batch.getStatus());
        List<BatchItemResult<String>> view = batch.getItems();
        assertThrows(UnsupportedOperationException.class, () -> view.add(null));
        assertThrows(UnsupportedOperationException.class, () -> view.remove(0));
        assertThrows(
                UnsupportedOperationException.class,
                () -> view.set(0, BatchItemResult.success(0, null, "b")));
    }

    static ErrorDefinition definition(String code, String message) {
        return new ErrorDefinition() {
            @Override
            public String getCode() {
                return code;
            }

            @Override
            public String getMessage() {
                return message;
            }
        };
    }
}
