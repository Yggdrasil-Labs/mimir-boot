package com.yggdrasil.labs.log.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

import com.yggdrasil.labs.log.converter.SensitiveDataConverter;

import ch.qos.logback.classic.LoggerContext;

/** 刷新判定（D-101）：本上下文 → bootstrap 跳过 → 预设父应用的子上下文跳过 → 其余最后刷新者发布 */
class LogMaskRefreshOrderTest {

    private LoggerContext loggerContext;

    @BeforeEach
    void setUp() {
        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        clearProperties();
        MaskTestSupport.reset();
    }

    @AfterEach
    void tearDown() {
        clearProperties();
        MaskTestSupport.reset();
    }

    @Test
    void runner_without_owner_uses_legacy_path_and_new_preset_selection() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LogMaskAutoConfiguration.class))
                .withPropertyValues(
                        "logging.config=classpath:com/yggdrasil/labs/log/logback/container.xml")
                .run(
                        context ->
                                assertThat(enabledProperty())
                                        .isEqualTo("password,token,secret,api_key"));
        clearProperties();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LogMaskAutoConfiguration.class))
                .withPropertyValues("mimir.boot.log.mask.enabled-patterns=token")
                .run(context -> assertThat(enabledProperty()).isEqualTo("token"));
    }

    @Test
    void bootstrap_context_never_publishes_even_without_owner() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(LogMaskAutoConfiguration.class))
                .withInitializer(
                        context ->
                                context.getEnvironment()
                                        .getPropertySources()
                                        .addFirst(new MapPropertySource("bootstrap", Map.of())))
                .withPropertyValues("mimir.boot.log.mask.enabled-patterns=token")
                .run(
                        context -> {
                            assertThat(enabledProperty()).isNull();
                            assertThat(mask("token=beta")).isEqualTo("token=beta");
                        });
    }

    @Test
    void independent_contexts_follow_last_refresh() {
        ApplicationContextRunner runner =
                new ApplicationContextRunner()
                        .withConfiguration(AutoConfigurations.of(LogMaskAutoConfiguration.class));
        runner.withPropertyValues("mimir.boot.log.mask.enabled-patterns=password")
                .run(
                        first -> {
                            assertThat(mask("password=alpha")).isEqualTo("password=****");
                            runner.withPropertyValues("mimir.boot.log.mask.enabled-patterns=token")
                                    .run(
                                            second ->
                                                    assertThat(mask("password=alpha token=beta"))
                                                            .isEqualTo(
                                                                    "password=alpha token=****"));
                        });
    }

    @Test
    void child_of_preset_parent_does_not_override_parent_rules() {
        GenericApplicationContext parent = new GenericApplicationContext();
        parent.getEnvironment()
                .getPropertySources()
                .addFirst(
                        new MapPropertySource(LogPresetSupport.RESOLVED_PROPERTY_SOURCE, Map.of()));
        parent.refresh();
        SensitiveDataConverter.publishConfiguration(List.of("password"), List.of(), "****");

        GenericApplicationContext child = new GenericApplicationContext();
        child.setParent(parent);
        child.refresh();
        LogMaskProperties childProperties = new LogMaskProperties();
        childProperties.setEnabledPatterns(List.of("token"));
        LogMaskAutoConfiguration configuration = new LogMaskAutoConfiguration(childProperties);
        configuration.setApplicationContext(child);
        configuration.transferConfig(new ContextRefreshedEvent(child));

        assertThat(enabledProperty()).as("子上下文不写属性").isNull();
        assertThat(mask("password=alpha token=beta")).isEqualTo("password=**** token=beta");
        child.close();
        parent.close();
    }

    @Test
    void child_of_legacy_parent_keeps_last_refresh_semantics() {
        GenericApplicationContext parent = new GenericApplicationContext();
        parent.refresh();
        GenericApplicationContext child = new GenericApplicationContext();
        child.setParent(parent);
        child.refresh();
        LogMaskProperties childProperties = new LogMaskProperties();
        childProperties.setEnabledPatterns(List.of("token"));
        LogMaskAutoConfiguration configuration = new LogMaskAutoConfiguration(childProperties);
        configuration.setApplicationContext(child);
        configuration.transferConfig(new ContextRefreshedEvent(child));

        assertThat(enabledProperty()).isEqualTo("token");
        child.close();
        parent.close();
    }

    private String enabledProperty() {
        return loggerContext.getProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY);
    }

    private static String mask(String message) {
        return new SensitiveDataConverter().maskSensitiveData(message);
    }

    private void clearProperties() {
        loggerContext.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, null);
        loggerContext.putProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY, null);
        loggerContext.putProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY, null);
    }
}
