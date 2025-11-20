package com.yggdrasil.labs.nacos.util;

import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.test.util.AssertUtils;
import com.yggdrasil.labs.test.util.TestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Nacos 配置加密工具类测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class NacosEncryptUtilTest extends BaseUnitTest {

    @Test
    void testGenerateKey() {
        String key = NacosEncryptUtil.generateKey();

        assertNotNull(key);
        assertFalse(key.isEmpty());
        assertTrue(key.length() > 20);
    }

    @Test
    void testGenerateKeyWithAlgorithm() {
        String key = NacosEncryptUtil.generateKey("AES");

        assertNotNull(key);
        assertFalse(key.isEmpty());
    }

    @Test
    void testEncrypt() {
        String key = NacosEncryptUtil.generateKey();
        String plaintext = TestUtils.randomUuid();

        String encrypted = NacosEncryptUtil.encrypt(plaintext, key);

        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);
        assertFalse(encrypted.isEmpty());
    }

    @Test
    void testEncryptWithAlgorithm() {
        String key = NacosEncryptUtil.generateKey("AES");
        String plaintext = "test";

        String encrypted = NacosEncryptUtil.encrypt(plaintext, key, "AES");

        assertNotNull(encrypted);
    }

    @Test
    void testDecrypt() {
        String key = NacosEncryptUtil.generateKey();
        String plaintext = TestUtils.randomUuid();

        String encrypted = NacosEncryptUtil.encrypt(plaintext, key);
        String decrypted = NacosEncryptUtil.decrypt(encrypted, key);

        AssertUtils.assertEquals(plaintext, decrypted);
    }

    @Test
    void testDecryptWithAlgorithm() {
        String key = NacosEncryptUtil.generateKey("AES");
        String plaintext = "test";

        String encrypted = NacosEncryptUtil.encrypt(plaintext, key, "AES");
        String decrypted = NacosEncryptUtil.decrypt(encrypted, key, "AES");

        AssertUtils.assertEquals(plaintext, decrypted);
    }

    @Test
    void testWrapWithEnc() {
        String encrypted = "encrypted-value-123";

        String wrapped = NacosEncryptUtil.wrapWithEnc(encrypted);

        AssertUtils.assertEquals("ENC(encrypted-value-123)", wrapped);
    }

    @Test
    void testWrapWithEncWithCustomPrefix() {
        String encrypted = "encrypted-value-123";
        String customPrefix = "ENCRYPTED";

        String wrapped = NacosEncryptUtil.wrapWithEnc(encrypted, customPrefix);

        AssertUtils.assertEquals("ENCRYPTED(encrypted-value-123)", wrapped);
    }

    @Test
    void testWrapWithEncNull() {
        String wrapped = NacosEncryptUtil.wrapWithEnc(null);

        assertNull(wrapped);
    }

    @Test
    void testWrapWithEncEmpty() {
        String wrapped = NacosEncryptUtil.wrapWithEnc("");

        AssertUtils.assertEquals("", wrapped);
    }

    @Test
    void testFullWorkflow() {
        // 1. 生成密钥
        String key = NacosEncryptUtil.generateKey();

        // 2. 加密配置值
        String plaintext = TestUtils.randomUuid();
        String encrypted = NacosEncryptUtil.encrypt(plaintext, key);

        // 3. 包装为 ENC() 格式
        String encValue = NacosEncryptUtil.wrapWithEnc(encrypted);

        // 4. 验证格式
        assertTrue(encValue.startsWith("ENC("));
        assertTrue(encValue.endsWith(")"));
        AssertUtils.assertEquals("ENC(" + encrypted + ")", encValue);

        // 5. 提取并解密验证
        String extracted = encValue.substring(4, encValue.length() - 1);
        String decrypted = NacosEncryptUtil.decrypt(extracted, key);

        AssertUtils.assertEquals(plaintext, decrypted);
    }
}
