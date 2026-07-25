package com.yggdrasil.labs.web.config;

import com.yggdrasil.labs.web.interceptor.TraceInterceptor;
import com.yggdrasil.labs.web.interceptor.WebInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Web MVC 拦截器注册测试。
 *
 * @author Yggdrasil Labs
 * @since 2.1.1
 */
class WebMvcConfigTest {

    @Test
    void shouldRegisterTraceInterceptorBeforeWebInterceptor() {
        TraceInterceptor traceInterceptor = new TraceInterceptor();
        WebInterceptor webInterceptor = new WebInterceptor();
        CapturingInterceptorRegistry registry = new CapturingInterceptorRegistry();

        new WebMvcConfig(webInterceptor, Optional.of(traceInterceptor)).addInterceptors(registry);

        List<Object> interceptors = registry.interceptors();
        assertEquals(2, interceptors.size());

        MappedInterceptor traceMapping = (MappedInterceptor) interceptors.get(0);
        assertSame(traceInterceptor, traceMapping.getInterceptor());
        assertArrayEquals(new String[]{"/**"}, traceMapping.getIncludePathPatterns());
        assertArrayEquals(new String[]{"/favicon.ico", "/error", "/actuator/**", "/swagger-ui/**",
                "/swagger-resources/**", "/v3/api-docs/**", "/doc.html"}, traceMapping.getExcludePathPatterns());

        MappedInterceptor webMapping = (MappedInterceptor) interceptors.get(1);
        assertSame(webInterceptor, webMapping.getInterceptor());
    }

    @Test
    void shouldRegisterWebInterceptorWhenTraceInterceptorIsAbsent() {
        WebInterceptor webInterceptor = new WebInterceptor();
        CapturingInterceptorRegistry registry = new CapturingInterceptorRegistry();

        new WebMvcConfig(webInterceptor, Optional.empty()).addInterceptors(registry);

        List<Object> interceptors = registry.interceptors();
        assertEquals(1, interceptors.size());
        assertSame(webInterceptor, ((MappedInterceptor) interceptors.get(0)).getInterceptor());
    }

    private static class CapturingInterceptorRegistry extends InterceptorRegistry {

        List<Object> interceptors() {
            return getInterceptors();
        }
    }
}
