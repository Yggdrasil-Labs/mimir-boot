package com.yggdrasil.labs.log.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;

/** 字段型扫描优化前后输出必须逐字一致：以优化前算法为参考实现做随机对照 */
class SensitiveDataConverterEquivalenceTest {

    private static final List<List<String>> RULE_SETS =
            List.of(
                    List.of("password", "token", "secret", "api_key"),
                    List.of(
                            "password",
                            "token",
                            "secret",
                            "api_key",
                            "account",
                            "id_card",
                            "phone",
                            "bank_card",
                            "email",
                            "name"),
                    List.of("secret"),
                    List.of("name", "account"));

    private static final String[] PIECES = {
        "password",
        "PassWord",
        "pwd",
        "passwd",
        "%70assword",
        "密码",
        "token",
        "access_token",
        "Refresh_Token",
        "secret",
        "secretKey",
        "SECRET_KEY",
        "private_key",
        "accessKey",
        "%73ecretKey",
        "私钥",
        "apikey",
        "api_key",
        "app_key",
        "account",
        "accountId",
        "not_account",
        "account_extra",
        "idcard",
        "身份证",
        "phone",
        "mobile",
        "tel",
        "手机",
        "电话",
        "bankcard",
        "银行卡",
        "email",
        "mail",
        "name",
        "realname",
        "真实姓名",
        "=",
        ":",
        " = ",
        " : ",
        "\"",
        "'",
        " ",
        "  ",
        "\t",
        ",",
        "}",
        "]",
        "{",
        "[",
        "\\",
        "\\\"",
        "value",
        "alpha",
        "123",
        "x",
        "user",
        "order=1",
        "\"password\":\"p\"",
        "'token': 'q'",
        "secret=s",
        "api_key=k",
        "ÄÖ",
        "ß",
        "İ",
        "ı",
        "ſ",
        "K"
    };

    @AfterEach
    void reset() {
        SensitiveDataConverter.resetForTest((LoggerContext) LoggerFactory.getILoggerFactory());
    }

    @Test
    void optimized_scan_matches_reference_on_random_messages() {
        Random random = new Random(20261102L);
        SensitiveDataConverter converter = new SensitiveDataConverter();
        for (List<String> rules : RULE_SETS) {
            List<String> fieldNames = SensitiveDataPattern.keyValueFieldNames(selected(rules));
            SensitiveDataConverter.publishConfiguration(rules, List.of(), "****");
            for (int i = 0; i < 20_000; i++) {
                String message = randomMessage(random);
                assertEquals(
                        ReferenceScanner.mask(message, fieldNames, "****"),
                        converter.maskSensitiveData(message),
                        () -> "rules=" + rules + " message=" + message);
            }
        }
    }

    @Test
    void optimized_scan_matches_reference_on_edge_cases() {
        SensitiveDataConverter converter = new SensitiveDataConverter();
        List<String> rules = List.of("password", "token", "secret", "api_key", "account", "name");
        SensitiveDataConverter.publishConfiguration(rules, List.of(), "****");
        List<String> fieldNames = SensitiveDataPattern.keyValueFieldNames(selected(rules));
        for (String message :
                List.of(
                        "=",
                        ":",
                        "password",
                        "password=",
                        "password =",
                        "=password=a",
                        "password\"   = \"x\"",
                        "password \" = x",
                        "secretKey=1 secret=2",
                        "secretKeyX=1",
                        "xxxxpassword=1",
                        "account_extra=alice not_account=bob",
                        "password=\"unterminated",
                        "token=a,token=b}token=c]token=d",
                        "  password   :   'q'  ",
                        "名字 真实姓名=张三 密码：x 密码=y",
                        "%70assword=z %73ecretKey=w")) {
            assertEquals(
                    ReferenceScanner.mask(message, fieldNames, "****"),
                    converter.maskSensitiveData(message),
                    message);
        }
    }

    private static List<SensitiveDataPattern> selected(List<String> rules) {
        List<SensitiveDataPattern> patterns = new ArrayList<>();
        for (String rule : rules) {
            patterns.add(SensitiveDataPattern.fromName(rule));
        }
        return patterns;
    }

    private static String randomMessage(Random random) {
        StringBuilder builder = new StringBuilder();
        int parts = 1 + random.nextInt(12);
        for (int i = 0; i < parts; i++) {
            builder.append(PIECES[random.nextInt(PIECES.length)]);
        }
        return builder.toString();
    }

    /** 优化前的逐位置扫描算法（自 SensitiveDataConverter 原样复制，仅作对照） */
    private static final class ReferenceScanner {

        static String mask(String message, List<String> fieldNames, String replacement) {
            if (message == null || message.isEmpty() || fieldNames.isEmpty()) {
                return message;
            }
            StringBuilder masked = null;
            int copiedUntil = 0;
            int index = 0;
            while (index < message.length()) {
                int[] fieldValue = find(message, index, fieldNames);
                if (fieldValue == null) {
                    index++;
                } else {
                    if (masked == null) {
                        masked = new StringBuilder(message.length());
                    }
                    masked.append(message, copiedUntil, fieldValue[0]);
                    if (isQuote(message.charAt(fieldValue[0]))) {
                        masked.append(message.charAt(fieldValue[0]))
                                .append(replacement)
                                .append(message.charAt(fieldValue[0]));
                    } else {
                        masked.append(replacement);
                    }
                    copiedUntil = fieldValue[1];
                    index = fieldValue[1];
                }
            }
            return masked == null
                    ? message
                    : masked.append(message, copiedUntil, message.length()).toString();
        }

        private static int[] find(String message, int index, List<String> fieldNames) {
            String fieldName = null;
            char initial = message.charAt(index);
            for (String candidate : fieldNames) {
                char first = candidate.charAt(0);
                boolean same =
                        initial == first
                                || ((initial ^ 32) == first
                                        && (initial >= 'A' && initial <= 'Z'
                                                || initial >= 'a' && initial <= 'z'));
                if (same && message.regionMatches(true, index, candidate, 0, candidate.length())) {
                    fieldName = candidate;
                    break;
                }
            }
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
            return valueEnd == valueStart ? null : new int[] {valueStart, valueEnd};
        }

        private static int valueEnd(String message, int valueStart) {
            if (valueStart >= message.length()) {
                return valueStart;
            }
            char first = message.charAt(valueStart);
            if (isQuote(first)) {
                int slashCount = 0;
                for (int cursor = valueStart + 1; cursor < message.length(); cursor++) {
                    char current = message.charAt(cursor);
                    if (current == '\\') {
                        slashCount++;
                    } else {
                        if (current == first && slashCount % 2 == 0) {
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
            return value == '"' || value == '\'';
        }
    }
}
