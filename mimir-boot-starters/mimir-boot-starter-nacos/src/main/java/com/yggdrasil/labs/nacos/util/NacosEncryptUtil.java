package com.yggdrasil.labs.nacos.util;

import com.yggdrasil.labs.nacos.crypto.ConfigCryptoUtils;

/**
 * Nacos 配置加密工具类
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>生成加密密钥</li>
 * <li>加密配置值</li>
 * <li>生成 ENC() 格式的配置值</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 1. 生成密钥
 * String key = NacosEncryptUtil.generateKey();
 * System.out.println("密钥: " + key);
 *
 * // 2. 加密配置值
 * String plaintext = "my-secret-value";
 * String encrypted = NacosEncryptUtil.encrypt(plaintext, key);
 * System.out.println("加密后: " + encrypted);
 *
 * // 3. 生成 ENC() 格式（用于 Nacos 配置）
 * String encValue = NacosEncryptUtil.wrapWithEnc(encrypted);
 * System.out.println("配置值: " + encValue); // 输出: ENC(encrypted)
 * }</pre>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class NacosEncryptUtil {

    /**
     * 生成加密密钥
     *
     * @return Base64 编码的密钥
     */
    public static String generateKey() {
        return ConfigCryptoUtils.generateKey();
    }

    /**
     * 生成指定算法的加密密钥
     *
     * @param algorithm 加密算法
     * @return Base64 编码的密钥
     */
    public static String generateKey(String algorithm) {
        return ConfigCryptoUtils.generateKey(algorithm);
    }

    /**
     * 加密配置值
     *
     * @param plaintext 明文
     * @param key      Base64 编码的密钥
     * @return Base64 编码的密文
     */
    public static String encrypt(String plaintext, String key) {
        return ConfigCryptoUtils.encrypt(plaintext, key);
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
        return ConfigCryptoUtils.encrypt(plaintext, key, algorithm);
    }

    /**
     * 将加密值包装为 ENC() 格式
     *
     * @param encryptedValue 加密后的值
     * @return ENC(encrypted_value) 格式的字符串
     */
    public static String wrapWithEnc(String encryptedValue) {
        return wrapWithEnc(encryptedValue, "ENC");
    }

    /**
     * 将加密值包装为自定义前缀格式，如 ENC(encrypted_value)
     *
     * @param encryptedValue 加密后的值
     * @param prefix         前缀，默认 ENC
     * @return prefix(encrypted_value) 格式的字符串
     */
    public static String wrapWithEnc(String encryptedValue, String prefix) {
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            return encryptedValue;
        }
        return prefix + "(" + encryptedValue + ")";
    }

    /**
     * 解密配置值
     *
     * @param ciphertext Base64 编码的密文
     * @param key        Base64 编码的密钥
     * @return 明文
     */
    public static String decrypt(String ciphertext, String key) {
        return ConfigCryptoUtils.decrypt(ciphertext, key);
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
        return ConfigCryptoUtils.decrypt(ciphertext, key, algorithm);
    }
}
