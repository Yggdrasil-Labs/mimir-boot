package com.yggdrasil.labs.log.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.boot.task.ThreadPoolTaskExecutorCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.yggdrasil.labs.log.task.MdcTasks;

@ExtendWith(OutputCaptureExtension.class)
class MdcTaskExecutionAutoConfigurationTest {

    private static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(
                            AutoConfigurations.of(
                                    MdcTaskExecutionAutoConfiguration.class,
                                    TaskExecutionAutoConfiguration.class))
                    .withPropertyValues("mimir.boot.log.mdc.enabled=true");

    @AfterEach
    void reset() {
        EVENTS.clear();
        MDC.clear();
    }

    @Test
    void adapter_is_off_when_property_missing() {
        new ApplicationContextRunner()
                .withConfiguration(
                        AutoConfigurations.of(
                                MdcTaskExecutionAutoConfiguration.class,
                                TaskExecutionAutoConfiguration.class))
                .run(
                        context -> {
                            assertThat(context)
                                    .doesNotHaveBean(MdcApplicationTaskExecutorPostProcessor.class);
                            assertThat(context).doesNotHaveBean(MdcVirtualThreadNotice.class);
                            assertThat(runAndCapture(executor(context))).isNull();
                        });
    }

    @Test
    void default_executor_propagates_mdc_and_keeps_boot_bean_names() {
        runner.run(
                context -> {
                    assertThat(context)
                            .hasSingleBean(MdcApplicationTaskExecutorPostProcessor.class);
                    assertThat(context.getBean("applicationTaskExecutor"))
                            .isSameAs(context.getBean("taskExecutor"))
                            .isInstanceOf(ThreadPoolTaskExecutor.class);
                    ThreadPoolTaskExecutor executor =
                            context.getBean(
                                    "applicationTaskExecutor", ThreadPoolTaskExecutor.class);
                    assertThat(executor.getThreadNamePrefix()).isEqualTo("task-");

                    assertThat(runAndCapture(executor)).isEqualTo("r");
                });
    }

    @Test
    void single_business_decorator_and_mdc_each_run_once() {
        runner.withUserConfiguration(OneDecorator.class)
                .run(
                        context -> {
                            assertThat(runAndCapture(executor(context))).isEqualTo("r");
                            assertThat(EVENTS).containsExactly("A:r");
                        });
    }

    @Test
    void multiple_decorators_with_primary_run_only_primary() {
        runner.withUserConfiguration(TwoDecoratorsWithPrimary.class)
                .run(
                        context -> {
                            assertThat(runAndCapture(executor(context))).isEqualTo("r");
                            assertThat(EVENTS).containsExactly("P:r");
                        });
    }

    @Test
    void multiple_decorators_without_unique_candidate_run_none() {
        runner.withUserConfiguration(TwoDecorators.class)
                .run(
                        context -> {
                            assertThat(runAndCapture(executor(context))).isEqualTo("r");
                            assertThat(EVENTS).isEmpty();
                        });
    }

    @Test
    void other_modern_customizer_setting_decorator_makes_mdc_yield(CapturedOutput output) {
        runner.withUserConfiguration(ModernCustomizerSettingDecorator.class)
                .run(
                        context -> {
                            ThreadPoolTaskExecutor executor = executor(context);
                            assertThat(runAndCapture(executor)).isNull();
                            assertThat(EVENTS).containsExactly("M:null");
                            assertThat(output)
                                    .containsOnlyOnce("未自动接入 MDC 传播")
                                    .contains("MdcTaskExecutionAutoConfigurationTest");
                        });
    }

    @Test
    @SuppressWarnings("removal")
    void other_legacy_customizer_only_tuning_pool_makes_mdc_yield(CapturedOutput output) {
        runner.withUserConfiguration(OneDecorator.class, LegacyCustomizerTuningPool.class)
                .run(
                        context -> {
                            ThreadPoolTaskExecutor executor = executor(context);
                            assertThat(executor.getCorePoolSize()).isEqualTo(3);
                            assertThat(runAndCapture(executor)).isNull();
                            assertThat(EVENTS).containsExactly("A:null");
                            assertThat(output).containsOnlyOnce("未自动接入 MDC 传播");
                        });
    }

    @Test
    void explicit_compose_in_user_customizer_runs_business_and_mdc_once() {
        runner.withUserConfiguration(OneDecorator.class, ComposingCustomizer.class)
                .run(
                        context -> {
                            assertThat(runAndCapture(executor(context))).isEqualTo("r");
                            assertThat(EVENTS).containsExactly("A:r");
                        });
    }

    @Test
    void reused_boot_builder_keeps_explicit_decorator_untouched() {
        runner.withUserConfiguration(OneDecorator.class)
                .run(
                        context -> {
                            ThreadPoolTaskExecutor custom =
                                    context.getBean(ThreadPoolTaskExecutorBuilder.class)
                                            .taskDecorator(recording("X"))
                                            .build();
                            custom.initialize();
                            try {
                                assertThat(runAndCapture(custom)).as("自建线程池不被自动接入").isNull();
                                assertThat(EVENTS).containsExactly("X:null");
                            } finally {
                                custom.shutdown();
                            }
                            EVENTS.clear();
                            assertThat(runAndCapture(executor(context))).isEqualTo("r");
                            assertThat(EVENTS).containsExactly("A:r");
                        });
    }

    @Test
    void user_executor_or_builder_or_disabled_turns_adapter_off() {
        runner.withUserConfiguration(UserExecutor.class)
                .run(
                        context ->
                                assertThat(context)
                                        .doesNotHaveBean(
                                                MdcApplicationTaskExecutorPostProcessor.class));
        runner.withUserConfiguration(UserBuilder.class)
                .run(
                        context ->
                                assertThat(context)
                                        .doesNotHaveBean(
                                                MdcApplicationTaskExecutorPostProcessor.class));
        runner.withUserConfiguration(UserLegacyBuilder.class)
                .run(
                        context ->
                                assertThat(context)
                                        .doesNotHaveBean(
                                                MdcApplicationTaskExecutorPostProcessor.class));
        runner.withPropertyValues("mimir.boot.log.mdc.enabled=false")
                .run(
                        context -> {
                            assertThat(context)
                                    .doesNotHaveBean(MdcApplicationTaskExecutorPostProcessor.class);
                            assertThat(runAndCapture(executor(context))).isNull();
                        });
    }

    /** 门禁不允许跳过测试：按运行 JDK 断言实际行为（JDK 21 起才进入虚拟线程模式） */
    @Test
    void virtual_threads_log_notice_without_adapter(CapturedOutput output) {
        boolean virtualThreadsSupported = Runtime.version().feature() >= 21;
        runner.withPropertyValues("spring.threads.virtual.enabled=true")
                .run(
                        context -> {
                            if (virtualThreadsSupported) {
                                assertThat(context)
                                        .doesNotHaveBean(
                                                MdcApplicationTaskExecutorPostProcessor.class);
                                assertThat(output).containsOnlyOnce("虚拟线程");
                            } else {
                                assertThat(context)
                                        .hasSingleBean(
                                                MdcApplicationTaskExecutorPostProcessor.class);
                                assertThat(context).doesNotHaveBean(MdcVirtualThreadNotice.class);
                                assertThat(output).doesNotContain("虚拟线程");
                            }
                        });
        runner.withPropertyValues(
                        "spring.threads.virtual.enabled=true", "mimir.boot.log.mdc.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MdcVirtualThreadNotice.class));
    }

    private static ThreadPoolTaskExecutor executor(
            org.springframework.context.ApplicationContext context) {
        return context.getBean("applicationTaskExecutor", ThreadPoolTaskExecutor.class);
    }

    /** 提交线程设置 requestId=r，返回任务内看到的 requestId，并确认提交线程上下文不变 */
    private static String runAndCapture(ThreadPoolTaskExecutor executor) throws Exception {
        MDC.put("requestId", "r");
        try {
            String seen = executor.submit(() -> MDC.get("requestId")).get(5, TimeUnit.SECONDS);
            List<String> firstTaskEvents = List.copyOf(EVENTS);
            String leaked = executor.submit(() -> MDC.get("requestId")).get(5, TimeUnit.SECONDS);
            assertThat(leaked).isEqualTo(seen);
            assertThat(MDC.getCopyOfContextMap()).isEqualTo(Map.of("requestId", "r"));
            MDC.clear();
            assertThat(executor.submit(() -> MDC.get("requestId")).get(5, TimeUnit.SECONDS))
                    .as("工作线程不得残留上一任务的 MDC")
                    .isNull();
            EVENTS.clear();
            EVENTS.addAll(firstTaskEvents);
            return seen;
        } finally {
            MDC.clear();
        }
    }

    private static TaskDecorator recording(String name) {
        return task ->
                () -> {
                    EVENTS.add(name + ":" + MDC.get("requestId"));
                    task.run();
                };
    }

    @Configuration(proxyBeanMethods = false)
    static class OneDecorator {
        @Bean
        TaskDecorator decoratorA() {
            return recording("A");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoDecorators {
        @Bean
        TaskDecorator decoratorA() {
            return recording("A");
        }

        @Bean
        TaskDecorator decoratorB() {
            return recording("B");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoDecoratorsWithPrimary {
        @Bean
        @Primary
        TaskDecorator decoratorP() {
            return recording("P");
        }

        @Bean
        TaskDecorator decoratorB() {
            return recording("B");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ModernCustomizerSettingDecorator {
        @Bean
        ThreadPoolTaskExecutorCustomizer businessCustomizer() {
            return executor -> executor.setTaskDecorator(recording("M"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @SuppressWarnings("removal")
    static class LegacyCustomizerTuningPool {
        @Bean
        org.springframework.boot.task.TaskExecutorCustomizer poolCustomizer() {
            return executor -> executor.setCorePoolSize(3);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ComposingCustomizer {
        @Bean
        ThreadPoolTaskExecutorCustomizer composingCustomizer(
                org.springframework.beans.factory.ObjectProvider<TaskDecorator> decorators) {
            return executor ->
                    executor.setTaskDecorator(MdcTasks.compose(decorators.getIfUnique()));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserExecutor {
        @Bean
        Executor userExecutor() {
            return Executors.newSingleThreadExecutor();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class UserBuilder {
        @Bean
        ThreadPoolTaskExecutorBuilder userBuilder() {
            return new ThreadPoolTaskExecutorBuilder();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @SuppressWarnings("removal")
    static class UserLegacyBuilder {
        @Bean
        org.springframework.boot.task.TaskExecutorBuilder userLegacyBuilder() {
            return new org.springframework.boot.task.TaskExecutorBuilder();
        }
    }
}
