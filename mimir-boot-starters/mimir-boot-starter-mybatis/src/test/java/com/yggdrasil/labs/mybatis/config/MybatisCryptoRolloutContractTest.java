package com.yggdrasil.labs.mybatis.config;

import com.yggdrasil.labs.mybatis.crypto.CryptoUtils;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 加密 v2 发布流程的本地演练 fixture。
 *
 * <p>本类只验证录入的实例能力与列容量证据如何决定开写或回退；它不会发现真实运行中的实例，
 * 也不会读取生产数据库元数据。因此通过本测试不构成生产发布验证，生产环境仍须在发布流程中
 * 收集全实例清单与列容量预检结果。</p>
 */
class MybatisCryptoRolloutFixtureTest extends BaseUnitTest {

    private static final int V2_SAMPLE_LENGTH = CryptoUtils.encrypt(
            "column-capacity-sample", "MDEyMzQ1Njc4OWFiY2RlZg==", "orders").length();

    @Test
    void recordedEvidence_requires_every_instance_to_read_v2_share_context_and_have_capacity() {
        assertFalse(canEnableWrite(List.of(new Instance(false, "orders", V2_SAMPLE_LENGTH))));
        assertFalse(canEnableWrite(List.of(new Instance(true, "orders", V2_SAMPLE_LENGTH),
                new Instance(true, "billing", V2_SAMPLE_LENGTH))));
        assertFalse(canEnableWrite(List.of(new Instance(true, "orders", V2_SAMPLE_LENGTH - 1))));
        assertTrue(canEnableWrite(List.of(new Instance(true, "orders", V2_SAMPLE_LENGTH),
                new Instance(true, "orders", V2_SAMPLE_LENGTH + 10))));
    }

    @Test
    void recordedRollbackEvidence_requires_v2_support_and_shared_application_context() {
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
