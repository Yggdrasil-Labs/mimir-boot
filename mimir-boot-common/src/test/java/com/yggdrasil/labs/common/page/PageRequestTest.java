package com.yggdrasil.labs.common.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.common.enums.OrderDirection;

class PageRequestTest {

    @Test
    void default_values_and_getOffset() {
        PageRequest pr = new PageRequest();
        assertEquals(CommonConstants.DEFAULT_PAGE_NUMBER, pr.getPageIndex());
        assertEquals(CommonConstants.DEFAULT_PAGE_SIZE, pr.getPageSize());
        assertEquals(OrderDirection.ASC.getCode(), pr.getOrderDirection());
        assertEquals(0L, pr.getOffset());
    }

    @Test
    void of_sets_values_and_validates() {
        PageRequest pr = PageRequest.of(2L, 5L, "id", "DESC");
        assertEquals(2L, pr.getPageIndex());
        assertEquals(5L, pr.getPageSize());
        assertEquals("id", pr.getOrderBy());
        assertEquals("DESC", pr.getOrderDirection());
        assertEquals(5L, pr.getOffset());
    }

    @Test
    void validateAndCorrect_clamps_invalid_values() {
        PageRequest pr = new PageRequest(0L, -1L, "id", "WRONG");
        assertEquals(CommonConstants.DEFAULT_PAGE_NUMBER, pr.getPageIndex());
        assertEquals(CommonConstants.DEFAULT_PAGE_SIZE, pr.getPageSize());
        assertEquals(OrderDirection.ASC.getCode(), pr.getOrderDirection());
    }

    @Test
    void validateAndCorrect_caps_to_max_page_size() {
        PageRequest pr = new PageRequest(1L, CommonConstants.MAX_PAGE_SIZE + 100, null, "DESC");
        assertEquals(CommonConstants.MAX_PAGE_SIZE, pr.getPageSize());
        assertEquals(OrderDirection.DESC.getCode(), pr.getOrderDirection());
    }

    @Test
    void setters_correct_values_before_direct_reads() {
        PageRequest pr = new PageRequest();
        pr.setPageIndex(-1L);
        assertEquals(CommonConstants.DEFAULT_PAGE_NUMBER, pr.getPageIndex());
        pr.setPageSize(0L);
        assertEquals(CommonConstants.DEFAULT_PAGE_SIZE, pr.getPageSize());
        pr.setOrderDirection("WRONG");
        assertEquals(OrderDirection.ASC.getCode(), pr.getOrderDirection());

        pr.setPageIndex(null);
        assertEquals(CommonConstants.DEFAULT_PAGE_NUMBER, pr.getPageIndex());
        pr.setPageSize(CommonConstants.MAX_PAGE_SIZE + 1);
        assertEquals(CommonConstants.MAX_PAGE_SIZE, pr.getPageSize());
        pr.setOrderDirection(null);
        assertEquals(OrderDirection.ASC.getCode(), pr.getOrderDirection());

        pr.setPageIndex(Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, pr.getPageIndex());
        pr.setPageSize(null);
        assertEquals(CommonConstants.DEFAULT_PAGE_SIZE, pr.getPageSize());
        pr.setOrderDirection("desc");
        assertEquals("desc", pr.getOrderDirection());
    }

    @Test
    void jackson_binding_matches_constructor_correction() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Long[][] inputs = {
            {-1L, 0L}, {0L, -1L}, {null, null}, {2L, CommonConstants.MAX_PAGE_SIZE + 1}
        };
        for (Long[] input : inputs) {
            String json =
                    mapper.createObjectNode()
                            .put("pageIndex", input[0])
                            .put("pageSize", input[1])
                            .put("orderDirection", "WRONG")
                            .toString();
            PageRequest bound = mapper.readValue(json, PageRequest.class);
            PageRequest constructed = new PageRequest(input[0], input[1], null, "WRONG");

            assertEquals(constructed, bound, json);
            assertEquals(constructed.getOffset(), bound.getOffset(), json);
        }
    }

    @Test
    void jackson_single_field_binding_corrects_values() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String[] inputs = {
            "{\"pageIndex\":-1}",
            "{\"pageIndex\":null}",
            "{\"pageSize\":0}",
            "{\"pageSize\":null}",
            "{\"orderDirection\":\"WRONG\"}"
        };
        for (String json : inputs) {
            assertEquals(new PageRequest(), mapper.readValue(json, PageRequest.class), json);
        }

        String oversizedPage =
                mapper.createObjectNode()
                        .put("pageSize", CommonConstants.MAX_PAGE_SIZE + 1)
                        .toString();
        assertEquals(
                PageRequest.of(CommonConstants.DEFAULT_PAGE_NUMBER, CommonConstants.MAX_PAGE_SIZE),
                mapper.readValue(oversizedPage, PageRequest.class),
                oversizedPage);
    }

    @Test
    void jackson_field_order_does_not_change_correction() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String[] inputs = {
            "{\"pageIndex\":-1,\"pageSize\":0,\"orderDirection\":\"WRONG\"}",
            "{\"orderDirection\":\"WRONG\",\"pageSize\":0,\"pageIndex\":-1}",
            "{\"pageIndex\":-1,\"orderDirection\":\"WRONG\",\"pageSize\":0}"
        };
        for (String json : inputs) {
            assertEquals(new PageRequest(), mapper.readValue(json, PageRequest.class), json);
        }
    }

    @Test
    void getOffset_corrects_values_assigned_after_construction()
            throws ReflectiveOperationException {
        PageRequest pr = new PageRequest();
        // 直接写字段，避免 setter 提前校正而掩盖 getOffset 的校验边界。
        setField(pr, "pageIndex", null);
        setField(pr, "pageSize", CommonConstants.MAX_PAGE_SIZE + 1);
        setField(pr, "orderDirection", "WRONG");
        assertNull(pr.getPageIndex());
        assertEquals(CommonConstants.MAX_PAGE_SIZE + 1, pr.getPageSize());
        assertEquals("WRONG", pr.getOrderDirection());

        assertEquals(0L, pr.getOffset());
        assertEquals(CommonConstants.DEFAULT_PAGE_NUMBER, pr.getPageIndex());
        assertEquals(CommonConstants.MAX_PAGE_SIZE, pr.getPageSize());
        assertEquals(OrderDirection.ASC.getCode(), pr.getOrderDirection());

        setField(pr, "pageIndex", 0L);
        setField(pr, "pageSize", -1L);
        setField(pr, "orderDirection", null);

        assertEquals(0L, pr.getOffset());
        assertEquals(CommonConstants.DEFAULT_PAGE_NUMBER, pr.getPageIndex());
        assertEquals(CommonConstants.DEFAULT_PAGE_SIZE, pr.getPageSize());
        assertEquals(OrderDirection.ASC.getCode(), pr.getOrderDirection());
    }

    @Test
    void returnsLargestRepresentableOffset() {
        long pageSize = CommonConstants.MAX_PAGE_SIZE;
        PageRequest request = PageRequest.of(Long.MAX_VALUE / pageSize + 1, pageSize);

        assertEquals((Long.MAX_VALUE / pageSize) * pageSize, request.getOffset());
    }

    @Test
    void rejectsOffsetOverflow() {
        long pageSize = CommonConstants.MAX_PAGE_SIZE;
        PageRequest request = PageRequest.of(Long.MAX_VALUE / pageSize + 2, pageSize);

        IllegalArgumentException exception =
                org.junit.jupiter.api.Assertions.assertThrows(
                        IllegalArgumentException.class, request::getOffset);
        assertEquals("分页偏移量超出 Long 范围", exception.getMessage());
    }

    private static void setField(PageRequest request, String name, Object value)
            throws ReflectiveOperationException {
        var field = PageRequest.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(request, value);
    }
}
