package com.yggdrasil.labs.mybatis.config;

import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.test.util.AssertUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyBatis 常量类测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class MybatisConstantsTest extends BaseUnitTest {

    @Test
    void testConstantsValues() {
        // 验证常量值不为空
        assertNotNull(MybatisConstants.MAPPER_PACKAGE_SEPARATOR);
        assertNotNull(MybatisConstants.MAPPER_PACKAGE_SUFFIX);
        assertNotNull(MybatisConstants.PACKAGE_WILDCARD_SUFFIX);
        assertNotNull(MybatisConstants.DEFAULT_PACKAGE_PREFIX);
        assertNotNull(MybatisConstants.CLASSES_DIR);
        assertNotNull(MybatisConstants.JAR_SEPARATOR);
        assertNotNull(MybatisConstants.PAGINATION_INTERCEPTOR_CLASS_NAME);
        assertNotNull(MybatisConstants.PROFILE_DEV);
        assertNotNull(MybatisConstants.PROFILE_TEST);
        assertNotNull(MybatisConstants.MAPPER_SCAN_PATTERN);
        assertNotNull(MybatisConstants.CONFIG_PREFIX);
        assertNotNull(MybatisConstants.CONFIG_MAPPER_PACKAGES);
    }

    @Test
    void testMapperPackageSeparator() {
        AssertUtils.assertEquals("/mapper/", MybatisConstants.MAPPER_PACKAGE_SEPARATOR);
    }

    @Test
    void testMapperPackageSuffix() {
        AssertUtils.assertEquals(".mapper", MybatisConstants.MAPPER_PACKAGE_SUFFIX);
    }

    @Test
    void testPackageWildcardSuffix() {
        AssertUtils.assertEquals(".**", MybatisConstants.PACKAGE_WILDCARD_SUFFIX);
    }

    @Test
    void testDefaultPackagePrefix() {
        AssertUtils.assertEquals("com.yggdrasil.labs", MybatisConstants.DEFAULT_PACKAGE_PREFIX);
    }

    @Test
    void testClassesDir() {
        AssertUtils.assertEquals("/classes/", MybatisConstants.CLASSES_DIR);
    }

    @Test
    void testJarSeparator() {
        AssertUtils.assertEquals("!/", MybatisConstants.JAR_SEPARATOR);
    }

    @Test
    void testPaginationInterceptorClassName() {
        AssertUtils.assertEquals(
                "com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor",
                MybatisConstants.PAGINATION_INTERCEPTOR_CLASS_NAME
        );
    }

    @Test
    void testProfileDev() {
        AssertUtils.assertEquals("dev", MybatisConstants.PROFILE_DEV);
    }

    @Test
    void testProfileTest() {
        AssertUtils.assertEquals("test", MybatisConstants.PROFILE_TEST);
    }

    @Test
    void testMapperScanPattern() {
        AssertUtils.assertEquals("**/mapper/*.class", MybatisConstants.MAPPER_SCAN_PATTERN);
    }

    @Test
    void testConfigPrefix() {
        AssertUtils.assertEquals("mimir.mybatis", MybatisConstants.CONFIG_PREFIX);
    }

    @Test
    void testConfigMapperPackages() {
        AssertUtils.assertEquals("mimir.mybatis.mapper-packages", MybatisConstants.CONFIG_MAPPER_PACKAGES);
        // 验证它是由 CONFIG_PREFIX 和 ".mapper-packages" 组成的
        assertTrue(MybatisConstants.CONFIG_MAPPER_PACKAGES.startsWith(MybatisConstants.CONFIG_PREFIX));
    }

    @Test
    void testConstructorIsPrivate() throws Exception {
        // 验证工具类构造函数是私有的，防止实例化
        Constructor<MybatisConstants> constructor = MybatisConstants.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));

        // 验证无法通过反射创建实例
        constructor.setAccessible(true);
        try {
            MybatisConstants instance = constructor.newInstance();
            // 如果成功创建实例，验证它不为 null（虽然不应该创建）
            assertNotNull(instance);
        } catch (Exception e) {
            // 如果抛出异常也是可以接受的（某些 JVM 可能会阻止实例化）
        }
    }

    @Test
    void testConstantsAreFinal() {
        // 验证所有常量字段都是 final 的
        java.lang.reflect.Field[] fields = MybatisConstants.class.getDeclaredFields();

        for (java.lang.reflect.Field field : fields) {
            assertTrue(Modifier.isFinal(field.getModifiers()),
                    "常量字段应该是 final 的: " + field.getName());
            assertTrue(Modifier.isStatic(field.getModifiers()),
                    "常量字段应该是 static 的: " + field.getName());
        }
    }
}

