package com.yggdrasil.labs.common.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogSanitizerTest {

    @Test
    void sanitize_null_returns_literal_null() {
        assertEquals("null", LogSanitizer.sanitize((String) null));
    }

    @Test
    void sanitize_keeps_printable_ascii_only() {
        String input = "AZaz09 ~!@#$%^&*()_+`-={}[]|:;\"'<>,.?/";
        String output = LogSanitizer.sanitize(input);
        assertEquals(input, output);
    }

    @Test
    void sanitize_removes_controls_and_non_ascii() {
        String input = "Hello\nWorld\t中文😊\u0007"; // 包含换行、制表、非 ASCII（中文、emoji）和 BEL 控制符
        String output = LogSanitizer.sanitize(input);
        assertEquals("HelloWorld", output);
    }

    @Test
    void sanitize_collection_null_returns_empty_list() {
        List<String> result = LogSanitizer.sanitize((List<String>) null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void sanitize_collection_maps_each_item_and_allows_null_element() {
        List<String> inputs = Arrays.asList("A\nB", null, "中文", "OK");
        List<String> result = LogSanitizer.sanitize(inputs);
        assertEquals(List.of("AB", "null", "", "OK"), result);
    }

    @Test
    void escapeControls_null_returns_null() {
        assertNull(LogSanitizer.escapeControls((String) null));
    }

    @Test
    void escapeControls_escapes_newline_carriage_tab_and_removes_other_controls_then_trim() {
        String input = " \nLine1\r\tLine2\u0001\u0002 ";
        String output = LogSanitizer.escapeControls(input);
        // 预期：\n -> \\n，\r -> \\r，\t -> \\t，\u0001/\u0002 被删除，首尾空白被 trim
        assertEquals("\\nLine1\\r\\tLine2", output);
    }

    @Test
    void escapeControls_keeps_normal_text_and_backslashes_as_is() {
        String input = "path \\ server"; // 普通反斜杠文本
        String output = LogSanitizer.escapeControls(input);
        assertEquals("path \\ server", output);
    }

    @Test
    void escapeControls_collection_null_returns_empty_list() {
        List<String> result = LogSanitizer.escapeControls((List<String>) null);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void escapeControls_collection_maps_each_item_and_preserves_null() {
        List<String> inputs = Arrays.asList("A\nB", null, "\tC", " ok \u0007 ");
        List<String> result = LogSanitizer.escapeControls(inputs);
        assertEquals(4, result.size());
        assertEquals("A\\nB", result.get(0));
        assertNull(result.get(1));
        assertEquals("\\tC", result.get(2));
        // \u0007 移除后并 trim -> "ok"
        assertEquals("ok", result.get(3));
    }
}
