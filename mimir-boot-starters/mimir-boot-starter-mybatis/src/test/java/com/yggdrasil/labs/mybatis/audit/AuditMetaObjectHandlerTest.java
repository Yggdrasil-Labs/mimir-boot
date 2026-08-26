package com.yggdrasil.labs.mybatis.audit;

import com.yggdrasil.labs.test.base.BaseUnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 审计元对象处理器测试
 *
 * <p>由于 MyBatis-Plus 的 strictInsertFill 和 strictUpdateFill 需要完整的
 * MyBatis-Plus 环境（包括 TableInfo），在单元测试中难以模拟，本测试主要
 * 验证 AuditMetaObjectHandler 的异常处理和 safeAuditor 方法的逻辑。</p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class AuditMetaObjectHandlerTest extends BaseUnitTest {

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
    }

    @Test
    void testHandlerCreation() {
        // 使用 lambda 表达式创建 provider，避免不必要的 mock
        AuditorProvider provider = () -> "test-user";
        AuditMetaObjectHandler testHandler = new AuditMetaObjectHandler(provider);
        assertNotNull(testHandler);
        assertInstanceOf(AuditMetaObjectHandler.class, testHandler);
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid-user", "", "   "})
    void testSafeAuditorWithVariousValues(String auditorValue) {
        // 使用 lambda 表达式创建 provider，避免不必要的 stubbing
        AuditorProvider provider = () -> auditorValue;
        AuditMetaObjectHandler testHandler = new AuditMetaObjectHandler(provider);
        
        // 验证 handler 可以正常创建
        assertNotNull(testHandler);
    }

    @ParameterizedTest
    @MethodSource("provideHandlerCreationScenarios")
    void testHandlerCreationWithVariousScenarios(String scenario) {
        AuditorProvider provider;
        
        // 根据场景创建不同的 provider
        switch (scenario) {
            case "null":
                provider = () -> null;
                break;
            case "exception":
                provider = () -> {
                    throw new RuntimeException("Test exception");
                };
                break;
            case "normal":
                provider = () -> "test-user";
                break;
            default:
                fail("Unknown scenario: " + scenario);
                return;
        }
        
        // 验证 handler 可以正常创建（构造函数不会调用 currentAuditor）
        AuditMetaObjectHandler testHandler = new AuditMetaObjectHandler(provider);
        assertNotNull(testHandler);
    }

    private static Stream<Arguments> provideHandlerCreationScenarios() {
        return Stream.of(
                Arguments.of("null"),
                Arguments.of("exception"),
                Arguments.of("normal")
        );
    }

    @Test
    void testHandlerWithMultipleAuditorProviders() {
        AuditorProvider provider1 = () -> "user1";
        AuditorProvider provider2 = () -> "user2";
        
        AuditMetaObjectHandler handler1 = new AuditMetaObjectHandler(provider1);
        AuditMetaObjectHandler handler2 = new AuditMetaObjectHandler(provider2);
        
        assertNotNull(handler1);
        assertNotNull(handler2);
        assertNotSame(handler1, handler2);
    }

    @Test
    void auditorProviderFailure_fallsBackToSystemAndWritesWarn() throws Exception {
        AuditorProvider provider = () -> { throw new IllegalStateException("provider-secret"); };
        AuditMetaObjectHandler handler = new AuditMetaObjectHandler(provider);
        Logger logger = (Logger) LoggerFactory.getLogger(AuditMetaObjectHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        try {
            java.lang.reflect.Method method = AuditMetaObjectHandler.class.getDeclaredMethod("safeAuditor");
            method.setAccessible(true);
            assertEquals("system", method.invoke(handler));
            assertTrue(appender.list.stream().anyMatch(event -> event.getLevel() == Level.WARN));
            assertFalse(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("provider-secret")));
        } finally {
            logger.detachAppender(appender);
        }
    }
}
