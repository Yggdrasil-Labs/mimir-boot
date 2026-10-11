package com.yggdrasil.labs.nacos.configdata;

import org.springframework.boot.context.config.ConfigDataResource;

import com.alibaba.cloud.nacos.configdata.NacosConfigDataResource;

/** 独立资源类型避免原 Nacos loader 与本适配器同时匹配。 */
final class ManagedNacosConfigDataResource extends ConfigDataResource {

    private final NacosConfigDataResource delegate;

    ManagedNacosConfigDataResource(NacosConfigDataResource delegate) {
        super(delegate.isOptional());
        this.delegate = delegate;
    }

    NacosConfigDataResource delegate() {
        return delegate;
    }

    NacosConfigDataResourceKey resourceKey() {
        return new NacosConfigDataResourceKey(
                delegate.getProperties().getServerAddr(),
                delegate.getProperties().getNamespace(),
                delegate.getConfig().getGroup(),
                delegate.getConfig().getDataId());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ManagedNacosConfigDataResource resource
                && delegate.equals(resource.delegate);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public String toString() {
        return "Nacos 配置资源 " + resourceKey();
    }
}
