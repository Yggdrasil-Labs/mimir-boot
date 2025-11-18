package com.yggdrasil.labs.test.util;

import com.yggdrasil.labs.common.exception.ErrorCode;
import com.yggdrasil.labs.common.exception.SystemException;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Mock 数据构建器
 *
 * <p>提供链式构建测试数据的方法</p>
 *
 * @param <T> 数据类型
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
public class MockDataBuilder<T> {

    private final T instance;
    private final Class<T> clazz;

    private MockDataBuilder(Class<T> clazz) {
        this.clazz = clazz;
        try {
            this.instance = clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new SystemException(ErrorCode.PARAM_ERROR.getCode(), "无法创建实例: " + clazz.getName(), e);
        }
    }

    /**
     * 创建构建器
     *
     * @param clazz 类型
     * @param <T>   类型参数
     * @return 构建器实例
     */
    public static <T> MockDataBuilder<T> of(Class<T> clazz) {
        return new MockDataBuilder<>(clazz);
    }

    /**
     * 设置属性值
     *
     * @param setter 设置器函数
     * @return 构建器实例
     */
    public MockDataBuilder<T> with(Consumer<T> setter) {
        setter.accept(instance);
        return this;
    }

    /**
     * 构建实例
     *
     * @return 实例
     */
    public T build() {
        return instance;
    }

    /**
     * 构建列表
     *
     * <p>注意：此方法会为每个实例应用相同的配置。如果需要不同的配置，请使用 {@link #buildList(int, Consumer)}</p>
     *
     * @param count 数量
     * @return 实例列表（所有实例使用相同的配置）
     */
    public List<T> buildList(int count) {
        return buildList(count, null);
    }

    /**
     * 构建列表，支持为每个实例自定义配置
     *
     * @param count      数量
     * @param customizer 每个实例的自定义配置器（可以为 null，表示复制当前实例的配置）
     * @return 实例列表
     */
    public List<T> buildList(int count, Consumer<T> customizer) {
        List<T> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            try {
                T item = clazz.getDeclaredConstructor().newInstance();
                if (customizer != null) {
                    // 使用自定义配置器
                    customizer.accept(item);
                } else {
                    // 复制当前实例的字段值
                    copyFields(instance, item);
                }
                list.add(item);
            } catch (Exception e) {
                throw new SystemException(ErrorCode.PARAM_ERROR.getCode(), "无法创建实例: " + clazz.getName(), e);
            }
        }
        return list;
    }

    /**
     * 复制源对象的字段值到目标对象
     *
     * @param source 源对象
     * @param target 目标对象
     */
    private void copyFields(T source, T target) {
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            Field[] fields = currentClass.getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(source);
                    if (value != null) {
                        field.set(target, value);
                    }
                } catch (IllegalAccessException e) {
                    // 忽略无法访问的字段
                }
            }
            currentClass = currentClass.getSuperclass();
        }
    }

    /**
     * 获取当前实例（用于复杂构建）
     *
     * @return 当前实例
     */
    public T getInstance() {
        return instance;
    }

    // ========== 常用测试数据生成 ==========

    /**
     * 生成测试时间戳
     *
     * @return 时间戳
     */
    public static LocalDateTime testTimestamp() {
        return LocalDateTime.now();
    }

    /**
     * 生成测试时间戳（指定偏移天数）
     *
     * @param daysOffset 天数偏移
     * @return 时间戳
     */
    public static LocalDateTime testTimestamp(int daysOffset) {
        return LocalDateTime.now().plusDays(daysOffset);
    }
}

