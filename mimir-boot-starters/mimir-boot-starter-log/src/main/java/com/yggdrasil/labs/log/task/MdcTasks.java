package com.yggdrasil.labs.log.task;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * MDC 任务传播统一入口
 *
 * <p>在提交处调用：{@code wrap} 调用时复制当前线程全部 MDC 条目（不按键筛选），执行时以快照替换工作线程上下文（快照为空则清空），结束后在 finally
 * 中整体恢复工作线程原上下文。重复执行同一包装任务会重用原快照，不会重新捕获。
 *
 * <p>自建执行器请显式使用 {@link #wrap(Runnable)} 或 {@link #compose(TaskDecorator)}；不创建或恢复 tracing Span。
 *
 * @author Yggdrasil Labs
 * @since 2.4.0
 */
public final class MdcTasks {

    private MdcTasks() {}

    /**
     * 以当前 MDC 快照包装任务
     *
     * @param task 任务，不能为 null
     * @return 包装后的任务，原异常直接传播
     * @throws IllegalArgumentException task 为 null
     */
    public static Runnable wrap(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("task 不能为 null");
        }
        Map<String, String> snapshot = snapshot();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            apply(snapshot);
            try {
                task.run();
            } finally {
                apply(previous);
            }
        };
    }

    /**
     * 以当前 MDC 快照包装 Callable
     *
     * @param task 任务，不能为 null
     * @param <T> 结果类型
     * @return 包装后的任务，结果与 checked exception 保持不变
     * @throws IllegalArgumentException task 为 null
     */
    public static <T> Callable<T> wrap(Callable<T> task) {
        if (task == null) {
            throw new IllegalArgumentException("task 不能为 null");
        }
        Map<String, String> snapshot = snapshot();
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            apply(snapshot);
            try {
                return task.call();
            } finally {
                apply(previous);
            }
        };
    }

    /**
     * 组合业务装饰器与 MDC 传播：{@code mdc.decorate(business.decorate(task))}
     *
     * <p>业务装饰器的前后逻辑在传播的上下文中执行，MDC 在最外层保证最终恢复。传入已由本方法组合的装饰器或 {@link MdcTaskDecorator} 时原样返回，避免重复包装。
     *
     * @param businessDecorator 业务装饰器，为 null 时返回仅传播 MDC 的装饰器
     * @return 组合后的装饰器
     */
    public static TaskDecorator compose(TaskDecorator businessDecorator) {
        if (businessDecorator instanceof MdcTaskDecorator mdcDecorator) {
            return mdcDecorator;
        }
        return new MdcTaskDecorator(businessDecorator);
    }

    private static Map<String, String> snapshot() {
        Map<String, String> current = MDC.getCopyOfContextMap();
        if (current == null || current.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new HashMap<>(current));
    }

    private static void apply(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
