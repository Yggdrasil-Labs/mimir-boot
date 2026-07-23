package com.yggdrasil.labs.rpc.feign.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.yggdrasil.labs.rpc.core.config.RpcCoreAutoConfiguration;
import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.hook.RpcHook;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import feign.Client;
import feign.Request;
import feign.Response;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;

class FeignAutoConfigurationEndToEndTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    RpcCoreAutoConfiguration.class,
                    FeignAutoConfiguration.class,
                    org.springframework.cloud.openfeign.FeignAutoConfiguration.class))
            .withUserConfiguration(FeignClientTestConfiguration.class);

    @Test
    void shouldDecorateCustomClientWithoutBypassingIt() {
        runner.run(context -> {
            context.getBean(EchoClient.class).ping();

            assertEquals(1, context.getBean(RecordingClient.class).invocations.get());
            assertEquals(1, context.getBean(RecordingRpcHook.class).beforeInvocations.get());
        });
    }

    @FeignClient(name = "rpc-feign-capability-test", url = "http://example.test")
    interface EchoClient {

        @GetMapping("/ping")
        void ping();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = EchoClient.class)
    static class FeignClientTestConfiguration {

        @Bean
        RecordingClient recordingClient() {
            return new RecordingClient();
        }

        @Bean
        RecordingRpcHook recordingRpcHook() {
            return new RecordingRpcHook();
        }

        @Bean
        RpcTracerBridge rpcTracerBridge() {
            return new RpcTracerBridge() {
                @Override
                public Map<String, String> inject(RpcCallContext context) {
                    return Collections.emptyMap();
                }

                @Override
                public void extract(RpcCallContext context, Map<String, String> carrier) {
                    // Feign 仅作为出站调用方注入上下文。
                }
            };
        }
    }

    static class RecordingClient implements Client {

        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public Response execute(Request request, Request.Options options) throws IOException {
            invocations.incrementAndGet();
            return Response.builder()
                    .request(request)
                    .status(204)
                    .reason("No Content")
                    .headers(Map.of())
                    .build();
        }
    }

    static class RecordingRpcHook implements RpcHook {

        private final AtomicInteger beforeInvocations = new AtomicInteger();

        @Override
        public void before(RpcCallContext context) {
            beforeInvocations.incrementAndGet();
        }
    }
}
