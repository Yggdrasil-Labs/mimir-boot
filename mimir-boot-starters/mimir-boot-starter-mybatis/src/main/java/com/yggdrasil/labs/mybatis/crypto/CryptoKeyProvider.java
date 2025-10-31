package com.yggdrasil.labs.mybatis.crypto;

/**
 * 对称加密密钥提供器接口。
 */
@FunctionalInterface
public interface CryptoKeyProvider {
    /**
     * 返回 Base64 编码的对称密钥。
     */
    String getKey();
}

