package com.yggdrasil.labs.log.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LogJsonSettingsCodecTest {

    @Test
    void encodes_utf8_without_property_placeholders_or_delimiters() {
        String value = ",=${missing}(中文) 😀/~+-._azAZ09";
        String encoded = LogJsonSettingsCodec.encode(value);

        assertThat(encoded).matches("[A-Za-z0-9._%\\-]*").doesNotContain(",", "=", "$", "{");
        assertThat(LogJsonSettingsCodec.decode(encoded)).isEqualTo(value);
        List<String> values = List.of(value, "", "/a,b=x");
        assertThat(LogJsonSettingsCodec.decodeList(LogJsonSettingsCodec.encodeList(values)))
                .isEqualTo(values);
        assertThat(LogJsonSettingsCodec.decodeList("")).isEmpty();
        Map<String, Integer> limits = new LinkedHashMap<>();
        limits.put("/attributes/" + value, 3);
        limits.put("/message", 12);
        assertThat(LogJsonSettingsCodec.decodeLimits(LogJsonSettingsCodec.encodeLimits(limits)))
                .containsExactlyEntriesOf(limits);
        assertThat(LogJsonSettingsCodec.decodeLimits("")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "%1", "%GG", "%FF", "%C3%28"})
    void rejects_malformed_encoding_without_echoing_value(String value) {
        assertThatThrownBy(() -> LogJsonSettingsCodec.decode(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JSON 日志内部配置编码不合法");
    }

    @ParameterizedTest
    @ValueSource(strings = {"private%FF", "private%C3%28"})
    void retains_utf8_error_cause_without_configuration_content(String value) {
        assertThatThrownBy(() -> LogJsonSettingsCodec.decode(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JSON 日志内部配置编码不合法")
                .cause()
                .isInstanceOf(java.nio.charset.CharacterCodingException.class)
                .hasMessageNotContaining("private")
                .hasMessageNotContaining(value)
                .hasNoCause();
    }

    @Test
    void distinguishes_empty_list_empty_elements_percent_and_nul() {
        for (List<String> values :
                List.of(List.<String>of(), List.of(""), List.of("", "%", "\0", ""))) {
            assertThat(LogJsonSettingsCodec.decodeList(LogJsonSettingsCodec.encodeList(values)))
                    .isEqualTo(values);
        }
        assertThat(LogJsonSettingsCodec.encodeList(List.of())).isEmpty();
        assertThat(LogJsonSettingsCodec.encodeList(List.of(""))).isEqualTo("%");
        assertThat(LogJsonSettingsCodec.encodeList(List.of("%"))).isEqualTo("%25");
        assertThat(LogJsonSettingsCodec.encodeList(List.of("\0"))).isEqualTo("%00");
    }
}
