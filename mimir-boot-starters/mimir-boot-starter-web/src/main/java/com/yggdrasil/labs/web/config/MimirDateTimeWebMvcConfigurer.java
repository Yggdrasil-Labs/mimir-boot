package com.yggdrasil.labs.web.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

import org.springframework.boot.autoconfigure.web.servlet.WebMvcProperties;
import org.springframework.core.Ordered;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterFactory;
import org.springframework.format.datetime.standard.Jsr310DateTimeFormatAnnotationFormatterFactory;
import org.springframework.format.datetime.standard.TemporalAccessorParser;
import org.springframework.format.datetime.standard.TemporalAccessorPrinter;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 补充三种本地日期类型的缺省格式，保留 Boot 配置、业务转换器和字段注解。 */
final class MimirDateTimeWebMvcConfigurer implements WebMvcConfigurer, Ordered {

    private final WebProperties properties;

    private final WebMvcProperties mvcProperties;

    MimirDateTimeWebMvcConfigurer(WebProperties properties, WebMvcProperties mvcProperties) {
        this.properties = properties;
        this.mvcProperties = mvcProperties;
    }

    @Override
    public int getOrder() {
        // Boot 在顺序 0 注册业务转换器 Bean，未指定顺序的业务配置器也可随后覆盖缺省格式。
        return -1;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        WebMvcProperties.Format format = mvcProperties.getFormat();
        WebProperties.Serialization serialization = properties.getSerialization();
        // 只为 Boot 未显式配置的类型补缺省格式，避免覆盖 spring.mvc.format。
        if (!StringUtils.hasText(format.getDate())) {
            addFormatter(
                    registry,
                    LocalDate.class,
                    serialization.getDateFormat(),
                    "mimir.boot.web.serialization.date-format");
        }
        if (!StringUtils.hasText(format.getTime())) {
            addFormatter(
                    registry,
                    LocalTime.class,
                    serialization.getTimeFormat(),
                    "mimir.boot.web.serialization.time-format");
        }
        if (!StringUtils.hasText(format.getDateTime())) {
            addFormatter(
                    registry,
                    LocalDateTime.class,
                    serialization.getDateTimeFormat(),
                    "mimir.boot.web.serialization.date-time-format");
        }
        // 普通类型格式器重新注册后，再恢复字段级 @DateTimeFormat 的优先级。
        registry.addFormatterForFieldAnnotation(
                new Jsr310DateTimeFormatAnnotationFormatterFactory());
    }

    private void addFormatter(
            FormatterRegistry registry,
            Class<? extends TemporalAccessor> type,
            String pattern,
            String property) {
        DateTimeFormatter formatter;
        try {
            if (!StringUtils.hasText(pattern)) {
                throw new IllegalArgumentException("日期格式不能为空");
            }
            formatter = new DateTimeFormatterFactory(pattern).createDateTimeFormatter();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("无效的日期格式配置: " + property, ex);
        }
        registry.addFormatterForFieldType(
                type,
                new TemporalAccessorPrinter(formatter),
                new TemporalAccessorParser(type, formatter));
    }
}
