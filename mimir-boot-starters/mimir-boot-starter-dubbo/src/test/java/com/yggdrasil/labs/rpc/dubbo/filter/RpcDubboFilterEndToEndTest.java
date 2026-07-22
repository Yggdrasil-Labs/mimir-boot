package com.yggdrasil.labs.rpc.dubbo.filter;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.hook.RpcHookChain;
import com.yggdrasil.labs.rpc.core.tracing.RpcTracerBridge;
import com.yggdrasil.labs.rpc.dubbo.config.DubboProperties;
import com.yggdrasil.labs.rpc.dubbo.support.RpcDubboSupportHolder;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals(1, tracerBridge.extractedCarriers().size());
        assertEquals("consumer-trace", tracerBridge.extractedCarriers().get(0).get("x-trace-id"));
    }

    interface EchoService {

        String echo(String value);
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
