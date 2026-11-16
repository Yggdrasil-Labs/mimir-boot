package com.yggdrasil.labs.log.json;

import com.yggdrasil.labs.log.converter.StructuredMaskSupport;

import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.composite.JsonProviders;
import net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders;
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder;

/** Mimir JSON 日志编码器，支持在自定义 Logback XML 中引用。 */
public class MimirJsonEncoder extends LoggingEventCompositeJsonEncoder {
    private boolean explicitProviders;

    @Override
    public void setProviders(JsonProviders<ILoggingEvent> providers) {
        explicitProviders = true;
        super.setProviders(providers);
    }

    @Override
    public void start() {
        StructuredMaskSupport.bindContext(getContext());
        if (!explicitProviders) {
            LoggingEventJsonProviders providers = new LoggingEventJsonProviders();
            providers.addProvider(
                    new GovernedFieldsJsonProvider(LogJsonSettings.from(getContext())));
            super.setProviders(providers);
        }
        super.start();
    }
}
