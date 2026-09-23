package com.yggdrasil.labs.log.converter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.yggdrasil.labs.test.base.BaseUnitTest;

/**
 * 敏感数据模式测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class SensitiveDataPatternTest extends BaseUnitTest {

    @Test
    void testPasswordPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.PASSWORD;
        assertEquals("password", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("password"));
    }

    @Test
    void testTokenPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.TOKEN;
        assertEquals("token", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("token"));
    }

    @Test
    void testSecretPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.SECRET;
        assertEquals("secret", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("secret"));
    }

    @Test
    void keyValueFieldNamesCoverAllFieldRulesAndLeavePureValueRulesOut() {
        Map<SensitiveDataPattern, List<String>> expectedAliases =
                Map.ofEntries(
                        Map.entry(
                                SensitiveDataPattern.PASSWORD,
                                List.of("password", "pwd", "passwd", "%70assword", "密码")),
                        Map.entry(
                                SensitiveDataPattern.TOKEN,
                                List.of("token", "access_token", "refresh_token")),
                        Map.entry(
                                SensitiveDataPattern.SECRET,
                                List.of(
                                        "secret",
                                        "private_key",
                                        "privateKey",
                                        "secret_key",
                                        "secretKey",
                                        "access_key",
                                        "accessKey",
                                        "%73ecretKey",
                                        "私钥")),
                        Map.entry(
                                SensitiveDataPattern.API_KEY,
                                List.of("apikey", "api_key", "app_key")),
                        Map.entry(
                                SensitiveDataPattern.ACCOUNT,
                                List.of("account", "accountId", "account_id", "账号")),
                        Map.entry(
                                SensitiveDataPattern.ID_CARD, List.of("idcard", "id_card", "身份证")),
                        Map.entry(
                                SensitiveDataPattern.PHONE,
                                List.of("phone", "mobile", "tel", "手机", "电话")),
                        Map.entry(
                                SensitiveDataPattern.BANK_CARD,
                                List.of("bankcard", "bank_card", "银行卡")),
                        Map.entry(SensitiveDataPattern.EMAIL, List.of("email", "mail")),
                        Map.entry(SensitiveDataPattern.NAME, List.of("name", "realname", "真实姓名")));

        expectedAliases.forEach(
                (pattern, expected) -> {
                    List<String> actual = SensitiveDataPattern.keyValueFieldNames(List.of(pattern));

                    assertEquals(Set.copyOf(expected), Set.copyOf(actual), pattern.getName());
                    assertEquals(expected.size(), actual.size(), pattern.getName());
                    for (int index = 1; index < actual.size(); index++) {
                        assertTrue(
                                actual.get(index - 1).length() >= actual.get(index).length(),
                                pattern.getName());
                    }
                    assertThrows(UnsupportedOperationException.class, () -> actual.add("mutable"));
                });

        assertEquals(
                List.of(),
                SensitiveDataPattern.keyValueFieldNames(
                        List.of(
                                SensitiveDataPattern.ID_CARD_NUMBER,
                                SensitiveDataPattern.PHONE_NUMBER,
                                SensitiveDataPattern.BANK_CARD_NUMBER,
                                SensitiveDataPattern.EMAIL_ADDRESS)));
        assertEquals(List.of(), SensitiveDataPattern.keyValueFieldNames(List.of()));
    }

    @Test
    void secretPatternCoversJsonAndPercentEncodedSensitiveKeysWithoutMatchingPublicKey() {
        Pattern pattern = Pattern.compile(SensitiveDataPattern.SECRET.getPattern());

        assertTrue(pattern.matcher("{\"privateKey\":\"sample-private-value\"}").find());
        assertTrue(pattern.matcher("%73ecretKey=sample-secret-value").find());
        assertTrue(pattern.matcher("accessKey: sample-access-value").find());
        assertFalse(pattern.matcher("publicKey=public-information").find());
    }

    @Test
    void testApiKeyPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.API_KEY;
        assertEquals("api_key", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("apikey"));
    }

    @Test
    void testAccountPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.ACCOUNT;
        assertEquals("account", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("account"));
    }

    @Test
    void testIdCardPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.ID_CARD;
        assertEquals("id_card", pattern.getName());
        assertNotNull(pattern.getPattern());
    }

    @Test
    void testPhonePattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.PHONE;
        assertEquals("phone", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("phone"));
    }

    @Test
    void testBankCardPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.BANK_CARD;
        assertEquals("bank_card", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("bankcard"));
    }

    @Test
    void testEmailPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.EMAIL;
        assertEquals("email", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("email"));
    }

    @Test
    void testNamePattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.NAME;
        assertEquals("name", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("name"));
    }

    @Test
    void testIdCardNumberPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.ID_CARD_NUMBER;
        assertEquals("id_card_number", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("\\d"));
    }

    @Test
    void testPhoneNumberPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.PHONE_NUMBER;
        assertEquals("phone_number", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().startsWith("1"));
    }

    @Test
    void testBankCardNumberPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.BANK_CARD_NUMBER;
        assertEquals("bank_card_number", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("\\d"));
    }

    @Test
    void testEmailAddressPattern() {
        SensitiveDataPattern pattern = SensitiveDataPattern.EMAIL_ADDRESS;
        assertEquals("email_address", pattern.getName());
        assertNotNull(pattern.getPattern());
        assertTrue(pattern.getPattern().contains("@"));
    }

    @Test
    void testFromNameExisting() {
        SensitiveDataPattern pattern = SensitiveDataPattern.fromName("password");
        assertNotNull(pattern);
        assertEquals(SensitiveDataPattern.PASSWORD, pattern);
    }

    @Test
    void testFromNameCaseInsensitive() {
        SensitiveDataPattern pattern = SensitiveDataPattern.fromName("PASSWORD");
        assertNotNull(pattern);
        assertEquals(SensitiveDataPattern.PASSWORD, pattern);
    }

    @Test
    void testFromNameNonExistent() {
        SensitiveDataPattern pattern = SensitiveDataPattern.fromName("non_existent");
        assertNull(pattern);
    }

    @Test
    void testFromNameNull() {
        SensitiveDataPattern pattern = SensitiveDataPattern.fromName(null);
        assertNull(pattern);
    }

    @Test
    void testAllPatternsHaveNameAndPattern() {
        for (SensitiveDataPattern pattern : SensitiveDataPattern.values()) {
            assertNotNull(pattern.getName());
            assertNotNull(pattern.getPattern());
            assertFalse(pattern.getName().isEmpty());
            assertFalse(pattern.getPattern().isEmpty());
        }
    }

    @Test
    void testAllPatternsUnique() {
        SensitiveDataPattern[] patterns = SensitiveDataPattern.values();
        for (int i = 0; i < patterns.length; i++) {
            for (int j = i + 1; j < patterns.length; j++) {
                assertNotEquals(
                        patterns[i].getName(),
                        patterns[j].getName(),
                        "Duplicate pattern name found: " + patterns[i].getName());
            }
        }
    }

    @Test
    void testValuesCount() {
        // 验证所有的枚举值都被定义了
        SensitiveDataPattern[] values = SensitiveDataPattern.values();
        // 实际有 14 个枚举值
        assertTrue(values.length >= 10, "至少应该有10个预置模式");
    }
}
