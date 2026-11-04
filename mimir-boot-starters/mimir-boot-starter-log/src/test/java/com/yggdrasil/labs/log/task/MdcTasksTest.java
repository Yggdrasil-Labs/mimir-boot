package com.yggdrasil.labs.log.task;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTasksTest {

    @BeforeEach
    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void snapshot_is_taken_at_wrap_time_with_all_keys() throws Exception {
        MDC.put("traceId", "t1");
        MDC.put("tenantId", "a");
        MDC.put("customKey", "x");
        AtomicReference<Map<String, String>> seen = new AtomicReference<>();
        Runnable task = MdcTasks.wrap(() -> seen.set(MDC.getCopyOfContextMap()));
        MDC.put("tenantId", "b");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(task).get();
        } finally {
            executor.shutdownNow();
        }

        assertEquals(Map.of("traceId", "t1", "tenantId", "a", "customKey", "x"), seen.get());
    }

    @Test
    void empty_snapshot_clears_worker_context_and_restores_afterwards() {
        Map<String, String> during = new HashMap<>();
        Runnable task =
                MdcTasks.wrap(
                        () -> {
                            during.putAll(contextOrEmpty());
                            MDC.put("written", "v");
                        });

        MDC.put("stale", "old");
        task.run();

        assertTrue(during.isEmpty(), "执行期间不应看到工作线程旧值");
        assertEquals(Map.of("stale", "old"), contextOrEmpty());
    }

    @Test
    void restores_empty_worker_context_by_clearing() {
        MDC.put("requestId", "r");
        Runnable task = MdcTasks.wrap(() -> MDC.put("temporary", "v"));
        MDC.clear();

        task.run();

        assertTrue(contextOrEmpty().isEmpty());
    }

    @Test
    void runnable_exception_propagates_and_context_is_restored() {
        MDC.put("requestId", "r");
        IllegalStateException boom = new IllegalStateException("boom");
        Runnable task =
                MdcTasks.wrap(
                        (Runnable)
                                () -> {
                                    MDC.put("temporary", "v");
                                    throw boom;
                                });
        MDC.clear();
        MDC.put("worker", "w");

        IllegalStateException thrown = assertThrows(IllegalStateException.class, task::run);

        assertSame(boom, thrown);
        assertEquals(Map.of("worker", "w"), contextOrEmpty());
    }

    @Test
    void callable_keeps_result_and_checked_exception() throws Exception {
        MDC.put("requestId", "r");
        Callable<String> ok = MdcTasks.wrap(() -> MDC.get("requestId"));
        IOException io = new IOException("io");
        Callable<String> failing =
                MdcTasks.wrap(
                        () -> {
                            throw io;
                        });
        MDC.clear();
        MDC.put("worker", "w");

        assertEquals("r", ok.call());
        assertSame(io, assertThrows(IOException.class, failing::call));
        assertEquals(Map.of("worker", "w"), contextOrEmpty());
    }

    @Test
    void nested_and_caller_runs_restore_each_level() {
        MDC.put("level", "outer");
        List<String> events = new ArrayList<>();
        Runnable outer =
                MdcTasks.wrap(
                        () -> {
                            events.add("outer:" + MDC.get("level"));
                            MDC.put("level", "inner");
                            Runnable inner =
                                    MdcTasks.wrap(
                                            () -> {
                                                events.add("inner:" + MDC.get("level"));
                                                MDC.put("leak", "x");
                                            });
                            MDC.put("level", "outer-after-submit");
                            inner.run();
                            events.add("back:" + MDC.get("level") + ":" + MDC.get("leak"));
                        });
        MDC.put("level", "caller");

        outer.run();

        assertEquals(List.of("outer:outer", "inner:inner", "back:outer-after-submit:null"), events);
        assertEquals(Map.of("level", "caller"), contextOrEmpty());
    }

    @Test
    void reused_wrapper_reuses_original_snapshot() {
        MDC.put("k", "first");
        List<String> seen = new ArrayList<>();
        Runnable task = MdcTasks.wrap((Runnable) () -> seen.add(MDC.get("k")));
        MDC.put("k", "second");

        task.run();
        task.run();

        assertEquals(List.of("first", "first"), seen);
    }

    @Test
    void rejected_task_keeps_rejection_and_submitter_context() {
        ThreadPoolExecutor executor =
                new ThreadPoolExecutor(
                        1,
                        1,
                        0,
                        TimeUnit.MILLISECONDS,
                        new java.util.concurrent.SynchronousQueue<>(),
                        new ThreadPoolExecutor.AbortPolicy());
        try {
            CompletableFuture<Void> blocker = new CompletableFuture<>();
            executor.execute(blocker::join);
            MDC.put("submitter", "s");

            assertThrows(
                    RejectedExecutionException.class,
                    () -> executor.execute(MdcTasks.wrap(() -> MDC.put("x", "y"))));
            assertEquals(Map.of("submitter", "s"), contextOrEmpty());
            blocker.complete(null);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void single_worker_reused_for_1000_tasks_has_no_leak() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> MDC.put("worker", "w")).get();
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                MDC.clear();
                MDC.put("tenantId", "t" + i);
                MDC.put("custom" + i, "v" + i);
                results.add(
                        executor.submit(
                                MdcTasks.wrap(
                                        () -> {
                                            Map<String, String> ctx = contextOrEmpty();
                                            MDC.put("taskWrite", "leak");
                                            return ctx.size() + ":" + ctx.get("tenantId");
                                        })));
            }
            for (int i = 0; i < 1000; i++) {
                assertEquals("2:t" + i, results.get(i).get());
            }
            assertEquals(
                    Map.of("worker", "w"), executor.submit(MdcTasksTest::contextOrEmpty).get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void compose_runs_business_inside_mdc_context_and_restores() {
        List<String> events = new ArrayList<>();
        org.springframework.core.task.TaskDecorator business =
                task ->
                        () -> {
                            events.add("business-before:" + MDC.get("requestId"));
                            MDC.put("business", "b");
                            try {
                                task.run();
                            } finally {
                                events.add("business-after");
                            }
                        };
        MDC.put("requestId", "r");
        Runnable decorated =
                MdcTasks.compose(business)
                        .decorate(() -> events.add("task:" + MDC.get("business")));
        MDC.clear();
        MDC.put("worker", "w");

        decorated.run();

        assertEquals(List.of("business-before:r", "task:b", "business-after"), events);
        assertEquals(Map.of("worker", "w"), contextOrEmpty());
    }

    @Test
    void compose_null_is_mdc_only_and_repeated_compose_stays_single_layer() {
        org.springframework.core.task.TaskDecorator mdcOnly = MdcTasks.compose(null);
        assertNotNull(mdcOnly);
        assertSame(mdcOnly, MdcTasks.compose(mdcOnly));

        org.springframework.core.task.TaskDecorator composed = MdcTasks.compose(task -> task);
        assertSame(composed, MdcTasks.compose(composed));
        MdcTaskDecorator plain = new MdcTaskDecorator();
        assertSame(plain, MdcTasks.compose(plain));
    }

    @Test
    void null_task_is_rejected_immediately() {
        assertThrows(IllegalArgumentException.class, () -> MdcTasks.wrap((Runnable) null));
        assertThrows(IllegalArgumentException.class, () -> MdcTasks.wrap((Callable<?>) null));
        assertThrows(IllegalArgumentException.class, () -> new MdcTaskDecorator().decorate(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> MdcTasks.compose(task -> task).decorate(null));
    }

    @Test
    void submitter_context_is_unchanged_after_wrapping() {
        MDC.put("requestId", "r");
        MdcTasks.wrap(() -> {});
        MdcTasks.wrap(() -> "x");
        assertEquals(Map.of("requestId", "r"), contextOrEmpty());
    }

    private static Map<String, String> contextOrEmpty() {
        Map<String, String> map = MDC.getCopyOfContextMap();
        return map == null ? Map.of() : map;
    }
}
