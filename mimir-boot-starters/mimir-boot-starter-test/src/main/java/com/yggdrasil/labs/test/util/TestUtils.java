package com.yggdrasil.labs.test.util;

import org.slf4j.MDC;

import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;

/**
 * 测试工具类
 *
 * <p>提供测试中常用的工具方法：</p>
 * <ul>
 * <li>生成测试数据</li>
 * <li>MDC 上下文管理</li>
 * <li>测试环境清理</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public final class TestUtils {

    private TestUtils() {
        // 工具类，禁止实例化
    }

    // ========== 测试数据生成 ==========

    /**
     * 生成随机 UUID 字符串
     *
     * @return UUID 字符串
     */
    public static String randomUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成随机 traceId
     *
     * @return traceId
     */
    public static String randomTraceId() {
        return "trace-" + randomUuid();
    }

    /**
     * 生成随机 requestId
     *
     * @return requestId
     */
    public static String randomRequestId() {
        return "req-" + randomUuid();
    }

    /**
     * 生成随机 userId
     *
     * @return userId
     */
    public static String randomUserId() {
        return "user-" + System.currentTimeMillis();
    }

    /**
     * 生成随机 IP 地址
     *
     * @return IP 地址（范围：0.0.0.0 - 255.255.255.255）
     */
    public static String randomIp() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return String.format(
                "%d.%d.%d.%d",
                random.nextInt(256),
                random.nextInt(256),
                random.nextInt(256),
                random.nextInt(256));
    }

    // ========== MDC 上下文管理 ==========

    /**
     * 设置测试用的 MDC 上下文
     *
     * @param traceId traceId
     * @param userId  userId
     * @param ip      IP 地址
     */
    public static void setupMdc(String traceId, String userId, String ip) {
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        if (userId != null) {
            MDC.put("userId", userId);
        }
        if (ip != null) {
            MDC.put("ip", ip);
        }
    }

    /**
     * 设置测试用的完整 MDC 上下文
     *
     * @param traceId   traceId
     * @param requestId requestId
     * @param userId    userId
     * @param tenantId  tenantId
     * @param ip        IP 地址
     * @param operation 操作类型
     */
    public static void setupFullMdc(
            String traceId, String requestId, String userId, String tenantId, String ip, String operation) {
        if (traceId != null) {
            MDC.put("traceId", traceId);
        }
        if (requestId != null) {
            MDC.put("requestId", requestId);
        }
        if (userId != null) {
            MDC.put("userId", userId);
        }
        if (tenantId != null) {
            MDC.put("tenantId", tenantId);
        }
        if (ip != null) {
            MDC.put("ip", ip);
        }
        if (operation != null) {
            MDC.put("operation", operation);
        }
    }

    /**
     * 清理 MDC 上下文
     */
    public static void clearMdc() {
        MDC.clear();
    }

    /**
     * 设置随机测试 MDC 上下文
     */
    public static void setupRandomMdc() {
        setupMdc(randomTraceId(), randomUserId(), randomIp());
    }

    // ========== 测试环境清理 ==========

    /**
     * 清理测试环境（MDC、ThreadLocal 等）
     */
    public static void cleanupTestEnvironment() {
        clearMdc();
        // 可以在这里添加其他清理逻辑
    }
}

