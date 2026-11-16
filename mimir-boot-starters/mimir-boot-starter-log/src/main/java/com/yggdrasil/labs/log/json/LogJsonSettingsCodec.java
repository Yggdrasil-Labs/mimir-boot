package com.yggdrasil.labs.log.json;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON 日志内部属性的 UTF-8 百分号编解码（内部实现，非公共 API）。 */
public final class LogJsonSettingsCodec {

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();
    private static final String INVALID_ENCODING_MESSAGE = "JSON 日志内部配置编码不合法";

    private LogJsonSettingsCodec() {}

    /** 编码单个值，仅保留字母、数字、横线、点和下划线。 */
    public static String encode(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte element : value.getBytes(StandardCharsets.UTF_8)) {
            int current = element & 0xff;
            if (isUnreserved(current)) {
                encoded.append((char) current);
            } else {
                encoded.append('%').append(HEX[current >>> 4]).append(HEX[current & 0x0f]);
            }
        }
        return encoded.toString();
    }

    /** 解码单个值，拒绝不完整转义及非法 UTF-8，不回显配置原值。 */
    public static String decode(String value) {
        ByteArrayOutputStream decoded = new ByteArrayOutputStream();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '%') {
                if (i + 2 >= value.length()) {
                    throw invalidEncoding();
                }
                i++;
                int high = Character.digit(value.charAt(i), 16);
                i++;
                int low = Character.digit(value.charAt(i), 16);
                if (high < 0 || low < 0) {
                    throw invalidEncoding();
                }
                decoded.write(high * 16 + low);
            } else if (isUnreserved(current)) {
                decoded.write(current);
            } else {
                throw invalidEncoding();
            }
        }
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(decoded.toByteArray()))
                    .toString();
        } catch (CharacterCodingException ex) {
            // JDK UTF-8 解码异常只记录错误字节长度，可保留原因链而不回显配置内容。
            throw new IllegalArgumentException(INVALID_ENCODING_MESSAGE, ex);
        }
    }

    /** 各项独立编码后以逗号连接；空元素使用裸百分号，与空列表区分。 */
    public static String encodeList(List<String> values) {
        return String.join(
                ",", values.stream().map(value -> value.isEmpty() ? "%" : encode(value)).toList());
    }

    /** 还原逗号连接的编码列表，未设置或空串返回空列表。 */
    public static List<String> decodeList(String value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        List<String> decoded = new ArrayList<>();
        for (String encoded : value.split(",", -1)) {
            decoded.add("%".equals(encoded) ? "" : decode(encoded));
        }
        return List.copyOf(decoded);
    }

    /** 编码路径与长度映射，保留传入映射的迭代顺序。 */
    public static String encodeLimits(Map<String, Integer> limits) {
        List<String> encoded = new ArrayList<>();
        limits.forEach((pointer, limit) -> encoded.add(encode(pointer) + "=" + limit));
        return String.join(",", encoded);
    }

    /** 还原路径与长度映射，失败时不传播含原值的数值解析异常。 */
    // NumberFormatException 的消息包含输入原文，此边界只报告固定诊断。
    @SuppressWarnings("PMD.PreserveStackTrace")
    public static Map<String, Integer> decodeLimits(String value) {
        Map<String, Integer> decoded = new LinkedHashMap<>();
        if (value == null || value.isEmpty()) {
            return decoded;
        }
        for (String item : value.split(",", -1)) {
            int separator = item.indexOf('=');
            if (separator < 0) {
                throw invalidEncoding();
            }
            try {
                decoded.put(
                        decode(item.substring(0, separator)),
                        Integer.valueOf(item.substring(separator + 1)));
            } catch (NumberFormatException ex) {
                throw invalidEncoding();
            }
        }
        return decoded;
    }

    private static boolean isUnreserved(int value) {
        return value >= 'A' && value <= 'Z'
                || value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '-'
                || value == '.'
                || value == '_';
    }

    private static IllegalArgumentException invalidEncoding() {
        return new IllegalArgumentException(INVALID_ENCODING_MESSAGE);
    }
}
