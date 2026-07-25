package com.yggdrasil.labs.rpc.core.config;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.support.RpcExecutionTemplate;
import com.yggdrasil.labs.rpc.core.tracing.NoopRpcTracerBridge;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RPC Core 自动配置行为测试。
 *
 * @author Yggdrasil Labs
 * @since 2.1.1
 */
class RpcCoreAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RpcCoreAutoConfiguration.class));

    @Test
    void defaultBeansRegistered() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(RpcCoreProperties.class);
            assertThat(context).hasSingleBean(RpcHookChain.class);
            assertThat(context).hasSingleBean(RpcTracerBridge.class);
            assertThat(context).hasSingleBean(NoopRpcTracerBridge.class);
            assertThat(context).hasSingleBean(RpcExecutionTemplate.class);

            RpcCoreProperties properties = context.getBean(RpcCoreProperties.class);
            assertThat(properties.isEnabled()).isTrue();
            assertThat(properties.isContextPropagationEnabled()).isTrue();
        });
    }

    @Test
    void beansNotRegisteredWhenDisabled() {
        runner.withPropertyValues("mimir.boot.rpc.core.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RpcCoreProperties.class);
                    assertThat(context).doesNotHaveBean(RpcHookChain.class);
                    assertThat(context).doesNotHaveBean(RpcTracerBridge.class);
                    assertThat(context).doesNotHaveBean(RpcExecutionTemplate.class);
                });
    }

    @Test
    void propertiesBoundIntoConfiguration() {
        runner.withPropertyValues(
                        "mimir.boot.rpc.core.enabled=true",
                        "mimir.boot.rpc.core.context-propagation-enabled=false")
                .run(context -> {
                    RpcCoreProperties properties = context.getBean(RpcCoreProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.isContextPropagationEnabled()).isFalse();
                    assertThat(context).hasSingleBean(RpcExecutionTemplate.class);
                });
    }

    @Test
    void customTracerBridgeOverridesDefault() {
        RpcTracerBridge customTracerBridge = new RpcTracerBridge() {
            @Override
            public Map<String, String> inject(RpcCallContext context) {
                return Map.of();
            }

            @Override
            public void extract(RpcCallContext context, Map<String, String> carrier) {
                // 测试替代实现无需提取上下文
            }
        };

        runner.withBean(RpcTracerBridge.class, () -> customTracerBridge)
                .run(context -> {
                    assertThat(context).hasSingleBean(RpcTracerBridge.class);
                    assertThat(context.getBean(RpcTracerBridge.class)).isSameAs(customTracerBridge);
                    assertThat(context).doesNotHaveBean(NoopRpcTracerBridge.class);
                });
    }
}
