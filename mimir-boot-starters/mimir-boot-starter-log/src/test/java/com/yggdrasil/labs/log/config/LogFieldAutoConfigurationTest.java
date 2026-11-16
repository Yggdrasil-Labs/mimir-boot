package com.yggdrasil.labs.log.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;

import com.yggdrasil.labs.log.field.LogEventView;
import com.yggdrasil.labs.log.field.LogFieldProvider;
import com.yggdrasil.labs.log.json.LogFieldProviderRegistry;

class LogFieldAutoConfigurationTest {

    @AfterEach
    void reset() {
        Object owner = new Object();
        LogFieldProviderRegistry.register(owner, List.of());
        LogFieldProviderRegistry.clear(owner);
    }

    @Test
    void publishesOnlyAfterRefreshAndClearsBeforeBeanDestruction() {
        AtomicBoolean destroyed = new AtomicBoolean();
        AnnotationConfigApplicationContext context = context();
        context.registerBean("tagger", LogFieldProvider.class, () -> view -> Map.of("k", 1));
        context.registerBean(
                "observer",
                AutoCloseable.class,
                () ->
                        () -> {
                            assertTrue(LogFieldProviderRegistry.current().isEmpty());
                            destroyed.set(true);
                        });
        assertTrue(LogFieldProviderRegistry.current().isEmpty());
        context.refresh();
        assertEquals(List.of("tagger"), names());
        context.close();
        assertTrue(destroyed.get());
        assertTrue(LogFieldProviderRegistry.current().isEmpty());
    }

    @Test
    void honorsFactoryMethodOrderOrderedAndClassAnnotationsThenBeanName() {
        try (AnnotationConfigApplicationContext context = context()) {
            context.register(OrderedProviders.class);
            context.refresh();
            assertEquals(List.of("ordered", "methodFirst", "annotated", "alpha", "beta"), names());
        }
    }

    @Test
    void changingRegistrationOrderDoesNotChangeEqualPriorityResults() {
        for (List<String> names : List.of(List.of("beta", "alpha"), List.of("alpha", "beta"))) {
            try (AnnotationConfigApplicationContext context = context()) {
                for (String name : names) {
                    context.registerBean(
                            name, LogFieldProvider.class, () -> view -> Map.of("winner", name));
                }
                context.refresh();
                assertEquals(List.of("alpha", "beta"), names());
            }
        }
    }

    @Test
    void ordinaryChildCannotPublishOrClearParentProviders() {
        try (AnnotationConfigApplicationContext parent = context()) {
            parent.registerBean("parentProvider", LogFieldProvider.class, () -> view -> Map.of());
            parent.refresh();
            try (AnnotationConfigApplicationContext child = context()) {
                child.setParent(parent);
                child.registerBean("childProvider", LogFieldProvider.class, () -> view -> Map.of());
                child.refresh();
                assertEquals(List.of("parentProvider"), names());
            }
            assertEquals(List.of("parentProvider"), names());
        }
        assertTrue(LogFieldProviderRegistry.current().isEmpty());
    }

    @Test
    void bootstrapEnvironmentDoesNotPublishButMainWithBootstrapParentDoes() {
        try (AnnotationConfigApplicationContext bootstrap = context()) {
            bootstrap.setId("bootstrap");
            bootstrap
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource("bootstrap", Map.of()));
            bootstrap.registerBean(
                    "bootstrapProvider", LogFieldProvider.class, () -> view -> Map.of());
            bootstrap.refresh();
            assertTrue(LogFieldProviderRegistry.current().isEmpty());
            // 模拟 Spring Cloud 在主上下文刷新前移除 bootstrap 标记源。
            bootstrap.getEnvironment().getPropertySources().remove("bootstrap");
            try (AnnotationConfigApplicationContext main = context()) {
                main.setParent(bootstrap);
                main.registerBean("mainProvider", LogFieldProvider.class, () -> view -> Map.of());
                main.refresh();
                assertEquals(List.of("mainProvider"), names());
            }
        }
    }

    @Test
    void ignoresForeignRefreshAndOnlyCurrentOwnerMayClearRegistry() {
        try (AnnotationConfigApplicationContext first = context();
                AnnotationConfigApplicationContext second = context()) {
            first.registerBean("first", LogFieldProvider.class, () -> view -> Map.of());
            second.registerBean("second", LogFieldProvider.class, () -> view -> Map.of());
            first.refresh();
            second.refresh();
            first.getBean(LogFieldAutoConfiguration.class)
                    .registerProviders(new ContextRefreshedEvent(second));
            assertEquals(List.of("second"), names());
            first.close();
            assertEquals(List.of("second"), names());
        }
        assertTrue(LogFieldProviderRegistry.current().isEmpty());
    }

    private static AnnotationConfigApplicationContext context() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(LogFieldAutoConfiguration.class);
        return context;
    }

    private static List<String> names() {
        return LogFieldProviderRegistry.current().stream()
                .map(LogFieldProviderRegistry.NamedProvider::beanName)
                .toList();
    }

    @Configuration(proxyBeanMethods = false)
    static class OrderedProviders {
        @Bean
        LogFieldProvider beta() {
            return view -> Map.of();
        }

        @Bean
        LogFieldProvider alpha() {
            return view -> Map.of();
        }

        @Bean
        LogFieldProvider annotated() {
            return new AnnotatedProvider();
        }

        @Bean
        @Order(-10)
        LogFieldProvider ordered() {
            return new OrderedProvider();
        }

        @Bean
        @Order(1)
        LogFieldProvider methodFirst() {
            return view -> Map.of();
        }
    }

    @Order(5)
    static class AnnotatedProvider implements LogFieldProvider {
        @Override
        public Map<String, ?> provide(LogEventView event) {
            return Map.of();
        }
    }

    static class OrderedProvider implements LogFieldProvider, Ordered {
        @Override
        public int getOrder() {
            return 2;
        }

        @Override
        public Map<String, ?> provide(LogEventView event) {
            return Map.of();
        }
    }
}
