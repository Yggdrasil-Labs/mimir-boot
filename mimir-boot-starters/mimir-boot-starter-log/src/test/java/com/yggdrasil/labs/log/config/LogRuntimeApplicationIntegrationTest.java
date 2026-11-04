package com.yggdrasil.labs.log.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;

import com.yggdrasil.labs.log.converter.SensitiveDataConverter;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;

/** 真实 SpringApplication 启动：日志正式初始化后、Bean 创建期、刷新后三阶段的脱敏与生命周期 */
@ExtendWith(OutputCaptureExtension.class)
class LogRuntimeApplicationIntegrationTest {

    static final String CONTAINER = "classpath:com/yggdrasil/labs/log/logback/container.xml";
    static final String SECRETS = "password=alpha token=beta secret=gamma api_key=delta";
    private static final Logger LOG = LoggerFactory.getLogger("demo.runtime");
    static volatile String marker;

    @TempDir Path logPath;

    @BeforeEach
    void setUp() {
        MaskTestSupport.reset();
        marker = UUID.randomUUID().toString();
    }

    @AfterEach
    void tearDown() {
        MaskTestSupport.reset();
        MDC.clear();
    }

    @Test
    void container_preset_masks_all_three_phases_and_exception_chain(CapturedOutput output) {
        try (ConfigurableApplicationContext context =
                start(Map.of(), "--logging.config=" + CONTAINER)) {
            LOG.info("phase3 {} {}", marker, SECRETS);
        }

        for (String phase : List.of("phase1", "phase2", "phase3")) {
            assertThat(lineOf(output, phase))
                    .contains("password=**** token=**** secret=**** api_key=****");
        }
        assertThat(output.getOut()).doesNotContain("alpha", "gamma", "delta");
        assertThat(output.getOut()).contains("cause token=****").doesNotContain("token=beta");
    }

    @Test
    void default_properties_single_rule_and_empty_list_hold_across_phases(CapturedOutput output) {
        try (ConfigurableApplicationContext context =
                start(
                        Map.of("mimir.boot.log.mask.enabled-patterns", "password"),
                        "--logging.config=" + CONTAINER)) {
            LOG.info("phase3 {} {}", marker, SECRETS);
        }
        for (String phase : List.of("phase1", "phase2", "phase3")) {
            assertThat(lineOf(output, phase))
                    .contains("password=**** token=beta secret=gamma api_key=delta");
        }

        marker = UUID.randomUUID().toString();
        try (ConfigurableApplicationContext context =
                start(
                        Map.of("mimir.boot.log.mask.enabled-patterns", List.of()),
                        "--logging.config=" + CONTAINER)) {
            LOG.info("phase3 {} {}", marker, SECRETS);
        }
        for (String phase : List.of("phase1", "phase2", "phase3")) {
            assertThat(lineOf(output, phase)).contains(SECRETS);
        }
    }

    @Test
    void configuration_argument_overrides_default_properties(CapturedOutput output) {
        try (ConfigurableApplicationContext context =
                start(
                        Map.of("mimir.boot.log.mask.enabled-patterns", "password"),
                        "--logging.config=" + CONTAINER,
                        "--mimir.boot.log.mask.enabled-patterns=token")) {
            LOG.info("phase3 {} {}", marker, SECRETS);
        }
        for (String phase : List.of("phase1", "phase2", "phase3")) {
            assertThat(lineOf(output, phase)).contains("password=alpha token=**** secret=gamma");
        }
    }

    @Test
    void sequential_new_legacy_new_starts_leave_no_residue(CapturedOutput output) {
        try (ConfigurableApplicationContext context =
                start(Map.of(), "--logging.config=" + CONTAINER)) {
            LOG.info("first {} {}", marker, SECRETS);
        }
        try (ConfigurableApplicationContext context =
                start(Map.of(), "--logging.file.path=" + logPath, "--spring.profiles.active=dev")) {
            LOG.info("legacy {} {}", marker, SECRETS);
        }
        try (ConfigurableApplicationContext context =
                start(
                        Map.of(),
                        "--logging.config=" + CONTAINER,
                        "--mimir.boot.log.mask.enabled-patterns=secret")) {
            LOG.info("third {} {}", marker, SECRETS);
        }

        assertThat(lineOf(output, "first")).contains("password=**** token=****");
        assertThat(lineOf(output, "legacy")).contains(SECRETS);
        assertThat(lineOf(output, "third"))
                .contains("password=alpha token=beta secret=**** api_key=delta");
    }

    @Test
    void reload_and_custom_pattern_during_bean_creation_keep_preset_defaults(
            CapturedOutput output) {
        try (ConfigurableApplicationContext context =
                start(Map.of("test.reload", "true"), "--logging.config=" + CONTAINER)) {
            LOG.info("phase3 {} {} order=42", marker, SECRETS);
        }
        assertThat(lineOf(output, "phase2"))
                .contains("password=**** token=**** secret=**** api_key=****", "order=****");
        assertThat(lineOf(output, "phase3"))
                .contains("password=**** token=**** secret=**** api_key=****", "order=****");
        SensitiveDataConverter.clearCustomPatterns();
    }

    @Test
    void startup_failure_stack_and_shutdown_logs_stay_masked(CapturedOutput output) {
        assertThatThrownBy(
                        () -> start(Map.of("test.fail", "true"), "--logging.config=" + CONTAINER))
                .hasRootCauseMessage("boom password=alpha");
        assertThat(output.getOut() + output.getErr())
                .contains("Application run failed")
                .contains("boom password=****");
        assertThat(output.getOut() + output.getErr()).doesNotContain("password=alpha");

        try (ConfigurableApplicationContext context =
                start(Map.of("test.destroy", "true"), "--logging.config=" + CONTAINER)) {
            assertThat(context.isActive()).isTrue();
        }
        assertThat(lineOf(output, "destroy")).contains("token=****");

        try (ConfigurableApplicationContext context =
                start(Map.of(), "--logging.file.path=" + logPath, "--spring.profiles.active=dev")) {
            LOG.info("after {} {}", marker, SECRETS);
        }
        assertThat(lineOf(output, "after")).contains(SECRETS);
    }

    @Test
    void invalid_preset_rule_fails_startup_without_echoing_value() {
        assertThatThrownBy(
                        () ->
                                start(
                                        Map.of(),
                                        "--logging.config=" + CONTAINER,
                                        "--mimir.boot.log.mask.enabled-patterns=password,unknown-rule"))
                .hasMessageContaining("mimir.boot.log.mask.enabled-patterns[1]")
                .hasMessageNotContaining("unknown-rule");
    }

    @Test
    void ordinary_child_context_does_not_change_parent_logging(CapturedOutput output) {
        ConfigurableApplicationContext parent = start(Map.of(), "--logging.config=" + CONTAINER);
        try {
            AnnotationConfigApplicationContext child = new AnnotationConfigApplicationContext();
            child.setParent(parent);
            child.getEnvironment()
                    .getPropertySources()
                    .addFirst(
                            new MapPropertySource(
                                    "child", Map.of("mimir.boot.log.mask.enabled-patterns", "")));
            child.register(ChildConfig.class);
            child.refresh();
            child.close();

            AnnotationConfigApplicationContext failing = new AnnotationConfigApplicationContext();
            failing.setParent(parent);
            failing.register(ChildConfig.class, FailingChildConfig.class);
            assertThatThrownBy(failing::refresh).isInstanceOf(Exception.class);

            LOG.info("parent-after-child {} {}", marker, SECRETS);
            assertThat(lineOf(output, "parent-after-child")).contains("password=**** token=****");
            assertThat(
                            ((ch.qos.logback.classic.Logger)
                                            LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME))
                                    .getAppender("CONSOLE"))
                    .isNotNull();
        } finally {
            parent.close();
        }
        try (ConfigurableApplicationContext next =
                start(Map.of(), "--logging.file.path=" + logPath, "--spring.profiles.active=dev")) {
            LOG.info("next {} {}", marker, SECRETS);
        }
        assertThat(lineOf(output, "next")).contains(SECRETS);
    }

    @Test
    void cached_second_application_without_cloud_follows_last_refresh(CapturedOutput output) {
        ConfigurableApplicationContext first = start(Map.of(), "--logging.config=" + CONTAINER);
        ConfigurableApplicationContext second = null;
        try {
            second =
                    start(
                            Map.of(),
                            "--logging.config=classpath:com/yggdrasil/labs/log/logback/server.xml",
                            "--logging.file.path=" + logPath,
                            "--mimir.boot.log.mask.enabled-patterns=");
            LOG.info("cached {} {}", marker, SECRETS);
            String cached = lineOf(output, "cached");
            assertThat(cached).as("Boot 未重载 XML：仍为首个应用的标准输出出口").isNotNull();
            assertThat(cached).as("后者刷新后发布自身规则（显式空列表）").contains(SECRETS);
            assertThat(
                            ((ConfigurableEnvironment) second.getEnvironment())
                                    .getPropertySources()
                                    .contains(LogPresetSupport.RESOLVED_PROPERTY_SOURCE))
                    .as("后者 EPP 的绑定与校验仍执行")
                    .isTrue();
        } finally {
            if (second != null) {
                second.close();
            }
            first.close();
        }
    }

    @Test
    void indexed_list_like_yaml_holds_across_phases(CapturedOutput output) {
        try (ConfigurableApplicationContext context =
                start(
                        Map.of(),
                        "--logging.config=" + CONTAINER,
                        "--mimir.boot.log.mask.enabled-patterns[0]=token")) {
            LOG.info("phase3 {} {}", marker, SECRETS);
        }
        for (String phase : List.of("phase1", "phase2", "phase3")) {
            assertThat(lineOf(output, phase))
                    .contains("password=alpha token=**** secret=gamma api_key=delta");
        }
    }

    @Test
    void concurrent_output_during_next_startup_never_leaks(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext previous =
                start(
                        Map.of(),
                        "--logging.config=" + CONTAINER,
                        "--mimir.boot.log.mask.enabled-patterns=password")) {
            assertThat(previous.isActive()).isTrue();
        }
        int from = (output.getOut() + output.getErr()).length();
        AtomicBoolean running = new AtomicBoolean(true);
        Thread writer =
                new Thread(
                        () -> {
                            while (running.get()) {
                                LoggerFactory.getLogger("demo.concurrent")
                                        .info("concurrent {} password=alpha", marker);
                            }
                        });
        writer.start();
        try (ConfigurableApplicationContext next =
                start(Map.of(), "--logging.config=" + CONTAINER)) {
            assertThat(next.isActive()).isTrue();
        } finally {
            running.set(false);
            writer.join(5000);
        }
        String captured = (output.getOut() + output.getErr()).substring(from);
        assertThat(captured).contains("concurrent " + marker).doesNotContain("password=alpha");
    }

    @Test
    void legacy_xml_reloaded_after_reset_keeps_published_rules(CapturedOutput output)
            throws Exception {
        try (ConfigurableApplicationContext context =
                start(Map.of(), "--logging.file.path=" + logPath, "--spring.profiles.active=dev")) {
            SensitiveDataConverter.publishConfiguration(List.of("token"), List.of(), "****");
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            loggerContext.reset();
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(loggerContext);
            configurator.doConfigure(
                    getClass().getClassLoader().getResource("mask-legacy-reload.xml"));
            LoggerFactory.getLogger("demo.reload").info("reloaded {} {}", marker, SECRETS);
        }
        assertThat(lineOf(output, "reloaded"))
                .as("旧 XML 不带 mask 属性，重载保留已发布规则（与 2.3.0 一致）")
                .contains("password=alpha token=****");
    }

    @Test
    void default_async_executor_propagates_and_restores_mdc() throws Exception {
        try (ConfigurableApplicationContext context =
                start(
                        Map.of(),
                        "--logging.config=" + CONTAINER,
                        "--mimir.boot.log.mdc.enabled=true")) {
            AsyncProbe probe = context.getBean(AsyncProbe.class);
            MDC.put("traceId", "t-" + marker);
            MDC.put("tenantId", "a");
            CompletableFuture<Map<String, String>> seen = probe.capture();
            MDC.put("tenantId", "b");
            assertThat(seen.get(5, TimeUnit.SECONDS))
                    .containsEntry("traceId", "t-" + marker)
                    .containsEntry("tenantId", "a");
            MDC.clear();
            assertThat(probe.capture().get(5, TimeUnit.SECONDS)).isEmpty();
        }
    }

    static ConfigurableApplicationContext start(Map<String, Object> defaults, String... args) {
        SpringApplication application = new SpringApplication(App.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(new HashMap<>(defaults));
        application.addListeners(
                (ApplicationPreparedEvent event) ->
                        LoggerFactory.getLogger("demo.phase")
                                .info("phase1 {} {}", marker, SECRETS));
        return application.run(args);
    }

    static String lineOf(CapturedOutput output, String phase) {
        String all = output.getOut() + output.getErr();
        return all.lines()
                .filter(
                        line ->
                                line.contains(phase + " " + marker)
                                        || (line.contains(phase) && line.contains(marker)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到输出: " + phase));
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
        LogMaskAutoConfiguration.class,
        TaskExecutionAutoConfiguration.class,
        MdcTaskExecutionAutoConfiguration.class
    })
    @EnableAsync
    static class App {

        @Bean
        PhaseBean phaseBean(org.springframework.core.env.Environment environment) {
            return new PhaseBean(environment);
        }

        @Bean
        AsyncProbe asyncProbe() {
            return new AsyncProbe();
        }
    }

    static class PhaseBean {

        private final boolean destroyLog;

        PhaseBean(org.springframework.core.env.Environment environment) {
            if (environment.getProperty("test.reload", Boolean.class, false)) {
                SensitiveDataConverter.reloadConfig();
                SensitiveDataConverter.addCustomPattern("order=\\d+");
            }
            LoggerFactory.getLogger("demo.bean")
                    .info(
                            "phase2 {} {} order=42",
                            marker,
                            SECRETS,
                            new IllegalStateException(
                                    "outer password=alpha",
                                    new IllegalArgumentException("cause token=beta")));
            if (environment.getProperty("test.fail", Boolean.class, false)) {
                throw new IllegalStateException("boom password=alpha");
            }
            this.destroyLog = environment.getProperty("test.destroy", Boolean.class, false);
        }

        @PreDestroy
        void destroy() {
            if (destroyLog) {
                LoggerFactory.getLogger("demo.bean").info("destroy {} token=beta", marker);
            }
        }
    }

    static class AsyncProbe {
        @Async
        public CompletableFuture<Map<String, String>> capture() {
            Map<String, String> copy = MDC.getCopyOfContextMap();
            MDC.put("leak", "x");
            return CompletableFuture.completedFuture(copy == null ? Map.of() : copy);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(LogMaskAutoConfiguration.class)
    static class ChildConfig {}

    @Configuration(proxyBeanMethods = false)
    static class FailingChildConfig {
        @Bean
        Object failing() {
            throw new IllegalStateException("child failure");
        }
    }
}
