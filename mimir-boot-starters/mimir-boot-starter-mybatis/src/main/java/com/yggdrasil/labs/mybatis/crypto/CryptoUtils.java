package com.yggdrasil.labs.mybatis.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 简单的对称加解密工具（AES）。
 *
 * <p>说明：采用 AES/GCM/NoPadding，并使用随机 12 字节 IV，密文按如下格式编码：
 * Base64( IV(12 bytes) || CIPHERTEXT )。GCM 认证标签包含在 CIPHERTEXT 中。</p>
 */
public class CryptoUtils {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE = 128;
    private static final int GCM_IV_LENGTH = 12; // 96 bits per NIST recommendation
    private static final int GCM_TAG_LENGTH = 128; // in bits
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static String generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_SIZE, new SecureRandom());
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate key", e);
        }
    }

    public static String encrypt(String plaintext, String key) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        try {
            SecretKeySpec secretKey = new SecretKeySpec(
                Base64.getDecoder().decode(key), ALGORITHM);
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] output = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(ciphertext, 0, output, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(output);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public static String decrypt(String ciphertext, String key) {
        if (ciphertext == null || ciphertext.isEmpty()) return ciphertext;
        try {
            SecretKeySpec secretKey = new SecretKeySpec(
                Base64.getDecoder().decode(key), ALGORITHM);
            byte[] input = Base64.getDecoder().decode(ciphertext);
            if (input.length < GCM_IV_LENGTH + 1) {
                throw new IllegalArgumentException("Invalid ciphertext");
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] actualCiphertext = new byte[input.length - GCM_IV_LENGTH];
            System.arraycopy(input, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(input, GCM_IV_LENGTH, actualCiphertext, 0, actualCiphertext.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);
            byte[] decrypted = cipher.doFinal(actualCiphertext);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}

