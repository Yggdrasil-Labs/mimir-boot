package com.yggdrasil.labs.nacos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.context.refresh.ConfigDataContextRefresher;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class NacosRefreshAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    NacosRefreshAutoConfiguration.class,
                                    RefreshAutoConfiguration.class));

    @Test
    void shouldSupplyDefaultConfigDataRefresher() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(ContextRefresher.class);
                    assertThat(context.getBean(ContextRefresher.class))
                            .isInstanceOf(NacosConfigDataContextRefresher.class);
                    assertThat(context.getBean("configDataContextRefresher"))
                            .isSameAs(context.getBean(ContextRefresher.class));
                });
    }

    @Test
    void shouldRetainUserRefresher() {
        runner.withUserConfiguration(UserConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(ContextRefresher.class);
                            assertThat(context.getBean(ContextRefresher.class))
                                    .isExactlyInstanceOf(ConfigDataContextRefresher.class);
                        });
    }

    @Test
    void shouldNotActivateWhenRefreshDisabled() {
        runner.withPropertyValues("spring.cloud.refresh.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ContextRefresher.class));
    }

    @Test
    void shouldNotReplaceLegacyBootstrapRefresher() {
        runner.withPropertyValues("spring.cloud.bootstrap.enabled=true")
                .run(
                        context ->
                                assertThat(context)
                                        .doesNotHaveBean(NacosConfigDataContextRefresher.class));
    }

    @Test
    void shouldBackOffWhenCloudRefreshAutoConfigurationIsExcludedByAnnotation() {
        try (ConfigurableApplicationContext context =
                runApplication(new SpringApplication(ExcludedApplication.class))) {
            assertThat(context.getBeansOfType(ContextRefresher.class)).isEmpty();
            assertThat(context.getBeansOfType(RefreshScope.class)).isEmpty();
        }
    }

    @Test
    void shouldBackOffWhenCloudRefreshAutoConfigurationIsExcludedByProperty() {
        try (ConfigurableApplicationContext context =
                runApplication(
                        new SpringApplication(EnabledApplication.class),
                        "--spring.autoconfigure.exclude="
                                + RefreshAutoConfiguration.class.getName())) {
            assertThat(context.getBeansOfType(ContextRefresher.class)).isEmpty();
            assertThat(context.getBeansOfType(RefreshScope.class)).isEmpty();
        }
    }

    @Test
    void shouldHonorExclusionFromAnotherAutoConfigurationSelector() {
        try (ConfigurableApplicationContext context =
                runApplication(
                        new SpringApplication(
                                EnabledApplication.class, ExcludedApplication.class))) {
            assertThat(context.getBeansOfType(ContextRefresher.class)).isEmpty();
            assertThat(context.getBeansOfType(RefreshScope.class)).isEmpty();
        }
    }

    @Test
    void shouldRetainUserFactoryWithDefaultBeanName() {
        runner.withUserConfiguration(NamedUserConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(ContextRefresher.class);
                            assertThat(context.getBean("configDataContextRefresher"))
                                    .isExactlyInstanceOf(ConfigDataContextRefresher.class);
                        });
    }

    @Test
    void shouldResolveSpecializedTypeBeforeRefresherInstantiation() {
        runner.withUserConfiguration(RefresherConsumerConfiguration.class)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(ContextRefresher.class);
                            assertThat(context.getBean(RefresherConsumer.class).refresher())
                                    .isSameAs(
                                            context.getBean(NacosConfigDataContextRefresher.class));
                        });
    }

    @Test
    void shouldExposeSpecializedTypeToDependentAutoConfiguration() {
        runner.withConfiguration(AutoConfigurations.of(DependentAutoConfiguration.class))
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(RefresherConsumer.class);
                            assertThat(context.getBean(RefresherConsumer.class).refresher())
                                    .isSameAs(
                                            context.getBean(NacosConfigDataContextRefresher.class));
                        });
    }

    private static ConfigurableApplicationContext runApplication(
            SpringApplication application, String... args) {
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of("spring.cloud.nacos.config.enabled", "false"));
        return assertDoesNotThrow(
                () -> {
                    return application.run(args);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class EnabledApplication {}

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = RefreshAutoConfiguration.class)
    static class ExcludedApplication {}

    @Configuration(proxyBeanMethods = false)
    static class UserConfiguration {

        @Bean
        ConfigDataContextRefresher userRefresher(
                ConfigurableApplicationContext context, RefreshScope scope) {
            return new ConfigDataContextRefresher(context, scope);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class NamedUserConfiguration {

        @Bean
        ConfigDataContextRefresher configDataContextRefresher(
                ConfigurableApplicationContext context, RefreshScope scope) {
            return new ConfigDataContextRefresher(context, scope);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RefresherConsumerConfiguration {

        @Bean
        RefresherConsumer refresherConsumer(NacosConfigDataContextRefresher refresher) {
            return new RefresherConsumer(refresher);
        }
    }

    @AutoConfiguration(after = NacosRefreshAutoConfiguration.class)
    @ConditionalOnBean(NacosConfigDataContextRefresher.class)
    static class DependentAutoConfiguration {

        @Bean
        RefresherConsumer conditionalRefresherConsumer(NacosConfigDataContextRefresher refresher) {
            return new RefresherConsumer(refresher);
        }
    }

    record RefresherConsumer(NacosConfigDataContextRefresher refresher) {}
}
