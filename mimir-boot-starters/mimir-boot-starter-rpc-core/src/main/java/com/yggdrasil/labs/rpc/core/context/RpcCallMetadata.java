package com.yggdrasil.labs.rpc.core.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * RPC 调用元数据（接口、方法、目标、协议等）。
 */
public final class RpcCallMetadata {

    private final String service;
    private final String method;
    private final String protocol;
    private final String target;
    private final Map<String, String> attachments;

    private RpcCallMetadata(Builder builder) {
        this.service = builder.service;
        this.method = builder.method;
        this.protocol = builder.protocol;
        this.target = builder.target;
        this.attachments = builder.attachments == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(builder.attachments));
    }

    public String getService() {
        return service;
    }

    public String getMethod() {
        return method;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getTarget() {
        return target;
    }

    public Map<String, String> getAttachments() {
        return attachments;
    }

    public Builder toBuilder() {
        return new Builder()
                .service(service)
                .method(method)
                .protocol(protocol)
                .target(target)
                .attachments(attachments);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String service;
        private String method;
        private String protocol;
        private String target;
        private Map<String, String> attachments;

        public Builder service(String service) {
            this.service = service;
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        public Builder target(String target) {
            this.target = target;
            return this;
        }

        public Builder attachments(Map<String, String> attachments) {
            this.attachments = attachments;
            return this;
        }

        public RpcCallMetadata build() {
            Objects.requireNonNull(service, "service must not be null");
            Objects.requireNonNull(method, "method must not be null");
            return new RpcCallMetadata(this);
        }
    }
}

