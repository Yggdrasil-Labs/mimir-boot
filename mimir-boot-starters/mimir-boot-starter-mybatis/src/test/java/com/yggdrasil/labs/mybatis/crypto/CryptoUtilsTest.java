package com.yggdrasil.labs.mybatis.crypto;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 加密工具类测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class CryptoUtilsTest {

    @Test
    void generateKey_returns_non_empty_base64() {
        String key = CryptoUtils.generateKey();
        assertNotNull(key);
        assertFalse(key.isEmpty());
    }

    @Test
    void generateKey_returns_different_keys() {
        String key1 = CryptoUtils.generateKey();
        String key2 = CryptoUtils.generateKey();
        assertNotEquals(key1, key2);
    }

    @Test
    void encrypt_decrypt_roundtrip_and_pass_through_null_empty() {
        String key = CryptoUtils.generateKey();

        assertNull(CryptoUtils.encrypt(null, key));
        assertEquals("", CryptoUtils.encrypt("", key));

        String plaintext = "hello-世界-😊";
        String ciphertext = CryptoUtils.encrypt(plaintext, key);
        assertNotEquals(plaintext, ciphertext);

        String decrypted = CryptoUtils.decrypt(ciphertext, key);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void decrypt_with_null_returns_null() {
        String key = CryptoUtils.generateKey();
        assertNull(CryptoUtils.decrypt(null, key));
    }

    @Test
    void decrypt_with_empty_string_returns_empty() {
        String key = CryptoUtils.generateKey();
        assertEquals("", CryptoUtils.decrypt("", key));
    }

    @Test
    void decrypt_with_invalid_ciphertext_length_throws() {
        String key = CryptoUtils.generateKey();
        // 创建一个有效的Base64字符串，但解码后长度不足（小于 GCM_IV_LENGTH + 1 = 13）
        // 8字节的Base64编码是12个字符，解码后只有8字节 < 13
        String invalidCiphertext = "YWJjZGVmZ2g="; // "abcdefgh" in Base64, length = 8 bytes < 13

        assertThrows(IllegalStateException.class, () ->
                CryptoUtils.decrypt(invalidCiphertext, key));
    }

    @Test
    void decrypt_with_too_short_base64_throws() {
        String key = CryptoUtils.generateKey();
        // Base64编码后长度仍然不足的无效密文
        // 4字节的Base64编码是8个字符，解码后只有4字节 < 13
        String invalidCiphertext = "YWJjZA=="; // "abcd" in Base64, length = 4 bytes < 13

        assertThrows(IllegalStateException.class, () ->
                CryptoUtils.decrypt(invalidCiphertext, key));
    }

    @Test
    void same_plaintext_with_random_iv_produces_different_ciphertexts() {
        String key = CryptoUtils.generateKey();
        String plaintext = "repeat";
        String c1 = CryptoUtils.encrypt(plaintext, key);
        String c2 = CryptoUtils.encrypt(plaintext, key);
        assertNotEquals(c1, c2);
    }

    @Test
    void decrypt_with_wrong_key_throws() {
        String key1 = CryptoUtils.generateKey();
        String key2 = CryptoUtils.generateKey();
        String ciphertext = CryptoUtils.encrypt("secret", key1);
        assertThrows(IllegalStateException.class, () -> CryptoUtils.decrypt(ciphertext, key2));
    }

    @Test
    void decrypt_with_invalid_base64_throws() {
        String key = CryptoUtils.generateKey();
        // 无效的Base64字符串
        String invalidBase64 = "!!!invalid!!!";

        assertThrows(IllegalStateException.class, () ->
                CryptoUtils.decrypt(invalidBase64, key));
    }

    @Test
    void decrypt_with_corrupted_ciphertext_throws() {
        String key = CryptoUtils.generateKey();
        String validCiphertext = CryptoUtils.encrypt("test", key);

        // 修改密文的某些字节，使其损坏但Base64格式仍然有效
        // 获取Base64编码后的字符串，修改其中一个字符
        char[] chars = validCiphertext.toCharArray();
        chars[chars.length / 2] = (char) (chars[chars.length / 2] ^ 1);
        String corruptedCiphertext = new String(chars);

        assertThrows(IllegalStateException.class, () ->
                CryptoUtils.decrypt(corruptedCiphertext, key));
    }

    @Test
    void encrypt_with_invalid_key_throws() {
        // 无效的Base64密钥
        String invalidKey = "!!!invalid!!!";

        assertThrows(IllegalStateException.class, () ->
                CryptoUtils.encrypt("test", invalidKey));
    }

    @Test
    void decrypt_with_invalid_key_throws() {
        // 无效的Base64密钥
        String invalidKey = "!!!invalid!!!";
        String key = CryptoUtils.generateKey();
        String ciphertext = CryptoUtils.encrypt("test", key);

        assertThrows(IllegalStateException.class, () ->
                CryptoUtils.decrypt(ciphertext, invalidKey));
    }

    @Test
    void encrypt_with_wrong_key_length_throws() {
        // Base64编码的密钥长度不正确（不是128位/16字节）
        String wrongLengthKey = "YWJjZGVmZ2hpams="; // "abcdefghij" (10 bytes, not 16)

        assertThrows(IllegalStateException.class, () ->
                CryptoUtils.encrypt("test", wrongLengthKey));
    }

    @Test
    void decrypt_with_wrong_key_length_throws() {
        // Base64编码的密钥长度不正确
        String wrongLengthKey = "YWJjZGVmZ2hpams="; // "abcdefghij" (10 bytes, not 16)
        String key = CryptoUtils.generateKey();
        String ciphertext = CryptoUtils.encrypt("test", key);

        assertThrows(IllegalStateException.class, () ->
                CryptoUtils.decrypt(ciphertext, wrongLengthKey));
    }

    @Test
    void private_constructor_throws_exception() throws Exception {
        // 测试私有构造函数抛出异常
        Constructor<CryptoUtils> constructor = CryptoUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                constructor::newInstance);

        assertTrue(exception.getCause() instanceof IllegalStateException);
        assertEquals("Utility class", exception.getCause().getMessage());
    }

    @Test
    void encrypt_decrypt_various_plaintexts() {
        String key = CryptoUtils.generateKey();

        String[] plaintexts = {
                "a",
                "A",
                "1234567890",
                "!@#$%^&*()",
                "Hello World",
                "测试中文",
                "🚀🎉💯",
                "A".repeat(1000),
                "Multi\nLine\nText",
                "  spaces  ",
                "null\0byte"
        };

        for (String plaintext : plaintexts) {
            String ciphertext = CryptoUtils.encrypt(plaintext, key);
            assertNotNull(ciphertext);
            assertNotEquals(plaintext, ciphertext);

            String decrypted = CryptoUtils.decrypt(ciphertext, key);
            assertEquals(plaintext, decrypted);
        }
    }

    @Test
    void encrypt_produces_deterministic_decryption() {
        String key = CryptoUtils.generateKey();
        String plaintext = "consistent";

        // 多次加密，虽然密文不同，但都能正确解密
        for (int i = 0; i < 10; i++) {
            String ciphertext = CryptoUtils.encrypt(plaintext, key);
            String decrypted = CryptoUtils.decrypt(ciphertext, key);
            assertEquals(plaintext, decrypted);
        }
    }

    @Test
    void decrypt_with_minimal_valid_length() {
        String key = CryptoUtils.generateKey();
        // 创建一个最小长度的有效密文（刚好是GCM_IV_LENGTH + 1）
        // 这需要构造一个Base64编码后，解码后长度刚好是13字节的字符串
        // 由于Base64编码后长度是4的倍数，13字节编码后是20字符（向上取整）
        // 但实际测试中，我们使用一个有效的密文来测试边界情况

        // 创建一个只有1字节明文的密文（最小情况）
        String minimalPlaintext = "A";
        String minimalCiphertext = CryptoUtils.encrypt(minimalPlaintext, key);

        // 验证可以正常解密
        String decrypted = CryptoUtils.decrypt(minimalCiphertext, key);
        assertEquals(minimalPlaintext, decrypted);
    }
}
