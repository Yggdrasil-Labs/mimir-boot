package com.yggdrasil.labs.nacos.crypto;

import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.test.util.AssertUtils;
import com.yggdrasil.labs.test.util.TestUtils;
import org.junit.jupiter.api.Test;

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

        // 使用错误的密钥解密应该抛出异常
        assertThrows(RuntimeException.class, () -> {
            ConfigCryptoUtils.decrypt(encrypted, key2);
        });
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
}
