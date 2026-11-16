package com.yggdrasil.labs.log.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JsonPointerTest {

    @Test
    void decodes_tokens_and_restores_escaped_pointer() {
        JsonPointer pointer = JsonPointer.parse("/attributes/a~1b/~0~01//中文");

        assertThat(pointer.tokens()).containsExactly("attributes", "a/b", "~~1", "", "中文");
        assertThat(pointer.toString()).isEqualTo("/attributes/a~1b/~0~01//中文");
        assertThat(pointer.append("a/b~c").toString())
                .isEqualTo("/attributes/a~1b/~0~01//中文/a~1b~0c");
        assertThat(JsonPointer.parse("").tokens()).isEmpty();
        assertThat(new JsonPointer(List.of("a/b"))).isEqualTo(JsonPointer.parse("/a~1b"));
        assertThatThrownBy(() -> pointer.tokens().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"message", "/a~", "/a~2", "/a~x"})
    void rejects_invalid_pointer_without_echoing_value(String pointer) {
        assertThatThrownBy(() -> JsonPointer.parse(pointer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(pointer);
    }
}
