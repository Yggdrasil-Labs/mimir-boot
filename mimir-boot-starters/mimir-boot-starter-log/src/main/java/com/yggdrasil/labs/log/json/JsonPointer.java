package com.yggdrasil.labs.log.json;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** RFC 6901 路径（内部实现，非公共 API），用于配置校验与输出路径。 */
public record JsonPointer(List<String> tokens) {

    public JsonPointer {
        tokens = List.copyOf(tokens);
    }

    /** 解析 RFC 6901 指针，空串表示文档根；非法转义只报告固定诊断。 */
    public static JsonPointer parse(String value) {
        Objects.requireNonNull(value, "JSON Pointer 不能为空");
        if (value.isEmpty()) {
            return new JsonPointer(List.of());
        }
        if (!value.startsWith("/")) {
            throw new IllegalArgumentException("JSON Pointer 需要前导斜杠");
        }
        List<String> tokens = new ArrayList<>();
        for (String token : value.substring(1).split("/", -1)) {
            tokens.add(unescape(token));
        }
        return new JsonPointer(tokens);
    }

    /** 在当前路径后添加一个未转义的对象键或数组下标。 */
    public JsonPointer append(String token) {
        List<String> appended = new ArrayList<>(tokens);
        appended.add(Objects.requireNonNull(token, "JSON Pointer 片段不能为空"));
        return new JsonPointer(appended);
    }

    /** 将单个路径片段中的波浪号与斜杠转义。 */
    public static String escape(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        for (String token : tokens) {
            result.append('/').append(escape(token));
        }
        return result.toString();
    }

    private static String unescape(String token) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            char current = token.charAt(i);
            if (current != '~') {
                result.append(current);
                continue;
            }
            i++;
            if (i >= token.length()) {
                throw new IllegalArgumentException("JSON Pointer 转义不合法");
            }
            char escaped = token.charAt(i);
            if (escaped != '0' && escaped != '1') {
                throw new IllegalArgumentException("JSON Pointer 转义不合法");
            }
            result.append(escaped == '0' ? '~' : '/');
        }
        return result.toString();
    }
}
