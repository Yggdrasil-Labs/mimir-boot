package com.yggdrasil.labs.common.page;

import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.common.enums.OrderDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
