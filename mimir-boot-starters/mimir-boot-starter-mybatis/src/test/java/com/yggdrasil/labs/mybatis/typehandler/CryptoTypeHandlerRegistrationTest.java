package com.yggdrasil.labs.mybatis.typehandler;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.ibatis.type.IntegerTypeHandler;
import org.apache.ibatis.type.LongTypeHandler;
import org.apache.ibatis.type.StringTypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.yggdrasil.labs.mybatis.config.MybatisPlusCryptoConfiguration;

class CryptoTypeHandlerRegistrationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(MybatisPlusCryptoConfiguration.class))
                    .withPropertyValues(
                            "mimir.boot.mybatis.crypto-enabled=true",
                            "mimir.boot.mybatis.crypto-key=MDEyMzQ1Njc4OWFiY2RlZg==");

    @Test
    void registeringSpringCryptoHandlers_preservesDefaultJavaTypeHandlers() {
        runner.run(
                context -> {
                    TypeHandlerRegistry registry = new TypeHandlerRegistry();
                    var stringHandler = registry.getTypeHandler(String.class);
                    var longHandler = registry.getTypeHandler(Long.class);
                    var integerHandler = registry.getTypeHandler(Integer.class);

                    registry.register(context.getBean(StringCryptoTypeHandler.class));
                    registry.register(context.getBean(LongCryptoTypeHandler.class));
                    registry.register(context.getBean(IntegerCryptoTypeHandler.class));

                    assertAll(
                            () -> assertSame(stringHandler, registry.getTypeHandler(String.class)),
                            () -> assertSame(longHandler, registry.getTypeHandler(Long.class)),
                            () ->
                                    assertSame(
                                            integerHandler,
                                            registry.getTypeHandler(Integer.class)));
                });
    }

    @Test
    void registeringSpringCryptoHandlers_preservesApplicationDefaultHandlers() {
        runner.run(
                context -> {
                    TypeHandlerRegistry registry = new TypeHandlerRegistry();
                    StringTypeHandler stringHandler = new ApplicationStringTypeHandler();
                    LongTypeHandler longHandler = new ApplicationLongTypeHandler();
                    IntegerTypeHandler integerHandler = new ApplicationIntegerTypeHandler();
                    registry.register(String.class, stringHandler);
                    registry.register(Long.class, longHandler);
                    registry.register(Integer.class, integerHandler);

                    registry.register(context.getBean(StringCryptoTypeHandler.class));
                    registry.register(context.getBean(LongCryptoTypeHandler.class));
                    registry.register(context.getBean(IntegerCryptoTypeHandler.class));

                    assertAll(
                            () -> assertSame(stringHandler, registry.getTypeHandler(String.class)),
                            () -> assertSame(longHandler, registry.getTypeHandler(Long.class)),
                            () ->
                                    assertSame(
                                            integerHandler,
                                            registry.getTypeHandler(Integer.class)));
                });
    }

    @Test
    void registeringSpringCryptoHandlers_preservesExplicitHandlerClassMappings() {
        runner.run(
                context -> {
                    TypeHandlerRegistry registry = new TypeHandlerRegistry();
                    StringCryptoTypeHandler stringHandler =
                            context.getBean(StringCryptoTypeHandler.class);
                    LongCryptoTypeHandler longHandler =
                            context.getBean(LongCryptoTypeHandler.class);
                    IntegerCryptoTypeHandler integerHandler =
                            context.getBean(IntegerCryptoTypeHandler.class);

                    registry.register(stringHandler);
                    registry.register(longHandler);
                    registry.register(integerHandler);

                    assertAll(
                            () ->
                                    assertSame(
                                            stringHandler,
                                            registry.getMappingTypeHandler(
                                                    StringCryptoTypeHandler.class)),
                            () ->
                                    assertSame(
                                            longHandler,
                                            registry.getMappingTypeHandler(
                                                    LongCryptoTypeHandler.class)),
                            () ->
                                    assertSame(
                                            integerHandler,
                                            registry.getMappingTypeHandler(
                                                    IntegerCryptoTypeHandler.class)));
                });
    }

    private static class ApplicationStringTypeHandler extends StringTypeHandler {}

    private static class ApplicationLongTypeHandler extends LongTypeHandler {}

    private static class ApplicationIntegerTypeHandler extends IntegerTypeHandler {}
}
