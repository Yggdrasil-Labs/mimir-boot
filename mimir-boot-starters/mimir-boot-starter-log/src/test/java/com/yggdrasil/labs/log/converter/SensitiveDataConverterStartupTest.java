package com.yggdrasil.labs.log.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;

/** 原子发布、Context 属性惰性读取与 start() 规则代次切换语义（D-101） */
class SensitiveDataConverterStartupTest {

    private static final List<String> FOUR = List.of("password", "token", "secret", "api_key");
    private static final String SECRETS = "password=alpha token=beta secret=gamma api_key=delta";

    private LoggerContext context;
    private SensitiveDataConverter converter;

    @BeforeEach
    void setUp() {
        context = (LoggerContext) LoggerFactory.getILoggerFactory();
        clearContextProperties();
        SensitiveDataConverter.clearCustomPatterns();
        SensitiveDataConverter.resetForTest(context);
        converter = new SensitiveDataConverter();
        converter.setContext(context);
        converter.start();
    }

    @AfterEach
    void tearDown() {
        SensitiveDataConverter.clearCustomPatterns();
        clearContextProperties();
        SensitiveDataConverter.resetForTest(context);
    }

    @Test
    void reload_reads_context_properties_and_keeps_programmatic_rules() {
        context.putProperty(
                SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, String.join(",", FOUR));
        SensitiveDataConverter.reloadConfig();
        assertEquals("password=**** token=**** secret=**** api_key=****", mask(SECRETS));

        SensitiveDataConverter.addCustomPattern("order=\\d+");
        assertEquals("password=**** order=****", mask("password=alpha order=42"));

        SensitiveDataConverter.clearCustomPatterns();
        assertEquals("token=**** order=42", mask("token=beta order=42"));
    }

    @Test
    void start_with_mask_properties_switches_to_context_rules() {
        SensitiveDataConverter.publishConfiguration(List.of("password"), List.of(), "****");
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "token");

        startConverter();

        assertEquals("password=alpha token=****", mask("password=alpha token=beta"));
    }

    @Test
    void start_without_mask_properties_keeps_published_snapshot() {
        SensitiveDataConverter.publishConfiguration(List.of("password"), List.of(), "****");

        startConverter();

        assertEquals(
                "password=**** token=beta", mask("password=alpha token=beta"), "旧 XML 重新加载保留已发布规则");
    }

    @Test
    void start_with_empty_replacement_property_also_switches() {
        SensitiveDataConverter.publishConfiguration(List.of("password"), List.of(), "****");
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "");
        context.putProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY, "");

        startConverter();

        assertEquals("password=alpha", mask("password=alpha"), "预设显式空列表关闭规则");
    }

    @Test
    void reload_keeps_regex_quantifiers_and_literal_commas() {
        List<String> custom = List.of("\\d{15,18}", "[a,b]x", "(c,d)", "key:\\s*\\w+,\\s*\\w+");
        String encoded = MaskListCodec.join(custom);
        context.putProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY, encoded);
        SensitiveDataConverter.reloadConfig();

        assertEquals("id=****", mask("id=110101199001011234"));
        assertEquals("****", mask("ax"));
        assertEquals("****", mask("c,d"));
        assertEquals("key:****", mask("key: u, v"));
        assertEquals(4, MaskListCodec.split(encoded).size());
        assertEquals(List.of("password", " token"), MaskListCodec.split("password, token"));
        assertNull(MaskListCodec.join(List.of()));
    }

    @Test
    void start_without_context_keeps_global_reference_and_snapshot() {
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password");
        SensitiveDataConverter.reloadConfig();
        assertEquals("password=****", mask("password=alpha"));

        new SensitiveDataConverter().start();
        SensitiveDataConverter.reloadConfig();

        assertEquals("password=****", mask("password=alpha"));
    }

    @Test
    void independent_context_does_not_take_over_global_reference() {
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password");
        LoggerContext other = new LoggerContext();
        other.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "token");
        SensitiveDataConverter foreign = new SensitiveDataConverter();
        foreign.setContext(other);
        foreign.start();
        SensitiveDataConverter.reloadConfig();

        assertEquals("password=**** token=beta", mask("password=alpha token=beta"));
    }

    @Test
    void start_binds_first_context_when_unbound_and_rebinds_global() {
        LoggerContext other = new LoggerContext();
        other.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "token");
        SensitiveDataConverter.resetForTest(null);
        SensitiveDataConverter first = new SensitiveDataConverter();
        first.setContext(other);
        first.start();
        SensitiveDataConverter.reloadConfig();
        assertEquals("password=alpha token=****", mask("password=alpha token=beta"));

        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password");
        SensitiveDataConverter global = new SensitiveDataConverter();
        global.setContext(context);
        global.start();
        SensitiveDataConverter.reloadConfig();
        assertEquals("password=**** token=beta", mask("password=alpha token=beta"));
    }

    @Test
    void explicit_publish_then_reload_reads_context_properties() {
        SensitiveDataConverter.publishConfiguration(List.of("password"), List.of(), "****");
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "token");
        SensitiveDataConverter.addCustomPattern("order=\\d+");

        assertEquals(
                "password=alpha token=**** order=****", mask("password=alpha token=beta order=7"));
    }

    private void startConverter() {
        SensitiveDataConverter started = new SensitiveDataConverter();
        started.setContext(context);
        started.start();
    }

    private String mask(String message) {
        return converter.maskSensitiveData(message);
    }

    private void clearContextProperties() {
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, null);
        context.putProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY, null);
        context.putProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY, null);
    }
}
