package com.yggdrasil.labs.log.json;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ValueNormalizerTest {

    private final List<ProcessingError> errors = new ArrayList<>();

    @Test
    void copiesSupportedValuesIntoIndependentImmutableContainers() {
        List<Object> list = new ArrayList<>(Arrays.asList("a", null, true));
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("list", list);
        raw.put("integer", BigInteger.TEN);
        raw.put("decimal", BigDecimal.ONE);
        ValueNormalizer.Result result = normalize(raw);
        list.set(0, "changed");
        raw.clear();

        Map<?, ?> copy = (Map<?, ?>) result.value();
        assertTrue(result.accepted());
        assertEquals(Arrays.asList("a", null, true), copy.get("list"));
        assertEquals(List.of("list", "integer", "decimal"), new ArrayList<>(copy.keySet()));
        assertThrows(UnsupportedOperationException.class, copy::clear);
        assertThrows(UnsupportedOperationException.class, ((List<?>) copy.get("list"))::clear);
        assertTrue(errors.isEmpty());
    }

    @Test
    void removesFailedMembersAndKeepsOriginalListIndexesAndEscapedPaths() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("a/b", Arrays.asList(1, Double.NaN, new Object(), 4));
        raw.put("self", raw);

        assertEquals(Map.of("a/b", List.of(1, 4)), normalize(raw).value());
        assertEquals(
                List.of(
                        new ProcessingError("/attributes/m/a~1b/1", null, "invalid_value"),
                        new ProcessingError("/attributes/m/a~1b/2", null, "unsupported_type"),
                        new ProcessingError("/attributes/m/self", null, "circular_reference")),
                errors);
    }

    @Test
    void rejectsWholeMapWithInvalidKeyAndNeverStringifiesUnknownObjects() {
        assertFalse(normalize(Map.of(1, "x")).accepted());
        assertEquals(List.of(new ProcessingError("/attributes/m", null, "invalid_value")), errors);
        errors.clear();
        Object unsafe =
                new Object() {
                    @Override
                    public String toString() {
                        fail("不得调用未知对象的 toString");
                        return "secret";
                    }
                };
        for (Object value : List.of(unsafe, Set.of(1), new int[] {1}, 'x', Thread.State.NEW)) {
            assertFalse(normalize(value).accepted());
        }
        assertTrue(errors.stream().allMatch(e -> "unsupported_type".equals(e.reason())));
    }

    @Test
    void detectsDepthLimitButAllowsSharedValuesOutsideCurrentPath() {
        Object nested = "leaf";
        for (int i = 0; i < 34; i++) {
            nested = List.of(nested);
        }
        assertTrue(normalize(nested).accepted());
        assertEquals("invalid_value", errors.get(0).reason());
        errors.clear();
        Map<String, Object> shared = Map.of("n", 1);
        assertEquals(List.of(shared, shared), normalize(List.of(shared, shared)).value());
        assertTrue(errors.isEmpty());
    }

    @Test
    void treatsFirstAttributeValueAsDepthOneAndRejectsDepthThirtyThree() {
        Object allowed = "leaf";
        for (int i = 0; i < 31; i++) {
            allowed = List.of(allowed);
        }
        normalize(allowed);
        assertTrue(errors.isEmpty());
        normalize(List.of(allowed));
        assertEquals(
                List.of(
                        new ProcessingError(
                                "/attributes/m" + "/0".repeat(32), null, "invalid_value")),
                errors);
    }

    @Test
    void rejectsBigNumberSubclassesWithoutInvokingTheirFormatting() {
        BigInteger integer =
                new BigInteger("1") {
                    @Override
                    public String toString() {
                        throw new AssertionError("不得格式化未知 BigInteger 子类");
                    }
                };
        BigDecimal decimal =
                new BigDecimal("1") {
                    @Override
                    public String toString() {
                        throw new AssertionError("不得格式化未知 BigDecimal 子类");
                    }
                };

        assertFalse(normalize(integer).accepted());
        assertFalse(normalize(decimal).accepted());
        assertEquals(
                List.of(
                        new ProcessingError("/attributes/m", null, "unsupported_type"),
                        new ProcessingError("/attributes/m", null, "unsupported_type")),
                errors);
    }

    @Test
    void rejectsMutableBigNumberSubclassesInsteadOfKeepingLiveReferences() {
        AtomicInteger state = new AtomicInteger(1);
        BigInteger integer =
                new BigInteger("1") {
                    @Override
                    public int intValue() {
                        return state.get();
                    }
                };
        BigDecimal decimal =
                new BigDecimal("1") {
                    @Override
                    public int intValue() {
                        return state.get();
                    }
                };
        ValueNormalizer.Result integerResult = normalize(integer);
        ValueNormalizer.Result decimalResult = normalize(decimal);
        state.set(2);

        assertFalse(integerResult.accepted());
        assertFalse(decimalResult.accepted());
        assertEquals(
                List.of(
                        new ProcessingError("/attributes/m", null, "unsupported_type"),
                        new ProcessingError("/attributes/m", null, "unsupported_type")),
                errors);
    }

    private ValueNormalizer.Result normalize(Object value) {
        return ValueNormalizer.normalize(value, JsonPointer.parse("/attributes/m"), errors, null);
    }
}
