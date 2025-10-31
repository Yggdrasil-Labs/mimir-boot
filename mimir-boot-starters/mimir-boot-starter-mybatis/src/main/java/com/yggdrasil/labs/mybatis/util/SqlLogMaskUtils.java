package com.yggdrasil.labs.mybatis.util;

import com.yggdrasil.labs.mybatis.annotation.SensitiveField;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL 参数脱敏工具。
 *
 * <p>根据字段上的 {@link SensitiveField} 注解进行定向脱敏；
 * 对于基础类型与常见简单类型，直接透传。</p>
 */
public class SqlLogMaskUtils {

    public static Object maskParams(Object params) {
        if (params == null) return null;
        if (params instanceof Map) {
            return maskMap((Map<?, ?>) params);
        }
        if (params.getClass().isPrimitive() || params instanceof String || params instanceof Number) {
            return params;
        }
        return maskObject(params);
    }

    private static Object maskMap(Map<?, ?> map) {
        Map<Object, Object> result = new HashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                result.put(key, null);
                continue;
            }
            // Map 仅支持基于 key 匹配对象字段进行脱敏
            Field field = findField(value, String.valueOf(key));
            if (field != null && field.isAnnotationPresent(SensitiveField.class)) {
                SensitiveField anno = field.getAnnotation(SensitiveField.class);
                result.put(key, maskValue(String.valueOf(getFieldValue(field, value)), anno));
            } else {
                result.put(key, maskParams(value));
            }
        }
        return result;
    }

    private static Object maskObject(Object obj) {
        try {
            Map<String, Object> map = new HashMap<>();
            Class<?> clazz = obj.getClass();
            for (Field field : getAllFields(clazz)) {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value == null) {
                    map.put(field.getName(), null);
                    continue;
                }
                if (field.isAnnotationPresent(SensitiveField.class)) {
                    SensitiveField anno = field.getAnnotation(SensitiveField.class);
                    map.put(field.getName(), maskValue(value.toString(), anno));
                } else {
                    map.put(field.getName(), maskParams(value));
                }
            }
            return map;
        } catch (Exception e) {
            return obj;
        }
    }

    private static String maskValue(String value, SensitiveField anno) {
        if (value == null || value.isEmpty()) return value;
        if (anno == null) {
            return "******";
        }
        SensitiveField.MaskStrategy strategy = anno.strategy();
        String replacement = anno.replacement();
        return switch (strategy) {
            case ALL -> "******";
            case PHONE -> maskPhone(value);
            case ID_CARD -> maskIdCard(value);
            case BANK_CARD -> maskBankCard(value);
            case EMAIL -> maskEmail(value);
            case CUSTOM -> replacement;
        };
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "******";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) return "******";
        int len = idCard.length();
        return idCard.substring(0, 6) + "********" + idCard.substring(len - 4);
    }

    private static String maskBankCard(String card) {
        if (card == null || card.length() < 8) return "******";
        return card.substring(0, 4) + "****" + card.substring(card.length() - 4);
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "******";
        int atIndex = email.indexOf("@");
        if (atIndex <= 1) return "******";
        return email.charAt(0) + "****" + email.substring(atIndex);
    }

    private static Field findField(Object obj, String fieldName) {
        if (obj == null) return null;
        Class<?> clazz = obj.getClass();
        for (Field field : getAllFields(clazz)) {
            if (field.getName().equalsIgnoreCase(fieldName)) {
                return field;
            }
        }
        return null;
    }

    private static Object getFieldValue(Field field, Object target) {
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static Field[] getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        while (clazz != null && clazz != Object.class) {
            fields.addAll(java.util.Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields.toArray(new Field[0]);
    }
}

