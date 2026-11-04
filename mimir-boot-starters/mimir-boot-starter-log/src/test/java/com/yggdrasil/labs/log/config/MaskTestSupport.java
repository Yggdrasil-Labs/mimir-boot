package com.yggdrasil.labs.log.config;

import org.slf4j.LoggerFactory;

import com.yggdrasil.labs.log.converter.SensitiveDataConverterInternals;

/** 测试辅助：清空转换器快照，并把全局 Context 引用恢复为 SLF4J 当前 LoggerContext */
final class MaskTestSupport {

    private MaskTestSupport() {}

    static void reset() {
        SensitiveDataConverterInternals.resetForTest(
                (ch.qos.logback.core.Context) LoggerFactory.getILoggerFactory());
    }
}
