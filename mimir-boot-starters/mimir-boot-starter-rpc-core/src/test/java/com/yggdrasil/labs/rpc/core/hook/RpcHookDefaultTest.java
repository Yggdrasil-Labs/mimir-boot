package com.yggdrasil.labs.rpc.core.hook;

import com.yggdrasil.labs.rpc.core.context.RpcCallContext;
import com.yggdrasil.labs.rpc.core.context.RpcCallMetadata;
import com.yggdrasil.labs.rpc.core.context.RpcCallResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;

class RpcHookDefaultTest {

    @Test
    void defaultImplementationsShouldBeNoopAndLowestPrecedence() {
        RpcHook hook = new RpcHook() {};
        RpcCallContext context = RpcCallContext.create(
                RpcCallMetadata.builder().service("svc").method("m1").build());
        RpcCallResult result = RpcCallResult.success(java.time.Duration.ZERO);

        Assertions.assertDoesNotThrow(() -> {
            hook.before(context);
            hook.after(context, result);
            hook.onError(context, result);
            hook.cleanup(context);
        });
        Assertions.assertEquals(Ordered.LOWEST_PRECEDENCE, hook.getOrder());
    }
}
