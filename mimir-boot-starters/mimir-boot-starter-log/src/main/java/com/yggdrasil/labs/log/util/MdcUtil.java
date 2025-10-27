package com.yggdrasil.labs.log.util;

import java.util.HashMap;
import java.util.Map;

/**
 * MDC (Mapped Diagnostic Context) 工具类
 * 
 * <p>提供便捷的 MDC 操作方法</p>
 * 
 * <p>功能说明：</p>
 * <ul>
 * <li>设置和获取上下文信息</li>
 * <li>支持批量操作</li>
 * <li>自动传递到子线程（通过自定义 ThreadPoolExecutor）</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>{@code
 * // 设置用户 ID
 * MdcUtil.setUserId("12345");
 * 
 * // 设置请求 ID
 * MdcUtil.setRequestId("req-001");
 * 
 * // 批量设置
 * Map<String, String> context = new HashMap<>();
 * context.put("operation", "login");
 * context.put("ip", "192.168.1.1");
 * MdcUtil.putAll(context);
 * 
 * // 获取上下文
 * String userId = MdcUtil.getUserId();
 * 
 * // 清除上下文
 * MdcUtil.clear();
 * }</pre>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class MdcUtil {

    /** 用户 ID 键名 */
    public static final String USER_ID = "userId";

    /** 请求 ID 键名 */
    public static final String REQUEST_ID = "requestId";

    /** 操作名称键名 */
    public static final String OPERATION = "operation";

    /** IP 地址键名 */
    public static final String IP = "ip";

    /** 租户 ID 键名 */
    public static final String TENANT_ID = "tenantId";

    /**
     * 设置用户 ID
     */
    public static void setUserId(String userId) {
        put(USER_ID, userId);
    }

    /**
     * 获取用户 ID
     */
    public static String getUserId() {
        return get(USER_ID);
    }

    /**
     * 设置请求 ID
     */
    public static void setRequestId(String requestId) {
        put(REQUEST_ID, requestId);
    }

    /**
     * 获取请求 ID
     */
    public static String getRequestId() {
        return get(REQUEST_ID);
    }

    /**
     * 设置操作名称
     */
    public static void setOperation(String operation) {
        put(OPERATION, operation);
    }

    /**
     * 获取操作名称
     */
    public static String getOperation() {
        return get(OPERATION);
    }

    /**
     * 设置 IP 地址
     */
    public static void setIp(String ip) {
        put(IP, ip);
    }

    /**
     * 获取 IP 地址
     */
    public static String getIp() {
        return get(IP);
    }

    /**
     * 设置租户 ID
     */
    public static void setTenantId(String tenantId) {
        put(TENANT_ID, tenantId);
    }

    /**
     * 获取租户 ID
     */
    public static String getTenantId() {
        return get(TENANT_ID);
    }

    /**
     * 设置上下文值
     */
    public static void put(String key, String value) {
        if (value != null && !value.isEmpty()) {
            org.slf4j.MDC.put(key, value);
        }
    }

    /**
     * 获取上下文值
     */
    public static String get(String key) {
        return org.slf4j.MDC.get(key);
    }

    /**
     * 移除上下文值
     */
    public static void remove(String key) {
        org.slf4j.MDC.remove(key);
    }

    /**
     * 批量设置上下文
     */
    public static void putAll(Map<String, String> context) {
        if (context != null && !context.isEmpty()) {
            org.slf4j.MDC.setContextMap(context);
        }
    }

    /**
     * 获取所有上下文
     */
    public static Map<String, String> getAll() {
        Map<String, String> copy = org.slf4j.MDC.getCopyOfContextMap();
        return copy != null ? copy : new HashMap<>();
    }

    /**
     * 获取并复制当前上下文
     * 
     * @return 上下文的副本
     */
    public static Map<String, String> getCopy() {
        return new HashMap<>(getAll());
    }

    /**
     * 清除所有上下文
     */
    public static void clear() {
        org.slf4j.MDC.clear();
    }

    /**
     * 设置上下文（替换所有现有上下文）
     */
    public static void setContextMap(Map<String, String> context) {
        org.slf4j.MDC.setContextMap(context);
    }
}

