package com.yggdrasil.labs.web.config;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS 安全策略测试。
 *
 * @author Yggdrasil Labs
 * @since 2.1.1
 */
class CorsConfigTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebAutoConfiguration.class));

    @Test
    void shouldNotRegisterCorsFilterByDefault() {
        runner.run(context -> org.assertj.core.api.Assertions.assertThat(context)
                .doesNotHaveBean(CorsConfig.class));
    }

    @Test
    void shouldRejectWildcardOriginWhenCredentialsAreEnabled() {
        runner.withPropertyValues(
                "mimir.boot.web.cors.enabled=true",
                "mimir.boot.web.cors.allowed-origins[0]=*",
                "mimir.boot.web.cors.allow-credentials=true"
        ).run(context -> {
            org.assertj.core.api.Assertions.assertThat(context).hasFailed();
            org.assertj.core.api.Assertions.assertThat(context.getStartupFailure())
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        });
    }

    @Test
    void shouldRejectEnabledCorsWithoutAllowedOrigin() {
        runner.withPropertyValues("mimir.boot.web.cors.enabled=true")
                .run(context -> {
                    org.assertj.core.api.Assertions.assertThat(context).hasFailed();
                    org.assertj.core.api.Assertions.assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class);
                });
    }

    @Test
    void shouldAllowPreflightAndCredentialedRequestForExplicitOrigin() throws Exception {
        WebProperties properties = new WebProperties();
        properties.getCors().setAllowedOrigins(List.of("https://app.example.com"));
        properties.getCors().setAllowCredentials(true);

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new CorsTestController())
                .addFilters(new CorsConfig(properties).corsFilter())
                .build();

        mockMvc.perform(options("/cors-test")
                        .header(HttpHeaders.ORIGIN, "https://app.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example.com"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/cors-test")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        mockMvc.perform(get("/cors-test")
                        .header(HttpHeaders.ORIGIN, "https://app.example.com")
                        .cookie(new Cookie("SESSION", "session-value")))
                .andExpect(status().isOk())
                .andExpect(content().string("session-value"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://app.example.com"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @RestController
    static class CorsTestController {

        @GetMapping("/cors-test")
        String corsTest(@CookieValue("SESSION") String session) {
            return session;
        }
    }
}
