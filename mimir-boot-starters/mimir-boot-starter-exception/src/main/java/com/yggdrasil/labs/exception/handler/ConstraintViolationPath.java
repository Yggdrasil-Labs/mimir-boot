package com.yggdrasil.labs.exception.handler;

import static com.yggdrasil.labs.exception.handler.ValidationTypeMetadata.*;

import java.lang.reflect.Executable;
import java.util.List;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;

import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.util.ReflectionUtils;

import com.yggdrasil.labs.exception.validation.ValidationErrorSource;

/** 从结构化校验节点提取的安全位置，不包含 Map 键或实际参数值。 */
record ConstraintViolationPath(ValidationErrorSource source, String path, Integer parameterIndex) {

    /** 以可选的绑定根类型为起点，按框架提供的节点顺序解析位置。 */
    static ConstraintViolationPath from(ConstraintViolation<?> violation, ResolvableType rootType) {
        Reader reader = new Reader(violation, rootType);
        for (Path.Node node : violation.getPropertyPath()) {
            reader.read(node);
        }
        return reader.result();
    }

    /** 每次提取独立保存节点状态，后续未知属性不会重新暴露已隐藏的路径。 */
    private static final class Reader {
        private final ConstraintViolation<?> violation;
        private final StringBuilder properties = new StringBuilder();
        private Integer parameterIndex;
        private String parameterName = "";
        private boolean object;
        private boolean field;
        private Executable executable;
        private Class<?> declaringClass;
        private ResolvableType type;
        private boolean hidden;

        private Reader(ConstraintViolation<?> violation, ResolvableType rootType) {
            this.violation = violation;
            this.type = rootType;
        }

        /** 节点分类只更新对应状态，保留对象级错误高于字段和参数的优先级。 */
        private void read(Path.Node node) {
            switch (node.getKind()) {
                case METHOD, CONSTRUCTOR -> {
                    declaringClass = violation.getRootBeanClass();
                    executable = declaredExecutable(declaringClass, node);
                }
                case PARAMETER -> readParameter(node);
                case CROSS_PARAMETER -> {
                    object = true;
                    parameterIndex = null;
                }
                case BEAN -> {
                    object = true;
                }
                case PROPERTY -> {
                    if (!hidden) {
                        readProperty(node);
                        field = true;
                    }
                }
                case CONTAINER_ELEMENT -> {
                    if (!hidden) {
                        type = appendPosition(node);
                        field = true;
                    }
                }
                default -> {
                    // 方法名、构造器名和返回值等不属于输入字段路径。
                }
            }
        }

        /** 从可执行声明确认参数名和类型，声明缺失时保留 arg 序号回退。 */
        private void readParameter(Path.Node node) {
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

        /** 只接受声明类型可确认的属性，不从实际参数或容器内容推断路径。 */
        private void readProperty(Path.Node node) {
            type = appendPosition(node);
            String name = withoutControls(node.getName());
            ResolvableType property =
                    identifier(name) && !container(type)
                            ? propertyType(type, name)
                            : ResolvableType.NONE;
            if (property == ResolvableType.NONE) {
                hideProperty(node);
            } else {
                if (properties.length() > 0) properties.append('.');
                properties.append(name);
                type = property;
            }
        }

        /** 未知容器属性仅保留容器位置；其他未知子字段丢弃整个不完整前缀。 */
        private void hideProperty(Path.Node node) {
            if (container(type) && !node.isInIterable()) {
                if (properties.length() == 0) properties.append(parameterName);
                properties.append("[*]");
            } else {
                properties.setLength(0);
            }
            hidden = true;
        }

        /** 仅数组和 List 保留数字索引；Map 等容器使用通配符，不读取键。 */
        private ResolvableType appendPosition(Path.Node node) {
            if (!node.isInIterable() || !container(type)) return type;
            if (properties.length() == 0) properties.append(parameterName);
            properties.append(position(node.getIndex(), indexed(type)));
            return elementType(type);
        }

        /** 输出优先级及未知字段的美元符号回退与提取入口一致。 */
        private ConstraintViolationPath result() {
            if (object) {
                return new ConstraintViolationPath(
                        ValidationErrorSource.OBJECT, "$", parameterIndex);
            }
            if (field) {
                return new ConstraintViolationPath(
                        ValidationErrorSource.FIELD,
                        properties.length() == 0 ? "$" : properties.toString(),
                        parameterIndex);
            }
            return new ConstraintViolationPath(
                    ValidationErrorSource.PARAMETER, parameterName, parameterIndex);
        }
    }

    /** 仅查找根类上的声明；缺少构造器时返回空声明，不读取实际对象。 */
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
}
