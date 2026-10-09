package com.yggdrasil.labs.nacos.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
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

    @Configuration(proxyBeanMethods = false)
    static class UserConfiguration {

        @Bean
        ConfigDataContextRefresher userRefresher(
                ConfigurableApplicationContext context, RefreshScope scope) {
            return new ConfigDataContextRefresher(context, scope);
        }
    }
}
