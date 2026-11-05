package com.yggdrasil.labs.web.serialization;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.databind.ser.std.NumberSerializers;
import com.yggdrasil.labs.common.page.PageRequest;
import com.yggdrasil.labs.common.page.PageResult;
import com.yggdrasil.labs.common.response.R;

/** 按模型类型和逻辑属性名保留框架元数据的数字协议。 */
final class MimirMetadataSerializerModifier extends BeanSerializerModifier {

    @Override
    public List<BeanPropertyWriter> changeProperties(
            SerializationConfig config,
            BeanDescription beanDescription,
            List<BeanPropertyWriter> properties) {
        Set<String> metadata = metadataProperties(beanDescription.getBeanClass());
        if (metadata.isEmpty()) {
            return properties;
        }

        // 属性定义保留逻辑名称，避免命名策略或 JsonProperty 重命名使白名单失效。
        Set<String> serializedNames = new HashSet<>();
        for (BeanPropertyDefinition property : beanDescription.findProperties()) {
            if (metadata.contains(property.getInternalName())) {
                serializedNames.add(property.getName());
            }
        }
        for (BeanPropertyWriter property : properties) {
            // 框架分页与时间戳元数据保持数字格式，但不覆盖业务显式序列化配置。
            if (serializedNames.contains(property.getName())
                    && !hasExplicitSerialization(config, property)) {
                property.assignSerializer(new NumberSerializers.LongSerializer(Long.class));
            }
        }
        return properties;
    }

    private static Set<String> metadataProperties(Class<?> beanClass) {
        if (R.class.isAssignableFrom(beanClass)) {
            return Set.of("timestamp");
        }
        if (PageResult.class.isAssignableFrom(beanClass)) {
            return Set.of("totalCount", "pageIndex", "pageSize", "totalPages");
        }
        if (PageRequest.class.isAssignableFrom(beanClass)) {
            return Set.of("pageIndex", "pageSize", "offset");
        }
        return Set.of();
    }

    private static boolean hasExplicitSerialization(
            SerializationConfig config, BeanPropertyWriter property) {
        if (property.hasSerializer()) {
            return true;
        }
        AnnotatedMember member = property.getMember();
        if (member != null
                && config.getAnnotationIntrospector().findSerializationConverter(member) != null) {
            return true;
        }
        JsonFormat.Value format =
                member == null ? null : config.getAnnotationIntrospector().findFormat(member);
        return format != null && format.hasShape();
    }
}
