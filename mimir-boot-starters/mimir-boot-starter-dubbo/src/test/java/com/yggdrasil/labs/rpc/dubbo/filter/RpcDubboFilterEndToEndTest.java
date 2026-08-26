package com.yggdrasil.labs.rpc.dubbo.filter;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import com.yggdrasil.labs.rpc.core.hook.RpcHook;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.MdcRpcTracerBridge;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.rpc.dubbo.config.DubboProperties;
import com.yggdrasil.labs.rpc.dubbo.support.RpcDubboSupportHolder;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RpcDubboFilterEndToEndTest {

    private DubboBootstrap bootstrap;

    @AfterEach
    void tearDown() {
        if (bootstrap != null) {
            bootstrap.stop();
        }
        RpcDubboSupportHolder.set(null, null, null);
    }

    @Test
    void shouldPropagateTraceFromConsumerToProvider() {
        RecordingTracerBridge tracerBridge = new RecordingTracerBridge();
        DubboProperties properties = new DubboProperties();
        properties.setEnabled(true);
        properties.setContextPropagationEnabled(true);
        RpcDubboSupportHolder.set(new RpcHookChain(List.of()), tracerBridge, properties);

        ProtocolConfig protocol = new ProtocolConfig("injvm");
        ServiceConfig<EchoService> service = new ServiceConfig<>();
        service.setInterface(EchoService.class);
        service.setRef(value -> "echo:" + value);
        service.setProtocol(protocol);
        service.setFilter("rpcDubboFilter");

        ReferenceConfig<EchoService> reference = new ReferenceConfig<>();
        reference.setInterface(EchoService.class);
        reference.setInjvm(true);
        reference.setCheck(true);
        reference.setFilter("rpcDubboFilter");

        bootstrap = DubboBootstrap.newInstance();
        bootstrap.application(new ApplicationConfig("rpc-dubbo-filter-integration-test"))
                .protocol(protocol)
                .service(service)
                .reference(reference)
                .start();

        assertEquals("echo:hello", reference.get().echo("hello"));
        assertEquals(List.of(Map.of("x-trace-id", "consumer-trace")), tracerBridge.injectedCarriers());
        assertEquals(2, tracerBridge.extractedCarriers().size());
        assertEquals("consumer-trace", tracerBridge.extractedCarriers().get(0).get("x-trace-id"));
        assertEquals("consumer-trace", tracerBridge.extractedCarriers().get(1).get("x-trace-id"));
    }

    @Test
    void defaultMdcBridgePropagatesAndRestoresProviderContext() {
        DubboProperties properties = new DubboProperties();
        properties.setEnabled(true);
        properties.setContextPropagationEnabled(true);
        RpcDubboSupportHolder.set(new RpcHookChain(List.of()), new MdcRpcTracerBridge(), properties);
        MdcRecordingEchoService serviceImplementation = new MdcRecordingEchoService();

        ProtocolConfig protocol = new ProtocolConfig("injvm");
        ServiceConfig<EchoService> service = new ServiceConfig<>();
        service.setInterface(EchoService.class);
        service.setRef(serviceImplementation);
        service.setProtocol(protocol);
        service.setFilter("rpcDubboFilter");

        ReferenceConfig<EchoService> reference = new ReferenceConfig<>();
        reference.setInterface(EchoService.class);
        reference.setInjvm(true);
        reference.setCheck(true);
        reference.setFilter("rpcDubboFilter");

        bootstrap = DubboBootstrap.newInstance();
        bootstrap.application(new ApplicationConfig("rpc-dubbo-mdc-integration-test"))
                .protocol(protocol)
                .service(service)
                .reference(reference)
                .start();

        MDC.put(CommonConstants.TRACE_ID, "consumer-trace-id");
        MDC.put(CommonConstants.REQUEST_ID, "consumer-request-id");
        try {
            assertEquals("echo:hello", reference.get().echo("hello"));
            assertEquals("consumer-trace-id", serviceImplementation.traceId());
            assertEquals("consumer-request-id", serviceImplementation.requestId());
            assertEquals("consumer-trace-id", MDC.get(CommonConstants.TRACE_ID));
            assertEquals("consumer-request-id", MDC.get(CommonConstants.REQUEST_ID));
        } finally {
            MDC.clear();
        }
    }

    @Test
    void defaultMdcBridgeRestoresTraceContextForAsyncProviderCompletion() throws Exception {
        DubboProperties properties = new DubboProperties();
        properties.setEnabled(true);
        properties.setContextPropagationEnabled(true);
        AsyncCompletionHook hook = new AsyncCompletionHook();
        RpcDubboSupportHolder.set(new RpcHookChain(List.of(hook)), new MdcRpcTracerBridge(), properties);
        ExecutorService completionWorker = Executors.newSingleThreadExecutor();
        AtomicReference<CompletableFuture<String>> responseFuture = new AtomicReference<>();
        AsyncEchoService serviceImplementation = value -> {
            CompletableFuture<String> future = new CompletableFuture<>();
            responseFuture.set(future);
            return future;
        };

        ProtocolConfig protocol = new ProtocolConfig("injvm");
        ServiceConfig<AsyncEchoService> service = new ServiceConfig<>();
        service.setInterface(AsyncEchoService.class);
        service.setRef(serviceImplementation);
        service.setProtocol(protocol);
        service.setFilter("rpcDubboFilter");

        ReferenceConfig<AsyncEchoService> reference = new ReferenceConfig<>();
        reference.setInterface(AsyncEchoService.class);
        reference.setInjvm(true);
        reference.setCheck(true);
        reference.setFilter("rpcDubboFilter");

        bootstrap = DubboBootstrap.newInstance();
        bootstrap.application(new ApplicationConfig("rpc-dubbo-mdc-async-integration-test"))
                .protocol(protocol)
                .service(service)
                .reference(reference)
                .start();

        try {
            completionWorker.submit(() -> {
                MDC.put(CommonConstants.TRACE_ID, "worker-trace-id");
                MDC.put(CommonConstants.REQUEST_ID, "worker-request-id");
            }).get(5, TimeUnit.SECONDS);
            MDC.put(CommonConstants.TRACE_ID, "consumer-async-trace-id");
            MDC.put(CommonConstants.REQUEST_ID, "consumer-async-request-id");

            CompletableFuture<String> rpcResponse = reference.get().echoAsync("hello");
            completionWorker.submit(() -> responseFuture.get().complete("echo:hello")).get(5, TimeUnit.SECONDS);

            assertEquals("echo:hello", rpcResponse.get(5, TimeUnit.SECONDS));
            assertTrue(hook.awaitProviderCompletion(), "未在异步完成回调中观察到 Provider Trace 上下文");
            assertEquals("consumer-async-trace-id", hook.traceId());
            assertEquals("consumer-async-request-id", hook.requestId());
            assertEquals("consumer-async-trace-id", MDC.get(CommonConstants.TRACE_ID));
            assertEquals("consumer-async-request-id", MDC.get(CommonConstants.REQUEST_ID));
            assertEquals("worker-trace-id", completionWorker
                    .submit(() -> MDC.get(CommonConstants.TRACE_ID))
                    .get(5, TimeUnit.SECONDS));
            assertEquals("worker-request-id", completionWorker
                    .submit(() -> MDC.get(CommonConstants.REQUEST_ID))
                    .get(5, TimeUnit.SECONDS));
        } finally {
            MDC.clear();
            completionWorker.shutdownNow();
        }
    }

    interface EchoService {

        String echo(String value);
    }

    interface AsyncEchoService {

        CompletableFuture<String> echoAsync(String value);
    }

    private static final class AsyncCompletionHook implements RpcHook {

        private final CountDownLatch providerCompletion = new CountDownLatch(1);
        private final AtomicReference<String> traceId = new AtomicReference<>();
        private final AtomicReference<String> requestId = new AtomicReference<>();

        @Override
        public void after(RpcCallContext context, RpcCallResult result) {
            String currentTraceId = MDC.get(CommonConstants.TRACE_ID);
            if ("consumer-async-trace-id".equals(currentTraceId)) {
                traceId.set(currentTraceId);
                requestId.set(MDC.get(CommonConstants.REQUEST_ID));
                providerCompletion.countDown();
            }
        }

        boolean awaitProviderCompletion() throws InterruptedException {
            return providerCompletion.await(5, TimeUnit.SECONDS);
        }

        String traceId() {
            return traceId.get();
        }

        String requestId() {
            return requestId.get();
        }
    }

    private static final class MdcRecordingEchoService implements EchoService {

        private String traceId;
        private String requestId;

        @Override
        public String echo(String value) {
            traceId = MDC.get(CommonConstants.TRACE_ID);
            requestId = MDC.get(CommonConstants.REQUEST_ID);
            return "echo:" + value;
        }

        String traceId() {
            return traceId;
        }

        String requestId() {
            return requestId;
        }
    }

    private static final class RecordingTracerBridge implements RpcTracerBridge {

        private final List<Map<String, String>> injectedCarriers = new ArrayList<>();
        private final List<Map<String, String>> extractedCarriers = new ArrayList<>();

        @Override
        public Map<String, String> inject(RpcCallContext context) {
            Map<String, String> carrier = Map.of("x-trace-id", "consumer-trace");
            injectedCarriers.add(carrier);
            return carrier;
        }

        @Override
        public void extract(RpcCallContext context, Map<String, String> carrier) {
            extractedCarriers.add(new LinkedHashMap<>(carrier));
        }

        List<Map<String, String>> injectedCarriers() {
            return injectedCarriers;
        }

        List<Map<String, String>> extractedCarriers() {
            return extractedCarriers;
        }
    }
}
