package com.yggdrasil.labs.rpc.dubbo.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DubboPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        DubboProperties properties = new DubboProperties();
        assertTrue(properties.isEnabled());
        assertTrue(properties.isContextPropagationEnabled());
    }

    @Test
    void shouldSetAndGetEnabled() {
        DubboProperties properties = new DubboProperties();
        properties.setEnabled(false);
        assertFalse(properties.isEnabled());
        properties.setEnabled(true);
        assertTrue(properties.isEnabled());
    }

    @Test
    void shouldSetAndGetContextPropagationEnabled() {
        DubboProperties properties = new DubboProperties();
        properties.setContextPropagationEnabled(false);
        assertFalse(properties.isContextPropagationEnabled());
        properties.setContextPropagationEnabled(true);
        assertTrue(properties.isContextPropagationEnabled());
    }
}

