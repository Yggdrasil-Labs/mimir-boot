package com.yggdrasil.labs.log.json;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;

class GovernedFieldsJsonProviderTest {
    @org.junit.jupiter.api.BeforeEach
    @org.junit.jupiter.api.AfterEach
    void resetRules() {
        com.yggdrasil.labs.log.converter.SensitiveDataConverter.clearCustomPatterns();
        com.yggdrasil.labs.log.converter.SensitiveDataConverterInternals.resetForTest(null);
    }

    @Test
    void masksStructuredContainersAndExceptionAndIsolatesUnsafeValues() throws Exception {
        LoggerContext context = new LoggerContext();
        context.putProperty("mimir.boot.log.mask.enabledPatterns", "password,token");
        MimirJsonEncoder encoder = new MimirJsonEncoder();
        encoder.setContext(context);
        encoder.start();
        try {
            LoggingEvent event =
                    new LoggingEvent(
                            getClass().getName(),
                            context.getLogger("business"),
                            Level.ERROR,
                            "login password=alpha",
                            new IllegalStateException("token=beta"),
                            null);
            event.setMDCPropertyMap(Map.of("password", "hidden", "traceId", ""));
            event.addKeyValuePair(
                    new org.slf4j.event.KeyValuePair("userPassword", Map.of("secret", "hidden")));
            event.addKeyValuePair(
                    new org.slf4j.event.KeyValuePair(
                            "unsafe",
                            new Object() {
                                @Override
                                public String toString() {
                                    throw new AssertionError("不得序列化任意对象");
                                }
                            }));
            JsonNode json = new ObjectMapper().readTree(encoder.encode(event));
            assertThat(json.path("message").asText()).isEqualTo("login password=****");
            assertThat(json.path("context").path("password").asText()).isEqualTo("****");
            assertThat(json.path("attributes").path("userPassword").asText()).isEqualTo("****");
            assertThat(json.path("attributes").has("unsafe")).isFalse();
            assertThat(json.path("exception").path("message").asText()).isEqualTo("token=****");
            assertThat(json.path("exception").path("stackTrace").asText()).doesNotContain("beta");
            assertThat(json.has("traceId")).isFalse();
            assertThat(json.path("processing").path("errors").get(0).path("reason").asText())
                    .isEqualTo("unsupported_type");
        } finally {
            encoder.stop();
            context.stop();
        }
    }

    @Test
    void omitsOptionalMetadataAndProcessingWhenNothingNeedsHandling() throws Exception {
        LoggerContext context = new LoggerContext();
        MimirJsonEncoder encoder = new MimirJsonEncoder();
        encoder.setContext(context);
        encoder.start();
        try {
            LoggingEvent event =
                    new LoggingEvent(
                            getClass().getName(),
                            context.getLogger("SQL.JSON"),
                            Level.INFO,
                            "{\"sql\":\"select 1\"}",
                            null,
                            null);
            event.setMDCPropertyMap(Map.of());
            JsonNode json = new ObjectMapper().readTree(encoder.encode(event));
            assertThat(json.path("service").asText()).isEqualTo("application");
            assertThat(json.path("message").asText()).isEqualTo("{\"sql\":\"select 1\"}");
            for (String key :
                    java.util.List.of(
                            "environment",
                            "instance",
                            "traceId",
                            "spanId",
                            "context",
                            "attributes",
                            "exception",
                            "processing")) {
                assertThat(json.has(key)).as(key).isFalse();
            }
        } finally {
            encoder.stop();
            context.stop();
        }
    }

    @Test
    void masksNumericValuesAndSensitiveContainersBeforeTruncating() throws Exception {
        LoggerContext context = new LoggerContext();
        context.putProperty("mimir.boot.log.mask.enabledPatterns", "password,phone_number");
        context.putProperty("mimir.boot.log.mask.replacement", "******");
        context.putProperty(
                LogJsonKeys.FIELD_LENGTH_LIMITS,
                LogJsonSettingsCodec.encodeLimits(Map.of("/context/password", 4)));
        MimirJsonEncoder encoder = new MimirJsonEncoder();
        encoder.setContext(context);
        encoder.start();
        try {
            LoggingEvent event =
                    new LoggingEvent(
                            getClass().getName(),
                            context.getLogger("business"),
                            Level.INFO,
                            "message",
                            null,
                            null);
            event.setMDCPropertyMap(Map.of("password", "abcdefgh"));
            event.addKeyValuePair(new org.slf4j.event.KeyValuePair("count", 13800138000L));
            event.addKeyValuePair(new org.slf4j.event.KeyValuePair("contact", "13800138000"));
            event.addKeyValuePair(new org.slf4j.event.KeyValuePair("ok", false));
            JsonNode json = new ObjectMapper().readTree(encoder.encode(event));
            assertThat(json.at("/context/password").asText()).isEqualTo("****");
            assertThat(json.at("/attributes/count").isTextual()).isTrue();
            assertThat(json.at("/attributes/count").asText()).isEqualTo("******");
            assertThat(json.at("/attributes/contact").asText()).isEqualTo("******");
            assertThat(json.at("/attributes/ok").isBoolean()).isTrue();
            assertThat(json.at("/processing/truncatedFields/0").asText())
                    .isEqualTo("/context/password");
        } finally {
            encoder.stop();
            context.stop();
        }
    }

    @Test
    void explicitEmptyRulesLeaveSensitiveNamesUnmodified() throws Exception {
        LoggerContext context = new LoggerContext();
        context.putProperty("mimir.boot.log.mask.enabledPatterns", "");
        MimirJsonEncoder encoder = new MimirJsonEncoder();
        encoder.setContext(context);
        encoder.start();
        try {
            LoggingEvent event =
                    new LoggingEvent(
                            getClass().getName(),
                            context.getLogger("business"),
                            Level.INFO,
                            "password=abc",
                            null,
                            null);
            event.setMDCPropertyMap(Map.of("password", "abc"));
            JsonNode json = new ObjectMapper().readTree(encoder.encode(event));
            assertThat(json.at("/context/password").asText()).isEqualTo("abc");
            assertThat(json.at("/message").asText()).isEqualTo("password=abc");
            assertThat(json.has("processing")).isFalse();
        } finally {
            encoder.stop();
            context.stop();
        }
    }

    @Test
    void oneLineKeepsTypesAndTruncatesUnicodeAndEscapedPaths() throws Exception {
        LoggerContext context = new LoggerContext();
        context.putProperty(
                LogJsonKeys.EXCLUDE_MDC_KEYS,
                LogJsonSettingsCodec.encodeList(java.util.List.of("traceId")));
        context.putProperty(
                LogJsonKeys.FIELD_LENGTH_LIMITS,
                LogJsonSettingsCodec.encodeLimits(
                        Map.of("/message", 3, "/attributes/a~1b", 2, "/context/tenant.id", 1)));
        MimirJsonEncoder encoder = new MimirJsonEncoder();
        encoder.setContext(context);
        encoder.start();
        try {
            LoggingEvent event =
                    new LoggingEvent(
                            getClass().getName(),
                            context.getLogger("business"),
                            Level.INFO,
                            "a😀中文",
                            null,
                            null);
            event.setMDCPropertyMap(Map.of("traceId", "abc", "tenant.id", "t1"));
            event.addKeyValuePair(new org.slf4j.event.KeyValuePair("a/b", "xyz"));
            event.addKeyValuePair(new org.slf4j.event.KeyValuePair("count", 7));
            byte[] bytes = encoder.encode(event);
            JsonNode json = new ObjectMapper().readTree(bytes);
            assertThat(json.path("message").asText()).isEqualTo("a😀中");
            assertThat(json.path("attributes").path("a/b").asText()).isEqualTo("xy");
            assertThat(json.path("attributes").path("count").isInt()).isTrue();
            assertThat(json.path("traceId").asText()).isEqualTo("abc");
            assertThat(json.path("context").has("traceId")).isFalse();
            assertThat(json.path("context").path("tenant.id").asText()).isEqualTo("t");
            assertThat(json.path("processing").path("errors").isMissingNode()).isTrue();
            assertThat(new String(bytes, java.nio.charset.StandardCharsets.UTF_8).lines())
                    .hasSize(1);
        } finally {
            encoder.stop();
            context.stop();
        }
    }
}
