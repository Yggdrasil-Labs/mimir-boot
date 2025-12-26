package com.yggdrasil.labs.rpc.core.hook;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

class RpcHookChainTest {

    @Test
    void shouldInvokeHooksInOrder() {
        RecordingHook first = new RecordingHook(1);
        RecordingHook second = new RecordingHook(2);

        RpcHookChain chain = new RpcHookChain(List.of(second, first)); // intentionally shuffled
        RpcCallContext context = RpcCallContext.create(
                RpcCallMetadata.builder().service("svc").method("m1").build());
        RpcCallResult result = RpcCallResult.success(Duration.ofMillis(5));

        chain.before(context);
        chain.after(context, result);
        chain.onError(context, RpcCallResult.failure(Duration.ofMillis(5), new RuntimeException("boom")));
        chain.cleanup(context);

        Assertions.assertEquals("beforeafteronErrorcleanup", first.order.toString());
        Assertions.assertEquals("beforeafteronErrorcleanup", second.order.toString());
        Assertions.assertTrue(first.invokedBeforeSecond(second));
    }

    private static class RecordingHook implements RpcHook {
        private final int orderValue;
        private final StringBuilder order = new StringBuilder();

        RecordingHook(int orderValue) {
            this.orderValue = orderValue;
        }

        @Override
        public void before(RpcCallContext context) {
            order.append("before");
        }

        @Override
        public void after(RpcCallContext context, RpcCallResult result) {
            order.append("after");
        }

        @Override
        public void onError(RpcCallContext context, RpcCallResult result) {
            order.append("onError");
        }

        @Override
        public void cleanup(RpcCallContext context) {
            order.append("cleanup");
        }

        boolean invokedBeforeSecond(RecordingHook other) {
            return this.getOrder() < other.getOrder();
        }

        @Override
        public int getOrder() {
            return orderValue;
        }
    }
}

