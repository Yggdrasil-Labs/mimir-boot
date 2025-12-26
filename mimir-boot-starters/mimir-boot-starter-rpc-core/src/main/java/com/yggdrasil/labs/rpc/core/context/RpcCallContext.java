package com.yggdrasil.labs.rpc.core.context;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * RPC 调用上下文，包含元数据、开始时间与可变附件。
 */
public final class RpcCallContext {

    private final RpcCallMetadata metadata;
    private final Instant startTime;
    private final Map<String, String> mutableAttachments;

    private RpcCallContext(RpcCallMetadata metadata, Instant startTime, Map<String, String> attachments) {
        this.metadata = metadata;
        this.startTime = startTime;
        this.mutableAttachments = attachments == null ? new HashMap<>() : new HashMap<>(attachments);
    }

    public static RpcCallContext create(RpcCallMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata must not be null");
        return new RpcCallContext(metadata, Instant.now(), metadata.getAttachments());
    }

    public RpcCallMetadata getMetadata() {
        return metadata;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Map<String, String> getAttachments() {
        return Collections.unmodifiableMap(mutableAttachments);
    }

    public void putAttachment(String key, String value) {
        this.mutableAttachments.put(key, value);
    }
}

