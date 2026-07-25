package com.yggdrasil.labs.web.config;

import com.yggdrasil.labs.test.base.BaseUnitTest;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Web 配置属性测试
 *
 * <p>测试 WebProperties 的默认值和配置：</p>
 * <ul>
 * <li>默认配置值</li>
 * <li>CORS 配置</li>
 * <li>序列化配置</li>
 * <li>安全配置</li>
 * <li>响应配置</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class WebPropertiesTest extends BaseUnitTest {

    private WebProperties webProperties;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        webProperties = new WebProperties();
    }

    /**
     * 测试默认配置值
     */
    @Test
    void testDefaultValues() {
        assertTrue(webProperties.isEnabled());
        assertNotNull(webProperties.getCors());
        assertNotNull(webProperties.getSerialization());
        assertNotNull(webProperties.getResponse());
    }

    /**
     * 测试 CORS 默认配置
     */
    @Test
    void testCorsDefaultValues() {
        WebProperties.Cors cors = webProperties.getCors();

        assertFalse(cors.isEnabled());
        assertFalse(cors.isAllowCredentials());
        assertNotNull(cors.getAllowedOrigins());
        assertTrue(cors.getAllowedOrigins().isEmpty());
        assertNotNull(cors.getAllowedMethods());
        assertTrue(cors.getAllowedMethods().contains("GET"));
        assertTrue(cors.getAllowedMethods().contains("POST"));
        assertNotNull(cors.getAllowedHeaders());
        assertTrue(cors.getAllowedHeaders().contains("*"));
        assertNotNull(cors.getMaxAge());
        assertEquals(3600, cors.getMaxAge().getSeconds());
    }

    /**
     * 测试序列化默认配置
     */
    @Test
    void testSerializationDefaultValues() {
        WebProperties.Serialization serialization = webProperties.getSerialization();

        assertEquals("yyyy-MM-dd HH:mm:ss", serialization.getDateTimeFormat());
        assertEquals("yyyy-MM-dd", serialization.getDateFormat());
        assertEquals("HH:mm:ss", serialization.getTimeFormat());
        assertEquals("Asia/Shanghai", serialization.getTimeZone());
        assertFalse(serialization.isWriteNulls());
        assertFalse(serialization.isPrettyPrint());
        assertTrue(serialization.isIgnoreUnknownProperties());
    }

    @Test
    void shouldKeepDeprecatedSecurityApiForCompatibility() {
        var securityField = Arrays.stream(WebProperties.class.getDeclaredFields())
                .filter(field -> field.getName().equals("security"))
                .findFirst();
        var securityClass = Arrays.stream(WebProperties.class.getDeclaredClasses())
                .filter(nestedClass -> nestedClass.getSimpleName().equals("Security"))
                .findFirst();
        var securityGetter = Arrays.stream(WebProperties.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("getSecurity"))
                .findFirst();

        assertTrue(securityField.isPresent(), "应保留 security 字段以兼容既有绑定和 Java API");
        assertTrue(securityField.orElseThrow().isAnnotationPresent(Deprecated.class));
        assertTrue(securityClass.isPresent(), "应保留 WebProperties.Security 类型");
        assertTrue(securityClass.orElseThrow().isAnnotationPresent(Deprecated.class));
        assertTrue(securityGetter.isPresent(), "应保留 getSecurity() 方法");
        assertTrue(securityGetter.orElseThrow().isAnnotationPresent(Deprecated.class));
        assertTrue(securityGetter.orElseThrow().isAnnotationPresent(
                org.springframework.boot.context.properties.DeprecatedConfigurationProperty.class));
    }

    /**
     * 测试响应默认配置
     */
    @Test
    void testResponseDefaultValues() {
        WebProperties.Response response = webProperties.getResponse();

        assertTrue(response.isEnabled());
        assertTrue(response.isAutoFillTraceId());
    }

    /**
     * 测试 CORS 配置修改
     */
    @Test
    void testCorsConfigurationModification() {
        WebProperties.Cors cors = webProperties.getCors();

        cors.setEnabled(false);
        cors.setAllowCredentials(false);
        cors.getAllowedOrigins().clear();
        cors.getAllowedOrigins().add("https://example.com");

        assertFalse(cors.isEnabled());
        assertFalse(cors.isAllowCredentials());
        assertEquals(1, cors.getAllowedOrigins().size());
        assertTrue(cors.getAllowedOrigins().contains("https://example.com"));
    }

    /**
     * 测试序列化配置修改
     */
    @Test
    void testSerializationConfigurationModification() {
        WebProperties.Serialization serialization = webProperties.getSerialization();

        serialization.setDateTimeFormat("yyyy/MM/dd HH:mm:ss");
        serialization.setPrettyPrint(true);
        serialization.setWriteNulls(true);

        assertEquals("yyyy/MM/dd HH:mm:ss", serialization.getDateTimeFormat());
        assertTrue(serialization.isPrettyPrint());
        assertTrue(serialization.isWriteNulls());
    }
}
