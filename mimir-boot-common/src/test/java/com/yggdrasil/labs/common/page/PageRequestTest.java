package com.yggdrasil.labs.common.page;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        pr.setPageSize(0L);
        pr.setOrderDirection("WRONG");

        assertEquals(CommonConstants.DEFAULT_PAGE_NUMBER, pr.getPageIndex());
        assertEquals(CommonConstants.DEFAULT_PAGE_SIZE, pr.getPageSize());
        assertEquals(OrderDirection.ASC.getCode(), pr.getOrderDirection());

        pr.setPageIndex(null);
        pr.setPageSize(CommonConstants.MAX_PAGE_SIZE + 1);
        pr.setOrderDirection(null);
        assertEquals(CommonConstants.DEFAULT_PAGE_NUMBER, pr.getPageIndex());
        assertEquals(CommonConstants.MAX_PAGE_SIZE, pr.getPageSize());
        assertEquals(OrderDirection.ASC.getCode(), pr.getOrderDirection());

        pr.setPageIndex(Long.MAX_VALUE);
        pr.setPageSize(null);
        pr.setOrderDirection("desc");
        assertEquals(Long.MAX_VALUE, pr.getPageIndex());
        assertEquals(CommonConstants.DEFAULT_PAGE_SIZE, pr.getPageSize());
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
    void getOffset_corrects_values_assigned_after_construction() {
        PageRequest pr = new PageRequest();
        pr.setPageIndex(null);
        pr.setPageSize(CommonConstants.MAX_PAGE_SIZE + 1);
        pr.setOrderDirection("WRONG");

        assertEquals(0L, pr.getOffset());
        assertEquals(CommonConstants.DEFAULT_PAGE_NUMBER, pr.getPageIndex());
        assertEquals(CommonConstants.MAX_PAGE_SIZE, pr.getPageSize());
        assertEquals(OrderDirection.ASC.getCode(), pr.getOrderDirection());

        pr.setPageIndex(0L);
        pr.setPageSize(-1L);
        pr.setOrderDirection(null);

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
}
