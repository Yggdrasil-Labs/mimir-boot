package com.yggdrasil.labs.mybatis.config;

import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.test.util.AssertUtils;
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
class MybatisPropertiesTest extends BaseUnitTest {

    private MybatisProperties properties;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        properties = new MybatisProperties();
    }

    @Test
    void testDefaultValues() {
        assertNotNull(properties.getMapperPackages());
        assertTrue(properties.getMapperPackages().isEmpty());
        assertNull(properties.getEnableJsonSqlLog());
        assertNull(properties.getCryptoKey());
        assertFalse(properties.isCryptoV2WriteEnabled());
    }

    @Test
    void effectiveMapperPackages_are_deduplicated_and_legacy_query_is_deprecated() throws Exception {
        properties.setMapperPackages(Arrays.asList("com.example.mapper", "com.example.mapper"));

        String effective = properties.getEffectiveMapperPackages();

        assertTrue(effective.contains(MybatisProperties.DEFAULT_MAPPER_PACKAGE));
        assertTrue(effective.contains("com.example.mapper"));
        assertEquals(effective, properties.getFinalMapperPackages());
        Deprecated deprecated = MybatisProperties.class.getMethod("getFinalMapperPackages")
                .getAnnotation(Deprecated.class);
        assertNotNull(deprecated);
        assertEquals("2.2.1", deprecated.since());
        assertFalse(deprecated.forRemoval());
    }

    @Test
    void cryptoContext_and_v2WriteSwitch_are_exposed_with_safe_default() {
        properties.setCryptoContext("orders-service");
        properties.setCryptoV2WriteEnabled(true);

        assertEquals("orders-service", properties.getCryptoContext());
        assertTrue(properties.isCryptoV2WriteEnabled());
    }

    @Test
    void testMapperPackages() {
        List<String> packages = Arrays.asList("com.example.mapper", "com.example.other.mapper");
        properties.setMapperPackages(packages);

        AssertUtils.assertEquals(2, properties.getMapperPackages().size());
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
        AssertUtils.assertEquals(key, properties.getCryptoKey());

        properties.setCryptoKey(null);
        assertNull(properties.getCryptoKey());

        properties.setCryptoKey("");
        AssertUtils.assertEquals("", properties.getCryptoKey());
    }

    @Test
    void testAllPropertiesTogether() {
        List<String> packages = Arrays.asList("com.example.mapper");
        properties.setMapperPackages(packages);
        properties.setEnableJsonSqlLog(true);
        properties.setCryptoKey("test-key");

        AssertUtils.assertEquals(1, properties.getMapperPackages().size());
        assertTrue(properties.getEnableJsonSqlLog());
        AssertUtils.assertEquals("test-key", properties.getCryptoKey());
    }

    @Test
    void testGetFinalMapperPackages_WithDefaultOnly() {
        // 不设置 mapperPackages，应该只包含默认包
        String finalPackages = properties.getFinalMapperPackages();
        AssertUtils.assertEquals(MybatisProperties.DEFAULT_MAPPER_PACKAGE, finalPackages);
    }

    @Test
    void testGetFinalMapperPackages_WithEmptyList() {
        // 设置空列表，应该只包含默认包
        properties.setMapperPackages(Collections.emptyList());
        String finalPackages = properties.getFinalMapperPackages();
        AssertUtils.assertEquals(MybatisProperties.DEFAULT_MAPPER_PACKAGE, finalPackages);
    }

    @Test
    void testGetFinalMapperPackages_WithNullList() {
        // 设置 null，应该只包含默认包
        properties.setMapperPackages(null);
        String finalPackages = properties.getFinalMapperPackages();
        AssertUtils.assertEquals(MybatisProperties.DEFAULT_MAPPER_PACKAGE, finalPackages);
    }

    @Test
    void testGetFinalMapperPackages_WithCustomPackages() {
        // 设置自定义包，应该包含默认包和自定义包
        List<String> packages = Arrays.asList("com.example.mapper", "com.example.other.mapper");
        properties.setMapperPackages(packages);
        String finalPackages = properties.getFinalMapperPackages();
        
        // 应该包含默认包和自定义包，默认包在前
        assertTrue(finalPackages.contains(MybatisProperties.DEFAULT_MAPPER_PACKAGE));
        assertTrue(finalPackages.contains("com.example.mapper"));
        assertTrue(finalPackages.contains("com.example.other.mapper"));
        // 验证格式：默认包,自定义包1,自定义包2
        AssertUtils.assertEquals(
            MybatisProperties.DEFAULT_MAPPER_PACKAGE + ",com.example.mapper,com.example.other.mapper",
            finalPackages
        );
    }

    @Test
    void testGetFinalMapperPackages_WithSingleCustomPackage() {
        // 设置单个自定义包
        properties.setMapperPackages(Collections.singletonList("com.example.mapper"));
        String finalPackages = properties.getFinalMapperPackages();
        
        AssertUtils.assertEquals(
            MybatisProperties.DEFAULT_MAPPER_PACKAGE + ",com.example.mapper",
            finalPackages
        );
    }

    @Test
    void testGetFinalMapperPackages_WithDuplicatePackages() {
        // 测试去重功能：如果用户配置的包与默认包相同，应该去重
        properties.setMapperPackages(Collections.singletonList(MybatisProperties.DEFAULT_MAPPER_PACKAGE));
        String finalPackages = properties.getFinalMapperPackages();
        
        // 应该只包含一个默认包（去重后）
        AssertUtils.assertEquals(MybatisProperties.DEFAULT_MAPPER_PACKAGE, finalPackages);
    }
}
