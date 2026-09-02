package com.yggdrasil.labs.log.converter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SensitiveThrowableProxyConverterTest extends BaseUnitTest {

    private SensitiveThrowableProxyConverter converter;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        converter = new SensitiveThrowableProxyConverter();
        converter.setContext((LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory());
        converter.start();
        SensitiveDataConverter.publishConfiguration(List.of("password", "token"), List.of(), "****");
    }

    @Override
    @AfterEach
    protected void tearDown() {
        SensitiveDataConverter.reloadConfig();
        super.tearDown();
    }

    @Test
    void returnsEmptyStringWhenEventHasNoThrowable() {
        ILoggingEvent event = mock(ILoggingEvent.class);

        assertEquals("", converter.convert(event));
    }

    @Test
    void masksThrowableGraphAndPreservesStructure() {
        IllegalArgumentException cause = new IllegalArgumentException("token=cause-secret");
        IllegalStateException root = new IllegalStateException("password=root-secret", cause);
        root.addSuppressed(new RuntimeException("password=suppressed-secret"));
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getThrowableProxy()).thenReturn(new ThrowableProxy(root));

        String rendered = converter.convert(event);

        assertFalse(rendered.contains("root-secret"));
        assertFalse(rendered.contains("cause-secret"));
        assertFalse(rendered.contains("suppressed-secret"));
        assertTrue(rendered.contains(IllegalStateException.class.getName()));
        assertTrue(rendered.contains(IllegalArgumentException.class.getName()));
        assertTrue(rendered.contains("Suppressed:"));
        assertTrue(rendered.contains("SensitiveThrowableProxyConverterTest.java"));
    }

    @Test
    void registersDedicatedThrowableConverterAndUsesItInEveryPattern() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("logback-spring.xml")) {
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);

            assertTrue(xml.contains("conversionWord=\"maskThrowable\""));
            assertTrue(xml.contains("SensitiveThrowableProxyConverter"));
            assertEquals(4, occurrences(xml, "%mask%n%maskThrowable"));
            assertEquals(0, occurrences(xml, "%mask%maskThrowable%n"));
            assertEquals(0, occurrences(xml, "%mask%n\""));
        }
    }

    @Test
    void reusesMessageConverterAcrossEventsAndHonorsDynamicConfiguration() throws Exception {
        Field field = SensitiveThrowableProxyConverter.class.getDeclaredField("dataConverter");
        field.setAccessible(true);
        Object firstConverter = field.get(converter);
        assertNotNull(firstConverter);

        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getThrowableProxy()).thenReturn(new ThrowableProxy(
                new IllegalStateException("password=throwable-secret")));

        SensitiveDataConverter.publishConfiguration(List.of("password"), List.of(), "FIRST");
        String first = converter.convert(event);
        SensitiveDataConverter.publishConfiguration(List.of("password"), List.of(), "SECOND");
        String second = converter.convert(event);

        assertTrue(first.contains("FIRST"));
        assertTrue(second.contains("SECOND"));
        assertSame(firstConverter, field.get(converter));
    }

    @Test
    void configuredPatternKeepsThrowableOnNewLineWithoutBlankLineWhenAbsent() throws Exception {
        PatternLayout layout = new PatternLayout();
        LoggerContext context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        layout.setContext(context);
        layout.getInstanceConverterMap().put("mask", SensitiveDataConverter::new);
        layout.getInstanceConverterMap().put("maskThrowable", SensitiveThrowableProxyConverter::new);
        layout.setPattern("%mask%n%maskThrowable");
        layout.start();
        try {
            Logger logger = context.getLogger("THROWABLE_PATTERN_TEST");
            ILoggingEvent withoutThrowable = new LoggingEvent(
                    SensitiveThrowableProxyConverterTest.class.getName(), logger, Level.ERROR,
                    "message", null, null);
            assertEquals("message" + System.lineSeparator(), layout.doLayout(withoutThrowable));

            ILoggingEvent withThrowable = new LoggingEvent(
                    SensitiveThrowableProxyConverterTest.class.getName(), logger, Level.ERROR,
                    "message", new IllegalStateException("password=throwable-secret"), null);
            String rendered = layout.doLayout(withThrowable);
            assertTrue(rendered.startsWith("message" + System.lineSeparator()
                    + IllegalStateException.class.getName() + ": "));
            assertFalse(rendered.contains("message" + IllegalStateException.class.getName()));
            assertFalse(rendered.contains("password=throwable-secret"));
            assertFalse(rendered.contains(System.lineSeparator() + System.lineSeparator()));
        } finally {
            layout.stop();
        }
    }

    private static int occurrences(String text, String fragment) {
        return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }
}
