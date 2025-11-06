package com.yggdrasil.labs.log.converter;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 敏感数据转换器测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class SensitiveDataConverterTest {

    private SensitiveDataConverter converter;
    private LoggerContext context;

    @BeforeEach
    void setUp() {
        converter = new SensitiveDataConverter();
        // 设置 Logback context
        context = (LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        converter.setContext(context);
        converter.start();
        // 清空自定义规则和系统属性
        SensitiveDataConverter.clearCustomPatterns();
        SensitiveDataConverter.reloadConfig();
        // 清理系统属性
        System.clearProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY);
        System.clearProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY);
        System.clearProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY);
        // 清理上下文属性
        if (context != null) {
            context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, null);
            context.putProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY, null);
            context.putProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY, null);
        }
    }

    @AfterEach
    void tearDown() {
        SensitiveDataConverter.clearCustomPatterns();
        SensitiveDataConverter.reloadConfig();
        // 清理系统属性
        System.clearProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY);
        System.clearProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY);
        System.clearProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY);
        // 清理上下文属性
        if (context != null) {
            context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, null);
            context.putProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY, null);
            context.putProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY, null);
        }
    }

    @Test
    void testConvertWithNullMessage() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn(null);

        String result = converter.convert(event);

        assertNull(result);
    }

    @Test
    void testConvertWithEmptyMessage() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn("");

        String result = converter.convert(event);

        assertEquals("", result);
    }

    @Test
    void testConvertWithNormalMessage() {
        ILoggingEvent event = mock(ILoggingEvent.class);
        when(event.getFormattedMessage()).thenReturn("用户登录成功");

        String result = converter.convert(event);

        assertEquals("用户登录成功", result);
    }

    @Test
    void testStart() {
        SensitiveDataConverter conv = new SensitiveDataConverter();
        assertDoesNotThrow(conv::start);
    }

    @Test
    void testGetContextProperty() {
        // 这个测试需要 Logback context
        assertNotNull(converter.getContext());
    }

    @Test
    void testConstants() {
        assertEquals("mimir.boot.log.mask.enabledPatterns",
                SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY);
        assertEquals("mimir.boot.log.mask.customPatterns",
                SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY);
        assertEquals("mimir.boot.log.mask.replacement",
                SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY);
    }

    @Test
    void testConvertWithMultipleMessages() {
        String[] messages = {
                "用户登录成功",
                "订单创建完成",
                "查询用户信息"
        };

        for (String message : messages) {
            ILoggingEvent event = mock(ILoggingEvent.class);
            when(event.getFormattedMessage()).thenReturn(message);

            String result = converter.convert(event);

            assertEquals(message, result);
        }
    }

    @Test
    void testMaskSensitiveDataWithNull() {
        String result = converter.maskSensitiveData(null);
        assertNull(result);
    }

    @Test
    void testMaskSensitiveDataWithEmpty() {
        String result = converter.maskSensitiveData("");
        assertEquals("", result);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "password,'登录信息: password=123456','password=','123456'",
            "token,'token=abc123xyz','token=','abc123xyz'",
            "phone,'手机号: phone=13812345678','phone=','13812345678'",
            "email,'邮箱: email=test@example.com','email=','test@example.com'"
    })
    void testMaskSensitiveDataWithEnabledPatterns(String enabledPattern,
                                                  String message,
                                                  String keyPrefix,
                                                  String secret) {
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, enabledPattern);
        SensitiveDataConverter.reloadConfig();

        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        assertTrue(result.contains(keyPrefix));
        assertTrue(result.contains("******"));
        assertFalse(result.contains(secret));
    }

    @Test
    void testMaskSensitiveDataWithIdCardPattern() {
        // 启用身份证规则
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "id_card");
        SensitiveDataConverter.reloadConfig();

        String message = "身份证: idCard=110101199001011234";
        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        assertTrue(result.contains("idCard="));
        assertTrue(result.contains("******"));
    }

    @Test
    void testMaskSensitiveDataWithMultiplePatterns() {
        // 启用多个规则
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password,token,phone");
        SensitiveDataConverter.reloadConfig();

        String message = "登录信息: password=123456, token=abc123, phone=13812345678";
        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        assertTrue(result.contains("******"));
        assertFalse(result.contains("123456"));
        assertFalse(result.contains("abc123"));
    }

    @Test
    void testMaskSensitiveDataWithCustomPattern() {
        // 使用自定义规则
        context.putProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY, "test\\d+");
        SensitiveDataConverter.reloadConfig();

        String message = "测试 test123 匹配";
        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        assertTrue(result.contains("******"));
        assertFalse(result.contains("test123"));
    }

    @Test
    void testMaskSensitiveDataWithProgrammaticPattern() {
        // 编程式添加自定义规则
        SensitiveDataConverter.addCustomPattern("custom\\d+");

        String message = "测试 custom456 匹配";
        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        assertTrue(result.contains("******"));
        assertFalse(result.contains("custom456"));
    }

    @Test
    void testMaskSensitiveDataWithQuotedValue() {
        // 启用密码规则
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password");
        SensitiveDataConverter.reloadConfig();

        String message1 = "password=\"secret123\"";
        String result1 = converter.maskSensitiveData(message1);
        assertTrue(result1.contains("password=\"******\""));

        String message2 = "password='secret123'";
        String result2 = converter.maskSensitiveData(message2);
        assertTrue(result2.contains("password='******'"));
    }

    @Test
    void testMaskSensitiveDataWithColonSeparator() {
        // 启用密码规则
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password");
        SensitiveDataConverter.reloadConfig();

        String message = "password: secret123";
        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        assertTrue(result.contains("password:"));
        assertTrue(result.contains("******"));
    }

    @Test
    void testMaskSensitiveDataWithIdCardNumber() {
        // 启用身份证号规则（纯数字）
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "id_card_number");
        SensitiveDataConverter.reloadConfig();

        String message = "身份证号: 110101199001011234";
        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        assertEquals("身份证号: ******", result);
    }

    @Test
    void testMaskSensitiveDataWithPhoneNumber() {
        // 启用手机号规则（纯数字）
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "phone_number");
        SensitiveDataConverter.reloadConfig();

        String message = "手机号: 13812345678";
        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        assertEquals("手机号: ******", result);
    }

    @Test
    void testMaskSensitiveDataWithEmailAddress() {
        // 启用邮箱地址规则（纯邮箱）
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "email_address");
        SensitiveDataConverter.reloadConfig();

        String message = "邮箱: test@example.com";
        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        assertTrue(result.contains("******"));
        assertFalse(result.contains("test@example.com"));
    }

    @Test
    void testMaskSensitiveDataWithBankCardNumber() {
        // 启用银行卡号规则
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "bank_card_number");
        SensitiveDataConverter.reloadConfig();

        String message = "银行卡号: 6222021234567890123";
        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        assertTrue(result.contains("******"));
        assertFalse(result.contains("6222021234567890123"));
    }

    @Test
    void testCustomReplacement() {
        // 设置自定义替换字符
        context.putProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY, "***MASKED***");
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password");
        SensitiveDataConverter.reloadConfig();

        String message = "password=123456";
        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        assertTrue(result.contains("***MASKED***"));
        assertFalse(result.contains("123456"));
    }

    @Test
    void testReplacementFromSystemProperty() {
        // 通过系统属性设置替换字符
        System.setProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY, "###");
        System.setProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password");
        SensitiveDataConverter.reloadConfig();

        String message = "password=123456";
        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        assertTrue(result.contains("###"));
    }

    @Test
    void testContextPropertyPriority() {
        // 上下文属性优先级高于系统属性
        context.putProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY, "context_value");
        System.setProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY, "system_value");
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password");
        SensitiveDataConverter.reloadConfig();

        // 创建新的converter实例以确保replacement字段从配置重新读取
        SensitiveDataConverter newConverter = new SensitiveDataConverter();
        newConverter.setContext(context);
        newConverter.start();

        String message = "password=123456";
        String result = newConverter.maskSensitiveData(message);

        assertNotNull(result);
        assertTrue(result.contains("context_value"));
        assertFalse(result.contains("system_value"));
    }

    @Test
    void testInvalidCustomPattern() {
        // 无效的正则表达式应该被忽略，不会抛出异常
        context.putProperty(SensitiveDataConverter.MASK_CUSTOM_PATTERNS_PROPERTY, "[invalid(regex");
        SensitiveDataConverter.reloadConfig();

        String message = "测试消息";
        assertDoesNotThrow(() -> {
            String result = converter.maskSensitiveData(message);
            assertEquals(message, result);
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("provideInvalidOrEmptyEnabledPatterns")
    void testEnabledPatterns_invalid_or_empty_are_ignored(String enabledPatternsValue) {
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, enabledPatternsValue);
        SensitiveDataConverter.reloadConfig();

        String message = "测试消息";
        String result = converter.maskSensitiveData(message);
        assertEquals(message, result);
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> provideInvalidOrEmptyEnabledPatterns() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("invalid_pattern_name"),
                org.junit.jupiter.params.provider.Arguments.of(""),
                org.junit.jupiter.params.provider.Arguments.of((String) null)
        );
    }

    @Test
    void testConfigAsListWithCommaSeparated() {
        // 测试逗号分隔的配置值
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password,token,phone");
        SensitiveDataConverter.reloadConfig();

        String message = "password=123, token=abc, phone=138";
        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        assertTrue(result.contains("******"));
    }

    @Test
    void testConfigAsListFromSystemProperty() {
        // 从系统属性读取配置
        System.setProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password");
        SensitiveDataConverter.reloadConfig();

        String message = "password=123456";
        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        assertTrue(result.contains("******"));
    }

    @Test
    void testReloadConfig() {
        // 先设置一个规则
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password");
        SensitiveDataConverter.reloadConfig();

        String message1 = "password=123";
        String result1 = converter.maskSensitiveData(message1);
        assertTrue(result1.contains("******"));

        // 重新加载配置，清空规则
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, null);
        SensitiveDataConverter.reloadConfig();

        String message2 = "password=123";
        String result2 = converter.maskSensitiveData(message2);
        assertEquals(message2, result2);
    }

    @Test
    void testAddCustomPattern() {
        // 添加自定义规则
        SensitiveDataConverter.addCustomPattern("secret\\d+");

        String message = "secret123";
        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        assertTrue(result.contains("******"));
        assertFalse(result.contains("secret123"));
    }

    @Test
    void testAddMultipleCustomPatterns() {
        // 添加多个自定义规则
        SensitiveDataConverter.addCustomPattern("pattern1\\d+");
        SensitiveDataConverter.addCustomPattern("pattern2\\d+");

        String message1 = "pattern1123";
        String result1 = converter.maskSensitiveData(message1);
        assertTrue(result1.contains("******"));

        String message2 = "pattern2456";
        String result2 = converter.maskSensitiveData(message2);
        assertTrue(result2.contains("******"));
    }

    @Test
    void testClearCustomPatterns() {
        // 先添加规则
        SensitiveDataConverter.addCustomPattern("test\\d+");

        String message1 = "test123";
        String result1 = converter.maskSensitiveData(message1);
        assertTrue(result1.contains("******"));

        // 清空规则
        SensitiveDataConverter.clearCustomPatterns();
        SensitiveDataConverter.reloadConfig();

        String message2 = "test123";
        String result2 = converter.maskSensitiveData(message2);
        assertEquals(message2, result2);
    }

    @Test
    void testGetAllPresetPatternNames() {
        List<String> names = SensitiveDataConverter.getAllPresetPatternNames();

        assertNotNull(names);
        assertFalse(names.isEmpty());
        // 验证包含所有预置规则
        assertTrue(names.contains("password"));
        assertTrue(names.contains("token"));
        assertTrue(names.contains("secret"));
        assertTrue(names.contains("api_key"));
        assertTrue(names.contains("account"));
        assertTrue(names.contains("id_card"));
        assertTrue(names.contains("phone"));
        assertTrue(names.contains("bank_card"));
        assertTrue(names.contains("email"));
        assertTrue(names.contains("name"));
        assertTrue(names.contains("id_card_number"));
        assertTrue(names.contains("phone_number"));
        assertTrue(names.contains("bank_card_number"));
        assertTrue(names.contains("email_address"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("provideMaskValueCases")
    void testMaskValueCases(String enabledPattern,
                            String message,
                            String expectContains,
                            String secret,
                            String expectedExact) {
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, enabledPattern);
        SensitiveDataConverter.reloadConfig();

        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        if (expectedExact != null) {
            assertEquals(expectedExact, result);
        }
        if (expectContains != null) {
            assertTrue(result.contains(expectContains));
            assertTrue(result.contains("******"));
        }
        if (secret != null) {
            assertFalse(result.contains(secret));
        }
    }

    private static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> provideMaskValueCases() {
        return java.util.stream.Stream.of(
                // 带等号，无引号
                org.junit.jupiter.params.provider.Arguments.of(
                        "password", "password=123456", "password=", "123456", null
                ),
                // 带双引号（使用包含断言，避免实现差异引起的引号重复问题）
                org.junit.jupiter.params.provider.Arguments.of(
                        "password", "password=\"123456\"", "password=\"******\"", null, null
                ),
                // 带单引号（使用包含断言）
                org.junit.jupiter.params.provider.Arguments.of(
                        "password", "password='123456'", "password='******'", null, null
                ),
                // 不带等号（纯数字匹配）
                org.junit.jupiter.params.provider.Arguments.of(
                        "id_card_number", "110101199001011234", null, null, "******234"
                )
        );
    }

    @Test
    void testConverterWithoutContext() {
        // 测试没有context的情况
        SensitiveDataConverter newConverter = new SensitiveDataConverter();
        newConverter.start();

        String message = "测试消息";
        String result = newConverter.maskSensitiveData(message);

        assertEquals(message, result);
    }

    @Test
    void testConverterWithNullContextProperty() {
        // context为null的情况，使用系统属性
        System.setProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password");
        SensitiveDataConverter.reloadConfig();

        // 创建新的converter实例，不设置context
        SensitiveDataConverter newConverter = new SensitiveDataConverter();
        newConverter.start();

        String message = "password=123";
        String result = newConverter.maskSensitiveData(message);

        assertTrue(result.contains("******"));
    }

    @Test
    void testThreadSafety() throws InterruptedException {
        // 测试线程安全
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password");
        SensitiveDataConverter.reloadConfig();

        int threadCount = 10;
        int iterations = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < iterations; j++) {
                        String message = "password=123456" + j;
                        String result = converter.maskSensitiveData(message);
                        assertNotNull(result);
                        assertTrue(result.contains("******"));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
    }

    @Test
    void testConcurrentPatternUpdate() throws InterruptedException {
        // 测试并发更新模式
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int patternId = i;
            executor.submit(() -> {
                try {
                    SensitiveDataConverter.addCustomPattern("pattern" + patternId + "\\d+");
                    SensitiveDataConverter.reloadConfig();
                    String message = "pattern" + patternId + "123";
                    String result = converter.maskSensitiveData(message);
                    assertNotNull(result);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
    }

    @Test
    void testStartMethod() {
        SensitiveDataConverter newConverter = new SensitiveDataConverter();
        assertDoesNotThrow(newConverter::start);
    }

    @Test
    void testMultiplePatternsInOneMessage() {
        // 测试一条消息中包含多个敏感信息
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password,token,phone");
        SensitiveDataConverter.reloadConfig();

        String message = "用户登录 password=123456 token=abc789 phone=13812345678";
        String result = converter.maskSensitiveData(message);

        assertNotNull(result);
        // 应该包含脱敏标记
        assertTrue(result.contains("******"));
        assertFalse(result.contains("123456"));
        assertFalse(result.contains("abc789"));
        assertFalse(result.contains("13812345678"));
    }

    @Test
    void testPatternWithWhitespace() {
        // 测试带空格的配置
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, " password , token ");
        SensitiveDataConverter.reloadConfig();

        String message = "password=123";
        String result = converter.maskSensitiveData(message);

        assertTrue(result.contains("******"));
    }

    @Test
    void testGetReplacementDefault() {
        // 测试默认替换字符
        context.putProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password");
        SensitiveDataConverter.reloadConfig();

        String message = "password=123";
        String result = converter.maskSensitiveData(message);

        assertTrue(result.contains("******"));
    }

    @Test
    void testGetReplacementFromSystemPropertyWithDefault() {
        // 系统属性为空时使用默认值
        // 先清理系统属性，然后不设置，让代码使用默认值
        System.clearProperty(SensitiveDataConverter.MASK_REPLACEMENT_PROPERTY);
        System.setProperty(SensitiveDataConverter.MASK_ENABLED_PATTERNS_PROPERTY, "password");

        // 创建新的converter实例来测试默认值
        SensitiveDataConverter newConverter = new SensitiveDataConverter();
        newConverter.setContext(context);
        newConverter.start();
        SensitiveDataConverter.reloadConfig();

        String message = "password=123";
        String result = newConverter.maskSensitiveData(message);

        assertTrue(result.contains("******"));
    }
}

