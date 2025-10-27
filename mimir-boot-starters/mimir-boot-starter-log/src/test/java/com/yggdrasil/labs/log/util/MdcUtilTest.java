package com.yggdrasil.labs.log.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MDC 工具类测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class MdcUtilTest {

    @BeforeEach
    void setUp() {
        MdcUtil.clear();
    }

    @Test
    void testSetAndGetUserId() {
        MdcUtil.setUserId("12345");
        assertEquals("12345", MdcUtil.getUserId());
    }

    @Test
    void testSetAndGetRequestId() {
        MdcUtil.setRequestId("req-001");
        assertEquals("req-001", MdcUtil.getRequestId());
    }

    @Test
    void testSetAndGetOperation() {
        MdcUtil.setOperation("login");
        assertEquals("login", MdcUtil.getOperation());
    }

    @Test
    void testSetAndGetIp() {
        MdcUtil.setIp("192.168.1.1");
        assertEquals("192.168.1.1", MdcUtil.getIp());
    }

    @Test
    void testSetAndGetTenantId() {
        MdcUtil.setTenantId("tenant-001");
        assertEquals("tenant-001", MdcUtil.getTenantId());
    }

    @Test
    void testPutAndGet() {
        MdcUtil.put("customKey", "customValue");
        assertEquals("customValue", MdcUtil.get("customKey"));
    }

    @Test
    void testRemove() {
        MdcUtil.put("testKey", "testValue");
        assertEquals("testValue", MdcUtil.get("testKey"));

        MdcUtil.remove("testKey");
        assertNull(MdcUtil.get("testKey"));
    }

    @Test
    void testPutAll() {
        Map<String, String> context = new HashMap<>();
        context.put("key1", "value1");
        context.put("key2", "value2");

        MdcUtil.putAll(context);

        assertEquals("value1", MdcUtil.get("key1"));
        assertEquals("value2", MdcUtil.get("key2"));
    }

    @Test
    void testPutAllWithNull() {
        MdcUtil.putAll(null);
        // 不应该抛出异常
    }

    @Test
    void testPutAllWithEmptyMap() {
        MdcUtil.putAll(new HashMap<>());
        // 不应该抛出异常
    }

    @Test
    void testGetAll() {
        MdcUtil.put("key1", "value1");
        MdcUtil.put("key2", "value2");

        Map<String, String> all = MdcUtil.getAll();

        assertNotNull(all);
        assertEquals("value1", all.get("key1"));
        assertEquals("value2", all.get("key2"));
    }

    @Test
    void testGetCopy() {
        MdcUtil.put("key1", "value1");

        Map<String, String> copy1 = MdcUtil.getCopy();
        Map<String, String> copy2 = MdcUtil.getCopy();

        assertNotNull(copy1);
        assertNotNull(copy2);
        assertEquals("value1", copy1.get("key1"));
        assertEquals("value1", copy2.get("key1"));

        // 修改副本不应影响原值
        copy1.put("key2", "value2");
        assertNull(MdcUtil.get("key2"));
    }

    @Test
    void testClear() {
        MdcUtil.put("key1", "value1");
        MdcUtil.put("key2", "value2");

        MdcUtil.clear();

        assertNull(MdcUtil.get("key1"));
        assertNull(MdcUtil.get("key2"));
    }

    @Test
    void testPutWithNullValue() {
        MdcUtil.put("key", null);
        assertNull(MdcUtil.get("key"));
    }

    @Test
    void testPutWithEmptyValue() {
        MdcUtil.put("key", "");
        assertNull(MdcUtil.get("key"));
    }

    @Test
    void testGetWithNonExistentKey() {
        assertNull(MdcUtil.get("nonExistentKey"));
    }

    @Test
    void testSetContextMap() {
        Map<String, String> context = new HashMap<>();
        context.put("key1", "value1");
        context.put("key2", "value2");

        MdcUtil.setContextMap(context);

        assertEquals("value1", MdcUtil.get("key1"));
        assertEquals("value2", MdcUtil.get("key2"));
    }

    @Test
    void testMultipleOperations() {
        // 设置多个值
        MdcUtil.setUserId("user123");
        MdcUtil.setRequestId("req456");
        MdcUtil.setOperation("login");
        MdcUtil.setIp("192.168.1.1");
        MdcUtil.setTenantId("tenant789");

        // 验证所有值
        assertEquals("user123", MdcUtil.getUserId());
        assertEquals("req456", MdcUtil.getRequestId());
        assertEquals("login", MdcUtil.getOperation());
        assertEquals("192.168.1.1", MdcUtil.getIp());
        assertEquals("tenant789", MdcUtil.getTenantId());

        // 清空并验证
        MdcUtil.clear();
        assertNull(MdcUtil.getUserId());
        assertNull(MdcUtil.getRequestId());
        assertNull(MdcUtil.getOperation());
        assertNull(MdcUtil.getIp());
        assertNull(MdcUtil.getTenantId());
    }

    @Test
    void testConstants() {
        assertEquals("userId", MdcUtil.USER_ID);
        assertEquals("requestId", MdcUtil.REQUEST_ID);
        assertEquals("operation", MdcUtil.OPERATION);
        assertEquals("ip", MdcUtil.IP);
        assertEquals("tenantId", MdcUtil.TENANT_ID);
    }
}

