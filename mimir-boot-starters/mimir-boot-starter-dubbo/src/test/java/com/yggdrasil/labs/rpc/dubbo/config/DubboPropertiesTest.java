package com.yggdrasil.labs.rpc.dubbo.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DubboPropertiesTest {

    @Test
    void shouldHaveDefaultValues() {
        DubboProperties properties = new DubboProperties();
        assertTrue(properties.isEnabled());
        assertTrue(properties.isContextPropagationEnabled());
    }
}
