package com.yggdrasil.labs.log.converter;

/**
 * 测试辅助：跨包重置转换器静态状态
 *
 * @author Yggdrasil Labs
 * @since 2.4.0
 */
public final class SensitiveDataConverterInternals {

    private SensitiveDataConverterInternals() {}

    /** 清除快照并恢复全局 Context 引用 */
    public static void resetForTest(ch.qos.logback.core.Context context) {
        SensitiveDataConverter.resetForTest(context);
    }
}
