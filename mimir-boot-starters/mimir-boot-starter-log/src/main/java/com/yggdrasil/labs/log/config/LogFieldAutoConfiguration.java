package com.yggdrasil.labs.log.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import com.yggdrasil.labs.log.field.LogFieldProvider;
import com.yggdrasil.labs.log.json.LogFieldProviderRegistry;
import com.yggdrasil.labs.log.json.LogFieldProviderRegistry.NamedProvider;

import ch.qos.logback.classic.LoggerContext;

/** 主应用上下文刷新后发布日志字段扩展，关闭开始时清空。 */
@AutoConfiguration
@ConditionalOnClass(LoggerContext.class)
public class LogFieldAutoConfiguration implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /** 只发布本上下文的扩展；普通子上下文及 legacy bootstrap 上下文均不发布。 */
    @EventListener(ContextRefreshedEvent.class)
    public void registerProviders(ContextRefreshedEvent event) {
        ApplicationContext refreshed = event.getApplicationContext();
        if (!isOwnContext(refreshed)
                || LogPresetSupport.isBootstrapEnvironment(refreshed.getEnvironment())
                || !isMainApplication(refreshed)) {
            return;
        }
        ConfigurableListableBeanFactory factory =
                ((ConfigurableApplicationContext) refreshed).getBeanFactory();
        List<NamedProvider> providers = new ArrayList<>();
        String[] names = refreshed.getBeanNamesForType(LogFieldProvider.class);
        Arrays.sort(names);
        Map<Object, String> beanNames = new IdentityHashMap<>();
        for (String name : names) {
            LogFieldProvider provider = refreshed.getBean(name, LogFieldProvider.class);
            providers.add(new NamedProvider(name, provider));
            beanNames.put(provider, name);
        }
        OrderComparator order =
                factory instanceof DefaultListableBeanFactory listable
                                && listable.getDependencyComparator()
                                        instanceof OrderComparator configured
                        ? configured
                        : AnnotationAwareOrderComparator.INSTANCE;
        // 与 Spring orderedStream 相同的工厂方法、目标类型及 order 属性来源。
        var comparator = order.withSourceProvider(bean -> orderSources(factory, beanNames, bean));
        // 稳定排序保留同序 Bean 名升序。
        providers.sort((left, right) -> comparator.compare(left.provider(), right.provider()));
        LogFieldProviderRegistry.register(refreshed, providers);
    }

    /** 所有权检查阻止子上下文的关闭事件清空父应用注册表。 */
    @EventListener(ContextClosedEvent.class)
    public void clearProviders(ContextClosedEvent event) {
        if (isOwnContext(event.getApplicationContext())) {
            LogFieldProviderRegistry.clear(applicationContext);
        }
    }

    // 只接收本上下文实例的事件；相同 id 或 equals 结果不能代表同一生命周期。
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    private boolean isOwnContext(ApplicationContext context) {
        return context == applicationContext;
    }

    private static boolean isMainApplication(ApplicationContext context) {
        ApplicationContext parent = context.getParent();
        return parent == null || "bootstrap".equals(parent.getId());
    }

    private static Object orderSources(
            ConfigurableListableBeanFactory factory, Map<Object, String> beanNames, Object bean) {
        String name = beanNames.get(bean);
        if (name == null) {
            return null;
        }
        try {
            if (!(factory.getMergedBeanDefinition(name) instanceof RootBeanDefinition definition)) {
                return null;
            }
            List<Object> sources = new ArrayList<>();
            Object order = definition.getAttribute(AbstractBeanDefinition.ORDER_ATTRIBUTE);
            if (order instanceof Integer priority) {
                sources.add((Ordered) () -> priority);
            }
            if (definition.getResolvedFactoryMethod() != null) {
                sources.add(definition.getResolvedFactoryMethod());
            }
            Class<?> targetType = definition.getTargetType();
            if (targetType != null && targetType != bean.getClass()) {
                sources.add(targetType);
            }
            return sources.toArray();
        } catch (NoSuchBeanDefinitionException ex) {
            return null;
        }
    }
}
