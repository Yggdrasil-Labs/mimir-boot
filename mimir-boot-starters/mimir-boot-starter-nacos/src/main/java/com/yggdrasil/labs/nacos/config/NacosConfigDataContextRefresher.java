package com.yggdrasil.labs.nacos.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration.RefreshProperties;
import org.springframework.cloud.context.refresh.ConfigDataContextRefresher;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

import com.yggdrasil.labs.nacos.configdata.ManagedNacosPropertySource;
import com.yggdrasil.labs.nacos.configdata.NacosConfigDataResourceKey;
import com.yggdrasil.labs.nacos.decrypt.ConfigDecryptProcessor;

/** 在 Cloud 计算环境差异前同步 Nacos 源与解密覆盖层，保留完整刷新流程。 */
public class NacosConfigDataContextRefresher extends ConfigDataContextRefresher {

    private static final String RELOAD_STATE = NacosConfigDataContextRefresher.class.getName();
    private StandardEnvironment reloadedEnvironment;

    public NacosConfigDataContextRefresher(
            ConfigurableApplicationContext context,
            RefreshScope scope,
            RefreshProperties properties) {
        super(context, scope, properties);
    }

    @Override
    protected StandardEnvironment copyEnvironment(ConfigurableEnvironment input) {
        reloadedEnvironment = super.copyEnvironment(input);
        Map<String, PropertySource<?>> previous = new LinkedHashMap<>();
        input.getPropertySources().forEach(source -> previous.put(source.getName(), source));
        reloadedEnvironment.getPropertySources().addLast(new ReloadState(previous));
        return reloadedEnvironment;
    }

    @Override
    protected void updateEnvironment() {
        ConfigurableEnvironment environment = getContext().getEnvironment();
        List<PropertySource<?>> previous = new ArrayList<>();
        environment.getPropertySources().forEach(previous::add);
        try {
            super.updateEnvironment();
            Map<String, PropertySource<?>> previousByName = new LinkedHashMap<>();
            previous.forEach(source -> previousByName.put(source.getName(), source));
            reconcileNacosSources(environment, reloadedEnvironment, previousByName);
            if (NacosEncryptPropertiesResolver.isAnyPrefixBound(environment)) {
                NacosEncryptProperties properties =
                        NacosEncryptPropertiesResolver.resolve(
                                environment, new NacosEncryptProperties());
                new ConfigDecryptProcessor(properties).process(environment);
            } else {
                ConfigDecryptProcessor.clearDecryptedPropertySources(environment);
            }
        } catch (RuntimeException | Error failure) {
            restorePropertySources(environment.getPropertySources(), previous);
            throw failure;
        } finally {
            reloadedEnvironment = null;
        }
    }

    static void reconcileNacosSources(
            ConfigurableEnvironment environment,
            ConfigurableEnvironment reloaded,
            Map<String, PropertySource<?>> previous) {
        Set<NacosConfigDataResourceKey> loadedResources = new HashSet<>();
        Map<NacosConfigDataResourceKey, Set<String>> loadedNames = new LinkedHashMap<>();
        for (PropertySource<?> source : reloaded.getPropertySources()) {
            if (source instanceof ManagedNacosPropertySource managed
                    && previous.get(source.getName()) != source) {
                loadedResources.add(managed.resourceKey());
                loadedNames
                        .computeIfAbsent(managed.resourceKey(), ignored -> new HashSet<>())
                        .add(source.getName());
            }
        }
        List<String> obsolete = new ArrayList<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof ManagedNacosPropertySource managed
                    && loadedResources.contains(managed.resourceKey())
                    && !loadedNames.get(managed.resourceKey()).contains(source.getName())) {
                obsolete.add(source.getName());
            }
        }
        obsolete.forEach(environment.getPropertySources()::remove);
    }

    /** 在副本解密前排除同资源已被新加载结果替代的旧保留文档，不暴露内部状态源。 */
    static void prepareReloadedEnvironment(ConfigurableEnvironment environment) {
        PropertySource<?> source = environment.getPropertySources().get(RELOAD_STATE);
        if (source instanceof ReloadState state) {
            reconcileNacosSources(environment, environment, state.getSource());
            environment.getPropertySources().remove(RELOAD_STATE);
        }
    }

    private static final class ReloadState extends PropertySource<Map<String, PropertySource<?>>> {

        private ReloadState(Map<String, PropertySource<?>> previous) {
            super(RELOAD_STATE, previous);
        }

        @Override
        public Object getProperty(String name) {
            return null;
        }
    }

    private static void restorePropertySources(
            MutablePropertySources sources, List<PropertySource<?>> previous) {
        List<String> currentNames = new ArrayList<>();
        sources.forEach(source -> currentNames.add(source.getName()));
        currentNames.forEach(sources::remove);
        previous.forEach(sources::addLast);
    }
}
