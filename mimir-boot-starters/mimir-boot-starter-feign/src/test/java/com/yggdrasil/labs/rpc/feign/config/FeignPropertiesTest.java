package com.yggdrasil.labs.rpc.feign.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeignPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        FeignProperties properties = new FeignProperties();
        assertTrue(properties.isEnabled());
        assertTrue(properties.isContextPropagationEnabled());
    }

    @Test
    void shouldSetAndGetEnabled() {
        FeignProperties properties = new FeignProperties();
        properties.setEnabled(false);
        assertFalse(properties.isEnabled());
        properties.setEnabled(true);
        assertTrue(properties.isEnabled());
    }

    @Test
    void shouldSetAndGetContextPropagationEnabled() {
        FeignProperties properties = new FeignProperties();
        properties.setContextPropagationEnabled(false);
        assertFalse(properties.isContextPropagationEnabled());
        properties.setContextPropagationEnabled(true);
        assertTrue(properties.isContextPropagationEnabled());
    }
}

