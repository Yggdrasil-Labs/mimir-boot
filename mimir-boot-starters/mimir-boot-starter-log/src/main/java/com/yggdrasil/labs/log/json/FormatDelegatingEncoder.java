package com.yggdrasil.labs.log.json;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.EncoderBase;

/** 预设的格式委派入口，内部实现，非公共 API。 */
public class FormatDelegatingEncoder extends EncoderBase<ILoggingEvent> {
    private String pattern;
    private Charset charset = StandardCharsets.UTF_8;
    private Encoder<ILoggingEvent> delegate;

    /** 设置原文本格式。 */
    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    /** 设置文本字符集。 */
    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    @Override
    public void start() {
        if ("json".equals(getContext().getProperty(LogJsonKeys.FORMAT))) {
            delegate = JsonEncoderFactory.create(getContext());
        } else {
            PatternLayoutEncoder text = new PatternLayoutEncoder();
            text.setContext(getContext());
            text.setPattern(pattern);
            text.setCharset(charset);
            text.start();
            delegate = text;
        }
        if (delegate.isStarted()) {
            super.start();
        } else {
            addError("日志编码器启动失败");
        }
    }

    @Override
    public byte[] headerBytes() {
        return delegate.headerBytes();
    }

    @Override
    public byte[] encode(ILoggingEvent event) {
        return delegate.encode(event);
    }

    @Override
    public byte[] footerBytes() {
        return delegate.footerBytes();
    }

    @Override
    public void stop() {
        if (delegate != null) {
            delegate.stop();
        }
        super.stop();
    }
}
