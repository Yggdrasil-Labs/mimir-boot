package com.yggdrasil.labs.mybatis.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 加密工具类测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class CryptoUtilsTest {

    @Test
    void testGenerateKey() {
        String key = CryptoUtils.generateKey();

        assertNotNull(key);
        assertFalse(key.isEmpty());
        // Base64 编码的密钥长度检查（128位 = 16字节，Base64编码后约24字符）
        assertTrue(key.length() > 20);
    }

    @Test
    void testGenerateKeyMultipleTimes() {
        String key1 = CryptoUtils.generateKey();
        String key2 = CryptoUtils.generateKey();

        // 每次生成的密钥应该不同（概率极高）
        assertNotEquals(key1, key2);
    }

    @Test
    void testEncryptAndDecrypt() {
        String key = CryptoUtils.generateKey();
        String plaintext = "Hello, World!";

        String encrypted = CryptoUtils.encrypt(plaintext, key);
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);
        assertFalse(encrypted.isEmpty());

        String decrypted = CryptoUtils.decrypt(encrypted, key);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void testEncryptNullPlaintext() {
        String key = CryptoUtils.generateKey();
        String encrypted = CryptoUtils.encrypt(null, key);

        assertNull(encrypted);
    }

    @Test
    void testEncryptEmptyPlaintext() {
        String key = CryptoUtils.generateKey();
        String encrypted = CryptoUtils.encrypt("", key);

        assertEquals("", encrypted);
    }

    @Test
    void testDecryptNullCiphertext() {
        String key = CryptoUtils.generateKey();
        String decrypted = CryptoUtils.decrypt(null, key);

        assertNull(decrypted);
    }

    @Test
    void testDecryptEmptyCiphertext() {
        String key = CryptoUtils.generateKey();
        String decrypted = CryptoUtils.decrypt("", key);

        assertEquals("", decrypted);
    }

    @Test
    void testEncryptDecryptLongText() {
        String key = CryptoUtils.generateKey();
        String longText = "A".repeat(1000);

        String encrypted = CryptoUtils.encrypt(longText, key);
        String decrypted = CryptoUtils.decrypt(encrypted, key);

        assertEquals(longText, decrypted);
    }

    @Test
    void testEncryptDecryptSpecialCharacters() {
        String key = CryptoUtils.generateKey();
        String specialText = "!@#$%^&*()_+-=[]{}|;':\",./<>?";

        String encrypted = CryptoUtils.encrypt(specialText, key);
        String decrypted = CryptoUtils.decrypt(encrypted, key);

        assertEquals(specialText, decrypted);
    }

    @Test
    void testEncryptDecryptUnicode() {
        String key = CryptoUtils.generateKey();
        String unicodeText = "中文测试 🎉 Hello 世界";

        String encrypted = CryptoUtils.encrypt(unicodeText, key);
        String decrypted = CryptoUtils.decrypt(encrypted, key);

        assertEquals(unicodeText, decrypted);
    }

    @Test
    void testDecryptWithWrongKey() {
        String key1 = CryptoUtils.generateKey();
        String key2 = CryptoUtils.generateKey();
        String plaintext = "test message";

        String encrypted = CryptoUtils.encrypt(plaintext, key1);

        // 使用错误的密钥解密应该抛出异常
        assertThrows(RuntimeException.class, () -> {
            CryptoUtils.decrypt(encrypted, key2);
        });
    }

    @Test
    void testEncryptDecryptNumericStrings() {
        String key = CryptoUtils.generateKey();
        String numeric = "1234567890";

        String encrypted = CryptoUtils.encrypt(numeric, key);
        String decrypted = CryptoUtils.decrypt(encrypted, key);

        assertEquals(numeric, decrypted);
    }
}

