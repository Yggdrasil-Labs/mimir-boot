package com.yggdrasil.labs.log.task;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void decorate_captures_snapshot_at_decorate_time_and_restores() {
        List<String> seen = new ArrayList<>();
        MDC.put("traceId", "t1");
        Runnable decorated = new MdcTaskDecorator().decorate(() -> seen.add(MDC.get("traceId")));
        MDC.put("traceId", "t2");

        decorated.run();

        assertEquals(List.of("t1"), seen);
        assertEquals(Map.of("traceId", "t2"), MDC.getCopyOfContextMap());
    }

    @Test
    void decorate_rejects_null() {
        assertThrows(IllegalArgumentException.class, () -> new MdcTaskDecorator().decorate(null));
    }
}
