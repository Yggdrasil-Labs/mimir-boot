package com.yggdrasil.labs.exception.handler;

import java.beans.PropertyDescriptor;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeanUtils;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;

/** 仅查询声明类型与注解，不访问实际参数、属性值或容器内容。 */
final class ValidationTypeMetadata {
    private ValidationTypeMetadata() {}

    /** 仅反射声明类型，不触碰绑定目标、属性访问器或容器值。 */
    static ResolvableType propertyType(ResolvableType owner, String name) {
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

    static ResolvableType elementType(ResolvableType type) {
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

    static boolean indexed(ResolvableType type) {
        Class<?> resolved = type.resolve();
        return type.isArray() || (resolved != null && List.class.isAssignableFrom(resolved));
    }

    static boolean container(ResolvableType type) {
        Class<?> resolved = type.resolve();
        return type.isArray()
                || (resolved != null
                        && (Iterable.class.isAssignableFrom(resolved)
                                || Map.class.isAssignableFrom(resolved)));
    }

    static String position(Integer index, boolean indexed) {
        return "[" + (indexed && index != null && index >= 0 ? index : "*") + "]";
    }

    /** 没有索引时，仅凭声明约束所在位置区分集合本身与元素；歧义保留形参级定位。 */
    static String parameterPosition(
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

    static boolean hasConstraint(Annotation[] annotations, String code) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getSimpleName().equals(code)) return true;
        }
        return false;
    }

    static String parameterName(MethodParameter parameter) {
        String name = parameter.getParameterName();
        if (name == null && parameter.getParameter().isNamePresent()) {
            name = parameter.getParameter().getName();
        }
        name = withoutControls(name);
        return identifier(name) ? name : "arg" + parameter.getParameterIndex();
    }

    static boolean identifier(String name) {
        if (name == null
                || name.isEmpty()
                || !Character.isJavaIdentifierStart(name.codePointAt(0))) {
            return false;
        }
        return name.codePoints().allMatch(Character::isJavaIdentifierPart);
    }

    static String withoutControls(String value) {
        if (value == null) return null;
        StringBuilder normalized = new StringBuilder();
        value.codePoints()
                .filter(codePoint -> !Character.isISOControl(codePoint))
                .forEach(normalized::appendCodePoint);
        return normalized.toString();
    }
}
