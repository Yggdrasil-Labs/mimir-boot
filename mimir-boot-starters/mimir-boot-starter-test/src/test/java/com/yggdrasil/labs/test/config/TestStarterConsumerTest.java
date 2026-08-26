package com.yggdrasil.labs.test.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TestStarterConsumerTest {

    @Test
    void testProfileDoesNotInjectDangerousDefaults() {
        try (ConfigurableApplicationContext context = startTestProfile()) {
            Environment environment = context.getEnvironment();

            assertNull(environment.getProperty("spring.jpa.hibernate.ddl-auto"));
            assertNull(environment.getProperty("spring.jpa.show-sql"));
            assertNull(environment.getProperty("spring.application.name"));
        }
    }

    @Test
    void explicitConsumerConfigurationStillTakesEffect() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .properties(
                        "spring.main.banner-mode=off")
                .run(
                        "--spring.application.name=consumer-test",
                        "--spring.jpa.hibernate.ddl-auto=validate",
                        "--spring.jpa.show-sql=false")) {
            Environment environment = context.getEnvironment();

            assertEquals("consumer-test", environment.getProperty("spring.application.name"));
            assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
            assertEquals("false", environment.getProperty("spring.jpa.show-sql"));
        }
    }

    private ConfigurableApplicationContext startTestProfile() {
        return new SpringApplicationBuilder(TestConfiguration.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .properties("spring.main.banner-mode=off")
                .run();
    }
}
