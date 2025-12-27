package com.yggdrasil.labs.rpc.core.context;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RpcCallMetadataTest {

    @Test
    void shouldBuildMetadataAndCopyAttachments() {
        RpcCallMetadata metadata = RpcCallMetadata.builder()
                .service("svc")
                .method("m1")
                .protocol("grpc")
                .target("127.0.0.1:8080")
                .attachments(Map.of("k1", "v1"))
                .build();

        Assertions.assertEquals("svc", metadata.getService());
        Assertions.assertEquals("m1", metadata.getMethod());
        Assertions.assertEquals("grpc", metadata.getProtocol());
        Assertions.assertEquals("127.0.0.1:8080", metadata.getTarget());
        Assertions.assertEquals("v1", metadata.getAttachments().get("k1"));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> metadata.getAttachments().put("k2", "v2"));
    }

    @Test
    void shouldCloneWithBuilder() {
        RpcCallMetadata metadata = RpcCallMetadata.builder()
                .service("svc")
                .method("m1")
                .protocol("grpc")
                .target("tgt")
                .attachments(Map.of("k1", "v1"))
                .build();

        RpcCallMetadata cloned = metadata.toBuilder().build();

        Assertions.assertEquals(metadata.getService(), cloned.getService());
        Assertions.assertEquals(metadata.getMethod(), cloned.getMethod());
        Assertions.assertEquals(metadata.getProtocol(), cloned.getProtocol());
        Assertions.assertEquals(metadata.getTarget(), cloned.getTarget());
        Assertions.assertEquals(metadata.getAttachments(), cloned.getAttachments());
    }

    @Test
    void shouldValidateRequiredFields() {
        Assertions.assertThrows(NullPointerException.class, () -> RpcCallMetadata.builder().method("m1").build());
        Assertions.assertThrows(NullPointerException.class, () -> RpcCallMetadata.builder().service("svc").build());
    }
}
