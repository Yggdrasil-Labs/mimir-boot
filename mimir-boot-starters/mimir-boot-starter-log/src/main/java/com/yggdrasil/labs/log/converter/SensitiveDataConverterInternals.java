package com.yggdrasil.labs.log.converter;

/**
 * 转换器静态状态的测试入口
 *
 * <p>内部实现，非公共 API，不承诺兼容。仅供跨包测试重置转换器状态；生产代码不调用。
 *
 * @author Yggdrasil Labs
 * @since 2.4.0
 */
public final class SensitiveDataConverterInternals {

    private SensitiveDataConverterInternals() {}

    /** 测试钩子：清除快照并恢复全局 Context 引用；生产代码不调用 */
    public static void resetForTest(ch.qos.logback.core.Context context) {
        SensitiveDataConverter.resetForTest(context);
    }
}
