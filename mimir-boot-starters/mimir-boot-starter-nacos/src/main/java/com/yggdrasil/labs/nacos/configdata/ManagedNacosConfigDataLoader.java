package com.yggdrasil.labs.nacos.configdata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoader;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;

import com.alibaba.cloud.nacos.configdata.NacosConfigDataLoader;

/** 委托上游加载，成功空结果保留零键标记，optional 失败不授予删除权限。 */
public final class ManagedNacosConfigDataLoader
        implements ConfigDataLoader<ManagedNacosConfigDataResource> {

    private final NacosConfigDataLoader delegate;

    public ManagedNacosConfigDataLoader(DeferredLogFactory logFactory) {
        delegate = new NacosConfigDataLoader(logFactory);
    }

    @Override
    public ConfigData load(
            ConfigDataLoaderContext context, ManagedNacosConfigDataResource resource) {
        ConfigData loaded = delegate.load(context, resource.delegate());
        if (loaded == null) {
            return null;
        }
        List<PropertySource<?>> sources = new ArrayList<>();
        for (PropertySource<?> source : loaded.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                sources.add(new ManagedNacosPropertySource(resource.resourceKey(), enumerable));
            } else {
                // 自定义 parser 的非枚举源沿用原行为，不猜测配置键或授予删除归属。
                sources.add(source);
            }
        }
        if (sources.isEmpty()) {
            MapPropertySource empty =
                    new MapPropertySource("mimirNacosEmpty:" + resource.resourceKey(), Map.of());
            sources.add(new ManagedNacosPropertySource(resource.resourceKey(), empty));
        }
        return new ConfigData(
                sources,
                source ->
                        loaded.getOptions(
                                source instanceof ManagedNacosPropertySource managed
                                        ? managed.getSource()
                                        : source));
    }
}
