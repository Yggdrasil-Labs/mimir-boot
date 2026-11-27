package com.yggdrasil.labs.rpc.core.context;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

class RpcCallContextTest {

    @Test
    void shouldCreateContextWithImmutableAttachmentsAndAddNewOnes() {
        Map<String, String> original = new HashMap<>();
        original.put("k1", "v1");

        RpcCallMetadata metadata =
                RpcCallMetadata.builder().service("svc").method("m1").attachments(original).build();
        Instant startTime = Instant.parse("2026-11-27T12:00:00Z");
        RpcCallContext context;
        try (MockedStatic<Instant> clock = Mockito.mockStatic(Instant.class)) {
            clock.when(Instant::now).thenReturn(startTime);
            context = RpcCallContext.create(metadata);
        }

        Assertions.assertEquals(metadata, context.getMetadata());
        Assertions.assertEquals(startTime, context.getStartTime());

        // attachments are copied and immutable from outside
        Assertions.assertEquals("v1", context.getAttachments().get("k1"));
        Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> context.getAttachments().put("k2", "v2"));

        // new attachments are stored in internal mutable map
        context.putAttachment("k3", "v3");
        Assertions.assertEquals("v3", context.getAttachments().get("k3"));
        Assertions.assertFalse(original.containsKey("k3"));
    }
}
