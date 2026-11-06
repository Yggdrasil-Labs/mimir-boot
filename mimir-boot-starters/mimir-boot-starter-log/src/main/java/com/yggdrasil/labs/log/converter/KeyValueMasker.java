package com.yggdrasil.labs.log.converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按分隔符定位敏感字段，保留引号、空白和转义处理的既有扫描语义。 */
final class KeyValueMasker {

    private final FieldNameIndex fieldNames;

    KeyValueMasker(List<String> orderedNames) {
        this.fieldNames = new FieldNameIndex(orderedNames);
    }

    String mask(String message, String replacement) {
        return maskKeyValueFields(message, fieldNames, replacement);
    }

    /** 字段名按首字符分组的查找表：候选保持原长度降序；ASCII 字母同时登记大小写，其余字符只登记原字符，与逐个比较首字符的语义一致。 */
    private static final class FieldNameIndex {

        private final String[][] ascii = new String[128][];

        /** 非 ASCII 首字符按升序排列，配合二分查找，避免查找时装箱 */
        private final char[] otherInitials;

        private final String[][] otherCandidates;
        private final int maxLength;
        private final boolean empty;

        private FieldNameIndex(List<String> orderedNames) {
            Map<Character, List<String>> grouped = new LinkedHashMap<>();
            int longest = 0;
            for (String name : orderedNames) {
                char first = name.charAt(0);
                grouped.computeIfAbsent(first, key -> new ArrayList<>()).add(name);
                if (isAsciiLetter(first)) {
                    grouped.computeIfAbsent((char) (first ^ 32), key -> new ArrayList<>())
                            .add(name);
                }
                longest = Math.max(longest, name.length());
            }
            List<Character> others = new ArrayList<>();
            for (Map.Entry<Character, List<String>> entry : grouped.entrySet()) {
                if (entry.getKey() < 128) {
                    ascii[entry.getKey()] = entry.getValue().toArray(String[]::new);
                } else {
                    others.add(entry.getKey());
                }
            }
            Collections.sort(others);
            this.otherInitials = new char[others.size()];
            this.otherCandidates = new String[others.size()][];
            for (int i = 0; i < others.size(); i++) {
                otherInitials[i] = others.get(i);
                otherCandidates[i] = grouped.get(others.get(i)).toArray(String[]::new);
            }
            this.maxLength = longest;
            this.empty = orderedNames.isEmpty();
        }

        private String[] candidates(char initial) {
            if (initial < 128) {
                return ascii[initial];
            }
            int position = Arrays.binarySearch(otherInitials, initial);
            return position < 0 ? null : otherCandidates[position];
        }
    }

    private record SensitiveFieldValue(int valueStart, int valueEnd) {}

    private static String maskKeyValueFields(
            String message, FieldNameIndex fieldNames, String replacement) {
        if (fieldNames.empty) {
            return message;
        }
        // 字段只能以“字段名 + 可选引号 + 空白 + '=' 或 ':'”的形式出现，因此只需在每个分隔符之前、最长字段名长度的窗口内
        // 按原逐位置规则判断；窗口外的位置不可能匹配，结果与逐字符扫描一致。
        int length = message.length();
        int nextEquals = message.indexOf('=');
        int nextColon = message.indexOf(':');
        StringBuilder masked = null;
        int copiedUntil = 0;
        int index = 0;
        while (index < length) {
            if (nextEquals >= 0 && nextEquals < index) {
                nextEquals = message.indexOf('=', index);
            }
            if (nextColon >= 0 && nextColon < index) {
                nextColon = message.indexOf(':', index);
            }
            int separator = nextSeparator(nextEquals, nextColon);
            if (separator < 0) {
                break;
            }
            int keyEnd = fieldNameEnd(message, index, separator);
            SensitiveFieldValue fieldValue =
                    findSensitiveFieldBeforeSeparator(message, index, keyEnd, fieldNames);
            if (fieldValue == null) {
                index = separator + 1;
            } else {
                if (masked == null) {
                    masked = new StringBuilder(length);
                }
                masked.append(message, copiedUntil, fieldValue.valueStart());
                appendMaskedFieldValue(masked, message, fieldValue.valueStart(), replacement);
                copiedUntil = fieldValue.valueEnd();
                index = fieldValue.valueEnd();
            }
        }
        return masked == null
                ? message
                : masked.append(message, copiedUntil, message.length()).toString();
    }

    private static SensitiveFieldValue findSensitiveFieldValue(
            String message, int index, FieldNameIndex fieldNames) {
        String fieldName = matchingFieldName(message, index, fieldNames);
        if (fieldName == null) {
            return null;
        }
        int cursor = index + fieldName.length();
        if (cursor < message.length() && isQuote(message.charAt(cursor))) {
            cursor++;
        }
        while (cursor < message.length() && Character.isWhitespace(message.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= message.length()
                || (message.charAt(cursor) != '=' && message.charAt(cursor) != ':')) {
            return null;
        }
        cursor++;
        while (cursor < message.length() && Character.isWhitespace(message.charAt(cursor))) {
            cursor++;
        }
        int valueStart = cursor;
        int valueEnd = valueEnd(message, valueStart);
        return valueEnd == valueStart ? null : new SensitiveFieldValue(valueStart, valueEnd);
    }

    private static void appendMaskedFieldValue(
            StringBuilder masked, String message, int valueStart, String replacement) {
        if (isQuote(message.charAt(valueStart))) {
            masked.append(message.charAt(valueStart))
                    .append(replacement)
                    .append(message.charAt(valueStart));
        } else {
            masked.append(replacement);
        }
    }

    private static String matchingFieldName(String message, int index, FieldNameIndex fieldNames) {
        String[] candidates = fieldNames.candidates(message.charAt(index));
        if (candidates == null) {
            return null;
        }
        for (String fieldName : candidates) {
            if (message.regionMatches(true, index, fieldName, 0, fieldName.length())) {
                return fieldName;
            }
        }
        return null;
    }

    private static boolean isAsciiLetter(char value) {
        return value >= 'A' && value <= 'Z' || value >= 'a' && value <= 'z';
    }

    private static int valueEnd(String message, int valueStart) {
        if (valueStart >= message.length()) {
            return valueStart;
        }
        char firstCharacter = message.charAt(valueStart);
        if (isQuote(firstCharacter)) {
            int slashCount = 0;
            for (int cursor = valueStart + 1; cursor < message.length(); cursor++) {
                char current = message.charAt(cursor);
                if (current == '\\') {
                    slashCount++;
                } else {
                    if (current == firstCharacter && slashCount % 2 == 0) {
                        return cursor + 1;
                    }
                    slashCount = 0;
                }
            }
            return message.length();
        }
        int cursor = valueStart;
        while (cursor < message.length()
                && message.charAt(cursor) != ','
                && message.charAt(cursor) != '}'
                && message.charAt(cursor) != ']'
                && !Character.isWhitespace(message.charAt(cursor))) {
            cursor++;
        }
        return cursor;
    }

    private static boolean isQuote(char value) {
        return value == '\"' || value == '\'';
    }

    private static int nextSeparator(int nextEquals, int nextColon) {
        if (nextEquals < 0) {
            return nextColon;
        }
        return nextColon < 0 ? nextEquals : Math.min(nextEquals, nextColon);
    }

    private static int fieldNameEnd(String message, int index, int separator) {
        int keyEnd = separator;
        while (keyEnd > index && Character.isWhitespace(message.charAt(keyEnd - 1))) {
            keyEnd--;
        }
        return keyEnd > index && isQuote(message.charAt(keyEnd - 1)) ? keyEnd - 1 : keyEnd;
    }

    private static SensitiveFieldValue findSensitiveFieldBeforeSeparator(
            String message, int index, int keyEnd, FieldNameIndex fieldNames) {
        for (int start = Math.max(index, keyEnd - fieldNames.maxLength); start < keyEnd; start++) {
            SensitiveFieldValue fieldValue = findSensitiveFieldValue(message, start, fieldNames);
            if (fieldValue != null) {
                return fieldValue;
            }
        }
        return null;
    }
}
