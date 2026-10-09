package com.yggdrasil.labs.nacos.config;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration;
import org.springframework.cloud.autoconfigure.RefreshAutoConfiguration.RefreshProperties;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.cloud.context.scope.refresh.RefreshScope;
import org.springframework.cloud.util.ConditionalOnBootstrapDisabled;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.context.annotation.Role;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.MethodMetadata;

import com.alibaba.cloud.nacos.configdata.NacosConfigDataLocationResolver;

/** 为默认 ConfigData 刷新链补充 Nacos 属性源删除与解密差异同步。 */
@AutoConfiguration(after = RefreshAutoConfiguration.class)
@ConditionalOnClass(NacosConfigDataLocationResolver.class)
@ConditionalOnBootstrapDisabled
@ConditionalOnProperty(
        name = "spring.cloud.refresh.enabled",
        havingValue = "true",
        matchIfMissing = true)
@Import(NacosRefreshAutoConfiguration.RefresherRegistrar.class)
public class NacosRefreshAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static RefresherFactory nacosConfigDataRefresherFactory(
            ConfigurableApplicationContext context) {
        return new RefresherFactory(context);
    }

    static final class RefresherRegistrar implements ImportBeanDefinitionRegistrar {

        @Override
        public void registerBeanDefinitions(
                AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            if (!(registry instanceof ConfigurableListableBeanFactory beanFactory)) {
                return;
            }
            String[] names = beanFactory.getBeanNamesForType(ContextRefresher.class, false, false);
            if (names.length != 1 || !beanFactory.containsBeanDefinition(names[0])) {
                return;
            }
            BeanDefinition definition = beanFactory.getBeanDefinition(names[0]);
            if (!(definition instanceof AnnotatedBeanDefinition annotated)
                    || !(definition instanceof RootBeanDefinition root)
                    || root.getInstanceSupplier() != null) {
                return;
            }
            MethodMetadata factoryMethod = annotated.getFactoryMethodMetadata();
            if (factoryMethod == null
                    || !RefreshAutoConfiguration.class
                            .getName()
                            .equals(factoryMethod.getDeclaringClassName())
                    || !"configDataContextRefresher".equals(factoryMethod.getMethodName())) {
                return;
            }

            // 在后续配置条件评估前声明子类，保留 Cloud 原工厂元数据和 Bean 生命周期。
            root.setTargetType(NacosConfigDataContextRefresher.class);
            root.setInstanceSupplier(
                    () -> beanFactory.getBean(RefresherFactory.class).createRefresher());
            beanFactory.clearMetadataCache();
        }
    }

    private record RefresherFactory(ConfigurableApplicationContext context) {

        NacosConfigDataContextRefresher createRefresher() {
            // 依赖只在默认刷新器的正常实例化阶段取得，排除 Cloud 时没有硬依赖。
            return new NacosConfigDataContextRefresher(
                    context,
                    context.getBean(RefreshScope.class),
                    context.getBean(RefreshProperties.class));
        }
    }
}
