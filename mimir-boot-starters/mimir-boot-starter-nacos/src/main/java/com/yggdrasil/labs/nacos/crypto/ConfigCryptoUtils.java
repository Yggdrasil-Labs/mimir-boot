package com.yggdrasil.labs.nacos.crypto;

import com.yggdrasil.labs.common.exception.ErrorCode;
import com.yggdrasil.labs.common.exception.SystemException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 配置加解密工具类。
 *
 * <p>新密文采用 {@code v1:&lt;iv&gt;:&lt;ciphertext&gt;} 格式和 AES-GCM。无版本旧 AES 密文只能通过显式的
 * 已弃用迁移 API 读取，不能用于应用配置自动解密。</p>
 *
 * <p>Source: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/javax/crypto/Cipher.html</p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class ConfigCryptoUtils {

    public static final String DEFAULT_ALGORITHM = "AES/GCM/NoPadding";
    private static final String LEGACY_ALGORITHM = "AES";
    private static final String VERSION = "v1";
    private static final int KEY_SIZE = 256;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private ConfigCryptoUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 生成 AES 密钥。
     *
     * @return Base64 编码的密钥
     */
    public static String generateKey() {
        return generateKey(DEFAULT_ALGORITHM);
    }

    /**
     * 生成指定算法的密钥。
     *
     * @param algorithm 加密算法
     * @return Base64 编码的密钥
     */
    public static String generateKey(String algorithm) {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(normalizeKeyAlgorithm(algorithm));
            keyGenerator.init(KEY_SIZE, SECURE_RANDOM);
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new SystemException(ErrorCode.SYSTEM_ERROR.getCode(), "生成密钥失败", e);
        }
    }

    /**
     * 使用 AES-GCM 加密配置值。
     *
     * @param plaintext 明文
     * @param key       Base64 编码的 AES 密钥
     * @return 带版本和随机 IV 的密文
     */
    public static String encrypt(String plaintext, String key) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(DEFAULT_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, toAesKey(key), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return VERSION + ":" + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new SystemException(ErrorCode.SYSTEM_ERROR.getCode(), "加密失败", e);
        }
    }

    /**
     * 使用指定算法加密配置值。
     *
     * @deprecated 新配置请使用 {@link #encrypt(String, String)}。该重载仅保留旧格式迁移兼容。
     */
    @Deprecated(since = "2.1.1", forRemoval = false)
    public static String encrypt(String plaintext, String key, String algorithm) {
        if (DEFAULT_ALGORITHM.equals(algorithm)) {
            return encrypt(plaintext, key);
        }
        if (!LEGACY_ALGORITHM.equals(algorithm)) {
            throw new IllegalArgumentException("仅支持生成 AES-GCM 新密文，AES 仅用于旧密文迁移");
        }
        return encryptLegacy(plaintext, key, algorithm);
    }

    /**
     * 解密认证的 v1 AES-GCM 配置值。
     *
     * @param ciphertext 密文
     * @param key        Base64 编码的 AES 密钥
     * @return 明文
     */
    public static String decrypt(String ciphertext, String key) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        try {
            if (!ciphertext.startsWith(VERSION + ":")) {
                throw new IllegalArgumentException("仅支持认证的 v1 AES-GCM 密文");
            }
            return decryptGcm(ciphertext, key);
        } catch (Exception e) {
            throw new SystemException(ErrorCode.SYSTEM_ERROR.getCode(), "解密失败", e);
        }
    }

    /**
     * 使用指定算法解密配置值。
     *
     * @deprecated 新配置请使用 {@link #decrypt(String, String)}。该重载仅供离线迁移读取旧 AES 密文，
     * 不提供篡改检测，不能用于应用配置自动解密。
     */
    @Deprecated(since = "2.1.1", forRemoval = false)
    public static String decrypt(String ciphertext, String key, String algorithm) {
        if (ciphertext == null || ciphertext.isEmpty()) {
            return ciphertext;
        }
        if (ciphertext.startsWith(VERSION + ":")) {
            return decrypt(ciphertext, key);
        }
        if (!LEGACY_ALGORITHM.equals(algorithm)) {
            throw new IllegalArgumentException("仅支持 AES-GCM 新密文和 AES 旧密文迁移");
        }
        try {
            return decryptLegacy(ciphertext, key, algorithm);
        } catch (Exception e) {
            throw new SystemException(ErrorCode.SYSTEM_ERROR.getCode(), "解密失败", e);
        }
    }

    /**
     * 校验 Base64 AES 密钥的格式与长度。
     *
     * @param key Base64 编码的密钥
     */
    public static void validateKey(String key) {
        toAesKey(key);
    }

    /**
     * 校验应用配置中允许使用的算法标记。
     *
     * @param algorithm 算法标记
     */
    public static void validateConfiguredAlgorithm(String algorithm) {
        if (!DEFAULT_ALGORITHM.equals(algorithm)) {
            throw new IllegalArgumentException("应用配置仅支持 " + DEFAULT_ALGORITHM);
        }
    }

    private static String decryptGcm(String ciphertext, String key) throws Exception {
        String[] parts = ciphertext.split(":", -1);
        if (parts.length != 3 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("不支持的密文格式");
        }
        byte[] iv = Base64.getDecoder().decode(parts[1]);
        if (iv.length != GCM_IV_LENGTH) {
            throw new IllegalArgumentException("非法的初始化向量长度");
        }
        byte[] encrypted = Base64.getDecoder().decode(parts[2]);
        if (encrypted.length <= GCM_TAG_LENGTH / Byte.SIZE) {
            throw new IllegalArgumentException("非法的密文长度");
        }
        Cipher cipher = Cipher.getInstance(DEFAULT_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, toAesKey(key), new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static String encryptLegacy(String plaintext, String key, String algorithm) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        try {
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(key), algorithm));
            return Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new SystemException(ErrorCode.SYSTEM_ERROR.getCode(), "加密失败", e);
        }
    }

    private static String decryptLegacy(String ciphertext, String key, String algorithm) throws Exception {
        Cipher cipher = Cipher.getInstance(algorithm);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(Base64.getDecoder().decode(key), algorithm));
        return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
    }

    private static SecretKeySpec toAesKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("加密密钥不能为空");
        }
        byte[] decoded = Base64.getDecoder().decode(key);
        if (decoded.length != 16 && decoded.length != 24 && decoded.length != 32) {
            throw new IllegalArgumentException("AES 密钥长度必须为 128、192 或 256 位");
        }
        return new SecretKeySpec(decoded, LEGACY_ALGORITHM);
    }

    private static String normalizeKeyAlgorithm(String algorithm) {
        return DEFAULT_ALGORITHM.equals(algorithm) ? LEGACY_ALGORITHM : algorithm;
    }
}
