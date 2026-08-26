package com.yggdrasil.labs.mybatis.config;

import com.yggdrasil.labs.mybatis.crypto.CryptoUtils;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MybatisCryptoRolloutContractTest extends BaseUnitTest {

    private static final int V2_SAMPLE_LENGTH = CryptoUtils.encrypt(
            "column-capacity-sample", "MDEyMzQ1Njc4OWFiY2RlZg==", "orders").length();

    @Test
    void writeV2_requires_every_instance_to_read_v2_share_context_and_have_capacity() {
        assertFalse(canEnableWrite(List.of(new Instance(false, "orders", V2_SAMPLE_LENGTH))));
        assertFalse(canEnableWrite(List.of(new Instance(true, "orders", V2_SAMPLE_LENGTH),
                new Instance(true, "billing", V2_SAMPLE_LENGTH))));
        assertFalse(canEnableWrite(List.of(new Instance(true, "orders", V2_SAMPLE_LENGTH - 1))));
        assertTrue(canEnableWrite(List.of(new Instance(true, "orders", V2_SAMPLE_LENGTH),
                new Instance(true, "orders", V2_SAMPLE_LENGTH + 10))));
    }

    @Test
    void rollback_target_must_support_v2_and_share_the_application_context() {
        assertFalse(canRollback(new Instance(false, "orders", V2_SAMPLE_LENGTH), "orders"));
        assertFalse(canRollback(new Instance(true, "billing", V2_SAMPLE_LENGTH), "orders"));
        assertTrue(canRollback(new Instance(true, "orders", V2_SAMPLE_LENGTH), "orders"));
    }

    private static boolean canEnableWrite(List<Instance> instances) {
        return !instances.isEmpty() && instances.stream().allMatch(instance -> instance.readsV2
                && "orders".equals(instance.context) && instance.columnCapacity >= V2_SAMPLE_LENGTH);
    }

    private static boolean canRollback(Instance target, String expectedContext) {
        return target.readsV2 && expectedContext.equals(target.context);
    }

    private record Instance(boolean readsV2, String context, int columnCapacity) {
    }
}
