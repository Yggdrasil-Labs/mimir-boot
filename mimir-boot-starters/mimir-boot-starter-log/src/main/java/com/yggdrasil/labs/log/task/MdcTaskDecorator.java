package com.yggdrasil.labs.log.task;

import org.springframework.core.task.TaskDecorator;

/**
 * 传递提交时完整 MDC 的任务装饰器
 *
 * <p>{@link #decorate(Runnable)} 调用时复制全部 MDC 条目，执行时以该快照替换工作线程上下文，结束后在 finally 中整体恢复工作线程原上下文。经
 * {@link MdcTasks#compose(TaskDecorator)} 组合时，业务装饰器在内层、MDC 在最外层。
 *
 * <p>不创建或恢复 tracing Span。
 *
 * @author Yggdrasil Labs
 * @since 2.4.0
 */
public final class MdcTaskDecorator implements TaskDecorator {

    /** 内层业务装饰器，可为 null */
    private final TaskDecorator business;

    /** 创建仅传播 MDC 的装饰器 */
    public MdcTaskDecorator() {
        this(null);
    }

    MdcTaskDecorator(TaskDecorator business) {
        this.business = business;
    }

    /**
     * 包装任务并在当前线程捕获 MDC 快照
     *
     * @param runnable 任务，不能为 null
     * @return 包装后的任务
     * @throws IllegalArgumentException runnable 为 null
     */
    @Override
    public Runnable decorate(Runnable runnable) {
        if (runnable == null) {
            throw new IllegalArgumentException("runnable 不能为 null");
        }
        Runnable inner = business == null ? runnable : business.decorate(runnable);
        return MdcTasks.wrap(inner);
    }
}
