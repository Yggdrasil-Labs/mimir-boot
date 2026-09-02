package com.yggdrasil.labs.log.converter;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * 在保留 Logback 异常结构的前提下遮蔽异常链中的敏感值。
 *
 * @author Yggdrasil Labs
 * @since 2.2.1
 */
@SuppressWarnings("java:S110") // Logback conversionRule 要求继承 ThrowableProxyConverter 才能保留异常渲染契约。
public class SensitiveThrowableProxyConverter extends ThrowableProxyConverter {

    private final SensitiveDataConverter dataConverter = new SensitiveDataConverter();

    @Override
    public void start() {
        dataConverter.setContext(getContext());
        dataConverter.start();
        super.start();
    }

    @Override
    public void stop() {
        dataConverter.stop();
        super.stop();
    }

    @Override
    public String convert(ILoggingEvent event) {
        if (event.getThrowableProxy() == null) {
            return "";
        }
        return dataConverter.maskSensitiveData(super.convert(event));
    }
}
