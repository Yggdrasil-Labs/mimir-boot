package com.yggdrasil.labs.log.json;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.encoder.Encoder;

/** 隔离 JSON 依赖，避免文本路径加载可选依赖。 */
final class JsonEncoderFactory {
    private JsonEncoderFactory() {}

    static Encoder<ILoggingEvent> create(Context context) {
        MimirJsonEncoder encoder = new MimirJsonEncoder();
        encoder.setContext(context);
        encoder.start();
        return encoder;
    }
}
