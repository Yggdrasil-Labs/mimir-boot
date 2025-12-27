package com.yggdrasil.labs.rpc.core.context;

import java.time.Duration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RpcCallResultTest {

    @Test
    void shouldRepresentSuccess() {
        Duration duration = Duration.ofMillis(10);
        RpcCallResult result = RpcCallResult.success(duration);

        Assertions.assertTrue(result.isSuccess());
        Assertions.assertEquals(duration, result.getDuration());
        Assertions.assertTrue(result.getError().isEmpty());
    }

    @Test
    void shouldRepresentFailure() {
        Duration duration = Duration.ofMillis(5);
        RuntimeException error = new RuntimeException("boom");
        RpcCallResult result = RpcCallResult.failure(duration, error);

        Assertions.assertFalse(result.isSuccess());
        Assertions.assertEquals(duration, result.getDuration());
        Assertions.assertSame(error, result.getError().orElseThrow());
    }
}
