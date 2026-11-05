package com.yggdrasil.labs.exception.handler;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;

import org.springframework.beans.BeanUtils;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.util.ReflectionUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import com.yggdrasil.labs.exception.validation.ValidationErrorData;
import com.yggdrasil.labs.exception.validation.ValidationErrorDetail;
import com.yggdrasil.labs.exception.validation.ValidationErrorSource;

/** 仅从框架声明元数据构造结构化校验明细。 */
final class ValidationErrorExtractor {

    enum Origin {
        INPUT,
        OUTPUT,
        UNKNOWN
    }

    private static final int MAX_ERRORS = 100;

    private static final Comparator<ValidationErrorDetail> ORDER =
            Comparator.comparing(ValidationErrorDetail::source)
                    .thenComparing(
                            ValidationErrorDetail::parameterIndex,
                            Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(ValidationErrorDetail::path)
                    .thenComparing(ValidationErrorDetail::constraint)
                    .thenComparing(ValidationErrorDetail::message);

    private ValidationErrorExtractor() {}

    static Origin classify(HandlerMethodValidationException exception) {
        return exception.isForReturnValue() ? Origin.OUTPUT : Origin.INPUT;
    }

    static Origin classify(ConstraintViolationException exception) {
        // 只有每条违例都能证明来自输入才返回 400；混入返回值时优先按输出处理。
        boolean allInput = !exception.getConstraintViolations().isEmpty();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            boolean input = false;
            for (Path.Node node : violation.getPropertyPath()) {
                ElementKind kind = node.getKind();
                if (kind == ElementKind.RETURN_VALUE) {
                    return Origin.OUTPUT;
                }
                if (kind == ElementKind.PARAMETER || kind == ElementKind.CROSS_PARAMETER) {
                    input = true;
                }
            }
            allInput &= input;
        }
        return allInput ? Origin.INPUT : Origin.UNKNOWN;
    }

    static ValidationErrorData extract(HandlerMethodValidationException exception) {
        if (classify(exception) != Origin.INPUT) {
            return null;
        }
        List<ValidationErrorDetail> details = new ArrayList<>();
        for (ParameterValidationResult result : exception.getAllValidationResults()) {
            MethodParameter parameter = result.getMethodParameter();
            int parameterIndex = parameter.getParameterIndex();
            if (parameterIndex < 0) {
                return null;
            }
            ResolvableType type = ResolvableType.forMethodParameter(parameter);
            String name = parameterName(parameter);
            Integer index = result.getContainerIndex();
            boolean container = index != null || container(type);
            String position = container ? position(index, indexed(type)) : "";
            // Spring 将级联字段错误包装为 ParameterErrors；普通参数错误走另一条路径。
            if (result instanceof ParameterErrors errors) {
                String prefix = container ? name + position + "." : "";
                ResolvableType beanType = container ? elementType(type) : type;
                for (ObjectError error : errors.getAllErrors()) {
                    details.add(bindingError(error, parameterIndex, beanType, prefix));
                }
            } else {
                for (MessageSourceResolvable error : result.getResolvableErrors()) {
                    String code = constraint(error);
                    details.add(
                            detail(
                                    ValidationErrorSource.PARAMETER,
                                    name + parameterPosition(parameter, type, index, code),
                                    parameterIndex,
                                    code));
                }
            }
        }
        return snapshot(details);
    }

    static ValidationErrorData extract(ConstraintViolationException exception) {
        if (classify(exception) != Origin.INPUT) {
            return null;
        }
        List<ValidationErrorDetail> details = new ArrayList<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            ViolationPath path = violationPath(violation);
            String code =
                    violation
                            .getConstraintDescriptor()
                            .getAnnotation()
                            .annotationType()
                            .getSimpleName();
            details.add(detail(path.source(), path.path(), path.parameterIndex(), code));
        }
        return snapshot(details);
    }

    private static ViolationPath violationPath(ConstraintViolation<?> violation) {
        return violationPath(violation, ResolvableType.NONE);
    }

    private static ViolationPath violationPath(
            ConstraintViolation<?> violation, ResolvableType rootType) {
        Integer parameterIndex = null;
        String parameterName = "";
        StringBuilder properties = new StringBuilder();
        boolean object = false;
        boolean field = false;
        Executable executable = null;
        Class<?> declaringClass = null;
        ResolvableType type = rootType;
        boolean hidden = false;
        for (Path.Node node : violation.getPropertyPath()) {
            switch (node.getKind()) {
                case METHOD, CONSTRUCTOR -> {
                    declaringClass = violation.getRootBeanClass();
                    executable = declaredExecutable(declaringClass, node);
                }
                case PARAMETER -> {
                    parameterIndex = node.as(Path.ParameterNode.class).getParameterIndex();
                    if (parameterIndex < 0) {
                        throw new IllegalArgumentException("输入参数序号不能为负数");
                    }
                    if (executable != null && parameterIndex < executable.getParameterCount()) {
                        MethodParameter parameter =
                                MethodParameter.forExecutable(executable, parameterIndex)
                                        .withContainingClass(declaringClass);
                        parameterName = parameterName(parameter);
                        type = ResolvableType.forMethodParameter(parameter);
                    } else {
                        parameterName = "arg" + parameterIndex;
                    }
                }
                case CROSS_PARAMETER -> {
                    object = true;
                    parameterIndex = null;
                }
                case BEAN -> object = true;
                case PROPERTY -> {
                    if (hidden) continue;
                    type = appendPosition(properties, parameterName, node, type);
                    // 只接受声明类型可确认的属性，不从实际参数或容器内容推断路径。
                    String name = withoutControls(node.getName());
                    ResolvableType property =
                            identifier(name) && !container(type)
                                    ? propertyType(type, name)
                                    : ResolvableType.NONE;
                    if (property == ResolvableType.NONE) {
                        if (container(type) && !node.isInIterable()) {
                            if (properties.length() == 0) properties.append(parameterName);
                            properties.append("[*]");
                        } else {
                            // 子字段不可确认时，已有前缀也不能作为完整字段位置。
                            properties.setLength(0);
                        }
                        hidden = true;
                    } else {
                        if (properties.length() > 0) properties.append('.');
                        properties.append(name);
                        type = property;
                    }
                    field = true;
                }
                case CONTAINER_ELEMENT -> {
                    if (hidden) continue;
                    type = appendPosition(properties, parameterName, node, type);
                    field = true;
                }
                default -> {
                    // 方法名、构造器名和返回值等不属于输入字段路径。
                }
            }
        }
        if (object) {
            return new ViolationPath(ValidationErrorSource.OBJECT, "$", parameterIndex);
        }
        if (field) {
            return new ViolationPath(
                    ValidationErrorSource.FIELD,
                    properties.length() == 0 ? "$" : properties.toString(),
                    parameterIndex);
        }
        return new ViolationPath(ValidationErrorSource.PARAMETER, parameterName, parameterIndex);
    }

    private static Executable declaredExecutable(Class<?> rootClass, Path.Node node) {
        if (rootClass == null) return null;
        if (node.getKind() == ElementKind.METHOD) {
            List<Class<?>> parameters = node.as(Path.MethodNode.class).getParameterTypes();
            return ReflectionUtils.findMethod(
                    rootClass, node.getName(), parameters.toArray(Class<?>[]::new));
        }
        List<Class<?>> parameters = node.as(Path.ConstructorNode.class).getParameterTypes();
        try {
            return rootClass.getDeclaredConstructor(parameters.toArray(Class<?>[]::new));
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static ResolvableType appendPosition(
            StringBuilder path, String parameterName, Path.Node node, ResolvableType type) {
        if (!node.isInIterable() || !container(type)) {
            return type;
        }
        if (path.length() == 0) path.append(parameterName);
        // 仅数组和 List 保留数字索引；Map 等容器统一使用通配符，不读取键。
        path.append(position(node.getIndex(), indexed(type)));
        return elementType(type);
    }

    private record ViolationPath(
            ValidationErrorSource source, String path, Integer parameterIndex) {}

    static ValidationErrorData extract(BindingResult result, Integer parameterIndex) {
        return bindingErrors(result.getAllErrors(), parameterIndex, ResolvableType.NONE);
    }

    static ValidationErrorData extract(BindingResult result, MethodParameter parameter) {
        return bindingErrors(
                result.getAllErrors(),
                parameter.getParameterIndex(),
                ResolvableType.forMethodParameter(parameter));
    }

    private static ValidationErrorData bindingErrors(
            List<ObjectError> errors, Integer parameterIndex, ResolvableType type) {
        List<ValidationErrorDetail> details = new ArrayList<>();
        for (ObjectError error : errors) {
            details.add(bindingError(error, parameterIndex, type, ""));
        }
        return snapshot(details);
    }

    private static ValidationErrorDetail bindingError(
            ObjectError error, Integer parameterIndex, ResolvableType type, String prefix) {
        if (error instanceof FieldError fieldError) {
            if (error.contains(ConstraintViolation.class)) {
                // 结构化节点能区分 Map 键与后续属性；扁平字段名不能可靠区分。
                ConstraintViolation<?> violation = error.unwrap(ConstraintViolation.class);
                if (type.resolve() == null) {
                    Class<?> rootClass = violation.getRootBeanClass();
                    if (rootClass != null) type = ResolvableType.forClass(rootClass);
                }
                ViolationPath structured = violationPath(violation, type);
                if (structured.source() == ValidationErrorSource.FIELD) {
                    return detail(
                            ValidationErrorSource.FIELD,
                            structured.path(),
                            parameterIndex,
                            error.getCode());
                }
            }
            String path = fieldPath(fieldError.getField(), type);
            if (!"$".equals(path)) {
                path = prefix + path;
            }
            return detail(ValidationErrorSource.FIELD, path, parameterIndex, error.getCode());
        }
        return detail(ValidationErrorSource.OBJECT, "$", parameterIndex, error.getCode());
    }

    private static ValidationErrorData snapshot(List<ValidationErrorDetail> details) {
        // 先按输出字段排序再截断，避免校验器的遍历顺序影响前 100 项。
        details.sort(ORDER);
        int total = details.size();
        return new ValidationErrorData(
                details.subList(0, Math.min(total, MAX_ERRORS)), total, total > MAX_ERRORS);
    }

    private static ValidationErrorDetail detail(
            ValidationErrorSource source, String path, Integer parameterIndex, String code) {
        ValidationErrorDetail normalized =
                new ValidationErrorDetail(
                        source, path, parameterIndex, code == null ? "Validation" : code, "");
        String message =
                switch (normalized.constraint()) {
                    case "NotNull", "NotBlank", "NotEmpty" -> "不能为空";
                    case "Min",
                                    "Max",
                                    "DecimalMin",
                                    "DecimalMax",
                                    "Positive",
                                    "PositiveOrZero",
                                    "Negative",
                                    "NegativeOrZero" ->
                            "数值不符合约束";
                    case "Size", "Length" -> "长度不符合约束";
                    case "Email", "Pattern" -> "格式不符合约束";
                    case "typeMismatch" -> "类型不匹配";
                    default -> "参数不符合约束";
                };
        return new ValidationErrorDetail(
                source, normalized.path(), parameterIndex, normalized.constraint(), message);
    }

    private static String constraint(MessageSourceResolvable error) {
        if (error instanceof ObjectError objectError) {
            return objectError.getCode();
        }
        String[] codes = error.getCodes();
        if (codes != null) {
            for (int index = codes.length - 1; index >= 0; index--) {
                String code = withoutControls(codes[index]);
                if (code != null && code.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
                    return code;
                }
            }
        }
        return "Validation";
    }

    /** 仅反射声明类型，不触碰绑定目标、属性访问器或容器值。 */
    private static ResolvableType propertyType(ResolvableType owner, String name) {
        Class<?> resolved = owner.resolve();
        for (Class<?> current = resolved; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                return ResolvableType.forField(field, owner);
            } catch (NoSuchFieldException ignored) {
                // 继续查询继承的声明。
            }
        }
        if (resolved != null) {
            PropertyDescriptor descriptor = BeanUtils.getPropertyDescriptor(resolved, name);
            if (descriptor != null && descriptor.getReadMethod() != null) {
                return ResolvableType.forMethodReturnType(descriptor.getReadMethod(), resolved);
            }
        }
        return ResolvableType.NONE;
    }

    private static ResolvableType elementType(ResolvableType type) {
        if (type.isArray()) {
            return type.getComponentType();
        }
        Class<?> resolved = type.resolve();
        if (resolved != null && Map.class.isAssignableFrom(resolved)) {
            return type.as(Map.class).getGeneric(1);
        }
        if (resolved != null && Iterable.class.isAssignableFrom(resolved)) {
            return type.as(Iterable.class).getGeneric(0);
        }
        return ResolvableType.NONE;
    }

    private static boolean indexed(ResolvableType type) {
        Class<?> resolved = type.resolve();
        return type.isArray() || (resolved != null && List.class.isAssignableFrom(resolved));
    }

    private static boolean container(ResolvableType type) {
        Class<?> resolved = type.resolve();
        return type.isArray()
                || (resolved != null
                        && (Iterable.class.isAssignableFrom(resolved)
                                || Map.class.isAssignableFrom(resolved)));
    }

    private static String position(Integer index, boolean indexed) {
        return "[" + (indexed && index != null && index >= 0 ? index : "*") + "]";
    }

    /** 没有索引时，仅凭声明约束所在位置区分集合本身与元素；歧义保留形参级定位。 */
    private static String parameterPosition(
            MethodParameter parameter, ResolvableType type, Integer index, String code) {
        if (index != null) return position(index, indexed(type));
        if (!container(type)) return "";
        AnnotatedType annotated =
                parameter.getExecutable()
                        .getAnnotatedParameterTypes()[parameter.getParameterIndex()];
        boolean direct =
                hasConstraint(parameter.getParameterAnnotations(), code)
                        || hasConstraint(annotated.getAnnotations(), code);
        boolean element = false;
        if (annotated instanceof AnnotatedParameterizedType generic) {
            for (AnnotatedType argument : generic.getAnnotatedActualTypeArguments()) {
                element |= hasConstraint(argument.getAnnotations(), code);
            }
        }
        return element && !direct ? "[*]" : "";
    }

    private static boolean hasConstraint(Annotation[] annotations, String code) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getSimpleName().equals(code)) return true;
        }
        return false;
    }

    private static String parameterName(MethodParameter parameter) {
        String name = parameter.getParameterName();
        if (name == null && parameter.getParameter().isNamePresent()) {
            name = parameter.getParameter().getName();
        }
        name = withoutControls(name);
        return identifier(name) ? name : "arg" + parameter.getParameterIndex();
    }

    private static String fieldPath(String field, ResolvableType type) {
        if (type.resolve() == null) return "$";
        field = withoutControls(field);
        StringBuilder path = new StringBuilder();
        int offset = 0;
        while (offset < field.length()) {
            char character = field.charAt(offset);
            if (character == '[') {
                if (!container(type)) return "$";
                int start = ++offset;
                int depth = 1;
                while (offset < field.length() && depth > 0) {
                    char next = field.charAt(offset++);
                    if (next == '[') depth++;
                    if (next == ']') depth--;
                }
                String position = depth == 0 ? field.substring(start, offset - 1) : "";
                path.append('[')
                        .append(indexed(type) && position.matches("[0-9]+") ? position : "*")
                        .append(']');
                // 扁平路径无法区分 Map 键里的分隔符与实际后续属性，只保留已确认的容器位置。
                if (!indexed(type)) return path.toString();
                type = elementType(type);
                if (type.resolve() == null) {
                    return offset == field.length() ? path.toString() : "$";
                }
            } else if (character == '.') {
                offset++;
            } else {
                int start = offset;
                while (offset < field.length()
                        && field.charAt(offset) != '['
                        && field.charAt(offset) != '.') {
                    offset++;
                }
                String name = field.substring(start, offset);
                if (!identifier(name)) {
                    return "$";
                }
                ResolvableType property = propertyType(type, name);
                if (property == ResolvableType.NONE) {
                    return "$";
                }
                if (path.length() > 0) path.append('.');
                path.append(name);
                type = property;
            }
        }
        return path.length() == 0 ? "$" : path.toString();
    }

    private static boolean identifier(String name) {
        if (name == null
                || name.isEmpty()
                || !Character.isJavaIdentifierStart(name.codePointAt(0))) {
            return false;
        }
        return name.codePoints().allMatch(Character::isJavaIdentifierPart);
    }

    private static String withoutControls(String value) {
        if (value == null) return null;
        StringBuilder normalized = new StringBuilder();
        value.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .forEach(normalized::appendCodePoint);
        return normalized.toString();
    }
}
