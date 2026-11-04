package com.yggdrasil.labs.log.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.DefaultPropertiesPropertySource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;

import com.yggdrasil.labs.log.converter.SensitiveDataConverter;

import ch.qos.logback.classic.LoggerContext;

class LogPresetEnvironmentPostProcessorTest {

    private static final String CONTAINER =
            "classpath:com/yggdrasil/labs/log/logback/container.xml";
    private static final String SERVER = "classpath:com/yggdrasil/labs/log/logback/server.xml";
    private static final String SECRETS = "password=alpha token=beta secret=gamma api_key=delta";
    private static final String ALL_MASKED = "password=**** token=**** secret=**** api_key=****";
    private static final String LOGGING_SYSTEM = "org.springframework.boot.logging.LoggingSystem";

    private static final String RESOLVED = LogPresetSupport.RESOLVED_PROPERTY_SOURCE;
    private static final List<String> FOUR = List.of("password", "token", "secret", "api_key");

    private final List<String> infos = new ArrayList<>();
    private LogPresetEnvironmentPostProcessor processor;
    private StandardEnvironment lastEnvironment;

    @BeforeEach
    void setUp() {
        MaskTestSupport.reset();
        Log log = mock(Log.class);
        doAnswer(invocation -> infos.add(String.valueOf((Object) invocation.getArgument(0))))
                .when(log)
                .info(any());
        DeferredLogFactory factory = destination -> log;
        processor = new LogPresetEnvironmentPostProcessor(factory);
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(LOGGING_SYSTEM);
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, null);
        context.putProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY, null);
        context.putProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY, null);
        MaskTestSupport.reset();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                CONTAINER,
                "classpath:/com/yggdrasil/labs/log/logback/container.xml",
                "  classpath:com/yggdrasil/labs/log/logback/container.xml \t",
                SERVER,
                "classpath:/com/yggdrasil/labs/log/logback/server.xml"
            })
    void recognised_locations_publish_four_default_rules(String location) {
        run(environment(Map.of("logging.config", location)));

        StandardEnvironment environment = lastEnvironment;
        assertThat(environment.getPropertySources().contains(RESOLVED)).as("选中预设时内部源必定存在").isTrue();
        assertThat(environment.containsProperty(LogPresetSupport.INTERNAL_ENABLED_PATTERNS))
                .as("未配置时不写启用列表内部键，由 XML defaultValue 兜底")
                .isFalse();
        assertThat(maskAsPreset(environment)).isEqualTo(ALL_MASKED);
        assertThat(infos).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "/com/yggdrasil/labs/log/logback/server.xml",
                "com/yggdrasil/labs/log/logback/server.xml",
                "CLASSPATH:com/yggdrasil/labs/log/logback/container.xml",
                "classpath*:com/yggdrasil/labs/log/logback/server.xml",
                "file:/opt/app/com/yggdrasil/labs/log/logback/container.xml"
            })
    void unrecognised_variants_log_one_notice_and_keep_legacy_rules(String location) {
        run(environment(Map.of("logging.config", location)));

        assertThat(mask(SECRETS)).isEqualTo(SECRETS);
        assertThat(infos).hasSize(1).first().asString().contains("未被识别");
        // 提示不回显配置值（标准写法本身会出现在建议中，此处只检查非标准前缀）
        assertThat(infos.get(0))
                .doesNotContain("file:")
                .doesNotContain("CLASSPATH")
                .doesNotContain("classpath*");
        assertThat(lastEnvironment.getPropertySources().contains(RESOLVED)).isFalse();
    }

    @Test
    void copied_xml_with_other_name_does_not_log_notice() {
        run(environment(Map.of("logging.config", "classpath:my-logback.xml")));

        assertThat(infos).isEmpty();
        assertThat(mask(SECRETS)).isEqualTo(SECRETS);
    }

    @Test
    void default_properties_single_rule_empty_list_and_empty_string_are_respected() {
        assertThat(publishWithDefaults(Map.of("mimir.boot.log.mask.enabled-patterns", "password")))
                .isEqualTo("password=**** token=beta secret=gamma api_key=delta");
        assertThat(publishWithDefaults(Map.of("mimir.boot.log.mask.enabled-patterns", List.of())))
                .isEqualTo(SECRETS);
        assertThat(publishWithDefaults(Map.of("mimir.boot.log.mask.enabled-patterns", "")))
                .isEqualTo(SECRETS);
        assertThat(publishWithDefaults(Map.of("mimir.boot.log.mask.enabled-patterns[0]", "token")))
                .isEqualTo("password=alpha token=**** secret=gamma api_key=delta");
        assertThat(publishWithDefaults(Map.of("mimir.boot.log.mask.enabledPatterns", "secret")))
                .isEqualTo("password=alpha token=beta secret=**** api_key=delta");
    }

    @Test
    void default_source_is_moved_to_end_so_higher_priority_list_overrides() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("mimir.boot.log.mask.enabled-patterns", "password");
        defaults.put("untouched", "keep");
        StandardEnvironment environment = environment(Map.of("logging.config", CONTAINER));
        environment.getPropertySources().addFirst(new DefaultPropertiesPropertySource(defaults));
        environment
                .getPropertySources()
                .addLast(
                        new MapPropertySource(
                                "config", Map.of("mimir.boot.log.mask.enabled-patterns", "token")));

        run(environment);

        assertThat(maskAsPreset(environment))
                .isEqualTo("password=alpha token=**** secret=gamma api_key=delta");
        assertThat(
                        environment.getPropertySources().stream()
                                .reduce((a, b) -> b)
                                .orElseThrow()
                                .getName())
                .isEqualTo(DefaultPropertiesPropertySource.NAME);
        assertThat(defaults).containsEntry("untouched", "keep").hasSize(2);
        assertThat(environment.containsProperty("mimir.boot.log.mask.enabled-patterns[0]"))
                .isFalse();
    }

    @Test
    void legacy_configuration_does_not_publish_or_create_internal_source() {
        StandardEnvironment environment =
                environment(Map.of("mimir.boot.log.mask.enabled-patterns", "token"));
        run(environment);

        assertThat(environment.getPropertySources().contains(RESOLVED)).isFalse();
        assertThat(mask(SECRETS)).as("旧配置早期规则沿用 2.3.0 惰性读取，EPP 不发布").isEqualTo(SECRETS);
    }

    @Test
    void legacy_without_mask_properties_keeps_lazy_logback_or_system_property_rules() {
        run(environment(Map.of()));

        // 模拟 logback.xml <property>/系统属性在日志初始化后才可见：未发布快照时仍按原语义懒加载
        System.setProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "token");
        try {
            assertThat(mask(SECRETS))
                    .isEqualTo("password=alpha token=**** secret=gamma api_key=delta");
        } finally {
            System.clearProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY);
        }
    }

    @Test
    void reused_environment_drops_previous_internal_source() {
        StandardEnvironment environment = environment(Map.of("logging.config", SERVER));
        run(environment);
        assertThat(environment.getPropertySources().contains(RESOLVED)).isTrue();

        environment.getPropertySources().replace("test", new MapPropertySource("test", Map.of()));
        run(environment);

        assertThat(environment.getPropertySources().contains(RESOLVED))
                .as("新→旧复用 Environment 不残留")
                .isFalse();
    }

    @Test
    void runtime_refresh_invocation_never_changes_converter_rules() {
        SpringApplication application = new SpringApplication();
        SensitiveDataConverter.publishConfiguration(FOUR, List.of(), "****");

        assertThatThrownBy(
                        () ->
                                run(
                                        application,
                                        environment(
                                                Map.of(
                                                        "logging.config",
                                                        CONTAINER,
                                                        "mimir.boot.log.mask.enabled-patterns",
                                                        "unknown-rule"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("unknown-rule");
        run(application, environment(Map.of("mimir.boot.log.mask.enabled-patterns", "")));

        assertThat(mask(SECRETS)).as("运行期 EPP 调用只改 Environment，不改转换器规则").isEqualTo(ALL_MASKED);
    }

    @Test
    void without_spring_application_still_creates_internal_source() {
        StandardEnvironment environment = environment(Map.of("logging.config", CONTAINER));
        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getPropertySources().contains(RESOLVED)).isTrue();
    }

    @Test
    void invalid_rules_fail_with_property_and_index_without_value() {
        assertThatThrownBy(
                        () ->
                                run(
                                        environment(
                                                Map.of(
                                                        "logging.config",
                                                        CONTAINER,
                                                        "mimir.boot.log.mask.enabled-patterns",
                                                        "password,unknown-rule"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mimir.boot.log.mask.enabled-patterns[1]")
                .hasMessageNotContaining("unknown-rule");
        MaskTestSupport.reset();
        assertThatThrownBy(
                        () ->
                                run(
                                        environment(
                                                Map.of(
                                                        "logging.config",
                                                        CONTAINER,
                                                        "mimir.boot.log.mask.custom-patterns[0]",
                                                        "secret-(unclosed"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mimir.boot.log.mask.custom-patterns[0]")
                .hasMessageNotContaining("secret-(unclosed");
        assertThatThrownBy(
                        () ->
                                run(
                                        environment(
                                                Map.of(
                                                        "logging.config",
                                                        CONTAINER,
                                                        "mimir.boot.log.mask.custom-patterns[0]",
                                                        "id=${missing}"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mimir.boot.log.mask.custom-patterns[0]")
                .hasMessageNotContaining("missing");
        assertThatThrownBy(
                        () ->
                                run(
                                        environment(
                                                Map.of(
                                                        "logging.config",
                                                        CONTAINER,
                                                        "mimir.boot.log.mask.replacement",
                                                        "${missing}"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mimir.boot.log.mask.replacement")
                .hasMessageNotContaining("missing");
    }

    @Test
    void configured_mask_values_are_encoded_into_internal_keys() {
        StandardEnvironment environment =
                environment(
                        Map.of(
                                "logging.config", CONTAINER,
                                "mimir.boot.log.mask.enabled-patterns", "",
                                "mimir.boot.log.mask.custom-patterns[0]", "\\d{15,18}",
                                "mimir.boot.log.mask.custom-patterns[1]", "a,b",
                                "mimir.boot.log.mask.replacement", "#"));
        run(environment);

        assertThat(environment.getProperty(LogPresetSupport.INTERNAL_ENABLED_PATTERNS))
                .as("显式空列表写空字符串而非缺省")
                .isEmpty();
        assertThat(environment.getProperty(LogPresetSupport.INTERNAL_CUSTOM_PATTERNS))
                .isEqualTo("\\d{15,18},a\\,b");
        assertThat(environment.getProperty(LogPresetSupport.INTERNAL_REPLACEMENT)).isEqualTo("#");
        assertThat(maskAsPreset(environment)).isEqualTo(SECRETS);
        assertThat(new SensitiveDataConverter().maskSensitiveData("id=110101199001011234 a,b"))
                .isEqualTo("id=# #");
    }

    @Test
    void validation_failure_keeps_previous_snapshot_untouched() {
        SensitiveDataConverter.publishConfiguration(List.of("password"), List.of(), "****");
        StandardEnvironment environment =
                environment(
                        Map.of(
                                "logging.config",
                                CONTAINER,
                                "mimir.boot.log.mask.enabled-patterns",
                                "nope"));

        assertThatThrownBy(() -> run(environment));
        assertThat(environment.getPropertySources().contains(RESOLVED)).as("校验先于写入").isFalse();
        assertThat(mask("password=alpha"))
                .as("校验先于发布，失败不置空全局快照，同 JVM 其他应用规则不受影响")
                .isEqualTo("password=****");
    }

    @Test
    void server_preset_creates_resolved_source_and_validates_bounds() {
        StandardEnvironment environment =
                environment(
                        Map.of(
                                "logging.config", SERVER,
                                "mimir.boot.log.file.max-size", "50MB",
                                "mimir.boot.log.async.queue-size", "2048",
                                "mimir.boot.log.async.max-flush-time", "1500ms"));
        run(environment);

        assertThat(environment.getProperty("mimir.boot.log.internal.file.max-size-bytes"))
                .isEqualTo(String.valueOf(50L * 1024 * 1024));
        assertThat(environment.getProperty("mimir.boot.log.internal.async.queue-size"))
                .isEqualTo("2048");
        assertThat(environment.getProperty("mimir.boot.log.internal.async.max-flush-time-millis"))
                .isEqualTo("1500");
        assertThat(
                        environment.getProperty(
                                "mimir.boot.log.internal.file.error-total-size-cap-bytes"))
                .isEqualTo("5368709120");
        assertThat(environment.getProperty("mimir.boot.log.file.max-size")).isEqualTo("50MB");
    }

    @Test
    void server_numeric_boundaries() {
        assertServerAccepts("mimir.boot.log.file.max-size", "1MB");
        assertServerAccepts("mimir.boot.log.file.max-size", "1GB");
        assertServerRejects("mimir.boot.log.file.max-size", "1023KB", "file.max-size");
        assertServerRejects("mimir.boot.log.file.max-size", "1025MB", "file.max-size");
        assertServerRejects("mimir.boot.log.file.max-history", "-7", "file.max-history");
        assertServerRejects(
                "mimir.boot.log.file.error-max-history", "3651", "file.error-max-history");
        assertServerRejects("mimir.boot.log.file.total-size-cap", "50MB", "file.total-size-cap");
        assertServerRejects("mimir.boot.log.async.queue-size", "-3", "async.queue-size");
        assertServerAccepts("mimir.boot.log.async.max-flush-time", "1ms");
        assertServerAccepts("mimir.boot.log.async.max-flush-time", "60s");
        assertServerRejects("mimir.boot.log.async.max-flush-time", "500us", "async.max-flush-time");
        assertServerRejects("mimir.boot.log.async.max-flush-time", "61s", "async.max-flush-time");
        assertServerRejects(
                "mimir.boot.log.async.queue-size",
                "abc",
                "mimir.boot.log.file / mimir.boot.log.async");
    }

    @Test
    void container_ignores_server_only_values() {
        StandardEnvironment environment =
                environment(
                        Map.of(
                                "logging.config",
                                CONTAINER,
                                "mimir.boot.log.async.queue-size",
                                "0"));

        run(environment);

        assertThat(environment.getPropertySources().contains(RESOLVED)).isTrue();
        assertThat(environment.containsProperty("mimir.boot.log.internal.async.queue-size"))
                .as("容器预设不写数值键")
                .isFalse();
    }

    @Test
    void bootstrap_environment_is_ignored_and_main_application_can_own() {
        StandardEnvironment bootstrap =
                environment(
                        Map.of(
                                "logging.config",
                                "CLASSPATH:com/yggdrasil/labs/log/logback/container.xml"));
        bootstrap.getPropertySources().addFirst(new MapPropertySource("bootstrap", Map.of()));
        bootstrap
                .getPropertySources()
                .addLast(new MapPropertySource(RESOLVED, Map.of("residual", "x")));
        System.setProperty(LOGGING_SYSTEM, "none");

        run(bootstrap);

        assertThat(infos).isEmpty();
        assertThat(bootstrap.getPropertySources().contains(RESOLVED))
                .as("复制进 bootstrap 的残留内部源被移除，且不生成新源")
                .isFalse();
        System.clearProperty(LOGGING_SYSTEM);

        StandardEnvironment main = environment(Map.of("logging.config", CONTAINER));
        run(main);
        assertThat(maskAsPreset(main)).isEqualTo(ALL_MASKED);
    }

    @Test
    void logging_system_none_exits_without_preset_and_fails_with_preset() {
        System.setProperty(LOGGING_SYSTEM, "none");

        run(environment(Map.of()));
        assertThat(lastEnvironment.getPropertySources().contains(RESOLVED)).isFalse();

        assertThatThrownBy(() -> run(environment(Map.of("logging.config", SERVER))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Logback");
    }

    @Test
    void missing_logback_classes_exit_or_fail_and_still_log_notice() {
        SpringApplication application = new SpringApplication();
        application.setResourceLoader(new DefaultResourceLoader(new NoLogbackClassLoader()));

        run(
                application,
                environment(
                        Map.of(
                                "logging.config",
                                "file:/x/com/yggdrasil/labs/log/logback/server.xml")));
        assertThat(lastEnvironment.getPropertySources().contains(RESOLVED)).isFalse();
        assertThat(infos).hasSize(1);

        assertThatThrownBy(() -> run(application, environment(Map.of("logging.config", CONTAINER))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Logback");
    }

    private String publishWithDefaults(Map<String, Object> defaults) {
        MaskTestSupport.reset();
        StandardEnvironment environment = environment(Map.of("logging.config", CONTAINER));
        environment
                .getPropertySources()
                .addLast(new DefaultPropertiesPropertySource(new HashMap<>(defaults)));
        run(environment);
        return maskAsPreset(environment);
    }

    private void assertServerAccepts(String key, String value) {
        MaskTestSupport.reset();
        run(environment(Map.of("logging.config", SERVER, key, value)));
    }

    private void assertServerRejects(String key, String value, String expectedName) {
        MaskTestSupport.reset();
        assertThatThrownBy(() -> run(environment(Map.of("logging.config", SERVER, key, value))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(expectedName)
                .hasMessageNotContaining(value);
    }

    private void run(StandardEnvironment environment) {
        run(new SpringApplication(), environment);
    }

    private void run(SpringApplication application, StandardEnvironment environment) {
        lastEnvironment = environment;
        processor.postProcessEnvironment(environment, application);
    }

    /** 模拟预设 XML 的三条 springProperty：读取内部键（缺失取 defaultValue）写入 LoggerContext，再按转换器惰性路径脱敏 */
    private static String maskAsPreset(StandardEnvironment environment) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.putProperty(
                SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY,
                environment.getProperty(
                        LogPresetSupport.INTERNAL_ENABLED_PATTERNS,
                        "password,token,secret,api_key"));
        context.putProperty(
                SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY,
                environment.getProperty(LogPresetSupport.INTERNAL_CUSTOM_PATTERNS, ""));
        context.putProperty(
                SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY,
                environment.getProperty(LogPresetSupport.INTERNAL_REPLACEMENT, ""));
        SensitiveDataConverter.reloadConfig();
        return mask(SECRETS);
    }

    private static StandardEnvironment environment(Map<String, Object> properties) {
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .addFirst(new MapPropertySource("test", new HashMap<>(properties)));
        return environment;
    }

    private static String mask(String message) {
        return new SensitiveDataConverter().maskSensitiveData(message);
    }

    private static final class NoLogbackClassLoader extends ClassLoader {

        private NoLogbackClassLoader() {
            super(LogPresetEnvironmentPostProcessorTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("ch.qos.logback.")) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
