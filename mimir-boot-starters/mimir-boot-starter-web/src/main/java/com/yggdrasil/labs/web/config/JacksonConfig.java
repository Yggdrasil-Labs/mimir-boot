package com.yggdrasil.labs.web.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 序列化配置
 *
 * <p>功能说明：</p>
 * <ul>
 * <li>统一配置日期时间格式</li>
 * <li>配置空值处理策略</li>
 * <li>配置序列化特性</li>
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(ObjectMapper.class)
public class JacksonConfig {

    private final WebProperties webProperties;

    /**
     * 配置 Jackson ObjectMapper 自定义器
     * <p>
     * 使用 Jackson2ObjectMapperBuilderCustomizer 来配置 Jackson，
     * 这是 Spring Boot 推荐的方式，不会覆盖已有的 ObjectMapper Bean
     * </p>
     *
     * @return Jackson 自定义器
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            WebProperties.Serialization serialization = webProperties.getSerialization();

            // 注册 JavaTimeModule 并配置日期时间格式
            JavaTimeModule javaTimeModule = new JavaTimeModule();

            // 配置 LocalDateTime 序列化/反序列化
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(serialization.getDateTimeFormat());
            javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
            javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormatter));

            // 配置 LocalDate 序列化/反序列化
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(serialization.getDateFormat());
            javaTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(dateFormatter));
            javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(dateFormatter));

            // 配置 LocalTime 序列化/反序列化
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern(serialization.getTimeFormat());
            javaTimeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(timeFormatter));
            javaTimeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(timeFormatter));

            // 配置模块和时区
            builder.modules(javaTimeModule);
            builder.timeZone(java.util.TimeZone.getTimeZone(serialization.getTimeZone()));

            // 配置序列化特性
            if (!serialization.isWriteNulls()) {
                builder.serializationInclusion(JsonInclude.Include.NON_NULL);
            }

            if (serialization.isPrettyPrint()) {
                builder.featuresToEnable(SerializationFeature.INDENT_OUTPUT);
            }

            // 配置反序列化特性
            if (serialization.isIgnoreUnknownProperties()) {
                builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            }

            // 禁用将日期写为时间戳
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}

