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
    void generateKey_returns_non_empty_base64() {
        String key = CryptoUtils.generateKey();
        assertNotNull(key);
        assertTrue(key.length() > 0);
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
}
