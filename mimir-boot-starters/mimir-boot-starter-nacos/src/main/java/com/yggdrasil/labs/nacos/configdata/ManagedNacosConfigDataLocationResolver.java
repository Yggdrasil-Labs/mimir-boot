package com.yggdrasil.labs.nacos.configdata;

import java.util.List;

import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;

import com.alibaba.cloud.nacos.configdata.NacosConfigDataLocationResolver;
import com.alibaba.cloud.nacos.configdata.NacosConfigDataResource;

/** 委托 Alibaba 解析 nacos:，保留原 import 参数与 Bootstrap 注册流程。 */
public final class ManagedNacosConfigDataLocationResolver
        implements ConfigDataLocationResolver<ManagedNacosConfigDataResource>, Ordered {

    private final NacosConfigDataLocationResolver delegate;

    public ManagedNacosConfigDataLocationResolver(DeferredLogFactory logFactory) {
        delegate = new NacosConfigDataLocationResolver(logFactory);
    }

    @Override
    public int getOrder() {
        return -2;
    }

    @Override
    public boolean isResolvable(
            ConfigDataLocationResolverContext context, ConfigDataLocation location) {
        return delegate.isResolvable(context, location);
    }

    @Override
    public List<ManagedNacosConfigDataResource> resolve(
            ConfigDataLocationResolverContext context, ConfigDataLocation location) {
        return wrap(delegate.resolve(context, location));
    }

    @Override
    public List<ManagedNacosConfigDataResource> resolveProfileSpecific(
            ConfigDataLocationResolverContext context,
            ConfigDataLocation location,
            Profiles profiles) {
        return wrap(delegate.resolveProfileSpecific(context, location, profiles));
    }

    private List<ManagedNacosConfigDataResource> wrap(List<NacosConfigDataResource> resources) {
        return resources.stream().map(ManagedNacosConfigDataResource::new).toList();
    }
}
