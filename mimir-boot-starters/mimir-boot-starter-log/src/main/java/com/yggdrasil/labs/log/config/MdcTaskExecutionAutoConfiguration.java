package com.yggdrasil.labs.log.config;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.thread.Threading;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Boot 默认业务执行器 MDC 传播自动配置
 *
 * <p>仅在无用户 Executor 及新/旧执行器 Builder、平台线程且显式配置 {@code mimir.boot.log.mdc.enabled=true} 时注册适配器（默认关闭，
 * 避免静默改变 Boot 默认执行器行为）；默认执行器仍由 Boot 创建管理。
 *
 * @author Yggdrasil Labs
 * @since 2.4.0
 */
@AutoConfiguration(before = TaskExecutionAutoConfiguration.class)
@ConditionalOnClass(ThreadPoolTaskExecutor.class)
@ConditionalOnProperty(prefix = "mimir.boot.log.mdc", name = "enabled", havingValue = "true")
@ConditionalOnMissingBean(Executor.class)
@EnableConfigurationProperties(MdcPropagationProperties.class)
public class MdcTaskExecutionAutoConfiguration {

    /** 旧 Builder 在 Boot 3.4 移除，按类名判断，类不存在时自动忽略 */
    @Bean
    @ConditionalOnThreading(Threading.PLATFORM)
    @ConditionalOnMissingBean(
            value = ThreadPoolTaskExecutorBuilder.class,
            type = MdcApplicationTaskExecutorPostProcessor.LEGACY_BUILDER)
    static MdcApplicationTaskExecutorPostProcessor mdcApplicationTaskExecutorPostProcessor(
            ListableBeanFactory beanFactory,
            ObjectProvider<ThreadPoolTaskExecutorCustomizer> customizers,
            ObjectProvider<TaskDecorator> taskDecorators) {
        return new MdcApplicationTaskExecutorPostProcessor(
                beanFactory, customizers, taskDecorators);
    }

    @Bean
    @ConditionalOnThreading(Threading.VIRTUAL)
    MdcVirtualThreadNotice mdcVirtualThreadNotice() {
        return new MdcVirtualThreadNotice();
    }
}
