package com.yggdrasil.labs.nacos.crypto;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.test.util.AssertUtils;
import com.yggdrasil.labs.test.util.TestUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置加解密工具类测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class ConfigCryptoUtilsTest extends BaseUnitTest {

    @Test
    void testGenerateKey() {
        String key = ConfigCryptoUtils.generateKey();

        assertNotNull(key);
        assertFalse(key.isEmpty());
        // Base64 编码的密钥长度检查（128位 = 16字节，Base64编码后约24字符）
        assertTrue(key.length() > 20);
    }

    @Test
    void testGenerateKeyMultipleTimes() {
        String key1 = ConfigCryptoUtils.generateKey();
        String key2 = ConfigCryptoUtils.generateKey();

        // 每次生成的密钥应该不同（概率极高）
        assertNotEquals(key1, key2);
    }

    @Test
    void testGenerateKeyWithAlgorithm() {
        String key = ConfigCryptoUtils.generateKey("AES");

        assertNotNull(key);
        assertFalse(key.isEmpty());
        assertTrue(key.length() > 20);
    }

    @Test
    void testEncryptAndDecrypt() {
        String key = ConfigCryptoUtils.generateKey();
        String plaintext = TestUtils.randomUuid();

        String encrypted = ConfigCryptoUtils.encrypt(plaintext, key);
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);
        assertFalse(encrypted.isEmpty());

        String decrypted = ConfigCryptoUtils.decrypt(encrypted, key);
        AssertUtils.assertEquals(plaintext, decrypted);
    }

    @Test
    void shouldUseVersionedCiphertextWithRandomIv() {
        String key = ConfigCryptoUtils.generateKey();
        String plaintext = "same-plaintext";

        String first = ConfigCryptoUtils.encrypt(plaintext, key);
        String second = ConfigCryptoUtils.encrypt(plaintext, key);

        assertTrue(first.startsWith("v1:"));
        assertTrue(second.startsWith("v1:"));
        assertNotEquals(first, second);
    }

    @Test
    void testEncryptAndDecryptWithAlgorithm() {
        String key = ConfigCryptoUtils.generateKey("AES");
        String plaintext = TestUtils.randomUuid();

        String encrypted = ConfigCryptoUtils.encrypt(plaintext, key, "AES");
        String decrypted = ConfigCryptoUtils.decrypt(encrypted, key, "AES");

        AssertUtils.assertEquals(plaintext, decrypted);
    }

    @Test
    void testEncryptNullPlaintext() {
        String key = ConfigCryptoUtils.generateKey();
        String encrypted = ConfigCryptoUtils.encrypt(null, key);

        assertNull(encrypted);
    }

    @Test
    void testEncryptEmptyPlaintext() {
        String key = ConfigCryptoUtils.generateKey();
        String encrypted = ConfigCryptoUtils.encrypt("", key);

        AssertUtils.assertEquals("", encrypted);
    }

    @Test
    void testDecryptNullCiphertext() {
        String key = ConfigCryptoUtils.generateKey();
        String decrypted = ConfigCryptoUtils.decrypt(null, key);

        assertNull(decrypted);
    }

    @Test
    void testDecryptEmptyCiphertext() {
        String key = ConfigCryptoUtils.generateKey();
        String decrypted = ConfigCryptoUtils.decrypt("", key);

        AssertUtils.assertEquals("", decrypted);
    }

    @Test
    void testEncryptDecryptLongText() {
        String key = ConfigCryptoUtils.generateKey();
        String longText = "A".repeat(1000);

        String encrypted = ConfigCryptoUtils.encrypt(longText, key);
        String decrypted = ConfigCryptoUtils.decrypt(encrypted, key);

        AssertUtils.assertEquals(longText, decrypted);
    }

    @Test
    void testEncryptDecryptSpecialCharacters() {
        String key = ConfigCryptoUtils.generateKey();
        String specialText = "!@#$%^&*()_+-=[]{}|;':\",./<>?";

        String encrypted = ConfigCryptoUtils.encrypt(specialText, key);
        String decrypted = ConfigCryptoUtils.decrypt(encrypted, key);

        AssertUtils.assertEquals(specialText, decrypted);
    }

    @Test
    void testEncryptDecryptUnicode() {
        String key = ConfigCryptoUtils.generateKey();
        String unicodeText = "中文测试 🎉 Hello 世界";

        String encrypted = ConfigCryptoUtils.encrypt(unicodeText, key);
        String decrypted = ConfigCryptoUtils.decrypt(encrypted, key);

        AssertUtils.assertEquals(unicodeText, decrypted);
    }

    @Test
    void testDecryptWithWrongKey() {
        String key1 = ConfigCryptoUtils.generateKey();
        String key2 = ConfigCryptoUtils.generateKey();
        String plaintext = TestUtils.randomUuid();

        String encrypted = ConfigCryptoUtils.encrypt(plaintext, key1);

        assertThrows(RuntimeException.class, () -> ConfigCryptoUtils.decrypt(encrypted, key2));
    }

    @Test
    void shouldRejectTamperedGcmCiphertext() {
        String key = ConfigCryptoUtils.generateKey();
        String encrypted = ConfigCryptoUtils.encrypt("secret", key);
        String[] parts = encrypted.split(":", -1);
        String ciphertext = (parts[2].charAt(0) == 'A' ? "B" : "A") + parts[2].substring(1);

        assertThrows(RuntimeException.class,
                () -> ConfigCryptoUtils.decrypt(parts[0] + ":" + parts[1] + ":" + ciphertext, key));
    }

    @Test
    void shouldRequireExplicitApiToDecryptLegacyAesCiphertextForMigration() {
        String key = ConfigCryptoUtils.generateKey();
        String legacyCiphertext = ConfigCryptoUtils.encrypt("legacy-secret", key, "AES");

        assertFalse(legacyCiphertext.startsWith("v1:"));
        assertThrows(RuntimeException.class, () -> ConfigCryptoUtils.decrypt(legacyCiphertext, key));
        AssertUtils.assertEquals("legacy-secret", ConfigCryptoUtils.decrypt(legacyCiphertext, key, "AES"));
    }

    @Test
    void shouldWarnExactlyOnceForEachDirectLegacyEcbOperation() {
        String key = "MDEyMzQ1Njc4OWFiY2RlZg==";
        Logger logger = (Logger) LoggerFactory.getLogger(ConfigCryptoUtils.class);
        Level previousLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String ciphertext = ConfigCryptoUtils.encrypt("legacy-secret", key, "AES");
            assertEquals(1, countLegacyMigrationWarnings(appender));

            appender.list.clear();
            assertEquals("legacy-secret", ConfigCryptoUtils.decrypt(ciphertext, key, "AES"));
            assertEquals(1, countLegacyMigrationWarnings(appender));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    void shouldDecryptFixedLegacyAesEcbCiphertext() {
        assertEquals("legacy-secret", ConfigCryptoUtils.decrypt(
                "TU0hsTeAvom99Aj157kgoA==", "MDEyMzQ1Njc4OWFiY2RlZg==", "AES"));
    }

    @Test
    void shouldRejectNonAesAlgorithmForLegacyEncryption() {
        String key = ConfigCryptoUtils.generateKey();

        assertThrows(RuntimeException.class, () -> ConfigCryptoUtils.encrypt("secret", key, "DES"));
    }

    @Test
    void testEncryptDecryptNumericStrings() {
        String key = ConfigCryptoUtils.generateKey();
        String numeric = "1234567890";

        String encrypted = ConfigCryptoUtils.encrypt(numeric, key);
        String decrypted = ConfigCryptoUtils.decrypt(encrypted, key);

        AssertUtils.assertEquals(numeric, decrypted);
    }

    @Test
    void testDecryptInvalidCiphertext() {
        String key = ConfigCryptoUtils.generateKey();

        // 无效的密文应该抛出异常
        assertThrows(RuntimeException.class, () -> {
            ConfigCryptoUtils.decrypt("invalid-ciphertext", key);
        });
    }

    private long countLegacyMigrationWarnings(ListAppender<ILoggingEvent> appender) {
        return appender.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains("legacy ECB migration API"))
                .count();
    }
}
