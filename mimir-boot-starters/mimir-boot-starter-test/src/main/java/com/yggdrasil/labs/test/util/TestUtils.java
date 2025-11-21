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
@SuppressWarnings("java:S2245") // ThreadLocalRandom 用于生成测试数据，不涉及安全敏感操作
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

    // ========== Web 测试数据生成 ==========

    /**
     * 生成随机 URI
     *
     * @return 随机 URI（格式：/api/resource/{id}）
     */
    public static String randomUri() {
        return "/api/resource/" + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    /**
     * 生成随机 URI（指定路径）
     *
     * @param path 路径前缀
     * @return 随机 URI
     */
    public static String randomUri(String path) {
        return path + "/" + ThreadLocalRandom.current().nextInt(1000, 9999);
    }

    /**
     * 生成随机 User-Agent
     *
     * @return 随机 User-Agent
     */
    public static String randomUserAgent() {
        String[] userAgents = {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36",
                "Apache-HttpClient/4.5",
                "curl/7.68.0"
        };
        return userAgents[ThreadLocalRandom.current().nextInt(userAgents.length)];
    }

    /**
     * 生成随机 HTTP 方法
     *
     * @return 随机 HTTP 方法（GET, POST, PUT, DELETE, PATCH）
     */
    public static String randomHttpMethod() {
        String[] methods = {"GET", "POST", "PUT", "DELETE", "PATCH"};
        return methods[ThreadLocalRandom.current().nextInt(methods.length)];
    }

    /**
     * 生成随机查询字符串
     *
     * @return 随机查询字符串（格式：key1=value1&key2=value2）
     */
    public static String randomQueryString() {
        int paramCount = ThreadLocalRandom.current().nextInt(1, 4);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paramCount; i++) {
            if (i > 0) {
                sb.append("&");
            }
            sb.append("key").append(i + 1).append("=value").append(i + 1);
        }
        return sb.toString();
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

