package com.yggdrasil.labs.exception.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import com.yggdrasil.labs.test.base.BaseUnitTest;

/**
 * 异常处理配置属性测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class ExceptionPropertiesTest extends BaseUnitTest {

    @Test
    void testDefaultValues() {
        ExceptionProperties properties = new ExceptionProperties();

        assertTrue(properties.isEnabled(), "默认应启用全局异常处理");
        assertFalse(properties.getValidation().isStructuredDetailsEnabled());
    }

    @Test
    void testEnabledProperty() {
        ExceptionProperties properties = new ExceptionProperties();

        properties.setEnabled(false);
        assertFalse(properties.isEnabled());

        properties.setEnabled(true);
        assertTrue(properties.isEnabled());
    }

    @Test
    void bindsStructuredValidationFlagAndRejectsInvalidBoolean() {
        String key = "mimir.boot.exception.validation.structured-details-enabled";
        var enabled = new Binder(new MapConfigurationPropertySource(Map.of(key, "true")));
        ExceptionProperties result =
                enabled.bind("mimir.boot.exception", Bindable.of(ExceptionProperties.class)).get();
        assertTrue(result.getValidation().isStructuredDetailsEnabled());

        var invalid = new Binder(new MapConfigurationPropertySource(Map.of(key, "invalid")));
        assertThrows(
                org.springframework.boot.context.properties.bind.BindException.class,
                () -> invalid.bind("mimir.boot.exception", Bindable.of(ExceptionProperties.class)));
    }
}
