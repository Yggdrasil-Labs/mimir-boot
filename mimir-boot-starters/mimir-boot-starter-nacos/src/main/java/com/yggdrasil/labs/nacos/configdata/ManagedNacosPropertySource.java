package com.yggdrasil.labs.nacos.configdata;

import java.util.Objects;

import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.core.env.EnumerablePropertySource;

/** 保留原值与枚举能力，同时明确记录加载成功的 Nacos 资源归属。 */
public final class ManagedNacosPropertySource
        extends EnumerablePropertySource<EnumerablePropertySource<?>>
        implements OriginLookup<String> {

    private final NacosConfigDataResourceKey resourceKey;

    public ManagedNacosPropertySource(
            NacosConfigDataResourceKey resourceKey, EnumerablePropertySource<?> delegate) {
        super(delegate.getName(), delegate);
        this.resourceKey = Objects.requireNonNull(resourceKey, "Nacos 资源归属不能为空");
    }

    public NacosConfigDataResourceKey resourceKey() {
        return resourceKey;
    }

    @Override
    public String[] getPropertyNames() {
        return source.getPropertyNames();
    }

    @Override
    public Object getProperty(String name) {
        return source.getProperty(name);
    }

    @Override
    public Origin getOrigin(String name) {
        return OriginLookup.getOrigin(source, name);
    }

    @Override
    public boolean isImmutable() {
        return source instanceof OriginLookup<?> lookup && lookup.isImmutable();
    }

    @Override
    public String getPrefix() {
        return source instanceof OriginLookup<?> lookup ? lookup.getPrefix() : null;
    }
}
