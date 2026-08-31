package com.yggdrasil.labs.log.converter;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * 在保留 Logback 异常结构的前提下遮蔽异常链中的敏感值。
 *
 * @author Yggdrasil Labs
 * @since 2.2.1
 */
public class SensitiveThrowableProxyConverter extends ThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        if (event.getThrowableProxy() == null) {
            return "";
        }
        return new SensitiveDataConverter().maskSensitiveData(super.convert(event));
    }
}
