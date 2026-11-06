package com.yggdrasil.labs.exception.handler;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.metadata.ConstraintDescriptor;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.validation.AbstractBindingResult;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.beanvalidation.MethodValidationAdapter;
import org.springframework.validation.beanvalidation.SpringValidatorAdapter;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.yggdrasil.labs.exception.validation.ValidationErrorDetail;
import com.yggdrasil.labs.exception.validation.ValidationErrorSource;

class ValidationErrorExtractorTest {

    @Test
    void shouldRetainFlatPathFallbacksForMalformedListPositionsAndSeparators() throws Exception {
        var parameter = new MethodParameter(Inputs.class.getDeclaredMethod("body", Input.class), 0);
        Map<String, String> paths =
                Map.of(
                        "items[3[4]].name", "items[*].name",
                        "items[3", "items[*]",
                        "items[+3].name", "items[*].name",
                        "..name..", "name",
                        "", "$");
        for (var entry : paths.entrySet()) {
            var result = bindingResult();
            result.addError(safeFieldError(entry.getKey(), "NotNull"));
            assertEquals(
                    entry.getValue(),
                    ValidationErrorExtractor.extract(result, parameter).errors().get(0).path(),
                    entry.getKey());
        }
    }

    @Test
    void shouldRetainExecutableFallbackAndNegativeParameterFailure() {
        var missing =
                declaredViolation(
                        ConstructorInput.class,
                        executableNode("ConstructorInput", List.of(String.class), true),
                        node(ElementKind.PARAMETER, "untrustedName", 0));
        assertEquals(
                "arg0",
                ValidationErrorExtractor.extract(violations(missing)).errors().get(0).path());
        var negative = violation(node(ElementKind.PARAMETER, "input", -1));
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ValidationErrorExtractor.extract(violations(negative)));
        assertEquals("输入参数序号不能为负数", failure.getMessage());
    }

    @Test
    void shouldRetainRealBeanValidationBindingPathsFromWrappedDeclarationMetadata() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var input = new ValidatedInput();
            var validated = new BeanPropertyBindingResult(input, "input");
            new SpringValidatorAdapter(factory.getValidator()).validate(input, validated);
            var guarded = bindingResult();
            validated.getAllErrors().forEach(guarded::addError);
            var data = ValidationErrorExtractor.extract(guarded, (Integer) null);
            assertEquals(
                    List.of("items[0].name", "mapping[*].name", "name"),
                    data.errors().stream().map(ValidationErrorDetail::path).toList());
            assertFalse(
                    data.errors().stream()
                            .anyMatch(error -> error.path().contains("secretToken123")));
        }
    }

    @Test
    void shouldUseStructuredBindingNodesWhenMapKeysImitateDeclaredListPaths() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var input = new MappedInput();
            var nested = new MappedItem();
            nested.name = "valid";
            nested.items = List.of(new Leaf());
            input.mapping =
                    new HashMap<>(
                            Map.of("x].items[987654321", new MappedItem(), "ordinary", nested));
            var result = new BeanPropertyBindingResult(input, "input");
            new SpringValidatorAdapter(factory.getValidator()).validate(input, result);
            var parameter =
                    new MethodParameter(
                            Inputs.class.getDeclaredMethod("mapped", MappedInput.class), 0);
            for (var data :
                    List.of(
                            ValidationErrorExtractor.extract(result, (Integer) null),
                            ValidationErrorExtractor.extract(result, parameter))) {
                assertEquals(
                        List.of("mapping[*].items[0].name", "mapping[*].name"),
                        data.errors().stream().map(ValidationErrorDetail::path).toList());
            }
        }
    }

    @Test
    void shouldStopAtMapBoundaryWhenFlatBindingPathHasNoStructuredNodes() throws Exception {
        var result = bindingResult();
        result.addError(safeFieldError("mapping[x].items[987654321].name", "NotNull"));
        var parameter =
                new MethodParameter(Inputs.class.getDeclaredMethod("mapped", MappedInput.class), 0);
        assertEquals(
                "mapping[*]",
                ValidationErrorExtractor.extract(result, parameter).errors().get(0).path());
    }

    @Test
    void shouldRetainRealMethodValidationContainerPathsWithoutDuplicatingPrefixes()
            throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var method =
                    Inputs.class.getDeclaredMethod("validatedContainers", List.class, Map.class);
            var result =
                    new MethodValidationAdapter(factory.getValidator())
                            .validateArguments(
                                    new Inputs(),
                                    method,
                                    null,
                                    new Object[] {
                                        List.of(new Item()),
                                        Map.of("x].items[987654321", new Item())
                                    },
                                    new Class<?>[0]);
            var data =
                    ValidationErrorExtractor.extract(new HandlerMethodValidationException(result));
            assertEquals(
                    List.of("items[0].name", "mapping[*].name"),
                    data.errors().stream().map(ValidationErrorDetail::path).toList());
        }
    }

    @Test
    void shouldUseUnknownFieldLocationForPolymorphicExecutableArguments() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var method = Inputs.class.getDeclaredMethod("polymorphic", Object.class);
            var violations =
                    factory.getValidator()
                            .forExecutables()
                            .validateParameters(new Inputs(), method, new Object[] {new Item()});
            var data =
                    ValidationErrorExtractor.extract(new ConstraintViolationException(violations));
            assertEquals(1, data.totalCount());
            var detail = data.errors().get(0);
            assertEquals(ValidationErrorSource.FIELD, detail.source());
            assertEquals("$", detail.path());
            assertEquals(0, detail.parameterIndex());
        }
    }

    @Test
    void shouldUseUnknownFieldLocationForNestedObjectDeclarations() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var method = Inputs.class.getDeclaredMethod("nestedPolymorphic", OpaqueInput.class);
            var input = new OpaqueInput();
            var violations =
                    validator
                            .forExecutables()
                            .validateParameters(new Inputs(), method, new Object[] {input});
            var methodResult =
                    new MethodValidationAdapter(validator)
                            .validateArguments(
                                    new Inputs(),
                                    method,
                                    null,
                                    new Object[] {input},
                                    new Class<?>[0]);
            var binding = new BeanPropertyBindingResult(input, "input");
            new SpringValidatorAdapter(validator).validate(input, binding);
            var guarded = bindingResult();
            binding.getAllErrors().forEach(guarded::addError);
            for (var data :
                    List.of(
                            ValidationErrorExtractor.extract(
                                    new ConstraintViolationException(violations)),
                            ValidationErrorExtractor.extract(
                                    new HandlerMethodValidationException(methodResult)),
                            ValidationErrorExtractor.extract(
                                    guarded, new MethodParameter(method, 0)))) {
                assertEquals(1, data.totalCount());
                var detail = data.errors().get(0);
                assertEquals(ValidationErrorSource.FIELD, detail.source());
                assertEquals("$", detail.path());
                assertEquals(0, detail.parameterIndex());
            }
        }
    }

    @Test
    void shouldUseUnknownFieldLocationForPolymorphicContainerElements() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var method = Inputs.class.getDeclaredMethod("opaqueContainers", List.class);
            var arguments = new Object[] {List.of(new Item())};
            var violations =
                    validator.forExecutables().validateParameters(new Inputs(), method, arguments);
            var methodResult =
                    new MethodValidationAdapter(validator)
                            .validateArguments(
                                    new Inputs(), method, null, arguments, new Class<?>[0]);
            for (var data :
                    List.of(
                            ValidationErrorExtractor.extract(
                                    new ConstraintViolationException(violations)),
                            ValidationErrorExtractor.extract(
                                    new HandlerMethodValidationException(methodResult)))) {
                assertEquals(1, data.totalCount());
                var detail = data.errors().get(0);
                assertEquals(ValidationErrorSource.FIELD, detail.source());
                assertEquals("$", detail.path());
                assertEquals(0, detail.parameterIndex());
            }
        }
    }

    @Test
    void shouldDiscardPartialFlatPathsWhenNestedDeclarationsAreUnknown() throws Exception {
        var result = bindingResult();
        for (String path :
                List.of("payload.name", "items[3].name", "rawItems[2].name", "payload[0].name")) {
            result.addError(safeFieldError(path, "NotNull"));
        }
        var parameter =
                new MethodParameter(
                        Inputs.class.getDeclaredMethod("nestedPolymorphic", OpaqueInput.class), 0);
        var data = ValidationErrorExtractor.extract(result, parameter);
        assertEquals(4, data.totalCount());
        assertTrue(
                data.errors().stream()
                        .allMatch(error -> error.source() == ValidationErrorSource.FIELD));
        assertEquals(
                List.of("$", "$", "$", "$"),
                data.errors().stream().map(ValidationErrorDetail::path).toList());
    }

    @Test
    void shouldNotReplaceUnknownFieldLocationWithMethodContainerPrefix() throws Exception {
        var method = Inputs.class.getDeclaredMethod("opaqueContainers", List.class);
        var result = bindingResult();
        result.addError(safeFieldError("name", "NotNull"));
        var errors =
                new ParameterErrors(
                        new MethodParameter(method, 0), new Object(), result, null, 2, null) {
                    @Override
                    public Object getArgument() {
                        throw new AssertionError("不能读取方法实参");
                    }

                    @Override
                    public Object getContainer() {
                        throw new AssertionError("不能读取容器");
                    }

                    @Override
                    public Object getContainerKey() {
                        throw new AssertionError("不能读取Map键");
                    }
                };
        var exception =
                new HandlerMethodValidationException(
                        MethodValidationResult.create(new Inputs(), method, List.of(errors)));
        var detail = ValidationErrorExtractor.extract(exception).errors().get(0);
        assertEquals(ValidationErrorSource.FIELD, detail.source());
        assertEquals("$", detail.path());
        assertEquals(0, detail.parameterIndex());
    }

    @Test
    void shouldNotUsePolymorphicArgumentValuesToGuessFieldDeclarations() {
        var violation =
                declaredViolation(
                        Inputs.class,
                        executableNode("polymorphic", List.of(Object.class), false),
                        node(ElementKind.PARAMETER, "input", 0),
                        node(ElementKind.PROPERTY, "name", null));
        assertEquals(
                "$",
                ValidationErrorExtractor.extract(violations(violation)).errors().get(0).path());
    }

    @Test
    void shouldReadOnlyWrappedViolationRootClassForBindingDeclarations() {
        var error = safeFieldError("name", "NotNull");
        error.wrap(declaredViolation(ValidatedInput.class));
        var result = bindingResult();
        result.addError(error);
        assertEquals(
                "name",
                ValidationErrorExtractor.extract(result, (Integer) null).errors().get(0).path());
    }

    @Test
    void shouldHideUnverifiedFieldNamesWithAndWithoutBindingDeclarations() throws Exception {
        var result = bindingResult();
        result.addError(safeFieldError("secretToken123", "NotNull"));
        assertEquals(
                "$",
                ValidationErrorExtractor.extract(result, (Integer) null).errors().get(0).path());
        var parameter = new MethodParameter(Inputs.class.getDeclaredMethod("body", Input.class), 0);
        assertEquals(
                "$", ValidationErrorExtractor.extract(result, parameter).errors().get(0).path());
    }

    @Test
    void shouldHideCustomPropertyNodesUnlessTheyAreDeclaredOnTheParameterType() {
        var dynamic =
                declaredViolation(
                        Inputs.class,
                        executableNode("containers", List.of(List.class, Map.class), false),
                        node(ElementKind.PARAMETER, "mapping", 1),
                        node(ElementKind.PROPERTY, "secretToken123", null));
        var data = ValidationErrorExtractor.extract(violations(dynamic));
        assertEquals("mapping[*]", data.errors().get(0).path());
        assertFalse(data.errors().get(0).path().contains("secretToken123"));
        var declared =
                declaredViolation(
                        Inputs.class,
                        executableNode("body", List.of(Input.class), false),
                        node(ElementKind.PARAMETER, "input", 0),
                        node(ElementKind.PROPERTY, "items", null),
                        iterableNode(ElementKind.PROPERTY, "name", List.class, 3));
        assertEquals(
                "items[3].name",
                ValidationErrorExtractor.extract(violations(declared)).errors().get(0).path());
        var missing =
                violation(
                        node(ElementKind.PARAMETER, "input", 0),
                        node(ElementKind.PROPERTY, "secretToken123", null));
        assertFalse(
                ValidationErrorExtractor.extract(violations(missing))
                        .errors()
                        .get(0)
                        .path()
                        .contains("secretToken123"));
    }

    @Test
    void shouldUseConstructorDeclarationsToVerifyPropertyNodes() {
        var dynamic =
                declaredViolation(
                        ConstructorInput.class,
                        executableNode("ConstructorInput", List.of(Map.class), true),
                        node(ElementKind.PARAMETER, "mapping", 0),
                        node(ElementKind.PROPERTY, "secretToken123", null));
        assertEquals(
                "mapping[*]",
                ValidationErrorExtractor.extract(violations(dynamic)).errors().get(0).path());
    }

    @Test
    void shouldDistinguishDeclaredCollectionConstraintsFromElementConstraintsWithoutReadingValues()
            throws Exception {
        var method =
                Inputs.class.getDeclaredMethod(
                        "constraints", List.class, Map.class, Map.class, Map.class);
        List<ParameterValidationResult> results =
                List.of(
                        safeParameterResult(new MethodParameter(method, 0), null, "Size"),
                        safeParameterResult(new MethodParameter(method, 1), null),
                        safeParameterResult(new MethodParameter(method, 2), null),
                        safeParameterResult(new MethodParameter(method, 3), null));
        var exception =
                new HandlerMethodValidationException(
                        MethodValidationResult.create(new Inputs(), method, results));
        assertEquals(
                List.of("items", "mapping", "elements[*]", "ambiguous"),
                ValidationErrorExtractor.extract(exception).errors().stream()
                        .map(ValidationErrorDetail::path)
                        .toList());
    }

    private static Path.Node executableNode(
            String name, List<Class<?>> types, boolean constructor) {
        Class<?> nodeType = constructor ? Path.ConstructorNode.class : Path.MethodNode.class;
        return (Path.Node)
                Proxy.newProxyInstance(
                        Path.class.getClassLoader(),
                        new Class<?>[] {nodeType},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getKind" ->
                                            constructor
                                                    ? ElementKind.CONSTRUCTOR
                                                    : ElementKind.METHOD;
                                    case "getName" -> name;
                                    case "getParameterTypes" -> types;
                                    case "as" -> ((Class<?>) args[0]).cast(proxy);
                                    default ->
                                            throw new AssertionError(
                                                    "不能读取可执行节点: " + method.getName());
                                });
    }

    @Test
    void shouldSortBySourceThenParameterIndexThenNormalizedPathAndConstraint() {
        var data =
                ValidationErrorExtractor.extract(
                        violations(
                                sortingViolation(node(ElementKind.PARAMETER, "a", 2)),
                                sortingViolation(node(ElementKind.PARAMETER, "z", 0)),
                                sortingViolation(
                                        node(ElementKind.PARAMETER, "ignored", 2),
                                        node(ElementKind.PROPERTY, "z", null)),
                                sortingViolation(
                                        node(ElementKind.PARAMETER, "ignored", 0),
                                        node(ElementKind.PROPERTY, "z", null)),
                                sortingViolation(
                                        node(ElementKind.PARAMETER, "ignored", 0),
                                        node(ElementKind.PROPERTY, "a", null))));
        assertEquals(
                List.of("a", "z", "z", "z", "a"),
                data.errors().stream().map(ValidationErrorDetail::path).toList());
        assertEquals(
                List.of(0, 0, 2, 0, 2),
                data.errors().stream().map(ValidationErrorDetail::parameterIndex).toList());
        assertEquals(ValidationErrorSource.FIELD, data.errors().get(0).source());
        assertEquals(ValidationErrorSource.PARAMETER, data.errors().get(3).source());
        var result = bindingResult();
        result.addError(safeFieldError("a", "Size"));
        result.addError(safeFieldError("a", "Email"));
        assertEquals(
                List.of("Email", "Size"),
                ValidationErrorExtractor.extract(result, (Integer) null).errors().stream()
                        .map(ValidationErrorDetail::constraint)
                        .toList());
    }

    @Test
    void shouldNeverTreatMapKeyFragmentsAsDeclaredPropertyNames() throws Exception {
        var result = bindingResult();
        result.addError(safeFieldError("mapping[x].password[0].name", "NotNull"));
        var parameter = new MethodParameter(Inputs.class.getDeclaredMethod("body", Input.class), 0);
        assertEquals(
                "mapping[*]",
                ValidationErrorExtractor.extract(result, parameter).errors().get(0).path());
        assertEquals(
                "$",
                ValidationErrorExtractor.extract(result, (Integer) null).errors().get(0).path());
    }

    @Test
    void shouldSortNormalizedErrorsRetainDuplicatesAndTruncateOnlyAboveOneHundred() {
        var first = bindingResult();
        var second = bindingResult();
        List<ObjectError> errors = new ArrayList<>();
        for (int index = 99; index >= 0; index--) {
            errors.add(safeFieldError("field" + index, "NotNull"));
        }
        errors.forEach(first::addError);
        var hundred = ValidationErrorExtractor.extract(first, (Integer) null);
        assertEquals(100, hundred.totalCount());
        assertEquals(100, hundred.errors().size());
        assertFalse(hundred.truncated());
        errors.add(safeFieldError("field0", "NotNull"));
        first.addError(errors.get(100));
        Collections.reverse(errors);
        errors.forEach(second::addError);
        var data = ValidationErrorExtractor.extract(first, (Integer) null);
        assertEquals(data, ValidationErrorExtractor.extract(second, (Integer) null));
        assertEquals(101, data.totalCount());
        assertEquals(100, data.errors().size());
        assertTrue(data.truncated());
        assertEquals(data.errors().get(0), data.errors().get(1));
        assertEquals("$", data.errors().get(0).path());
        assertThrows(UnsupportedOperationException.class, () -> data.errors().clear());
    }

    @Test
    void shouldUseFixedSafeMessagesForEverySupportedConstraintAndUnknownIdentifiers() {
        Map<String, List<String>> constraints =
                Map.of(
                        "不能为空", List.of("NotNull", "NotBlank", "NotEmpty"),
                        "数值不符合约束",
                                List.of(
                                        "Min",
                                        "Max",
                                        "DecimalMin",
                                        "DecimalMax",
                                        "Positive",
                                        "PositiveOrZero",
                                        "Negative",
                                        "NegativeOrZero"),
                        "长度不符合约束", List.of("Size", "Length"),
                        "格式不符合约束", List.of("Email", "Pattern"),
                        "类型不匹配", List.of("typeMismatch"),
                        "参数不符合约束", List.of("MyCustom", "bad code", "A".repeat(65)));
        constraints.forEach(
                (message, codes) ->
                        codes.forEach(
                                code -> {
                                    var result = bindingResult();
                                    result.addError(safeFieldError("name", code));
                                    var detail =
                                            ValidationErrorExtractor.extract(result, (Integer) null)
                                                    .errors()
                                                    .get(0);
                                    assertEquals(message, detail.message());
                                    assertEquals(
                                            code.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")
                                                    ? code
                                                    : "Validation",
                                            detail.constraint());
                                }));
    }

    @Test
    void shouldNormalizeBeforeSortingAndTruncateDeclaredPaths() throws Exception {
        var result = bindingResult();
        result.addError(safeFieldError("na\nme", "NotNull\u0000"));
        result.addError(safeFieldError("name", "NotNull"));
        result.addError(safeFieldError("zchild.".repeat(100) + "name", "MyCustom"));
        var data =
                ValidationErrorExtractor.extract(
                        result,
                        new MethodParameter(
                                Inputs.class.getDeclaredMethod("body", Input.class), 0));
        assertEquals("name", data.errors().get(0).path());
        assertEquals(data.errors().get(0), data.errors().get(1));
        assertEquals("zchild.".repeat(100).substring(0, 256), data.errors().get(2).path());
        assertEquals("不能为空", data.errors().get(0).message());
    }

    @Test
    void shouldReturnEmptyInputSnapshotAndPropagateMetadataFailures() {
        assertEquals(
                0, ValidationErrorExtractor.extract(bindingResult(), (Integer) null).totalCount());
        var broken =
                new AbstractBindingResult("safe") {
                    @Override
                    public Object getTarget() {
                        throw new AssertionError("不能读取绑定目标");
                    }

                    @Override
                    protected Object getActualFieldValue(String name) {
                        throw new AssertionError("不能读取字段值");
                    }

                    @Override
                    public List<ObjectError> getAllErrors() {
                        throw new IllegalStateException("元数据故障");
                    }
                };
        assertThrows(
                IllegalStateException.class,
                () -> ValidationErrorExtractor.extract(broken, (Integer) null));
    }

    @Test
    void shouldHideUnknownContainerPositionsAndUnbalancedMapKeys() throws Exception {
        var result = bindingResult();
        result.addError(safeFieldError("mapping[secret[password]].name", "NotNull"));
        result.addError(safeFieldError("mapping[secret", "NotNull"));
        result.addError(safeFieldError("items[notAnIndex].name", "NotNull"));
        var parameter = new MethodParameter(Inputs.class.getDeclaredMethod("body", Input.class), 0);
        assertEquals(
                List.of("items[*].name", "mapping[*]", "mapping[*]"),
                ValidationErrorExtractor.extract(result, parameter).errors().stream()
                        .map(ValidationErrorDetail::path)
                        .toList());
        var unknown =
                violation(
                        node(ElementKind.PARAMETER, "query", 0),
                        node(ElementKind.PROPERTY, "items", null),
                        iterableNode(ElementKind.PROPERTY, "name", null, 7));
        assertEquals(
                "$", ValidationErrorExtractor.extract(violations(unknown)).errors().get(0).path());
    }

    @Test
    void shouldClassifyEveryViolationAndGiveReturnValuePriority() {
        var input = violation(node(ElementKind.PARAMETER, "query", 0));
        var unknown = violation(node(ElementKind.PROPERTY, "name", null));
        var output =
                violation(
                        node(ElementKind.METHOD, "secretMethod", null),
                        node(ElementKind.RETURN_VALUE, "secretReturn", null));
        assertEquals(
                ValidationErrorExtractor.Origin.INPUT,
                ValidationErrorExtractor.classify(violations(input)));
        assertEquals(
                ValidationErrorExtractor.Origin.UNKNOWN,
                ValidationErrorExtractor.classify(violations()));
        assertEquals(
                ValidationErrorExtractor.Origin.UNKNOWN,
                ValidationErrorExtractor.classify(violations(unknown)));
        assertEquals(
                ValidationErrorExtractor.Origin.UNKNOWN,
                ValidationErrorExtractor.classify(violations(input, unknown)));
        assertEquals(
                ValidationErrorExtractor.Origin.OUTPUT,
                ValidationErrorExtractor.classify(violations(unknown, input, output)));
        assertNull(ValidationErrorExtractor.extract(violations(input, unknown)));
        assertNull(ValidationErrorExtractor.extract(violations(input, output)));
    }

    @Test
    void shouldBuildSafeConstraintPathsFromTypedNodesWithoutReadingValuesOrKeys() {
        var parameter = node(ElementKind.PARAMETER, "input", 0);
        var method = executableNode("body", List.of(Input.class), false);
        var direct = violation(node(ElementKind.PARAMETER, null, 2));
        var field =
                declaredViolation(
                        Inputs.class,
                        method,
                        parameter,
                        node(ElementKind.PROPERTY, "items", null),
                        iterableNode(ElementKind.PROPERTY, "name", List.class, 3));
        var map =
                declaredViolation(
                        Inputs.class,
                        method,
                        parameter,
                        node(ElementKind.PROPERTY, "mapping", null),
                        iterableNode(ElementKind.PROPERTY, "name", Map.class, 123));
        var container =
                declaredViolation(
                        Inputs.class,
                        executableNode("containerOnly", List.of(List.class), false),
                        parameter,
                        iterableNode(
                                ElementKind.CONTAINER_ELEMENT, "<list element>", List.class, 4));
        var bean = violation(parameter, node(ElementKind.BEAN, null, null));
        var cross =
                violation(
                        node(ElementKind.METHOD, "secretMethod", null),
                        node(ElementKind.CROSS_PARAMETER, "<cross-parameter>", null));
        var data =
                ValidationErrorExtractor.extract(
                        violations(direct, field, map, container, bean, cross));
        assertEquals(6, data.totalCount());
        assertEquals(
                List.of("items[3].name", "mapping[*].name", "query[4]", "arg2", "$", "$"),
                data.errors().stream().map(ValidationErrorDetail::path).toList());
        assertEquals(
                List.of(
                        ValidationErrorSource.FIELD,
                        ValidationErrorSource.FIELD,
                        ValidationErrorSource.FIELD,
                        ValidationErrorSource.PARAMETER,
                        ValidationErrorSource.OBJECT,
                        ValidationErrorSource.OBJECT),
                data.errors().stream().map(ValidationErrorDetail::source).toList());
        assertEquals(0, data.errors().get(4).parameterIndex());
        assertNull(data.errors().get(5).parameterIndex());
        assertTrue(
                data.errors().stream()
                        .allMatch(
                                error ->
                                        error.constraint().equals("NotNull")
                                                && error.message().equals("不能为空")));
    }

    private static ConstraintViolation<Object> sortingViolation(Path.Node... nodes) {
        Path.Node[] declared = new Path.Node[nodes.length + 1];
        declared[0] =
                executableNode(
                        "sort",
                        List.of(SortingInput.class, SortingInput.class, SortingInput.class),
                        false);
        System.arraycopy(nodes, 0, declared, 1, nodes.length);
        return declaredViolation(Inputs.class, declared);
    }

    @SafeVarargs
    private static ConstraintViolationException violations(
            ConstraintViolation<Object>... violations) {
        return new ConstraintViolationException("敏感插值消息", new LinkedHashSet<>(List.of(violations)));
    }

    @SuppressWarnings("unchecked")
    private static ConstraintViolation<Object> violation(Path.Node... nodes) {
        return declaredViolation(null, nodes);
    }

    @SuppressWarnings("unchecked")
    private static ConstraintViolation<Object> declaredViolation(
            Class<?> rootClass, Path.Node... nodes) {
        Path path =
                (Path)
                        Proxy.newProxyInstance(
                                Path.class.getClassLoader(),
                                new Class<?>[] {Path.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("iterator"))
                                        return List.of(nodes).iterator();
                                    throw new AssertionError("不能读取完整路径: " + method.getName());
                                });
        ConstraintDescriptor<?> descriptor =
                (ConstraintDescriptor<?>)
                        Proxy.newProxyInstance(
                                ConstraintDescriptor.class.getClassLoader(),
                                new Class<?>[] {ConstraintDescriptor.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("getAnnotation"))
                                        return Item.class
                                                .getDeclaredField("name")
                                                .getAnnotation(NotNull.class);
                                    throw new AssertionError("不能读取约束参数: " + method.getName());
                                });
        return (ConstraintViolation<Object>)
                Proxy.newProxyInstance(
                        ConstraintViolation.class.getClassLoader(),
                        new Class<?>[] {ConstraintViolation.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getPropertyPath" -> path;
                                    case "getConstraintDescriptor" -> descriptor;
                                    case "getRootBeanClass" -> rootClass;
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "equals" -> proxy == args[0];
                                    default ->
                                            throw new AssertionError(
                                                    "不能读取违例值或消息: " + method.getName());
                                });
    }

    private static Path.Node node(ElementKind kind, String name, Integer parameterIndex) {
        return node(kind, name, parameterIndex, null, null, false);
    }

    private static Path.Node iterableNode(
            ElementKind kind, String name, Class<?> containerClass, Integer index) {
        return node(kind, name, null, containerClass, index, true);
    }

    private static Path.Node node(
            ElementKind kind,
            String name,
            Integer parameterIndex,
            Class<?> containerClass,
            Integer index,
            boolean iterable) {
        Class<?> nodeType =
                switch (kind) {
                    case PARAMETER -> Path.ParameterNode.class;
                    case PROPERTY -> Path.PropertyNode.class;
                    case CONTAINER_ELEMENT -> Path.ContainerElementNode.class;
                    case BEAN -> Path.BeanNode.class;
                    default -> Path.Node.class;
                };
        return (Path.Node)
                Proxy.newProxyInstance(
                        Path.class.getClassLoader(),
                        new Class<?>[] {nodeType},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getKind" -> kind;
                                    case "getName" -> name;
                                    case "getParameterIndex" -> parameterIndex;
                                    case "isInIterable" -> iterable;
                                    case "getIndex" -> index;
                                    case "getContainerClass" -> containerClass;
                                    case "as" -> ((Class<?>) args[0]).cast(proxy);
                                    default ->
                                            throw new AssertionError(
                                                    "不能读取节点的值或Map键: " + method.getName());
                                });
    }

    @Test
    void shouldExtractMethodParameterFieldAndBeanGlobalErrorsWithoutSensitiveGetters()
            throws Exception {
        var method = Inputs.class.getDeclaredMethod("body", Input.class);
        var parameter = new MethodParameter(method, 0);
        var binding = bindingResult();
        binding.addError(safeFieldError("items[3].name", "Email"));
        binding.addError(new ObjectError("secret", new String[] {"Custom"}, null, "secret"));
        var bean =
                new ParameterErrors(parameter, new Object(), binding, null, null, null) {
                    @Override
                    public Object getArgument() {
                        throw new AssertionError("不能读取方法实参");
                    }

                    @Override
                    public Object getContainer() {
                        throw new AssertionError("不能读取容器");
                    }

                    @Override
                    public Object getContainerKey() {
                        throw new AssertionError("不能读取Map键");
                    }

                    @Override
                    public String toString() {
                        throw new AssertionError("不能打印验证结果");
                    }
                };
        var value = safeParameterResult(parameter, null);
        var exception =
                new HandlerMethodValidationException(
                        MethodValidationResult.create(new Inputs(), method, List.of(value, bean)));
        assertEquals(
                ValidationErrorExtractor.Origin.INPUT,
                ValidationErrorExtractor.classify(exception));
        var errors = ValidationErrorExtractor.extract(exception).errors();
        assertEquals(3, errors.size());
        assertEquals("items[3].name", errors.get(0).path());
        assertEquals("格式不符合约束", errors.get(0).message());
        assertEquals(ValidationErrorSource.PARAMETER, errors.get(1).source());
        assertEquals("input", errors.get(1).path());
        assertEquals("NotNull", errors.get(1).constraint());
        assertEquals(ValidationErrorSource.OBJECT, errors.get(2).source());
        assertEquals("$", errors.get(2).path());
        assertTrue(errors.stream().allMatch(error -> error.parameterIndex() == 0));
    }

    @Test
    void shouldFallbackToArgIndexAndHideMapKeysInMethodResults() throws Exception {
        var method = Inputs.class.getDeclaredMethod("containers", List.class, Map.class);
        MethodParameter unnamed =
                new MethodParameter(String.class.getMethod("substring", int.class), 0);
        var results =
                List.of(
                        safeParameterResult(unnamed, null),
                        safeParameterResult(new MethodParameter(method, 0), 4),
                        safeParameterResult(new MethodParameter(method, 1), null));
        var exception =
                new HandlerMethodValidationException(
                        MethodValidationResult.create(new Inputs(), method, results));
        assertEquals(
                List.of("arg0", "items[4]", "mapping"),
                ValidationErrorExtractor.extract(exception).errors().stream()
                        .map(ValidationErrorDetail::path)
                        .toList());
    }

    @Test
    void shouldNeverReadReturnValueDetails() throws Exception {
        var method = Inputs.class.getDeclaredMethod("body", Input.class);
        var result = safeParameterResult(new MethodParameter(method, -1), null);
        var exception =
                new HandlerMethodValidationException(
                        MethodValidationResult.create(new Inputs(), method, List.of(result))) {
                    @Override
                    public List<ParameterValidationResult> getAllValidationResults() {
                        throw new AssertionError("不能读取返回值明细");
                    }
                };
        assertEquals(
                ValidationErrorExtractor.Origin.OUTPUT,
                ValidationErrorExtractor.classify(exception));
        assertNull(ValidationErrorExtractor.extract(exception));
    }

    private static ParameterValidationResult safeParameterResult(
            MethodParameter parameter, Integer index) {
        return safeParameterResult(parameter, index, "NotNull");
    }

    private static ParameterValidationResult safeParameterResult(
            MethodParameter parameter, Integer index, String code) {
        MessageSourceResolvable error =
                new MessageSourceResolvable() {
                    @Override
                    public String[] getCodes() {
                        return new String[] {"bad code", code, "bad code"};
                    }

                    @Override
                    public Object[] getArguments() {
                        throw new AssertionError("不能读取消息参数");
                    }

                    @Override
                    public String getDefaultMessage() {
                        throw new AssertionError("不能读取插值消息");
                    }

                    @Override
                    public String toString() {
                        throw new AssertionError("不能打印错误");
                    }
                };
        return new ParameterValidationResult(
                parameter, new Object(), List.of(error), new Object(), index, new Object()) {
            @Override
            public Object getArgument() {
                throw new AssertionError("不能读取方法实参");
            }

            @Override
            public Object getContainer() {
                throw new AssertionError("不能读取容器");
            }

            @Override
            public Object getContainerKey() {
                throw new AssertionError("不能读取Map键");
            }

            @Override
            public String toString() {
                throw new AssertionError("不能打印验证结果");
            }
        };
    }

    @Test
    void shouldExtractFieldAndObjectErrorsWithoutReadingRequestValues() {
        var result = bindingResult();
        result.addError(safeFieldError("name", "NotBlank"));
        result.addError(
                new ObjectError("secretObjectName", new String[] {"Custom"}, null, "secret"));
        var data = ValidationErrorExtractor.extract(result, (Integer) null);
        assertEquals(2, data.totalCount());
        assertEquals(
                new ValidationErrorDetail(
                        ValidationErrorSource.FIELD, "$", null, "NotBlank", "不能为空"),
                data.errors().get(0));
        assertEquals(
                new ValidationErrorDetail(
                        ValidationErrorSource.OBJECT, "$", null, "Custom", "参数不符合约束"),
                data.errors().get(1));
    }

    @Test
    void shouldPreserveOnlyDeclaredListAndArrayIndexesAndHideAllMapKeys() throws Exception {
        var result = bindingResult();
        for (String path :
                List.of(
                        "items[7].name",
                        "array[2].name",
                        "mapping[123].name",
                        "mapping[secret].name")) {
            result.addError(safeFieldError(path, "NotNull"));
        }
        var parameter = new MethodParameter(Inputs.class.getDeclaredMethod("body", Input.class), 0);
        var data = ValidationErrorExtractor.extract(result, parameter);
        assertEquals(
                List.of("array[2].name", "items[7].name", "mapping[*]", "mapping[*]"),
                data.errors().stream().map(ValidationErrorDetail::path).toList());
        assertTrue(data.errors().stream().allMatch(error -> error.parameterIndex() == 0));
        var unknown = ValidationErrorExtractor.extract(result, (Integer) null);
        assertEquals(
                List.of("$", "$", "$", "$"),
                unknown.errors().stream().map(ValidationErrorDetail::path).toList());
    }

    private static AbstractBindingResult bindingResult() {
        return new AbstractBindingResult("secretObjectName") {
            @Override
            public Object getTarget() {
                throw new AssertionError("不能读取绑定目标");
            }

            @Override
            public Object getFieldValue(String field) {
                throw new AssertionError("不能读取字段值");
            }

            @Override
            public Class<?> getFieldType(String field) {
                throw new AssertionError("不能通过目标对象推导字段类型");
            }

            @Override
            protected Object getActualFieldValue(String field) {
                throw new AssertionError("不能读取字段值");
            }
        };
    }

    private static FieldError safeFieldError(String path, String code) {
        return new FieldError(
                "secretObjectName",
                path,
                new Object(),
                false,
                new String[] {code},
                null,
                "secret") {
            @Override
            public Object getRejectedValue() {
                throw new AssertionError("不能读取拒绝值");
            }

            @Override
            public Object[] getArguments() {
                throw new AssertionError("不能读取消息参数");
            }

            @Override
            public String getDefaultMessage() {
                throw new AssertionError("不能读取插值消息");
            }

            @Override
            public String toString() {
                throw new AssertionError("不能打印完整错误");
            }
        };
    }

    static class Inputs {
        void body(Input input) {}

        void mapped(MappedInput input) {}

        void polymorphic(@Valid Object input) {}

        void nestedPolymorphic(@Valid OpaqueInput input) {}

        void opaqueContainers(@Valid List<Object> items) {}

        void validatedContainers(@Valid List<Item> items, @Valid Map<String, Item> mapping) {}

        void containerOnly(List<Item> query) {}

        void sort(SortingInput z, SortingInput ignored, SortingInput a) {}

        void containers(List<Item> items, Map<String, Item> mapping) {}

        void constraints(
                @Size List<Item> items,
                @NotNull Map<String, Item> mapping,
                Map<String, @NotNull Item> elements,
                @NotNull Map<String, @NotNull Item> ambiguous) {}
    }

    static class ConstructorInput {
        ConstructorInput(Map<String, Item> mapping) {}
    }

    static class Input {
        String name;
        Input zchild;
        List<Item> items;
        Item[] array;
        Map<String, Item> mapping;
    }

    static class SortingInput {
        String a;
        String z;
    }

    static class Item {
        @NotNull String name;
    }

    static class OpaqueInput {
        @Valid Object payload = new Item();
        List<Object> items;
        List<?> rawItems;
    }

    static class ValidatedInput {
        @NotNull String name;
        @Valid List<Item> items = List.of(new Item());
        @Valid Map<String, Item> mapping = Map.of("secretToken123", new Item());
    }

    static class MappedInput {
        @Valid Map<String, MappedItem> mapping;

        public Map<String, MappedItem> getMapping() {
            return mapping;
        }
    }

    static class MappedItem {
        @NotNull String name;
        @Valid List<Leaf> items;

        public String getName() {
            return name;
        }

        public List<Leaf> getItems() {
            return items;
        }
    }

    static class Leaf {
        @NotNull String name;

        public String getName() {
            return name;
        }
    }
}
