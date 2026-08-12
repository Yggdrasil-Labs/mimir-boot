package com.yggdrasil.labs.common.util;

import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.common.constant.HttpHeaderConstants;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * 客户端 IP 解析工具。
 *
 * <p>默认只信任连接对端地址。转发头只能在调用方已明确识别连接对端为可信代理时，
 * 通过 {@link #resolveForwardedClientIp(UnaryOperator, Supplier, Predicate)} 显式处理。</p>
 */
public final class IpUtils {

    private static final String UNKNOWN = CommonConstants.UNKNOWN;

    private IpUtils() {
    }

    /**
     * 返回 servlet 容器提供的直连对端地址。
     *
     * @param remoteAddrSupplier remoteAddr 供应器，如 request::getRemoteAddr
     * @return 直连对端地址
     */
    public static String resolveClientIp(Supplier<String> remoteAddrSupplier) {
        return Objects.requireNonNull(remoteAddrSupplier, "remoteAddrSupplier 不能为 null").get();
    }

    /**
     * 兼容旧调用点的客户端 IP 解析方法。
     *
     * <p>此方法不再信任任何转发头；请改用 {@link #resolveClientIp(Supplier)}。如网络入口已建立
     * 可信代理边界，可显式调用 {@link #resolveForwardedClientIp(UnaryOperator, Supplier, Predicate)}。</p>
     *
     * @param headerGetter       兼容保留，不再读取
     * @param remoteAddrSupplier remoteAddr 供应器，如 request::getRemoteAddr
     * @return 直连对端地址
     * @deprecated 自 2.1.2 起不再解析转发头，请迁移到单参数 {@link #resolveClientIp(Supplier)}。
     */
    @Deprecated(since = "2.1.2", forRemoval = false)
    public static String resolveClientIp(UnaryOperator<String> headerGetter, Supplier<String> remoteAddrSupplier) {
        Objects.requireNonNull(headerGetter, "headerGetter 不能为 null");
        return resolveClientIp(remoteAddrSupplier);
    }

    /**
     * 在直连对端已被调用方判定为可信代理时，按 X-Forwarded-For 链解析客户端地址。
     *
     * <p>从链条最右侧向左跳过可信代理，返回第一个不可信地址。token 仅做空白与 unknown 过滤，
     * 不会去除方括号、端口或正规化 IPv6；可信判定由调用方提供。</p>
     *
     * @param headerGetter          header 获取函数，如 request::getHeader
     * @param remoteAddrSupplier    remoteAddr 供应器，如 request::getRemoteAddr
     * @param trustedProxyPredicate 可信代理判定
     * @return 客户端地址；未找到有效非可信 token 时返回直连对端地址
     */
    public static String resolveForwardedClientIp(
            UnaryOperator<String> headerGetter,
            Supplier<String> remoteAddrSupplier,
            Predicate<String> trustedProxyPredicate) {
        Objects.requireNonNull(headerGetter, "headerGetter 不能为 null");
        Objects.requireNonNull(remoteAddrSupplier, "remoteAddrSupplier 不能为 null");
        Objects.requireNonNull(trustedProxyPredicate, "trustedProxyPredicate 不能为 null");

        String remoteAddr = remoteAddrSupplier.get();
        if (!trustedProxyPredicate.test(remoteAddr)) {
            return remoteAddr;
        }

        String forwardedFor = headerGetter.apply(HttpHeaderConstants.X_FORWARDED_FOR);
        if (forwardedFor == null) {
            return remoteAddr;
        }

        String[] tokens = forwardedFor.split(",");
        for (int index = tokens.length - 1; index >= 0; index--) {
            String token = tokens[index].trim();
            if (token.isEmpty() || UNKNOWN.equalsIgnoreCase(token)) {
                continue;
            }
            if (!trustedProxyPredicate.test(token)) {
                return token;
            }
        }
        return remoteAddr;
    }
}
