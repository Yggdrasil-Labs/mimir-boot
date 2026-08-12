package com.yggdrasil.labs.common.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IpUtilsTest {

    private static final Predicate<String> TRUSTED_PRIVATE_PROXY = ip -> ip.startsWith("10.");

    @Test
    void resolveClientIpUsesOnlyDirectRemoteAddress() {
        assertEquals("198.51.100.10", resolveClientIp(() -> "198.51.100.10"));
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedResolveClientIpIgnoresForwardedHeaders() {
        assertEquals("198.51.100.10", IpUtils.resolveClientIp(
                header -> "203.0.113.10",
                () -> "198.51.100.10"));
    }

    @Test
    void forwardedResolutionDoesNotReadHeadersForUntrustedDirectPeer() {
        AtomicBoolean headerRead = new AtomicBoolean();

        String resolved = resolveForwardedClientIp(header -> {
            headerRead.set(true);
            throw new AssertionError("直连不可信时不得读取转发头");
        }, () -> "198.51.100.10", TRUSTED_PRIVATE_PROXY);

        assertEquals("198.51.100.10", resolved);
        assertFalse(headerRead.get());
    }

    @Test
    void forwardedResolutionSkipsTrustedProxiesFromRightToLeft() {
        String resolved = resolveForwardedClientIp(
                header -> "198.51.100.20, 10.0.0.7, 10.0.0.8",
                () -> "10.0.0.9",
                TRUSTED_PRIVATE_PROXY);

        assertEquals("198.51.100.20", resolved);
    }

    @Test
    void forwardedResolutionIgnoresUnknownAndEmptyTokens() {
        String resolved = resolveForwardedClientIp(
                header -> " unknown, , 198.51.100.21 , 10.0.0.8, UNKNOWN ",
                () -> "10.0.0.9",
                TRUSTED_PRIVATE_PROXY);

        assertEquals("198.51.100.21", resolved);
    }

    @Test
    void forwardedResolutionRetainsIpv6TokenWithoutNormalizingIt() {
        String resolved = resolveForwardedClientIp(
                header -> "2001:db8::10, 2001:db8::ff",
                () -> "2001:db8::ff",
                "2001:db8::ff"::equals);

        assertEquals("2001:db8::10", resolved);
    }

    @Test
    void forwardedResolutionFallsBackToDirectPeerWhenAllTokensAreTrusted() {
        String resolved = resolveForwardedClientIp(
                header -> "10.0.0.7, 10.0.0.8",
                () -> "10.0.0.9",
                TRUSTED_PRIVATE_PROXY);

        assertEquals("10.0.0.9", resolved);
    }

    @Test
    void forwardedResolutionRejectsNullCollaborators() {
        assertThrows(NullPointerException.class,
                () -> resolveForwardedClientIp(null, () -> "10.0.0.9", TRUSTED_PRIVATE_PROXY));
    }

    private static String resolveClientIp(Supplier<String> remoteAddrSupplier) {
        return invoke("resolveClientIp", new Class<?>[]{Supplier.class}, remoteAddrSupplier);
    }

    private static String resolveForwardedClientIp(
            UnaryOperator<String> headerGetter,
            Supplier<String> remoteAddrSupplier,
            Predicate<String> trustedProxyPredicate) {
        return invoke(
                "resolveForwardedClientIp",
                new Class<?>[]{UnaryOperator.class, Supplier.class, Predicate.class},
                headerGetter,
                remoteAddrSupplier,
                trustedProxyPredicate);
    }

    private static String invoke(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = IpUtils.class.getMethod(methodName, parameterTypes);
            return (String) method.invoke(null, arguments);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError("IP 解析 API 调用失败", ex.getCause());
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("缺少约定的 IP 解析 API: " + methodName, ex);
        }
    }
}
