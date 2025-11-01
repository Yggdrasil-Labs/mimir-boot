package com.yggdrasil.labs.mybatis.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.yggdrasil.labs.mybatis.audit.AuditorProvider;
import com.yggdrasil.labs.mybatis.audit.AuditMetaObjectHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyBatis-Plus 审计配置测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class MybatisPlusAuditConfigurationTest {

    private MybatisPlusAuditConfiguration configuration;

    @BeforeEach
    void setUp() {
        configuration = new MybatisPlusAuditConfiguration();
    }

    @Test
    void testDefaultAuditorProviderCreation() {
        AuditorProvider provider = configuration.defaultAuditorProvider();

        assertNotNull(provider);
        assertEquals("system", provider.currentAuditor());
    }

    @Test
    void testAuditMetaObjectHandlerCreation() {
        AuditorProvider provider = () -> "test-user";
        MetaObjectHandler handler = configuration.auditMetaObjectHandler(provider);

        assertNotNull(handler);
        assertInstanceOf(AuditMetaObjectHandler.class, handler);
    }

    @Test
    void testMultipleAuditorProviderCreation() {
        AuditorProvider provider1 = configuration.defaultAuditorProvider();
        AuditorProvider provider2 = configuration.defaultAuditorProvider();

        assertNotNull(provider1);
        assertNotNull(provider2);
        // 验证两者功能相同
        assertEquals("system", provider1.currentAuditor());
        assertEquals("system", provider2.currentAuditor());
        // 注意：lambda 表达式可能会被 JVM 优化，导致引用相同
        // 但在 Spring 容器中，@Bean 方法会确保单例行为
    }
}

