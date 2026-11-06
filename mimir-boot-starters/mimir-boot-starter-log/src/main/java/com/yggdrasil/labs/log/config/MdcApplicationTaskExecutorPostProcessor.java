package com.yggdrasil.labs.log.config;

import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.ClassUtils;

import com.yggdrasil.labs.log.task.MdcTasks;

/**
 * 仅为 Boot 默认业务执行器 {@code applicationTaskExecutor} 接入 MDC 传播（内部实现）
 *
 * <p>在执行器初始化前按 Boot 相同的 {@code getIfUnique()} 选择业务装饰器，并以 {@link MdcTasks#compose(TaskDecorator)}
 * 组合。存在任何其他新/旧执行器定制器时输出一条 INFO 并退让。只处理该 Bean，业务复用 Boot Builder 自建的线程池（含显式设置的装饰器）不受影响。
 */
class MdcApplicationTaskExecutorPostProcessor implements BeanPostProcessor {

    /** Boot 3.2 起废弃、3.4 移除的旧类型，只按类名引用，避免升级 Boot 后类缺失 */
    static final String LEGACY_BUILDER = "org.springframework.boot.task.TaskExecutorBuilder";

    static final String LEGACY_CUSTOMIZER = "org.springframework.boot.task.TaskExecutorCustomizer";

    private static final Logger LOGGER =
            LoggerFactory.getLogger(MdcApplicationTaskExecutorPostProcessor.class);

    private final ListableBeanFactory beanFactory;
    private final ObjectProvider<ThreadPoolTaskExecutorCustomizer> customizers;
    private final ObjectProvider<TaskDecorator> taskDecorators;

    MdcApplicationTaskExecutorPostProcessor(
            ListableBeanFactory beanFactory,
            ObjectProvider<ThreadPoolTaskExecutorCustomizer> customizers,
            ObjectProvider<TaskDecorator> taskDecorators) {
        this.beanFactory = beanFactory;
        this.customizers = customizers;
        this.taskDecorators = taskDecorators;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        if (!TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME.equals(beanName)
                || !(bean instanceof ThreadPoolTaskExecutor executor)) {
            return bean;
        }
        List<String> others =
                Stream.concat(legacyCustomizers(), customizers.orderedStream())
                        .map(c -> c.getClass().getName())
                        .toList();
        if (!others.isEmpty()) {
            LOGGER.info(
                    "检测到 {} 个其他执行器定制器 {}，默认执行器未自动接入 MDC 传播；如需传播请在定制器中使用 MdcTasks.compose(existing)",
                    others.size(),
                    others);
            return bean;
        }
        executor.setTaskDecorator(MdcTasks.compose(taskDecorators.getIfUnique()));
        return bean;
    }

    private Stream<?> legacyCustomizers() {
        ClassLoader classLoader = getClass().getClassLoader();
        if (!ClassUtils.isPresent(LEGACY_CUSTOMIZER, classLoader)) {
            return Stream.empty();
        }
        return beanFactory
                .getBeanProvider(ClassUtils.resolveClassName(LEGACY_CUSTOMIZER, classLoader))
                .orderedStream();
    }
}
