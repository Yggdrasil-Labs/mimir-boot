package com.yggdrasil.labs.exception.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.exception.handler.DefaultExceptionResponseFactory;

class ValidationErrorDataTest {

    private final ValidationErrorDetail detail =
            new ValidationErrorDetail(ValidationErrorSource.FIELD, "name", null, "NotNull", "不能为空");

    @Test
    void shouldTakeImmutableSerializableSnapshot() throws Exception {
        ArrayList<ValidationErrorDetail> original = new ArrayList<>(List.of(detail));
        ValidationErrorData data = new ValidationErrorData(original, 1, false);
        original.clear();
        assertEquals(List.of(detail), data.errors());
        assertThrows(UnsupportedOperationException.class, () -> data.errors().clear());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(data);
        }
        try (ObjectInputStream input =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(data, input.readObject());
        }
    }

    @Test
    void shouldRejectInconsistentCountsAndNulls() {
        assertThrows(NullPointerException.class, () -> new ValidationErrorData(null, 0, false));
        assertThrows(
                NullPointerException.class,
                () -> new ValidationErrorData(Arrays.asList(detail, null), 2, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ValidationErrorData(List.of(detail), 0, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ValidationErrorData(List.of(detail), 2, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ValidationErrorData(List.of(detail), 1, true));
        assertEquals(new ValidationErrorData(List.of(), 0, false).errors(), List.of());
        assertTrue(new ValidationErrorData(List.of(detail), 2, true).truncated());
    }

    @Test
    void shouldNormalizeControlsAndTruncateByCodePoints() {
        String supplementary = "𠮷";
        ValidationErrorDetail normalized =
                new ValidationErrorDetail(
                        ValidationErrorSource.FIELD,
                        "\n" + supplementary.repeat(257),
                        0,
                        "NotNull\u0000",
                        "\t" + supplementary.repeat(257));
        assertEquals(supplementary.repeat(256), normalized.path());
        assertEquals(supplementary.repeat(256), normalized.message());
        assertEquals("NotNull", normalized.constraint());
        assertEquals(
                "Validation",
                new ValidationErrorDetail(
                                ValidationErrorSource.OBJECT, "$", null, "A".repeat(65), "安全")
                        .constraint());
        assertThrows(
                NullPointerException.class,
                () -> new ValidationErrorDetail(null, "$", null, "Validation", "安全"));
        assertThrows(
                NullPointerException.class,
                () ->
                        new ValidationErrorDetail(
                                ValidationErrorSource.FIELD, null, null, "A", "安全"));
        assertThrows(
                NullPointerException.class,
                () ->
                        new ValidationErrorDetail(
                                ValidationErrorSource.FIELD, "a", null, null, "安全"));
        assertThrows(
                NullPointerException.class,
                () -> new ValidationErrorDetail(ValidationErrorSource.FIELD, "a", null, "A", null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ValidationErrorDetail(
                                ValidationErrorSource.PARAMETER, "arg0", -1, "A", "安全"));
    }

    @Test
    void shouldRetainRecordJsonNamesAndDefaultFactoryData() throws Exception {
        ValidationErrorData data = new ValidationErrorData(List.of(detail), 1, false);
        R<?> response =
                (R<?>) new DefaultExceptionResponseFactory().createResponse("00004", "参数错误", data);
        assertSame(data, response.getData());
        var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsBytes(data));
        assertEquals(3, json.size());
        assertEquals(1, json.get("totalCount").intValue());
        assertFalse(json.get("truncated").booleanValue());
        var error = json.get("errors").get(0);
        assertEquals(5, error.size());
        assertEquals("FIELD", error.get("source").textValue());
        assertEquals("name", error.get("path").textValue());
        assertTrue(error.get("parameterIndex").isNull());
        assertEquals("NotNull", error.get("constraint").textValue());
        assertEquals("不能为空", error.get("message").textValue());
    }
}
