package com.yggdrasil.labs.nacos.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 配置加解密工具类
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>支持 AES 对称加密算法</li>
 * <li>密钥使用 Base64 编码</li>
 * <li>加密结果使用 Base64 编码</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class ConfigCryptoUtils {

    private static final String DEFAULT_ALGORITHM = "AES";
    private static final int KEY_SIZE = 128;

    /**
     * 生成加密密钥
     *
     * @return Base64 编码的密钥
     */
    public static String generateKey() {
        return generateKey(DEFAULT_ALGORITHM);
    }

    /**
     * 生成指定算法的加密密钥
     *
     * @param algorithm 加密算法
     * @return Base64 编码的密钥
     */
    public static String generateKey(String algorithm) {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(algorithm);
            keyGenerator.init(KEY_SIZE, new SecureRandom());
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("生成密钥失败: " + algorithm, e);
        }
    }

    /**
     * 加密配置值
     *
     * @param plaintext 明文
     * @param key       Base64 编码的密钥
     * @return Base64 编码的密文
     */
    public static String encrypt(String plaintext, String key) {
        return encrypt(plaintext, key, DEFAULT_ALGORITHM);
    }

    /**
     * 使用指定算法加密配置值
     *
     * @param plaintext 明文
     * @param key       Base64 编码的密钥
     * @param algorithm 加密算法
     * @return Base64 编码的密文
     */
    public static String encrypt(String plaintext, String key, String algorithm) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            SecretKeySpec secretKey = new SecretKeySpec(
                    Base64.getDecoder().decode(key), algorithm);
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /**
     * 解密配置值
     *
     * @param ciphertext Base64 编码的密文
     * @param key        Base64 编码的密钥
     * @return 明文
     */
    public static String decrypt(String ciphertext, String key) {
        return decrypt(ciphertext, key, DEFAULT_ALGORITHM);
    }

    /**
     * 使用指定算法解密配置值
     *
     * @param ciphertext Base64 编码的密文
     * @param key        Base64 编码的密钥
     * @param algorithm  加密算法
     * @return 明文
     */
    public static String decrypt(String ciphertext, String key, String algorithm) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        try {
            SecretKeySpec secretKey = new SecretKeySpec(
                    Base64.getDecoder().decode(key), algorithm);
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(ciphertext));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
}
