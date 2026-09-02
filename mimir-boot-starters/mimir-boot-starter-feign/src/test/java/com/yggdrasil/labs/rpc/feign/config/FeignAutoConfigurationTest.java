package com.yggdrasil.labs.rpc.feign.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import com.yggdrasil.labs.rpc.core.config.RpcCoreAutoConfiguration;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.feign.client.RpcFeignCapability;
import com.yggdrasil.labs.rpc.feign.client.RpcFeignClient;
import feign.Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class FeignAutoConfigurationTest {

    private FeignAutoConfiguration configuration;
    private FeignProperties properties;
    private RpcHookChain hookChain;
    private RpcTracerBridge tracerBridge;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RpcCoreAutoConfiguration.class, FeignAutoConfiguration.class));

    @BeforeEach
    void setUp() {
        configuration = new FeignAutoConfiguration();
        properties = new FeignProperties();
        hookChain = mock(RpcHookChain.class);
        tracerBridge = mock(RpcTracerBridge.class);
    }

    @Test
    void shouldCreateRpcFeignCapability() {
        RpcFeignCapability capability = configuration.rpcFeignCapability(hookChain, tracerBridge, properties);

        assertNotNull(capability);
    }

    @Test
    void shouldWrapDelegateClient() {
        Client delegate = mock(Client.class);

        Client client = configuration.rpcFeignCapability(hookChain, tracerBridge, properties).enrich(delegate);

        assertNotNull(client);
        assertSame(RpcFeignClient.class, client.getClass());
    }

    @Test
    void shouldReuseExistingRpcFeignClient() {
        properties.setEnabled(false);
        properties.setContextPropagationEnabled(false);
        Client delegate = mock(Client.class);
        RpcFeignClient existing = new RpcFeignClient(delegate, hookChain, tracerBridge, properties);

        Client client = configuration.rpcFeignCapability(hookChain, tracerBridge, properties).enrich(existing);

        assertSame(existing, client);
    }
    @Test
    void honorsAdapterSwitch() {
        runner.withPropertyValues("mimir.boot.feign.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RpcFeignCapability.class);
                });
    }

    @Test
    void skipsDefaultAdapterWhenCoreDisabled() {
        runner.withPropertyValues("mimir.boot.rpc.core.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RpcFeignCapability.class);
                });
    }

    @Test
    void skipsExplicitAdapterWhenCoreDisabled() {
        runner.withPropertyValues("mimir.boot.rpc.core.enabled=false", "mimir.boot.feign.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(RpcFeignCapability.class);
                });
    }

    @Test
    void registersDefaultAdapter() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(RpcFeignCapability.class);
        });
    }

    @Test
    void runsAfterRpcCoreAutoConfiguration() {
        AutoConfiguration autoConfiguration = FeignAutoConfiguration.class.getAnnotation(AutoConfiguration.class);

        assertThat(autoConfiguration).isNotNull();
        assertThat(autoConfiguration.after()).containsExactly(RpcCoreAutoConfiguration.class);
    }
}
