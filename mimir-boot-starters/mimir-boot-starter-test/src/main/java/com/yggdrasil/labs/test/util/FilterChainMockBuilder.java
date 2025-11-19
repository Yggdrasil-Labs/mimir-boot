package com.yggdrasil.labs.test.util;

import com.yggdrasil.labs.common.exception.ErrorCode;
import com.yggdrasil.labs.common.exception.SystemException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.mockito.stubbing.Answer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/**
 * FilterChain Mock 构建器
 *
 * <p>提供链式 API 简化 FilterChain 的 mock 设置，特别是状态码设置。</p>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * FilterChain chain = FilterChainMockBuilder.create()
 *     .statusCode(404)
 *     .build();
 * }</pre>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class FilterChainMockBuilder {

    private final FilterChain chain;
    private int statusCode = 200;

    private FilterChainMockBuilder(FilterChain chain) {
        this.chain = chain;
    }

    /**
     * 创建构建器
     *
     * @return 构建器实例
     */
    public static FilterChainMockBuilder create() {
        return new FilterChainMockBuilder(mock(FilterChain.class));
    }

    /**
     * 设置响应状态码
     *
     * @param statusCode HTTP 状态码
     * @return 构建器实例
     */
    public FilterChainMockBuilder statusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    /**
     * 构建 FilterChain mock
     *
     * @return FilterChain mock
     * @throws RuntimeException 如果设置 mock 时发生错误
     */
    public FilterChain build() {
        try {
            doAnswer((Answer<Void>) invocation -> {
                HttpServletResponse resp = invocation.getArgument(1);
                resp.setStatus(statusCode);
                return null;
            }).when(chain).doFilter(any(), any());
        } catch (Exception e) {
            throw new SystemException(ErrorCode.SYSTEM_ERROR.getCode(), "Failed to setup FilterChain mock", e);
        }
        return chain;
    }
}

