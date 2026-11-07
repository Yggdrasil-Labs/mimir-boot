package com.yggdrasil.labs.log.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import com.yggdrasil.labs.log.converter.SensitiveDataConverter;

import ch.qos.logback.classic.LoggerContext;

/**
 * D-100：开启 Spring Cloud legacy bootstrap 时由主应用生成预设内部源并发布规则，bootstrap 应用不写内部源（独立 JVM，含
 * spring-cloud-context）
 */
@ExtendWith(OutputCaptureExtension.class)
class LegacyBootstrapScenarios {

    private static final String SECRETS = "password=alpha token=beta secret=gamma api_key=delta";
    private static final String CONTAINER =
            "classpath:com/yggdrasil/labs/log/logback/container.xml";
    private static volatile String marker;

    @BeforeEach
    void setUp() {
        MaskTestSupport.reset();
        marker = UUID.randomUUID().toString();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty("logging.config");
        MaskTestSupport.reset();
    }

    @Test
    void new_preset_in_application_config_is_resolved_by_main_application(CapturedOutput output) {
        try (ConfigurableApplicationContext context =
                start(null, "--spring.config.name=mimir-legacy-bootstrap-new")) {
            assertThat(context.getParent()).as("bootstrap 父上下文").isNotNull();
            assertThat(hasResolvedSource(context)).as("主应用生成预设内部源").isTrue();
            assertThat(hasResolvedSource(context.getParent())).as("bootstrap 应用不写内部源").isFalse();
            LoggerFactory.getLogger("demo.bootstrap").info("phase3 {} {}", marker, SECRETS);
            assertThat(contextProperty()).isEqualTo("password,token,secret,api_key");
        }
        assertThreePhases(output, "password=**** token=**** secret=**** api_key=****");
    }

    @Test
    void legacy_rules_in_application_config_still_apply(CapturedOutput output) {
        try (ConfigurableApplicationContext ignoredContext =
                start(null, "--spring.config.name=mimir-legacy-bootstrap-old")) {
            LoggerFactory.getLogger("demo.bootstrap").info("phase3 {} {}", marker, SECRETS);
            assertThat(contextProperty()).isEqualTo("password,token");
        }
        assertThat(lineOf(output, "phase3")).contains("password=**** token=**** secret=gamma");
    }

    @Test
    void preset_from_system_property_loads_in_bootstrap_with_defaults(CapturedOutput output) {
        System.setProperty("logging.config", CONTAINER);
        try (ConfigurableApplicationContext ignoredContext = start(null)) {
            LoggerFactory.getLogger("demo.bootstrap").info("phase3 {} {}", marker, SECRETS);
        }
        assertThreePhases(output, "password=**** token=**** secret=**** api_key=****");
        assertThat(output.getOut() + output.getErr()).doesNotContain("ERROR in ch.qos.logback");
    }

    @Test
    void preset_from_environment_variable_loads_in_bootstrap_with_defaults(CapturedOutput output) {
        StandardEnvironment environment = new StandardEnvironment();
        Map<String, Object> variables = new HashMap<>(System.getenv());
        variables.put("LOGGING_CONFIG", CONTAINER);
        environment
                .getPropertySources()
                .replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                                variables));
        try (ConfigurableApplicationContext ignoredContext = start(environment)) {
            LoggerFactory.getLogger("demo.bootstrap").info("phase3 {} {}", marker, SECRETS);
        }
        assertThreePhases(output, "password=**** token=**** secret=**** api_key=****");
    }

    @Test
    void cached_second_application_with_cloud_context_reloads_own_xml_and_rules(
            CapturedOutput output) {
        ConfigurableApplicationContext first =
                startPlain(CONTAINER, "--mimir.boot.log.mask.enabled-patterns=password");
        try (ConfigurableApplicationContext ignoredSecond =
                startPlain(
                        "classpath:com/yggdrasil/labs/log/logback/server.xml",
                        "--mimir.boot.log.async.queue-size=4096",
                        "--mimir.boot.log.mask.enabled-patterns=token",
                        "--logging.file.path="
                                + System.getProperty("java.io.tmpdir")
                                + "/mimir-cached-"
                                + marker)) {
            ch.qos.logback.classic.Logger root =
                    (ch.qos.logback.classic.Logger)
                            LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            assertThat(root.getAppender("ASYNC_FILE_INFO")).as("后者按自身 XML 重新加载").isNotNull();
            assertThat(
                            ((ch.qos.logback.classic.AsyncAppender)
                                            root.getAppender("ASYNC_FILE_INFO"))
                                    .getQueueSize())
                    .as("D-101：后者按自身内部源加载参数")
                    .isEqualTo(4096);
            assertThat(new SensitiveDataConverter().maskSensitiveData("password=alpha token=beta"))
                    .as("D-101：重新加载后即为后者规则")
                    .isEqualTo("password=alpha token=****");
        } finally {
            first.close();
        }
    }

    @Test
    void config_data_refresh_with_invalid_preset_rule_fails_and_keeps_rules() throws Exception {
        SpringApplication application = new SpringApplication(App.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context =
                application.run("--logging.config=" + CONTAINER)) {
            // spring-cloud-context 只在子 JVM 类路径中，按类名反射构造真实 ConfigDataContextRefresher
            Object scope =
                    Class.forName("org.springframework.cloud.context.scope.refresh.RefreshScope")
                            .getConstructor()
                            .newInstance();
            Object refresher =
                    Class.forName(
                                    "org.springframework.cloud.context.refresh.ConfigDataContextRefresher")
                            .getConstructor(
                                    org.springframework.context.ConfigurableApplicationContext
                                            .class,
                                    scope.getClass())
                            .newInstance(context, scope);
            // 与 Spring Cloud 运行时一致：刷新器经该事件得知原 SpringApplication
            Class<?> eventType =
                    Class.forName(
                            "org.springframework.cloud.context.config.ContextRefreshedWithApplicationEvent");
            Object event =
                    eventType
                            .getConstructor(
                                    SpringApplication.class,
                                    String[].class,
                                    ConfigurableApplicationContext.class)
                            .newInstance(application, new String[0], context);
            refresher
                    .getClass()
                    .getMethod("onApplicationEvent", eventType)
                    .invoke(refresher, event);
            java.lang.reflect.Method refreshEnvironment =
                    refresher.getClass().getMethod("refreshEnvironment");
            System.setProperty("mimir.boot.log.mask.enabled-patterns", "unknown-rule");
            try {
                assertThatThrownBy(() -> refreshEnvironment.invoke(refresher))
                        .hasStackTraceContaining("mimir.boot.log.mask.enabled-patterns[0]")
                        .satisfies(ex -> assertThat(stackTrace(ex)).doesNotContain("unknown-rule"));
            } finally {
                System.clearProperty("mimir.boot.log.mask.enabled-patterns");
            }
            assertThat(new SensitiveDataConverter().maskSensitiveData(SECRETS))
                    .as("运行期刷新失败不改变当前规则")
                    .isEqualTo("password=**** token=**** secret=**** api_key=****");
        }
    }

    private static String stackTrace(Throwable ex) {
        java.io.StringWriter writer = new java.io.StringWriter();
        ex.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }

    private void assertThreePhases(CapturedOutput output, String expected) {
        for (String phase : List.of("phase1", "phase2", "phase3")) {
            assertThat(lineOf(output, phase)).contains(expected);
        }
    }

    private static String contextProperty() {
        return ((LoggerContext) LoggerFactory.getILoggerFactory())
                .getProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY);
    }

    private static String lineOf(CapturedOutput output, String phase) {
        return (output.getOut() + output.getErr())
                .lines()
                .filter(line -> line.contains(phase + " " + marker))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到输出: " + phase));
    }

    private static ConfigurableApplicationContext start(
            StandardEnvironment environment, String... args) {
        SpringApplication application = new SpringApplication(App.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        if (environment != null) {
            application.setEnvironment(environment);
        }
        application.addListeners(
                (ApplicationPreparedEvent event) -> {
                    if (!event.getApplicationContext()
                            .getEnvironment()
                            .getPropertySources()
                            .contains("bootstrap")) {
                        LoggerFactory.getLogger("demo.phase").info("phase1 {} {}", marker, SECRETS);
                    }
                });
        String[] all = new String[args.length + 1];
        all[0] = "--spring.cloud.bootstrap.enabled=true";
        System.arraycopy(args, 0, all, 1, args.length);
        return application.run(all);
    }

    private static ConfigurableApplicationContext startPlain(String config, String... args) {
        SpringApplication application = new SpringApplication(App.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        String[] all = new String[args.length + 1];
        all[0] = "--logging.config=" + config;
        System.arraycopy(args, 0, all, 1, args.length);
        return application.run(all);
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(LogMaskAutoConfiguration.class)
    static class App {
        @Bean
        Object phaseBean() {
            LoggerFactory.getLogger("demo.bean").info("phase2 {} {}", marker, SECRETS);
            return new Object();
        }
    }

    private static boolean hasResolvedSource(
            org.springframework.context.ApplicationContext context) {
        return ((org.springframework.core.env.ConfigurableEnvironment) context.getEnvironment())
                .getPropertySources()
                .contains(LogPresetSupport.RESOLVED_PROPERTY_SOURCE);
    }
}
