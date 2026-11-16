package com.yggdrasil.labs.log.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ch.qos.logback.classic.LoggerContext;

class LogJsonSettingsTest {

    @Test
    void missing_context_properties_use_safe_defaults() {
        LogJsonSettings settings = LogJsonSettings.from(new LoggerContext());

        assertThat(settings.service()).isEqualTo("application");
        assertThat(settings.environment()).isNull();
        assertThat(settings.instance()).isNull();
        assertThat(settings.excludeMdcKeys()).isEmpty();
        assertThat(settings.limits()).isEmpty();
    }

    @Test
    void decodes_context_once_and_preserves_raw_service_name() {
        LoggerContext context = new LoggerContext();
        String special = "a,b=${unresolved}(中文)";
        context.putProperty(LogJsonKeys.SERVICE, "order%20service");
        context.putProperty(LogJsonKeys.ENVIRONMENT, LogJsonSettingsCodec.encode(special));
        context.putProperty(LogJsonKeys.INSTANCE, LogJsonSettingsCodec.encode(special));
        context.putProperty(
                LogJsonKeys.EXCLUDE_MDC_KEYS, LogJsonSettingsCodec.encodeList(List.of(special)));
        context.putProperty(
                LogJsonKeys.FIELD_LENGTH_LIMITS,
                LogJsonSettingsCodec.encodeLimits(Map.of("/attributes/a~1b", 3)));

        LogJsonSettings settings = LogJsonSettings.from(context);
        context.putProperty(LogJsonKeys.ENVIRONMENT, "changed");

        assertThat(settings.service()).isEqualTo("order%20service");
        assertThat(settings.environment()).isEqualTo(special);
        assertThat(settings.instance()).isEqualTo(special);
        assertThat(settings.excludeMdcKeys()).containsExactly(special);
        assertThat(settings.limits())
                .containsOnly(Map.entry(JsonPointer.parse("/attributes/a~1b"), 3));
    }

    @Test
    void settings_copy_the_input_collections() {
        Set<String> exclusions = new LinkedHashSet<>(Set.of("private"));
        Map<JsonPointer, Integer> limits =
                new LinkedHashMap<>(Map.of(JsonPointer.parse("/message"), 3));
        LogJsonSettings settings = new LogJsonSettings("app", null, null, exclusions, limits);

        exclusions.clear();
        limits.clear();

        assertThat(settings.excludeMdcKeys()).containsExactly("private");
        assertThat(settings.limits()).hasSize(1);
        assertThatThrownBy(() -> settings.excludeMdcKeys().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> settings.limits().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
