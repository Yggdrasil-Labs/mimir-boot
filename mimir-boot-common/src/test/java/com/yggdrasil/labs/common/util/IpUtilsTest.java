package com.yggdrasil.labs.common.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IpUtilsTest {

    private static final String UNKNOWN = "unknown";

    private static Map<String, String> headers(String... kv) {
        if (kv == null) {
            throw new IllegalArgumentException("headers 参数 kv 不能为 null");
        }
        if ((kv.length & 1) == 1) {
            throw new IllegalArgumentException("headers 参数 kv 必须为偶数个元素（key/value 成对），当前长度=" + kv.length);
        }
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put(kv[i], kv[i + 1]);
        }
        return map;
    }

    @Test
    void resolve_uses_first_ip_in_x_forwarded_for() {
        Map<String, String> map = headers(
                "X-Forwarded-For", " 10.0.0.1, 10.0.0.2 ",
                "X-Real-IP", "192.168.0.10"
        );
        String ip = IpUtils.resolveClientIp(map::get, () -> "127.0.0.1");
        assertEquals("10.0.0.1", ip);
    }

    @Test
    void resolve_fallbacks_to_x_real_ip_when_xff_missing_or_unknown() {
        Map<String, String> map1 = headers("X-Real-IP", "203.0.113.5");
        assertEquals("203.0.113.5", IpUtils.resolveClientIp(map1::get, () -> "127.0.0.1"));

        Map<String, String> map2 = headers(
                "X-Forwarded-For", UNKNOWN,
                "X-Real-IP", "203.0.113.6"
        );
        assertEquals("203.0.113.6", IpUtils.resolveClientIp(map2::get, () -> "127.0.0.1"));
    }

    @Test
    void resolve_fallbacks_to_proxy_and_wl_proxy() {
        Map<String, String> map = headers(
                "X-Forwarded-For", "",
                "X-Real-IP", null,
                "Proxy-Client-IP", "172.16.0.2"
        );
        assertEquals("172.16.0.2", IpUtils.resolveClientIp(map::get, () -> "127.0.0.1"));

        Map<String, String> map2 = headers(
                "X-Forwarded-For", " ",
                "X-Real-IP", UNKNOWN,
                "Proxy-Client-IP", UNKNOWN,
                "WL-Proxy-Client-IP", "172.16.0.3"
        );
        assertEquals("172.16.0.3", IpUtils.resolveClientIp(map2::get, () -> "127.0.0.1"));
    }

    @Test
    void resolve_trims_value_and_uses_remote_addr_as_last_resort() {
        Map<String, String> map = headers(
                "X-Forwarded-For", "  ",
                "X-Real-IP", "  "
        );
        String ip = IpUtils.resolveClientIp(map::get, () -> "10.10.10.10");
        assertEquals("10.10.10.10", ip);

        Map<String, String> map2 = headers("X-Real-IP", "  198.51.100.8  ");
        String ip2 = IpUtils.resolveClientIp(map2::get, () -> "10.10.10.10");
        assertEquals("198.51.100.8", ip2);
    }
}


