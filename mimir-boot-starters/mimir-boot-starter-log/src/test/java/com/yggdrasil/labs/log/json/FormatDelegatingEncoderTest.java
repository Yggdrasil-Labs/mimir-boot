package com.yggdrasil.labs.log.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.encoder.Encoder;

class FormatDelegatingEncoderTest {
    @ParameterizedTest
    @ValueSource(strings = {"text", "json"})
    void delegatesOriginalEncoderBytesIncludingExceptionAndMdc(String format) {
        LoggerContext context = new LoggerContext();
        context.start();
        String pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger - %msg %X%n%ex";
        context.putProperty(LogJsonKeys.FORMAT, format);
        Encoder<ILoggingEvent> original;
        if ("json".equals(format)) {
            original = new MimirJsonEncoder();
        } else {
            PatternLayoutEncoder text = new PatternLayoutEncoder();
            text.setPattern(pattern);
            text.setCharset(StandardCharsets.UTF_8);
            original = text;
        }
        original.setContext(context);
        original.start();
        FormatDelegatingEncoder delegate = new FormatDelegatingEncoder();
        delegate.setContext(context);
        delegate.setPattern(pattern);
        delegate.setCharset(StandardCharsets.UTF_8);
        delegate.start();
        try {
            LoggingEvent event =
                    new LoggingEvent(
                            getClass().getName(),
                            context.getLogger("SQL.SLOW"),
                            Level.ERROR,
                            "中文特殊字符\n\"{}\"",
                            new IllegalStateException("bad"),
                            new Object[] {7});
            event.setTimeStamp(123456789L);
            event.setThreadName("fixed");
            event.setMDCPropertyMap(Map.of("traceId", "trace"));
            assertThat(delegate.isStarted()).isTrue();
            assertThat(delegate.headerBytes()).isEqualTo(original.headerBytes());
            assertThat(delegate.encode(event)).isEqualTo(original.encode(event));
            assertThat(delegate.footerBytes()).isEqualTo(original.footerBytes());
        } finally {
            delegate.stop();
            original.stop();
            context.stop();
        }
    }
}
