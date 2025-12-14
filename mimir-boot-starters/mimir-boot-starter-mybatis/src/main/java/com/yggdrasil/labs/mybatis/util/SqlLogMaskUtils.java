package com.yggdrasil.labs.mybatis.util;

import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.mybatis.annotation.SensitiveField;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * SQL 参数脱敏工具。
 *
 * <p>根据字段上的 {@link SensitiveField} 注解进行定向脱敏；
 * 对于基础类型与常见简单类型，直接透传。</p>
 */
public class SqlLogMaskUtils {

    /**
     * 最大递归深度，防止堆栈溢出
     */
    private static final int MAX_DEPTH = 5;

    private SqlLogMaskUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static Object maskParams(Object params) {
        return maskParams(params, 0, new HashSet<>());
    }

    private static Object maskParams(Object params, int depth, Set<Object> visited) {
        if (params == null) return null;
        
        // 深度限制，防止无限递归
        if (depth >= MAX_DEPTH) {
            return getSimpleRepresentation(params);
        }
        
        // 检测循环引用
        if (visited.contains(params)) {
            return getSimpleRepresentation(params);
        }
        
        if (params instanceof Map) {
            return maskMap((Map<?, ?>) params, depth, visited);
        }
        
        // 基础类型和简单类型直接返回
        if (params.getClass().isPrimitive() 
                || params instanceof String 
                || params instanceof Number
                || params instanceof Boolean
                || params instanceof Character) {
            return params;
        }
        
        // Collection 类型特殊处理
        if (params instanceof Collection) {
            return maskCollection((Collection<?>) params, depth, visited);
        }
        
        // MyBatis-Plus Wrapper 类型特殊处理，避免深度递归
        if (isMyBatisPlusWrapper(params)) {
            return getSimpleRepresentation(params);
        }
        
        return maskObject(params, depth, visited);
    }

    private static Object maskMap(Map<?, ?> map, int depth, Set<Object> visited) {
        Map<Object, Object> result = new HashMap<>();
        Set<Object> newVisited = new HashSet<>(visited);
        newVisited.add(map);
        
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
                result.put(key, maskParams(value, depth + 1, newVisited));
            }
        }
        return result;
    }

    private static Object maskCollection(Collection<?> collection, int depth, Set<Object> visited) {
        List<Object> result = new ArrayList<>();
        Set<Object> newVisited = new HashSet<>(visited);
        newVisited.add(collection);
        
        for (Object item : collection) {
            if (item == null) {
                result.add(null);
            } else {
                result.add(maskParams(item, depth + 1, newVisited));
            }
        }
        return result;
    }

    private static Object maskObject(Object obj, int depth, Set<Object> visited) {
        try {
            Set<Object> newVisited = new HashSet<>(visited);
            newVisited.add(obj);
            
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
                    map.put(field.getName(), maskParams(value, depth + 1, newVisited));
                }
            }
            return map;
        } catch (Exception e) {
            return getSimpleRepresentation(obj);
        }
    }
    
    /**
     * 判断是否为 MyBatis-Plus 的 Wrapper 类型
     */
    private static boolean isMyBatisPlusWrapper(Object obj) {
        if (obj == null) {
            return false;
        }
        Class<?> clazz = obj.getClass();
        String className = clazz.getName();
        // 检查是否为 MyBatis-Plus 的 Wrapper 类
        return className.contains("com.baomidou.mybatisplus") 
                && (className.contains("Wrapper") || className.contains("QueryWrapper") || className.contains("UpdateWrapper"));
    }
    
    /**
     * 获取对象的简单字符串表示，避免深度递归
     */
    private static String getSimpleRepresentation(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return obj.getClass().getSimpleName() + "@" + Integer.toHexString(obj.hashCode());
        } catch (Exception e) {
            return obj.toString();
        }
    }

    private static String maskValue(String value, SensitiveField anno) {
        if (value == null || value.isEmpty()) return value;
        if (anno == null) {
            return CommonConstants.MASKED;
        }
        SensitiveField.MaskStrategy strategy = anno.strategy();
        String replacement = anno.replacement();
        return switch (strategy) {
            case ALL -> CommonConstants.MASKED;
            case PHONE -> maskPhone(value);
            case ID_CARD -> maskIdCard(value);
            case BANK_CARD -> maskBankCard(value);
            case EMAIL -> maskEmail(value);
            case CUSTOM -> replacement;
        };
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return CommonConstants.MASKED;
        int len = phone.length();
        // 常规手机号长度为11位，保留前3位和后4位；非常规长度使用更保守方案，保留前3位和后2位
        int prefixLength = 3;
        int suffixLength = len == 11 ? 4 : 2;
        return phone.substring(0, prefixLength) + CommonConstants.MASKED + phone.substring(len - suffixLength);
    }

    private static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) return CommonConstants.MASKED;
        int len = idCard.length();
        // 常规身份证长度为15位或18位，保留前6位和后4位；非常规长度使用更保守方案，保留前4位和后4位
        int prefixLength = (len == 15 || len == 18) ? 6 : 4;
        return idCard.substring(0, prefixLength) + CommonConstants.MASKED + idCard.substring(len - 4);
    }

    private static String maskBankCard(String card) {
        if (card == null || card.length() < 8) return CommonConstants.MASKED;
        int len = card.length();
        // 常规银行卡长度为16位或19位，保留前4位和后4位；非常规长度使用更保守方案，保留前3位和后3位
        int prefixLength = (len == 16 || len == 19) ? 4 : 3;
        int suffixLength = (len == 16 || len == 19) ? 4 : 3;
        return card.substring(0, prefixLength) + CommonConstants.MASKED + card.substring(len - suffixLength);
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return CommonConstants.MASKED;
        int atIndex = email.indexOf("@");
        if (atIndex <= 1) return CommonConstants.MASKED;
        return email.charAt(0) + CommonConstants.MASKED + email.substring(atIndex);
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

