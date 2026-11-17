package com.yggdrasil.labs.log.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

import com.yggdrasil.labs.log.task.MdcTasks;

/** 去掉 Logback、改用 Log4j2 的场景；由 LogIsolatedJvmIntegrationTest 在子 JVM 中运行 */
class BackendCompatibilityScenarios {

    @Test
    void classpath_really_has_no_logback() {
        assertThat(ClassUtils.isPresent("ch.qos.logback.classic.LoggerContext", null)).isFalse();
        assertThat(LoggerFactory.getILoggerFactory().getClass().getName())
                .containsIgnoringCase("log4j");
    }

    @Test
    void application_without_preset_starts_refreshes_and_closes() {
        try (ConfigurableApplicationContext context = start()) {
            assertThat(context.containsBean("logMaskAutoConfiguration")).isFalse();
            assertThat(
                            context.getEnvironment()
                                    .getPropertySources()
                                    .contains(LogPresetSupport.RESOLVED_PROPERTY_SOURCE))
                    .isFalse();
        }
    }

    @Test
    void startup_failure_without_token_cleans_up_without_linkage_error() {
        assertThatThrownBy(() -> start("--test.fail=true"))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("boom");
    }

    @Test
    void explicit_preset_is_rejected_with_compatibility_error() {
        assertThatThrownBy(
                        () ->
                                start(
                                        "--logging.config=classpath:com/yggdrasil/labs/log/logback/server.xml"))
                .hasMessageContaining("Logback")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void mdc_tasks_propagate_and_restore_with_log4j2_mdc() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> MDC.put("worker", "w")).get();
            MDC.put("traceId", "t1");
            String seen =
                    executor.submit(
                                    MdcTasks.wrap(
                                            () -> MDC.get("traceId") + "/" + MDC.get("worker")))
                            .get(5, TimeUnit.SECONDS);
            assertThat(seen).isEqualTo("t1/null");
            assertThat(executor.submit(() -> MDC.getCopyOfContextMap()).get())
                    .isEqualTo(Map.of("worker", "w"));
        } finally {
            MDC.clear();
            executor.shutdownNow();
        }
    }

    @Test
    void access_filter_remains_usable_without_logback() throws Exception {
        assertThat(ClassUtils.isPresent("ch.qos.logback.classic.Logger", null)).isFalse();
        var request =
                new org.springframework.mock.web.MockHttpServletRequest("GET", "/backend-access");
        request.setRemoteAddr("127.0.0.1");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        java.util.concurrent.atomic.AtomicBoolean invoked =
                new java.util.concurrent.atomic.AtomicBoolean();
        new com.yggdrasil.labs.log.web.AccessLogFilter(3000, java.util.List.of())
                .doFilter(request, response, (req, res) -> invoked.set(true));
        assertThat(invoked.get()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private static ConfigurableApplicationContext start(String... args) {
        SpringApplication application = new SpringApplication(App.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        return application.run(args);
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
        LogMaskAutoConfiguration.class,
        MdcTaskExecutionAutoConfiguration.class
    })
    static class App {
        @Bean
        Object probe(org.springframework.core.env.Environment environment) {
            if (environment.getProperty("test.fail", Boolean.class, false)) {
                throw new IllegalStateException("boom");
            }
            return new Object();
        }
    }
}
