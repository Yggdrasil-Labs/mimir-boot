package com.yggdrasil.labs.exception.handler;

import static com.yggdrasil.labs.exception.handler.ValidationTypeMetadata.container;
import static com.yggdrasil.labs.exception.handler.ValidationTypeMetadata.elementType;
import static com.yggdrasil.labs.exception.handler.ValidationTypeMetadata.indexed;
import static com.yggdrasil.labs.exception.handler.ValidationTypeMetadata.parameterName;
import static com.yggdrasil.labs.exception.handler.ValidationTypeMetadata.parameterPosition;
import static com.yggdrasil.labs.exception.handler.ValidationTypeMetadata.position;
import static com.yggdrasil.labs.exception.handler.ValidationTypeMetadata.withoutControls;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
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
    private static final Set<String> NUMERIC_CONSTRAINTS =
            Set.of(
                    "Min",
                    "Max",
                    "DecimalMin",
                    "DecimalMax",
                    "Positive",
                    "PositiveOrZero",
                    "Negative",
                    "NegativeOrZero");

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
            appendMethodErrors(details, result, parameter, parameterIndex);
        }
        return snapshot(details);
    }

    /** 参数序号确认有效后，分别处理级联 Bean 错误与普通参数错误。 */
    private static void appendMethodErrors(
            List<ValidationErrorDetail> details,
            ParameterValidationResult result,
            MethodParameter parameter,
            int parameterIndex) {
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

    static ValidationErrorData extract(ConstraintViolationException exception) {
        if (classify(exception) != Origin.INPUT) {
            return null;
        }
        List<ValidationErrorDetail> details = new ArrayList<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            ConstraintViolationPath path =
                    ConstraintViolationPath.from(violation, ResolvableType.NONE);
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
                ConstraintViolationPath structured = ConstraintViolationPath.from(violation, type);
                if (structured.source() == ValidationErrorSource.FIELD) {
                    return detail(
                            ValidationErrorSource.FIELD,
                            structured.path(),
                            parameterIndex,
                            error.getCode());
                }
            }
            String path = BindingFieldPath.resolve(fieldError.getField(), type);
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
        String message = safeMessage(normalized.constraint());
        return new ValidationErrorDetail(
                source, normalized.path(), parameterIndex, normalized.constraint(), message);
    }

    /** 约束标识已规范化，消息只由白名单映射生成，不读取插值消息。 */
    private static String safeMessage(String constraint) {
        if (NUMERIC_CONSTRAINTS.contains(constraint)) {
            return "数值不符合约束";
        }
        return switch (constraint) {
            case "NotNull", "NotBlank", "NotEmpty" -> "不能为空";
            case "Size", "Length" -> "长度不符合约束";
            case "Email", "Pattern" -> "格式不符合约束";
            case "typeMismatch" -> "类型不匹配";
            default -> "参数不符合约束";
        };
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
}
