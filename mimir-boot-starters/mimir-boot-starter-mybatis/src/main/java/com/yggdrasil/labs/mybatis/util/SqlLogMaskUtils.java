package com.yggdrasil.labs.mybatis.util;

import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.mybatis.annotation.SensitiveField;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL 参数脱敏工具。
 *
 * <p>根据字段上的 {@link SensitiveField} 注解和内置敏感参数名进行定向脱敏；
 * 对于基础类型与常见简单类型，直接透传。</p>
 */
public class SqlLogMaskUtils {

    /**
     * 最大递归深度，防止堆栈溢出
     */
    private static final int MAX_DEPTH = 5;

    private static final Set<String> SENSITIVE_PARAMETER_NAMES = Set.of(
            "password", "passwd", "pwd", "token", "accesstoken", "refreshtoken", "idtoken",
            "secret", "clientsecret", "authorization", "apikey");

    private static final Pattern SENSITIVE_SQL_ASSIGNMENT = Pattern.compile(
            "(?i)\\b(password|passwd|pwd|token|access[_-]?token|refresh[_-]?token|id[_-]?token|secret|client[_-]?secret|authorization|api[_-]?key)"
                    + "\\b(\\s*=\\s*)(?:'[^']*'|\\\"[^\\\"]*\\\"|[^\\s,;)]+)");

    private SqlLogMaskUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static Object maskParams(Object params) {
        // 使用 IdentityHashMap 来避免调用对象的 hashCode() 方法
        // IdentityHashMap 使用对象引用（==）而不是 equals() 和 hashCode() 来比较键
        return maskParams(params, 0, new IdentityHashMap<>());
    }

    /**
     * 对 SQL 文本中的敏感赋值进行脱敏，避免常量值绕过参数对象的脱敏路径。
     */
    public static String maskSql(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }
        return SENSITIVE_SQL_ASSIGNMENT.matcher(sql)
                .replaceAll("$1$2" + CommonConstants.MASKED);
    }

    private static Object maskParams(Object params, int depth, IdentityHashMap<Object, Boolean> visited) {
        if (params == null) return null;
        
        // 深度限制，防止无限递归
        if (depth >= MAX_DEPTH) {
            return getSimpleRepresentation(params);
        }
        
        // 检测循环引用
        if (visited.containsKey(params)) {
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

    private static Object maskMap(Map<?, ?> map, int depth, IdentityHashMap<Object, Boolean> visited) {
        // 使用 IdentityHashMap 作为结果，避免当 key/value 是 Map 时调用 hashCode() 导致的堆栈溢出
        // IdentityHashMap 使用对象引用（==）而不是 equals() 和 hashCode() 来比较键
        // 对于日志脱敏场景，使用 IdentityHashMap 是可以接受的
        Map<Object, Object> result = new IdentityHashMap<>();
        IdentityHashMap<Object, Boolean> sensitiveParameterValues = findSensitiveParameterValues(map);
        // 创建新的 visited 集合，避免修改原始集合，并先将当前 map 标记进去以便检测自引用
        IdentityHashMap<Object, Boolean> newVisited = new IdentityHashMap<>(visited);
        newVisited.put(map, Boolean.TRUE);

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();

            Object safeKey = toSafeKey(key);
            Object safeValue = toSafeValue(key, value, depth, visited, newVisited, sensitiveParameterValues);
            result.put(safeKey, safeValue);
        }
        return result;
    }

    private static IdentityHashMap<Object, Boolean> findSensitiveParameterValues(Map<?, ?> map) {
        IdentityHashMap<Object, Boolean> sensitiveValues = new IdentityHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value != null && isSensitiveParameterName(entry.getKey())) {
                sensitiveValues.put(value, Boolean.TRUE);
            }
        }
        return sensitiveValues;
    }

    /**
     * 生成用于结果 Map 的安全 key：
     * - 对于 Map 类型 key，使用简单字符串表示，避免调用 hashCode()/toString 导致栈溢出
     * - 其它类型直接透传
     */
    private static Object toSafeKey(Object key) {
        if (key instanceof Map) {
            return getSimpleRepresentation(key);
        }
        return key;
    }

    /**
     * 生成用于结果 Map 的安全 value。
     */
    private static Object toSafeValue(
            Object key,
            Object value,
            int depth,
            IdentityHashMap<Object, Boolean> visited,
            IdentityHashMap<Object, Boolean> newVisited,
            IdentityHashMap<Object, Boolean> sensitiveParameterValues) {

        if (value == null) {
            return null;
        }
        if (isSensitiveParameterName(key) || sensitiveParameterValues.containsKey(value)) {
            return CommonConstants.MASKED;
        }
        if (value instanceof Map) {
            return handleMapValue((Map<?, ?>) value, depth, visited, newVisited);
        }
        return handleNonMapValue(key, value, depth, newVisited);
    }

    /**
     * 处理 Map 类型的 value，包含循环引用检测与递归脱敏。
     */
    private static Object handleMapValue(
            Map<?, ?> value,
            int depth,
            IdentityHashMap<Object, Boolean> visited,
            IdentityHashMap<Object, Boolean> newVisited) {

        // 循环引用（包括自引用）直接返回简单表示，不再递归
        if (newVisited.containsKey(value)) {
            return getSimpleRepresentation(value);
        }

        Object masked = maskParams(value, depth + 1, newVisited);
        // 如果递归结果仍然是 Map 且原始 value 在 visited 中（历史循环），也返回简单表示
        if (masked instanceof Map && visited.containsKey(value)) {
            return getSimpleRepresentation(masked);
        }
        return masked;
    }

    /**
     * 处理非 Map 类型的 value，包括基于 key 的字段匹配（仅当 key 不是 Map 时）。
     */
    private static Object handleNonMapValue(
            Object key,
            Object value,
            int depth,
            IdentityHashMap<Object, Boolean> newVisited) {

        // 当 key 是 Map（尤其是包含循环引用的 Map）时，调用 String.valueOf(key)
        // 会触发 Map.toString() 从而导致堆栈溢出；此时跳过基于 key 的字段匹配逻辑
        Field field = null;
        if (!(key instanceof Map)) {
            field = findField(value, String.valueOf(key));
        }

        if (field != null && field.isAnnotationPresent(SensitiveField.class)) {
            SensitiveField anno = field.getAnnotation(SensitiveField.class);
            return maskValue(String.valueOf(getFieldValue(field, value)), anno);
        }

        return maskParams(value, depth + 1, newVisited);
    }

    private static boolean isSensitiveParameterName(Object key) {
        if (!(key instanceof String keyName)) {
            return false;
        }
        String normalized = keyName.toLowerCase(Locale.ROOT)
                .replace("_", "")
                .replace("-", "");
        return SENSITIVE_PARAMETER_NAMES.contains(normalized);
    }

    private static Object maskCollection(Collection<?> collection, int depth, IdentityHashMap<Object, Boolean> visited) {
        List<Object> result = new ArrayList<>();
        // 创建新的 visited 集合，避免修改原始集合
        IdentityHashMap<Object, Boolean> newVisited = new IdentityHashMap<>(visited);
        newVisited.put(collection, Boolean.TRUE);
        
        for (Object item : collection) {
            if (item == null) {
                result.add(null);
            } else {
                result.add(maskParams(item, depth + 1, newVisited));
            }
        }
        return result;
    }

    private static Object maskObject(Object obj, int depth, IdentityHashMap<Object, Boolean> visited) {
        try {
            // 创建新的 visited 集合，避免修改原始集合
            IdentityHashMap<Object, Boolean> newVisited = new IdentityHashMap<>(visited);
            newVisited.put(obj, Boolean.TRUE);
            
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
     * 使用 System.identityHashCode() 避免调用对象的 hashCode() 方法
     * 因为 Map 的 hashCode() 会遍历所有 entry，如果包含循环引用会导致堆栈溢出
     */
    private static String getSimpleRepresentation(Object obj) {
        if (obj == null) {
            return null;
        }
        // 使用 System.identityHashCode() 而不是 obj.hashCode()
        // 以避免当 obj 是包含循环引用的 Map 时触发堆栈溢出
        return obj.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(obj));
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
