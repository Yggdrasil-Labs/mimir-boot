package com.yggdrasil.labs.common.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.common.page.PageRequest;

class PageQueryTest {

    static class UserPageQuery extends PageQuery {
        private static final long serialVersionUID = 1L;
    }

    @Test
    void toPageRequest_returns_inner_page_and_tracesetters_work() {
        UserPageQuery q = new UserPageQuery();
        q.setTraceId("trace-1");
        assertEquals("trace-1", q.getTraceId());

        PageRequest pr = q.toPageRequest();
        assertNotNull(pr);

        // 修改内部 PageRequest 并验证引用一致性
        pr.setPageIndex(3L);
        assertEquals(3L, q.getPage().getPageIndex());
    }

    @Test
    void jackson_bound_page_is_corrected_before_conversion() throws Exception {
        UserPageQuery query =
                new ObjectMapper()
                        .readValue(
                                "{\"page\":{\"pageIndex\":-1,\"pageSize\":1001,\"orderDirection\":\"WRONG\"}}",
                                UserPageQuery.class);

        PageRequest request = query.toPageRequest();
        assertSame(query.getPage(), request);
        assertEquals(CommonConstants.DEFAULT_PAGE_NUMBER, request.getPageIndex());
        assertEquals(CommonConstants.MAX_PAGE_SIZE, request.getPageSize());
        assertEquals("ASC", request.getOrderDirection());
    }
}
