package com.yggdrasil.labs.common.batch;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.yggdrasil.labs.common.response.R;

class BatchSerializationTest {

    private static final TypeReference<BatchItemResult<String>> ITEM_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<BatchResult<String>> BATCH_TYPE = new TypeReference<>() {};

    private static BatchResult<String> sample() {
        return BatchResult.of(
                List.of(
                        BatchItemResult.success(0, "a", "ok"),
                        BatchItemResult.failure(
                                1, "b", BatchResultTest.definition("ORDER_NOT_FOUND", "订单不存在"))));
    }

    @Test
    void java_serialization_round_trip() throws Exception {
        BatchResult<String> batch = sample();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(R.success(batch));
        }
        R<?> copy;
        try (ObjectInputStream in =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            copy = (R<?>) in.readObject();
        }

        BatchResult<?> restored = (BatchResult<?>) copy.getData();
        assertEquals(2, restored.getTotalCount());
        assertEquals(1, restored.getSuccessCount());
        assertEquals(BatchStatus.PARTIAL_SUCCESS, restored.getStatus());
        assertEquals("ok", restored.getItems().get(0).getData());
        assertEquals("ORDER_NOT_FOUND", restored.getItems().get(1).getCode());
        assertThrows(UnsupportedOperationException.class, () -> restored.getItems().remove(0));
    }

    @Test
    void default_jackson_output_matches_documented_fields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(sample()));

        assertEquals(
                Set.of("totalCount", "successCount", "failureCount", "status", "items"),
                fieldNames(json));
        assertEquals(2, json.get("totalCount").asInt());
        assertEquals(1, json.get("successCount").asInt());
        assertEquals(1, json.get("failureCount").asInt());
        assertEquals("PARTIAL_SUCCESS", json.get("status").asText());
        JsonNode first = json.get("items").get(0);
        assertEquals(
                Set.of("index", "inputId", "success", "data", "code", "message"),
                fieldNames(first));
        assertEquals(0, first.get("index").asInt());
        assertEquals("a", first.get("inputId").asText());
        assertTrue(first.get("success").asBoolean());
        assertEquals("ok", first.get("data").asText());
        assertTrue(first.get("code").isNull());
        JsonNode second = json.get("items").get(1);
        assertFalse(second.get("success").asBoolean());
        assertTrue(second.get("data").isNull());
        assertEquals("ORDER_NOT_FOUND", second.get("code").asText());
        assertEquals("订单不存在", second.get("message").asText());
    }

    @Test
    void batch_can_be_wrapped_in_r() throws Exception {
        R<BatchResult<String>> response = R.success(sample());
        ObjectMapper mapper = new ObjectMapper();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(response));

        assertTrue(json.get("success").asBoolean());
        assertEquals("PARTIAL_SUCCESS", json.get("data").get("status").asText());
        assertEquals(2, json.get("data").get("items").size());
    }

    @Test
    void test_mapper_simulates_boot_creator_detection() throws Exception {
        // 前置自检：模拟 Boot 的 mapper 必须能经创建器读取合法条目，否则下方不变量用例空转
        BatchItemResult<String> item =
                bootLikeMapper(false)
                        .readValue(
                                "{\"index\":0,\"inputId\":\"a\",\"data\":\"ok\",\"code\":null,\"message\":null}",
                                ITEM_TYPE);

        assertTrue(item.isSuccess());
        assertEquals("ok", item.getData());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void derived_values_cannot_be_overwritten_by_json(boolean failOnUnknown) {
        ObjectMapper mapper = bootLikeMapper(failOnUnknown);
        List<String> itemJsons =
                List.of(
                        "{\"index\":1,\"code\":\"E\",\"success\":true}",
                        "{\"index\":1,\"data\":\"x\",\"success\":false}",
                        "{\"index\":1,\"code\":\"E\",\"data\":\"x\"}",
                        "{\"index\":1,\"message\":\"m\"}",
                        "{\"code\":\"E\"}",
                        "{\"index\":-1,\"data\":\"x\"}",
                        "{\"inputId\":\" \",\"data\":\"x\"}",
                        "{\"index\":0,\"code\":\"00000\"}");

        for (String json : itemJsons) {
            BatchItemResult<String> item = readOrNull(mapper, json, ITEM_TYPE);
            if (item != null) {
                assertItemInvariants(item);
            }
        }

        String batchJson =
                "{\"totalCount\":9,\"successCount\":9,\"failureCount\":0,\"status\":\"ALL_SUCCESS\","
                    + "\"items\":[{\"index\":0,\"code\":\"E\",\"success\":true}]}";
        BatchResult<String> batch = readOrNull(mapper, batchJson, BATCH_TYPE);
        if (batch != null) {
            batch.getItems().forEach(BatchSerializationTest::assertItemInvariants);
            long success = batch.getItems().stream().filter(BatchItemResult::isSuccess).count();
            assertEquals(batch.getItems().size(), batch.getTotalCount());
            assertEquals(success, batch.getSuccessCount());
            assertEquals(batch.getTotalCount() - success, batch.getFailureCount());
        }
    }

    @Test
    void instance_fields_equal_private_constructor_parameters() {
        assertFieldsMatchConstructor(
                BatchItemResult.class, Set.of("index", "inputId", "data", "code", "message"));
        assertFieldsMatchConstructor(BatchResult.class, Set.of("items"));
    }

    private static void assertFieldsMatchConstructor(Class<?> type, Set<String> expected) {
        Set<String> fields =
                Arrays.stream(type.getDeclaredFields())
                        .filter(f -> !Modifier.isStatic(f.getModifiers()))
                        .peek(f -> assertTrue(Modifier.isFinal(f.getModifiers()), f.getName()))
                        .map(Field::getName)
                        .collect(Collectors.toSet());
        Constructor<?>[] constructors = type.getDeclaredConstructors();
        assertEquals(1, constructors.length);
        assertTrue(Modifier.isPrivate(constructors[0].getModifiers()));
        Set<String> parameters =
                Arrays.stream(constructors[0].getParameters())
                        .peek(p -> assertTrue(p.isNamePresent(), "需要 -parameters 编译"))
                        .map(Parameter::getName)
                        .collect(Collectors.toSet());

        assertEquals(expected, fields);
        assertEquals(expected, parameters);
    }

    private static void assertItemInvariants(BatchItemResult<?> item) {
        assertEquals(item.getCode() == null, item.isSuccess());
        if (item.isSuccess()) {
            assertNull(item.getMessage());
        } else {
            assertNull(item.getData());
            assertFalse(item.getCode().isBlank());
            assertNotEquals("00000", item.getCode());
        }
        if (item.getIndex() != null) {
            assertTrue(item.getIndex() >= 0);
        } else {
            assertNotNull(item.getInputId());
            assertFalse(item.getInputId().isBlank());
        }
    }

    private static <T> T readOrNull(ObjectMapper mapper, String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            // 跨服务反序列化不承诺：读取失败同样视为通过
            return null;
        }
    }

    private static ObjectMapper bootLikeMapper(boolean failOnUnknown) {
        return new ObjectMapper()
                .registerModule(new ParameterNamesModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, failOnUnknown);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
