package com.yggdrasil.labs.test.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TestUtils 工具类测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class TestUtilsTest {

    @BeforeEach
    void setUp() {
        // 清理 MDC，确保测试环境干净
        TestUtils.clearMdc();
    }

    @AfterEach
    void tearDown() {
        // 清理 MDC
        TestUtils.clearMdc();
    }

    // ========== 测试数据生成 ==========

    @Test
    void testRandomUuid() {
        String uuid1 = TestUtils.randomUuid();
        String uuid2 = TestUtils.randomUuid();

        assertNotNull(uuid1, "UUID 不应为 null");
        assertNotNull(uuid2, "UUID 不应为 null");
        assertNotEquals(uuid1, uuid2, "两次生成的 UUID 应该不同");
        assertTrue(uuid1.length() > 0, "UUID 长度应大于 0");
        // UUID 格式：xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
        assertTrue(uuid1.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
                "UUID 格式应正确");
    }

    @Test
    void testRandomTraceId() {
        String traceId1 = TestUtils.randomTraceId();
        String traceId2 = TestUtils.randomTraceId();

        assertNotNull(traceId1, "traceId 不应为 null");
        assertNotNull(traceId2, "traceId 不应为 null");
        assertTrue(traceId1.startsWith("trace-"), "traceId 应以 'trace-' 开头");
        assertTrue(traceId2.startsWith("trace-"), "traceId 应以 'trace-' 开头");
        assertNotEquals(traceId1, traceId2, "两次生成的 traceId 应该不同");
    }

    @Test
    void testRandomRequestId() {
        String requestId1 = TestUtils.randomRequestId();
        String requestId2 = TestUtils.randomRequestId();

        assertNotNull(requestId1, "requestId 不应为 null");
        assertNotNull(requestId2, "requestId 不应为 null");
        assertTrue(requestId1.startsWith("req-"), "requestId 应以 'req-' 开头");
        assertTrue(requestId2.startsWith("req-"), "requestId 应以 'req-' 开头");
        assertNotEquals(requestId1, requestId2, "两次生成的 requestId 应该不同");
    }

    @Test
    void testRandomUserId() {
        String userId1 = TestUtils.randomUserId();
        // 等待一小段时间确保时间戳不同
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String userId2 = TestUtils.randomUserId();

        assertNotNull(userId1, "userId 不应为 null");
        assertNotNull(userId2, "userId 不应为 null");
        assertTrue(userId1.startsWith("user-"), "userId 应以 'user-' 开头");
        assertTrue(userId2.startsWith("user-"), "userId 应以 'user-' 开头");
        assertNotEquals(userId1, userId2, "两次生成的 userId 应该不同（基于时间戳）");
    }

    @Test
    void testRandomIp() {
        // IP 地址正则表达式：0.0.0.0 - 255.255.255.255
        Pattern ipPattern = Pattern.compile("^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
                + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
                + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
                + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");

        // 生成多个 IP 地址进行测试
        for (int i = 0; i < 100; i++) {
            String ip = TestUtils.randomIp();
            assertNotNull(ip, "IP 地址不应为 null");
            assertTrue(ipPattern.matcher(ip).matches(), "IP 地址格式应正确: " + ip);

            // 验证每个段都在 0-255 范围内
            String[] parts = ip.split("\\.");
            assertEquals(4, parts.length, "IP 地址应有 4 段");
            for (String part : parts) {
                int value = Integer.parseInt(part);
                assertTrue(value >= 0 && value <= 255, "IP 段应在 0-255 范围内: " + value);
            }
        }
    }

    // ========== MDC 上下文管理 ==========

    @Test
    void testSetupMdc() {
        String traceId = "test-trace-123";
        String userId = "test-user-456";
        String ip = "192.168.1.1";

        TestUtils.setupMdc(traceId, userId, ip);

        assertEquals(traceId, MDC.get("traceId"), "traceId 应设置到 MDC");
        assertEquals(userId, MDC.get("userId"), "userId 应设置到 MDC");
        assertEquals(ip, MDC.get("ip"), "ip 应设置到 MDC");
    }

    @Test
    void testSetupMdcWithNullValues() {
        TestUtils.setupMdc(null, "user-123", null);

        assertNull(MDC.get("traceId"), "null traceId 不应设置到 MDC");
        assertEquals("user-123", MDC.get("userId"), "userId 应设置到 MDC");
        assertNull(MDC.get("ip"), "null ip 不应设置到 MDC");
    }

    @Test
    void testSetupFullMdc() {
        String traceId = "trace-123";
        String requestId = "req-456";
        String userId = "user-789";
        String tenantId = "tenant-001";
        String ip = "10.0.0.1";
        String operation = "CREATE";

        TestUtils.setupFullMdc(traceId, requestId, userId, tenantId, ip, operation);

        assertEquals(traceId, MDC.get("traceId"), "traceId 应设置到 MDC");
        assertEquals(requestId, MDC.get("requestId"), "requestId 应设置到 MDC");
        assertEquals(userId, MDC.get("userId"), "userId 应设置到 MDC");
        assertEquals(tenantId, MDC.get("tenantId"), "tenantId 应设置到 MDC");
        assertEquals(ip, MDC.get("ip"), "ip 应设置到 MDC");
        assertEquals(operation, MDC.get("operation"), "operation 应设置到 MDC");
    }

    @Test
    void testSetupFullMdcWithNullValues() {
        TestUtils.setupFullMdc("trace-123", null, "user-456", null, "192.168.1.1", null);

        assertEquals("trace-123", MDC.get("traceId"));
        assertNull(MDC.get("requestId"), "null requestId 不应设置到 MDC");
        assertEquals("user-456", MDC.get("userId"));
        assertNull(MDC.get("tenantId"), "null tenantId 不应设置到 MDC");
        assertEquals("192.168.1.1", MDC.get("ip"));
        assertNull(MDC.get("operation"), "null operation 不应设置到 MDC");
    }

    @Test
    void testClearMdc() {
        // 先设置一些值
        TestUtils.setupMdc("trace-123", "user-456", "192.168.1.1");
        assertNotNull(MDC.get("traceId"));

        // 清理 MDC
        TestUtils.clearMdc();

        assertNull(MDC.get("traceId"), "清理后 traceId 应为 null");
        assertNull(MDC.get("userId"), "清理后 userId 应为 null");
        assertNull(MDC.get("ip"), "清理后 ip 应为 null");
    }

    @Test
    void testSetupRandomMdc() {
        TestUtils.setupRandomMdc();

        String traceId = MDC.get("traceId");
        String userId = MDC.get("userId");
        String ip = MDC.get("ip");

        assertNotNull(traceId, "随机 traceId 不应为 null");
        assertNotNull(userId, "随机 userId 不应为 null");
        assertNotNull(ip, "随机 ip 不应为 null");
        assertTrue(traceId.startsWith("trace-"), "traceId 应以 'trace-' 开头");
        assertTrue(userId.startsWith("user-"), "userId 应以 'user-' 开头");

        // 验证 IP 格式
        Pattern ipPattern = Pattern.compile("^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
                + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
                + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\."
                + "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
        assertTrue(ipPattern.matcher(ip).matches(), "IP 地址格式应正确");
    }

    // ========== 测试环境清理 ==========

    @Test
    void testCleanupTestEnvironment() {
        // 先设置一些 MDC 值
        TestUtils.setupMdc("trace-123", "user-456", "192.168.1.1");
        assertNotNull(MDC.get("traceId"));

        // 清理测试环境
        TestUtils.cleanupTestEnvironment();

        assertNull(MDC.get("traceId"), "清理后 traceId 应为 null");
        assertNull(MDC.get("userId"), "清理后 userId 应为 null");
        assertNull(MDC.get("ip"), "清理后 ip 应为 null");
    }
}

