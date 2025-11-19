package com.yggdrasil.labs.test.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FilterChainMockBuilder 测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class FilterChainMockBuilderTest {

    @Test
    void testCreate() {
        FilterChainMockBuilder builder = FilterChainMockBuilder.create();
        assertNotNull(builder, "构建器不应为 null");
    }

    @Test
    void testStatusCode() {
        FilterChainMockBuilder builder = FilterChainMockBuilder.create();
        FilterChainMockBuilder result = builder.statusCode(404);
        
        assertSame(builder, result, "应返回自身以支持链式调用");
    }

    @Test
    void testBuild_DefaultStatusCode() throws Exception {
        FilterChain chain = FilterChainMockBuilder.create().build();
        assertNotNull(chain, "FilterChain 不应为 null");

        HttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        chain.doFilter(request, response);

        assertEquals(200, response.getStatus(), "默认状态码应为 200");
    }

    @Test
    void testBuild_CustomStatusCode() throws Exception {
        FilterChain chain = FilterChainMockBuilder.create()
                .statusCode(404)
                .build();

        HttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        chain.doFilter(request, response);

        assertEquals(404, response.getStatus(), "状态码应为 404");
    }

    @Test
    void testBuild_MultipleStatusCodes() throws Exception {
        int[] statusCodes = {200, 201, 204, 301, 302, 400, 401, 403, 404, 500, 502, 503};

        for (int statusCode : statusCodes) {
            FilterChain chain = FilterChainMockBuilder.create()
                    .statusCode(statusCode)
                    .build();

            HttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            chain.doFilter(request, response);

            assertEquals(statusCode, response.getStatus(),
                    "状态码 " + statusCode + " 应正确设置");
        }
    }

    @Test
    void testBuild_ChainedCalls() throws Exception {
        FilterChain chain = FilterChainMockBuilder.create()
                .statusCode(201)
                .statusCode(202)
                .statusCode(204)
                .build();

        HttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        chain.doFilter(request, response);

        assertEquals(204, response.getStatus(), "最后一次设置的状态码应生效");
    }
}

