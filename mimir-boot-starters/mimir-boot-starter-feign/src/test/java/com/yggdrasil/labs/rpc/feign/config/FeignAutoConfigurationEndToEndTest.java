package com.yggdrasil.labs.rpc.feign.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;

import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.rpc.core.config.RpcCoreAutoConfiguration;
import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.MdcRpcTracerBridge;
import com.yggdrasil.labs.rpc.core.hook.RpcHook;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.feign.client.RpcFeignClient;
import com.yggdrasil.labs.web.config.WebAutoConfiguration;
import feign.Client;
import feign.Request;
import feign.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @Test
    void defaultBridgePropagatesMdcThroughFeignToWebInterceptor() throws Exception {
        try (ConfigurableApplicationContext webContext = new SpringApplicationBuilder(WebEndpointConfiguration.class)
                .web(WebApplicationType.SERVLET)
                .properties("server.port=0")
                .run()) {
            int port = ((WebServerApplicationContext) webContext).getWebServer().getPort();
            FeignProperties properties = new FeignProperties();
            MDC.put(CommonConstants.TRACE_ID, "feign-trace-id");
            MDC.put(CommonConstants.REQUEST_ID, "feign-request-id");
            try {
                Request request = Request.create(
                        Request.HttpMethod.GET,
                        "http://127.0.0.1:" + port + "/trace",
                        Map.of(),
                        null,
                        StandardCharsets.UTF_8,
                        null);
                RpcFeignClient client = new RpcFeignClient(
                        new Client.Default(null, null), new RpcHookChain(Collections.emptyList()), new MdcRpcTracerBridge(), properties);

                Response response = client.execute(request, new Request.Options());

                assertThat(response.status()).isEqualTo(200);
                assertThat(webContext.getBean(TraceEndpoint.class).traceId()).isEqualTo("feign-trace-id");
                assertThat(webContext.getBean(TraceEndpoint.class).requestId()).isEqualTo("feign-request-id");
            } finally {
                MDC.clear();
            }
        }
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

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({WebAutoConfiguration.class, TraceEndpoint.class})
    static class WebEndpointConfiguration {
    }

    @RestController
    static class TraceEndpoint {

        private volatile String traceId;
        private volatile String requestId;

        @GetMapping("/trace")
        String trace() {
            traceId = MDC.get(CommonConstants.TRACE_ID);
            requestId = MDC.get(CommonConstants.REQUEST_ID);
            return "ok";
        }

        String traceId() {
            return traceId;
        }

        String requestId() {
            return requestId;
        }
    }
}
