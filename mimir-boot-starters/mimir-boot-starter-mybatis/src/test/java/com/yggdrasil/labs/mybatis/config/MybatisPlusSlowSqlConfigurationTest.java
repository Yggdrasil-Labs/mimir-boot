package com.yggdrasil.labs.mybatis.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.yggdrasil.labs.mybatis.log.SlowSqlInterceptor;

class MybatisPlusSlowSqlConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(MybatisPlusSlowSqlConfiguration.class));

    @Test
    void defaultsEnableTheInterceptorWithOneSecondThreshold() {
        runner.run(
                context -> {
                    assertNull(context.getStartupFailure());
                    SlowSqlProperties properties = context.getBean(SlowSqlProperties.class);
                    assertTrue(properties.isEnabled());
                    assertEquals(1000, properties.getThresholdMs());
                    assertEquals(1, context.getBeansOfType(SlowSqlInterceptor.class).size());
                });
    }

    @Test
    void disabledStillBindsPropertiesButDoesNotRegisterAnInterceptor() {
        runner.withPropertyValues("mimir.boot.mybatis.slow-sql.enabled=false")
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertFalse(context.getBean(SlowSqlProperties.class).isEnabled());
                            assertTrue(context.getBeansOfType(SlowSqlInterceptor.class).isEmpty());
                        });
    }

    @Test
    void invalidThresholdFailsEvenWhenDisabled() {
        for (String value : new String[] {"0", "-1", "9223372036855"}) {
            runner.withPropertyValues(
                            "mimir.boot.mybatis.slow-sql.enabled=false",
                            "mimir.boot.mybatis.slow-sql.threshold-ms=" + value)
                    .run(context -> assertNotNull(context.getStartupFailure(), value));
        }
    }

    @Test
    void maximumThresholdAndCustomInterceptorAreSupported() {
        SlowSqlInterceptor custom = new SlowSqlInterceptor(12);
        runner.withPropertyValues("mimir.boot.mybatis.slow-sql.threshold-ms=9223372036854")
                .withBean(SlowSqlInterceptor.class, () -> custom)
                .run(
                        context -> {
                            assertNull(context.getStartupFailure());
                            assertEquals(
                                    9223372036854L,
                                    context.getBean(SlowSqlProperties.class).getThresholdMs());
                            assertSame(custom, context.getBean(SlowSqlInterceptor.class));
                        });
    }

    @Test
    void malformedEnabledValueCannotSilentlyDisableTheFeature() {
        runner.withPropertyValues("mimir.boot.mybatis.slow-sql.enabled=invalid")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }
}
