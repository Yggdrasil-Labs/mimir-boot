package com.yggdrasil.labs.rpc.feign.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeignPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        FeignProperties properties = new FeignProperties();
        assertTrue(properties.isEnabled());
        assertTrue(properties.isContextPropagationEnabled());
    }
}
