package com.yggdrasil.labs.log.config;

import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 日志脱敏配置属性测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class LogMaskPropertiesTest extends BaseUnitTest {

    private LogMaskProperties properties;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        properties = new LogMaskProperties();
    }

    /**
     * 测试默认值
     */
    @Test
    void testDefaultValues() {
        assertNotNull(properties.getEnabledPatterns());
        assertTrue(properties.getEnabledPatterns().isEmpty());
        assertNotNull(properties.getCustomPatterns());
        assertTrue(properties.getCustomPatterns().isEmpty());
        assertEquals(CommonConstants.MASKED, properties.getReplacement());
    }

    /**
     * 测试设置启用的脱敏规则
     */
    @Test
    void testSetEnabledPatterns() {
        List<String> patterns = Arrays.asList("password", "token", "secret");
        properties.setEnabledPatterns(patterns);

        assertEquals(3, properties.getEnabledPatterns().size());
        assertTrue(properties.getEnabledPatterns().contains("password"));
        assertTrue(properties.getEnabledPatterns().contains("token"));
        assertTrue(properties.getEnabledPatterns().contains("secret"));
    }

    /**
     * 测试设置自定义脱敏规则
     */
    @Test
    void testSetCustomPatterns() {
        List<String> customPatterns = Arrays.asList(
                "\\d{4}-\\d{4}-\\d{4}-\\d{4}",  // 信用卡号
                "\\d{11}"  // 手机号
        );
        properties.setCustomPatterns(customPatterns);

        assertEquals(2, properties.getCustomPatterns().size());
        assertTrue(properties.getCustomPatterns().contains("\\d{4}-\\d{4}-\\d{4}-\\d{4}"));
        assertTrue(properties.getCustomPatterns().contains("\\d{11}"));
    }

    /**
     * 测试设置替换字符
     */
    @Test
    void testSetReplacement() {
        String customReplacement = "***MASKED***";
        properties.setReplacement(customReplacement);

        assertEquals(customReplacement, properties.getReplacement());
    }

    /**
     * 测试设置 null 值
     */
    @Test
    void testSetNullValues() {
        properties.setEnabledPatterns(null);
        properties.setCustomPatterns(null);
        properties.setReplacement(null);

        assertNull(properties.getEnabledPatterns());
        assertNull(properties.getCustomPatterns());
        assertNull(properties.getReplacement());
    }

    /**
     * 测试设置空列表
     */
    @Test
    void testSetEmptyLists() {
        properties.setEnabledPatterns(new ArrayList<>());
        properties.setCustomPatterns(new ArrayList<>());

        assertNotNull(properties.getEnabledPatterns());
        assertTrue(properties.getEnabledPatterns().isEmpty());
        assertNotNull(properties.getCustomPatterns());
        assertTrue(properties.getCustomPatterns().isEmpty());
    }

    /**
     * 测试所有属性的组合使用
     */
    @Test
    void testAllPropertiesTogether() {
        List<String> enabledPatterns = Arrays.asList("password", "api_key");
        List<String> customPatterns = Arrays.asList("\\d{16}");
        String replacement = "****";

        properties.setEnabledPatterns(enabledPatterns);
        properties.setCustomPatterns(customPatterns);
        properties.setReplacement(replacement);

        assertEquals(enabledPatterns, properties.getEnabledPatterns());
        assertEquals(customPatterns, properties.getCustomPatterns());
        assertEquals(replacement, properties.getReplacement());
    }

    /**
     * 测试多次设置属性
     */
    @Test
    void testMultipleSetOperations() {
        // 第一次设置
        properties.setEnabledPatterns(Arrays.asList("password"));
        properties.setCustomPatterns(Arrays.asList("pattern1"));
        properties.setReplacement("REPL1");

        // 第二次设置
        properties.setEnabledPatterns(Arrays.asList("token", "secret"));
        properties.setCustomPatterns(Arrays.asList("pattern2", "pattern3"));
        properties.setReplacement("REPL2");

        assertEquals(2, properties.getEnabledPatterns().size());
        assertEquals(2, properties.getCustomPatterns().size());
        assertEquals("REPL2", properties.getReplacement());
    }
}

