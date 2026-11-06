package com.yggdrasil.labs.exception.handler;

import static com.yggdrasil.labs.exception.handler.ValidationTypeMetadata.*;

import org.springframework.core.ResolvableType;

/** 按声明类型解析扁平绑定路径，在无法验证的位置保留原有回退语义。 */
final class BindingFieldPath {
    private final String field;
    private final StringBuilder path = new StringBuilder();
    private ResolvableType type;
    private int offset;

    private BindingFieldPath(String field, ResolvableType type) {
        this.field = field;
        this.type = type;
    }

    /** 未知根类型直接回退；已知类型仅允许声明属性与可确认的容器位置。 */
    static String resolve(String field, ResolvableType type) {
        if (type.resolve() == null) return "$";
        return new BindingFieldPath(withoutControls(field), type).parse();
    }

    /** 按原顺序处理容器、分隔符和属性，不从输入值补全缺失类型。 */
    private String parse() {
        while (offset < field.length()) {
            char character = field.charAt(offset);
            if (character == '[') {
                String completed = appendContainer();
                if (completed != null) return completed;
            } else if (character == '.') {
                offset++;
            } else if (!appendProperty()) {
                return "$";
            }
        }
        return path.length() == 0 ? "$" : path.toString();
    }

    /** Map 等非索引容器在当前位置终止，防止将键片段误识别为后续属性。 */
    private String appendContainer() {
        if (!container(type)) return "$";
        String position = readPosition();
        path.append('[')
                .append(indexed(type) && position.matches("[0-9]+") ? position : "*")
                .append(']');
        if (!indexed(type)) return path.toString();
        type = elementType(type);
        if (type.resolve() == null) {
            return offset == field.length() ? path.toString() : "$";
        }
        return null;
    }

    /** 保留嵌套括号的扫描顺序；括号不完整时将位置视为未知。 */
    private String readPosition() {
        offset++;
        int start = offset;
        int depth = 1;
        while (offset < field.length() && depth > 0) {
            char next = field.charAt(offset);
            offset++;
            if (next == '[') depth++;
            if (next == ']') depth--;
        }
        return depth == 0 ? field.substring(start, offset - 1) : "";
    }

    /** 仅当名称与声明属性均可验证时追加属性，否则由调用方回退为未知路径。 */
    private boolean appendProperty() {
        int start = offset;
        while (offset < field.length()
                && field.charAt(offset) != '['
                && field.charAt(offset) != '.') {
            offset++;
        }
        String name = field.substring(start, offset);
        if (!identifier(name)) return false;
        ResolvableType property = propertyType(type, name);
        if (property == ResolvableType.NONE) return false;
        if (path.length() > 0) path.append('.');
        path.append(name);
        type = property;
        return true;
    }
}
