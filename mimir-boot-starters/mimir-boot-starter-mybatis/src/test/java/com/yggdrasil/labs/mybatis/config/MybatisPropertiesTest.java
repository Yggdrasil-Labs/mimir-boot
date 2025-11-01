package com.yggdrasil.labs.mybatis.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyBatis 配置属性测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class MybatisPropertiesTest {

    private MybatisProperties properties;

    @BeforeEach
    void setUp() {
        properties = new MybatisProperties();
    }

    @Test
    void testDefaultValues() {
        assertNotNull(properties.getMapperPackages());
        assertTrue(properties.getMapperPackages().isEmpty());
        assertNull(properties.getEnableSqlStdout());
        assertNull(properties.getEnableJsonSqlLog());
        assertNull(properties.getCryptoKey());
    }

    @Test
    void testMapperPackages() {
        List<String> packages = Arrays.asList("com.example.mapper", "com.example.other.mapper");
        properties.setMapperPackages(packages);

        assertEquals(2, properties.getMapperPackages().size());
        assertTrue(properties.getMapperPackages().contains("com.example.mapper"));
        assertTrue(properties.getMapperPackages().contains("com.example.other.mapper"));
    }

    @Test
    void testEmptyMapperPackages() {
        properties.setMapperPackages(Collections.emptyList());
        assertTrue(properties.getMapperPackages().isEmpty());
    }

    @Test
    void testNullMapperPackages() {
        properties.setMapperPackages(null);
        assertNull(properties.getMapperPackages());
    }

    @Test
    void testEnableSqlStdout() {
        properties.setEnableSqlStdout(true);
        assertTrue(properties.getEnableSqlStdout());

        properties.setEnableSqlStdout(false);
        assertFalse(properties.getEnableSqlStdout());

        properties.setEnableSqlStdout(null);
        assertNull(properties.getEnableSqlStdout());
    }

    @Test
    void testEnableJsonSqlLog() {
        properties.setEnableJsonSqlLog(true);
        assertTrue(properties.getEnableJsonSqlLog());

        properties.setEnableJsonSqlLog(false);
        assertFalse(properties.getEnableJsonSqlLog());

        properties.setEnableJsonSqlLog(null);
        assertNull(properties.getEnableJsonSqlLog());
    }

    @Test
    void testCryptoKey() {
        String key = "base64encodedkey1234567890";
        properties.setCryptoKey(key);
        assertEquals(key, properties.getCryptoKey());

        properties.setCryptoKey(null);
        assertNull(properties.getCryptoKey());

        properties.setCryptoKey("");
        assertEquals("", properties.getCryptoKey());
    }

    @Test
    void testAllPropertiesTogether() {
        List<String> packages = Arrays.asList("com.example.mapper");
        properties.setMapperPackages(packages);
        properties.setEnableSqlStdout(true);
        properties.setEnableJsonSqlLog(true);
        properties.setCryptoKey("test-key");

        assertEquals(1, properties.getMapperPackages().size());
        assertTrue(properties.getEnableSqlStdout());
        assertTrue(properties.getEnableJsonSqlLog());
        assertEquals("test-key", properties.getCryptoKey());
    }
}

