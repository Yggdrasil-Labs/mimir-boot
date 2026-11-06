package com.yggdrasil.labs.rpc.core.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** RPC 调用元数据（接口、方法、目标、协议等）。 */
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
        this.attachments =
                builder.attachments == null
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

    /** 创建复制当前元数据的构建器，附件在构建时重新生成快照。 */
    public Builder toBuilder() {
        return new Builder()
                .service(service)
                .method(method)
                .protocol(protocol)
                .target(target)
                .attachments(attachments);
    }

    /** 创建空的调用元数据构建器。 */
    public static Builder builder() {
        return new Builder();
    }

    /** 组装调用元数据，并在构建时校验服务名与方法名。 */
    public static final class Builder {
        private String service;
        private String method;
        private String protocol;
        private String target;
        private Map<String, String> attachments;

        /** 设置被调用的服务或接口名称。 */
        public Builder service(String service) {
            this.service = service;
            return this;
        }

        /** 设置被调用的方法名称。 */
        public Builder method(String method) {
            this.method = method;
            return this;
        }

        /** 设置调用协议。 */
        public Builder protocol(String protocol) {
            this.protocol = protocol;
            return this;
        }

        /** 设置调用目标地址。 */
        public Builder target(String target) {
            this.target = target;
            return this;
        }

        /** 设置附件，构建出的元数据保存其不可变副本。 */
        public Builder attachments(Map<String, String> attachments) {
            this.attachments = attachments;
            return this;
        }

        /** 校验必填信息并创建不可变调用元数据。 */
        public RpcCallMetadata build() {
            Objects.requireNonNull(service, "service must not be null");
            Objects.requireNonNull(method, "method must not be null");
            return new RpcCallMetadata(this);
        }
    }
}
