package com.yggdrasil.labs.mybatis.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.util.StringUtils;

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
    private static final String V2_PREFIX = "v2:";
    private static final String V2_AAD_PREFIX = "mimir-boot:v2:application:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoUtils() {
        throw new IllegalStateException("Utility class");
    }

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

    /**
     * 使用应用级上下文作为 AAD 写入 v2 密文。
     *
     * <p>应用级 AAD 仅认证密文所属应用，不提供字段或记录级绑定。</p>
     */
    public static String encrypt(String plaintext, String key, String aad) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(key), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            cipher.updateAAD(toApplicationAad(aad));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] output = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, output, 0, iv.length);
            System.arraycopy(ciphertext, 0, output, iv.length, ciphertext.length);
            return V2_PREFIX + Base64.getEncoder().encodeToString(output);
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * 读取 v1 密文或带有应用级 AAD 的 v2 密文。
     */
    public static String decrypt(String ciphertext, String key, String aad) {
        if (ciphertext == null || ciphertext.isEmpty()) return ciphertext;
        if (!ciphertext.startsWith(V2_PREFIX)) {
            return decrypt(ciphertext, key);
        }
        try {
            byte[] input = Base64.getDecoder().decode(ciphertext.substring(V2_PREFIX.length()));
            if (input.length < GCM_IV_LENGTH + 1) {
                throw new IllegalArgumentException("Invalid ciphertext");
            }
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] actualCiphertext = new byte[input.length - GCM_IV_LENGTH];
            System.arraycopy(input, 0, iv, 0, iv.length);
            System.arraycopy(input, iv.length, actualCiphertext, 0, actualCiphertext.length);
            Cipher cipher = initCipher(Cipher.DECRYPT_MODE, key, aad, iv);
            byte[] decrypted = cipher.doFinal(actualCiphertext);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    private static SecretKeySpec keySpec(String key) {
        return new SecretKeySpec(Base64.getDecoder().decode(key), ALGORITHM);
    }

    private static Cipher initCipher(int mode, String key, String aad, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(mode, keySpec(key), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        cipher.updateAAD(toApplicationAad(aad));
        return cipher;
    }

    private static byte[] toApplicationAad(String context) {
        if (!StringUtils.hasText(context)) {
            throw new IllegalArgumentException("crypto context must not be blank for v2 ciphertext");
        }
        return (V2_AAD_PREFIX + context).getBytes(StandardCharsets.UTF_8);
    }
}
