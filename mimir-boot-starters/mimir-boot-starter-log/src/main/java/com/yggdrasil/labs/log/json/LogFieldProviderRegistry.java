package com.yggdrasil.labs.log.json;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.yggdrasil.labs.log.field.LogFieldProvider;

/** 内部实现：Spring 上下文向产生线程发布的扩展快照，不属于公共 SPI。 */
public final class LogFieldProviderRegistry {

    private static final AtomicReference<Registration> REGISTRATION =
            new AtomicReference<>(new Registration(null, List.of()));

    private LogFieldProviderRegistry() {}

    /** 覆盖发布已经排序的不可变扩展列表。 */
    public static synchronized void register(Object owner, List<NamedProvider> providers) {
        REGISTRATION.set(new Registration(owner, List.copyOf(providers)));
    }

    /** 只有当前发布者可以清空；其他上下文关闭时不影响已发布扩展。 */
    // owner 是发布者的身份令牌，不能让 equals 相等的其他实例取得清理权限。
    @SuppressWarnings("PMD.CompareObjectsWithEquals")
    public static synchronized void clear(Object owner) {
        if (REGISTRATION.get().owner() == owner) {
            REGISTRATION.set(new Registration(null, List.of()));
        }
    }

    /** 未完成刷新或已关闭时为空列表；读取无锁。 */
    public static List<NamedProvider> current() {
        return REGISTRATION.get().providers();
    }

    /** 扩展的 Spring Bean 名与实例。 */
    public record NamedProvider(String beanName, LogFieldProvider provider) {}

    private record Registration(Object owner, List<NamedProvider> providers) {}
}
