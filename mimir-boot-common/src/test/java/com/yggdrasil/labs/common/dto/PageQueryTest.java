package com.yggdrasil.labs.common.dto;

import com.yggdrasil.labs.common.page.PageRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
}


