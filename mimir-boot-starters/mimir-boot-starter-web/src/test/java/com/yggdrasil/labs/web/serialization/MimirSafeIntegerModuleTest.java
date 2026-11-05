package com.yggdrasil.labs.web.serialization;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.util.StdConverter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yggdrasil.labs.common.page.PageRequest;
import com.yggdrasil.labs.common.page.PageResult;
import com.yggdrasil.labs.common.response.R;

/** 安全整数模块的真实 Jackson 序列化测试。 */
class MimirSafeIntegerModuleTest {

    private static final long SAFE_MAX = 9_007_199_254_740_991L;
    private static final long LARGE = SAFE_MAX + 1;

    @Test
    void writesLongBoundariesWithoutLosingPrecision() throws Exception {
        List<Long> values =
                Arrays.asList(
                        Long.MIN_VALUE,
                        -LARGE,
                        -SAFE_MAX,
                        0L,
                        SAFE_MAX,
                        LARGE,
                        Long.MAX_VALUE,
                        null);
        JsonNode result = read(mapper(), values);

        for (int i = 0; i < values.size() - 1; i++) {
            Long value = values.get(i);
            boolean safe = value >= -SAFE_MAX && value <= SAFE_MAX;
            assertEquals(safe, result.get(i).isIntegralNumber(), "值为 " + value);
            assertEquals(value.toString(), result.get(i).asText());
        }
        assertTrue(result.get(7).isNull());
    }

    @Test
    void writesArbitraryBigIntegerBoundariesWithoutNarrowing() throws Exception {
        BigInteger maximum = BigInteger.valueOf(SAFE_MAX);
        List<BigInteger> values =
                List.of(
                        maximum.negate().subtract(BigInteger.ONE),
                        maximum.negate(),
                        BigInteger.ZERO,
                        maximum,
                        maximum.add(BigInteger.ONE),
                        BigInteger.TEN.pow(100));
        JsonNode result = read(mapper(), values);

        for (int i = 0; i < values.size(); i++) {
            BigInteger value = values.get(i);
            boolean safe = value.compareTo(maximum.negate()) >= 0 && value.compareTo(maximum) <= 0;
            assertEquals(safe, result.get(i).isIntegralNumber(), "值为 " + value);
            assertEquals(value.toString(), result.get(i).asText());
        }
    }

    @Test
    void appliesTheSameThresholdToFieldsAndContainersWithoutChangingMapKeys() throws Exception {
        Containers bean = new Containers();
        bean.map.put(LARGE, LARGE);
        bean.map.put(-LARGE, -SAFE_MAX);
        JsonNode result = read(mapper(), bean);

        assertTrue(result.get("primitive").isTextual());
        assertTrue(result.get("boxed").isTextual());
        assertEquals("[1,\"9007199254740992\",null]", result.get("list").toString());
        assertEquals("[1,\"9007199254740992\",null]", result.get("boxedArray").toString());
        assertEquals("[1,\"9007199254740992\"]", result.get("primitiveArray").toString());
        assertEquals("[1,\"9007199254740992\"]", result.get("bigArray").toString());
        assertTrue(result.get("map").get(Long.toString(LARGE)).isTextual());
        assertTrue(result.get("map").get(Long.toString(-LARGE)).isIntegralNumber());
    }

    @Test
    void keepsExistingNullInclusionAndEmptyArrayRules() throws Exception {
        ObjectMapper mapper = mapper().setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        JsonNode result = read(mapper, new EmptyContainers());

        assertTrue(result.isEmpty());
        assertEquals("[]", mapper.writeValueAsString(new long[0]));
        assertEquals("null", mapper.writeValueAsString(null));
    }

    @Test
    void honorsExplicitNumberAndStringShapesForScalarsAndContainerValues() throws Exception {
        JsonNode result = read(mapper(), new FormattedNumbers());

        assertTrue(result.get("numberLong").isIntegralNumber());
        assertTrue(result.get("numberBigInteger").isIntegralNumber());
        assertEquals("1", result.get("stringLong").textValue());
        assertEquals("1", result.get("stringBigInteger").textValue());
        assertEquals("[9007199254740992]", result.get("numberList").toString());
        assertEquals("[\"1\"]", result.get("stringBoxedArray").toString());
        assertEquals("[9007199254740992]", result.get("numberPrimitiveArray").toString());
        assertEquals("[\"1\"]", result.get("stringPrimitiveArray").toString());
    }

    @Test
    void honorsExplicitValueAndContentSerializers() throws Exception {
        JsonNode result = read(mapper(), new ExplicitNumbers());

        assertEquals("custom-9007199254740992", result.get("value").textValue());
        assertEquals("[\"custom-9007199254740992\"]", result.get("list").toString());
        assertEquals("[\"custom-9007199254740992\"]", result.get("array").toString());
        assertEquals("whole-array", result.get("primitiveArray").textValue());
    }

    @Test
    void preservesPrimitiveArraySingletonUnwrapping() throws Exception {
        ObjectMapper mapper =
                mapper().enable(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED);

        assertEquals("\"9007199254740992\"", mapper.writeValueAsString(new long[] {LARGE}));
        JsonNode result = read(mapper, new WrappedArray());
        assertEquals("[\"9007199254740992\"]", result.get("values").toString());
    }

    @Test
    void leavesDeserializationUnchanged() throws Exception {
        ObjectMapper mapper = mapper();

        assertEquals(LARGE, mapper.readValue(Long.toString(LARGE), Long.class));
        assertEquals(LARGE, mapper.readValue("\"" + LARGE + "\"", Long.class));
        assertArrayEquals(
                new long[] {1, LARGE}, mapper.readValue("[1," + LARGE + "]", long[].class));
    }

    @Test
    void keepsOnlyFrameworkMetadataAsNumbers() throws Exception {
        R<Long> response = new R<>();
        response.setTimestamp(LARGE);
        response.setData(LARGE);
        JsonNode responseJson = read(mapper(), response);
        assertTrue(responseJson.get("timestamp").isIntegralNumber());
        assertTrue(responseJson.get("data").isTextual());

        PageResult<Long> page = new PageResult<>();
        page.setTotalCount(LARGE);
        page.setPageIndex(LARGE);
        page.setPageSize(LARGE);
        page.setTotalPages(LARGE);
        page.setData(List.of(LARGE));
        JsonNode pageJson = read(mapper(), page);
        assertNumbers(pageJson, "totalCount", "pageIndex", "pageSize", "totalPages");
        assertTrue(pageJson.get("data").get(0).isTextual());

        PageRequest request = new PageRequest(LARGE + 1, 1L);
        JsonNode requestJson = read(mapper(), request);
        assertNumbers(requestJson, "pageIndex", "pageSize", "offset");
        assertEquals(LARGE, requestJson.get("offset").longValue());

        JsonNode businessJson = read(mapper(), new BusinessMetadata());
        businessJson.forEach(value -> assertTrue(value.isTextual()));
    }

    @Test
    void preservesMetadataForInheritedPropertiesAndSubclassBusinessFields() throws Exception {
        InheritedResponse response = new InheritedResponse();
        response.setTimestamp(LARGE);
        JsonNode responseJson = read(mapper(), response);
        assertNumbers(responseJson, "timestamp");
        assertTrue(responseJson.get("business").isTextual());

        InheritedPage page = new InheritedPage();
        page.setTotalCount(LARGE);
        page.setPageIndex(LARGE);
        page.setPageSize(LARGE);
        page.setTotalPages(LARGE);
        JsonNode pageJson = read(mapper(), page);
        assertNumbers(pageJson, "totalCount", "pageIndex", "pageSize", "totalPages");
        assertTrue(pageJson.get("business").isTextual());

        InheritedRequest request = new InheritedRequest();
        request.setPageIndex(LARGE + 1);
        request.setPageSize(1L);
        JsonNode requestJson = read(mapper(), request);
        assertNumbers(requestJson, "pageIndex", "pageSize", "offset");
        assertTrue(requestJson.get("business").isTextual());
    }

    @Test
    void preservesMetadataWhenSubclassesOverrideGettersWithoutAnnotations() throws Exception {
        assertNumbers(read(mapper(), new OverriddenResponse()), "timestamp");
        assertNumbers(
                read(mapper(), new OverriddenPage()),
                "totalCount",
                "pageIndex",
                "pageSize",
                "totalPages");
        assertNumbers(read(mapper(), new OverriddenRequest()), "pageIndex", "pageSize", "offset");
    }

    @Test
    void honorsExplicitMetadataGetterAnnotationsAndMixIns() throws Exception {
        JsonNode explicitResponse = read(mapper(), new ExplicitResponse());
        assertEquals(Long.toString(LARGE), explicitResponse.get("timestamp").textValue());
        JsonNode explicitPage = read(mapper(), new ExplicitPage());
        assertEquals("custom-" + LARGE, explicitPage.get("totalCount").textValue());

        ObjectMapper mapper = mapper();
        mapper.addMixIn(R.class, ResponseMixin.class);
        mapper.addMixIn(PageResult.class, PageMixin.class);
        mapper.addMixIn(PageRequest.class, RequestMixin.class);
        assertEquals(
                Long.toString(LARGE),
                read(mapper, new OverriddenResponse()).get("timestamp").textValue());
        assertEquals(
                "custom-" + LARGE,
                read(mapper, new OverriddenPage()).get("totalCount").textValue());
        assertEquals(
                Long.toString(LARGE),
                read(mapper, new OverriddenRequest()).get("offset").textValue());
    }

    @Test
    void honorsExplicitMetadataConvertersOnGettersAndMixins() throws Exception {
        assertEquals(
                "converted-" + LARGE,
                read(mapper(), new ConvertedResponse()).get("timestamp").textValue());

        ObjectMapper mapper = mapper();
        mapper.addMixIn(PageResult.class, ConvertedPageMixin.class);
        assertEquals(
                "converted-" + LARGE,
                read(mapper, new OverriddenPage()).get("totalCount").textValue());
    }

    @Test
    void identifiesMetadataByLogicalPropertyEvenWithNamingStrategy() throws Exception {
        ObjectMapper mapper =
                mapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        assertNumbers(
                read(mapper, new OverriddenPage()),
                "total_count",
                "page_index",
                "page_size",
                "total_pages");
        assertNumbers(read(mapper, new OverriddenRequest()), "page_index", "page_size", "offset");
    }

    @Test
    void letsLaterConsumerModuleOverrideFallbacksWhileMetadataStaysNumeric() throws Exception {
        SimpleModule consumer = new SimpleModule("consumer-numbers");
        consumer.addSerializer(Long.class, new PrefixLongSerializer());
        consumer.addSerializer(Long.TYPE, new PrefixLongSerializer());
        consumer.addSerializer(long[].class, new WholeArraySerializer());
        ObjectMapper mapper = mapper().registerModule(consumer);

        assertEquals("\"custom-1\"", mapper.writeValueAsString(1L));
        JsonNode ordinary = read(mapper, new Containers());
        assertEquals("custom-" + LARGE, ordinary.get("primitive").textValue());
        assertEquals("whole-array", ordinary.get("primitiveArray").textValue());
        assertNumbers(read(mapper, new OverriddenResponse()), "timestamp");
        assertNumbers(
                read(mapper, new OverriddenPage()),
                "totalCount",
                "pageIndex",
                "pageSize",
                "totalPages");
        assertNumbers(read(mapper, new OverriddenRequest()), "pageIndex", "pageSize", "offset");

        ObjectMapper reverse = new ObjectMapper().registerModule(consumer);
        reverse.registerModule(new MimirSafeIntegerModule());
        assertEquals("\"" + LARGE + "\"", reverse.writeValueAsString(LARGE));
    }

    @Test
    void checksRealBuilderModuleOrderAndConsumerOverrides() throws Exception {
        SimpleModule consumer = new SimpleModule("consumer-builder-overrides");
        consumer.addSerializer(Long.class, new PrefixLongSerializer());
        consumer.addSerializer(BigInteger.class, new PrefixBigIntegerSerializer());
        consumer.addSerializer(LocalDateTime.class, new CustomDateTimeSerializer());
        MimirSafeIntegerModule safeIntegers = new MimirSafeIntegerModule();
        ObjectMapper mapper =
                Jackson2ObjectMapperBuilder.json()
                        .modulesToInstall(safeIntegers, consumer, new JavaTimeModule())
                        .build();

        List<Object> actualIds = new ArrayList<>(mapper.getRegisteredModuleIds());
        assertTrue(actualIds.indexOf(safeIntegers.getTypeId()) >= 0);
        assertTrue(
                actualIds.indexOf(safeIntegers.getTypeId())
                        < actualIds.indexOf(consumer.getTypeId()));
        assertEquals("\"custom-1\"", mapper.writeValueAsString(1L));
        assertEquals("\"custom-big-1\"", mapper.writeValueAsString(BigInteger.ONE));
        assertEquals(
                "\"custom-date\"", mapper.writeValueAsString(LocalDateTime.of(2026, 11, 4, 1, 2)));
        assertNumbers(read(mapper, new OverriddenResponse()), "timestamp");
    }

    @Test
    void allowsConsumersToSerializeTheEntireFrameworkModel() throws Exception {
        SimpleModule consumer = new SimpleModule("consumer-response");
        consumer.addSerializer(
                OverriddenResponse.class,
                new JsonSerializer<OverriddenResponse>() {
                    @Override
                    public void serialize(
                            OverriddenResponse value,
                            JsonGenerator gen,
                            SerializerProvider provider)
                            throws java.io.IOException {
                        gen.writeString("whole-response");
                    }
                });

        assertEquals(
                "\"whole-response\"",
                mapper().registerModule(consumer).writeValueAsString(new OverriddenResponse()));
    }

    @Test
    void propagatesConsumerSerializationFailures() {
        JsonMappingException failure =
                assertThrows(
                        JsonMappingException.class,
                        () -> mapper().writeValueAsString(new FailingNumber()));

        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals("消费者序列化失败", failure.getCause().getMessage());
    }

    @Test
    void handlesAllPrimitiveArrayBoundariesAndLeavesOtherNumbersUnchanged() throws Exception {
        ObjectMapper mapper = mapper();
        long[] values = {Long.MIN_VALUE, -LARGE, -SAFE_MAX, 0, SAFE_MAX, LARGE, Long.MAX_VALUE};

        assertEquals(
                mapper.writeValueAsString(Arrays.stream(values).boxed().toList()),
                mapper.writeValueAsString(values));
        assertEquals("123", mapper.writeValueAsString(123));
        assertEquals("1.25", mapper.writeValueAsString(new java.math.BigDecimal("1.25")));
        assertEquals("1.25", mapper.writeValueAsString(1.25d));
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new MimirSafeIntegerModule());
    }

    private static JsonNode read(ObjectMapper mapper, Object value) throws Exception {
        return mapper.readTree(mapper.writeValueAsString(value));
    }

    private static void assertNumbers(JsonNode result, String... properties) {
        for (String property : properties) {
            assertTrue(result.get(property).isIntegralNumber(), property + ": " + result);
        }
    }

    static class BusinessMetadata {
        public Long timestamp = LARGE;
        public Long totalCount = LARGE;
        public Long pageIndex = LARGE;
        public Long pageSize = LARGE;
        public Long totalPages = LARGE;
        public Long offset = LARGE;
    }

    static class InheritedResponse extends R<String> {
        public Long business = LARGE;
    }

    static class InheritedPage extends PageResult<String> {
        public Long business = LARGE;
    }

    static class InheritedRequest extends PageRequest {
        public Long business = LARGE;
    }

    static class OverriddenResponse extends R<String> {
        @Override
        public Long getTimestamp() {
            return LARGE;
        }
    }

    static class OverriddenPage extends PageResult<String> {
        @Override
        public Long getTotalCount() {
            return LARGE;
        }

        @Override
        public Long getPageIndex() {
            return LARGE;
        }

        @Override
        public Long getPageSize() {
            return LARGE;
        }

        @Override
        public Long getTotalPages() {
            return LARGE;
        }
    }

    static class OverriddenRequest extends PageRequest {
        @Override
        public Long getPageIndex() {
            return LARGE;
        }

        @Override
        public Long getPageSize() {
            return LARGE;
        }

        @Override
        public Long getOffset() {
            return LARGE;
        }
    }

    static class ExplicitResponse extends OverriddenResponse {
        @Override
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long getTimestamp() {
            return super.getTimestamp();
        }
    }

    static class ConvertedResponse extends OverriddenResponse {
        @Override
        @JsonSerialize(converter = PrefixLongConverter.class)
        public Long getTimestamp() {
            return super.getTimestamp();
        }
    }

    abstract static class ConvertedPageMixin {
        @JsonSerialize(converter = PrefixLongConverter.class)
        abstract Long getTotalCount();
    }

    public static class PrefixLongConverter extends StdConverter<Long, String> {
        @Override
        public String convert(Long value) {
            return "converted-" + value;
        }
    }

    static class ExplicitPage extends OverriddenPage {
        @Override
        @JsonSerialize(using = PrefixLongSerializer.class)
        public Long getTotalCount() {
            return super.getTotalCount();
        }
    }

    abstract static class ResponseMixin {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        abstract Long getTimestamp();
    }

    abstract static class PageMixin {
        @JsonSerialize(using = PrefixLongSerializer.class)
        abstract Long getTotalCount();
    }

    abstract static class RequestMixin {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        abstract Long getOffset();
    }

    static class Containers {
        public long primitive = LARGE;
        public Long boxed = LARGE;
        public List<Long> list = Arrays.asList(1L, LARGE, null);
        public Long[] boxedArray = {1L, LARGE, null};
        public long[] primitiveArray = {1L, LARGE};
        public BigInteger[] bigArray = {BigInteger.ONE, BigInteger.valueOf(LARGE)};
        public Map<Long, Long> map = new LinkedHashMap<>();
    }

    static class EmptyContainers {
        public Long value;
        public long[] values = {};
    }

    static class FormattedNumbers {
        @JsonFormat(shape = JsonFormat.Shape.NUMBER)
        public Long numberLong = LARGE;

        @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
        public BigInteger numberBigInteger = BigInteger.TEN.pow(30);

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public long stringLong = 1;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public BigInteger stringBigInteger = BigInteger.ONE;

        @JsonFormat(shape = JsonFormat.Shape.NUMBER)
        public List<Long> numberList = List.of(LARGE);

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public Long[] stringBoxedArray = {1L};

        @JsonFormat(shape = JsonFormat.Shape.NUMBER)
        public long[] numberPrimitiveArray = {LARGE};

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public long[] stringPrimitiveArray = {1};
    }

    static class ExplicitNumbers {
        @JsonSerialize(using = PrefixLongSerializer.class)
        public Long value = LARGE;

        @JsonSerialize(contentUsing = PrefixLongSerializer.class)
        public List<Long> list = List.of(LARGE);

        @JsonSerialize(contentUsing = PrefixLongSerializer.class)
        public Long[] array = {LARGE};

        @JsonSerialize(using = WholeArraySerializer.class)
        public long[] primitiveArray = {LARGE};
    }

    static class WrappedArray {
        @JsonFormat(without = JsonFormat.Feature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
        public long[] values = {LARGE};
    }

    public static class PrefixLongSerializer extends JsonSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider provider)
                throws java.io.IOException {
            gen.writeString("custom-" + value);
        }
    }

    public static class WholeArraySerializer extends JsonSerializer<long[]> {
        @Override
        public void serialize(long[] value, JsonGenerator gen, SerializerProvider provider)
                throws java.io.IOException {
            gen.writeString("whole-array");
        }
    }

    public static class PrefixBigIntegerSerializer extends JsonSerializer<BigInteger> {
        @Override
        public void serialize(BigInteger value, JsonGenerator gen, SerializerProvider provider)
                throws java.io.IOException {
            gen.writeString("custom-big-" + value);
        }
    }

    public static class CustomDateTimeSerializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider)
                throws java.io.IOException {
            gen.writeString("custom-date");
        }
    }

    static class FailingNumber {
        @JsonSerialize(using = FailingLongSerializer.class)
        public Long value = LARGE;
    }

    public static class FailingLongSerializer extends JsonSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializerProvider provider) {
            throw new IllegalStateException("消费者序列化失败");
        }
    }
}
