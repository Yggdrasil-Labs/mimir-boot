package com.yggdrasil.labs.web.serialization;

import java.io.IOException;
import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.ContainerSerializer;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.ArraySerializerBase;
import com.fasterxml.jackson.databind.ser.std.NumberSerializer;
import com.fasterxml.jackson.databind.ser.std.NumberSerializers;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.databind.type.TypeFactory;

/**
 * Web 内部安全整数输出模块；仅在相应配置开启时安装。
 *
 * <p>本类型用于框架装配，不作为消费者扩展 SPI。
 */
public final class MimirSafeIntegerModule extends SimpleModule {

    // JavaScript 能精确表示的整数边界；超出后以字符串输出，避免客户端精度丢失。
    private static final long SAFE_MAX = 9_007_199_254_740_991L;
    private static final BigInteger BIG_SAFE_MAX = BigInteger.valueOf(SAFE_MAX);
    private static final BigInteger BIG_SAFE_MIN = BIG_SAFE_MAX.negate();

    public MimirSafeIntegerModule() {
        super(MimirSafeIntegerModule.class.getName());
        LongSerializer longSerializer = new LongSerializer();
        addSerializer(Long.class, longSerializer);
        addSerializer(Long.TYPE, longSerializer);
        addSerializer(BigInteger.class, new BigIntegerSerializer());
        addSerializer(long[].class, new LongArraySerializer());
        setSerializerModifier(new MimirMetadataSerializerModifier());
    }

    private static final class LongSerializer extends StdScalarSerializer<Long>
            implements ContextualSerializer {

        private LongSerializer() {
            super(Long.class);
        }

        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            if (value >= -SAFE_MAX && value <= SAFE_MAX) {
                gen.writeNumber(value);
            } else {
                gen.writeString(value.toString());
            }
        }

        @Override
        public JsonSerializer<?> createContextual(
                SerializerProvider provider, BeanProperty property) throws JsonMappingException {
            // 属性显式声明的输出形态优先于全局安全整数策略。
            JsonFormat.Value format = findFormatOverrides(provider, property, handledType());
            if (format != null) {
                if (format.getShape() == JsonFormat.Shape.STRING) {
                    return ToStringSerializer.instance;
                }
                if (format.getShape().isNumeric()) {
                    return new NumberSerializers.LongSerializer(Long.class);
                }
            }
            return this;
        }
    }

    private static final class BigIntegerSerializer extends StdScalarSerializer<BigInteger>
            implements ContextualSerializer {

        private BigIntegerSerializer() {
            super(BigInteger.class);
        }

        @Override
        public void serialize(BigInteger value, JsonGenerator gen, SerializerProvider provider)
                throws IOException {
            if (value.compareTo(BIG_SAFE_MIN) >= 0 && value.compareTo(BIG_SAFE_MAX) <= 0) {
                gen.writeNumber(value);
            } else {
                gen.writeString(value.toString());
            }
        }

        @Override
        public JsonSerializer<?> createContextual(
                SerializerProvider provider, BeanProperty property) throws JsonMappingException {
            // BigInteger 与 Long 保持相同的字段级格式覆盖规则。
            JsonFormat.Value format = findFormatOverrides(provider, property, handledType());
            if (format != null) {
                if (format.getShape() == JsonFormat.Shape.STRING) {
                    return ToStringSerializer.instance;
                }
                if (format.getShape().isNumeric()) {
                    return new NumberSerializer(BigInteger.class);
                }
            }
            return this;
        }
    }

    private static final class LongArraySerializer extends ArraySerializerBase<long[]> {

        private final JsonSerializer<Long> elementSerializer;

        private LongArraySerializer() {
            super(long[].class);
            elementSerializer = new LongSerializer();
        }

        private LongArraySerializer(
                LongArraySerializer source,
                BeanProperty property,
                Boolean unwrapSingle,
                JsonSerializer<Long> elementSerializer) {
            super(source, property, unwrapSingle);
            this.elementSerializer = elementSerializer;
        }

        @Override
        public JsonSerializer<?> _withResolved(BeanProperty property, Boolean unwrapSingle) {
            return new LongArraySerializer(this, property, unwrapSingle, elementSerializer);
        }

        @Override
        @SuppressWarnings("unchecked")
        public JsonSerializer<?> createContextual(
                SerializerProvider provider, BeanProperty property) throws JsonMappingException {
            Boolean unwrapSingle =
                    findFormatFeature(
                            provider,
                            property,
                            long[].class,
                            JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED);
            // 基本类型数组不查询全局 Long serializer，保持 Jackson 原生的数组覆盖边界。
            JsonSerializer<Long> serializer =
                    (JsonSerializer<Long>)
                            new LongSerializer().createContextual(provider, property);
            return new LongArraySerializer(this, property, unwrapSingle, serializer);
        }

        @Override
        public ContainerSerializer<?> _withValueTypeSerializer(TypeSerializer typeSerializer) {
            return this;
        }

        @Override
        public JavaType getContentType() {
            return TypeFactory.defaultInstance().constructType(Long.TYPE);
        }

        @Override
        public JsonSerializer<?> getContentSerializer() {
            return elementSerializer;
        }

        @Override
        public boolean isEmpty(SerializerProvider provider, long[] value) {
            return value.length == 0;
        }

        @Override
        public boolean hasSingleElement(long[] value) {
            return value.length == 1;
        }

        @Override
        protected void serializeContents(
                long[] value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            for (long item : value) {
                elementSerializer.serialize(item, gen, provider);
            }
        }
    }
}
