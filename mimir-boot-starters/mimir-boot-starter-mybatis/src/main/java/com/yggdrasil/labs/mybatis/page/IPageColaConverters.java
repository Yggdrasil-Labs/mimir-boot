package com.yggdrasil.labs.mybatis.page;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.alibaba.cola.dto.PageResponse;
import com.baomidou.mybatisplus.core.metadata.IPage;

/** 将 MyBatis-Plus 分页转换为 COLA 分页响应。 */
public final class IPageColaConverters {

    private IPageColaConverters() {}

    /** 保持元素原样转换为 COLA 分页响应。 */
    public static <T> PageResponse<T> toCola(IPage<T> source) {
        return toCola(source, Function.identity());
    }

    /** 逐条映射后转换为 COLA 分页响应。 */
    public static <S, T> PageResponse<T> toCola(
            IPage<S> source, Function<? super S, ? extends T> mapper) {
        if (source == null) {
            throw new IllegalArgumentException("source 不能为 null");
        }
        if (mapper == null) {
            throw new IllegalArgumentException("mapper 不能为 null");
        }

        long total = source.getTotal();
        long current = source.getCurrent();
        long size = source.getSize();
        int totalCount = requireInt("total", total, 0);
        int pageIndex = requireInt("current", current, 1);
        int pageSize = requireInt("size", size, 1);

        List<S> records = source.getRecords();
        List<T> mapped = new ArrayList<>(records == null ? 0 : records.size());
        if (records != null) {
            for (S item : records) {
                mapped.add(item == null ? null : mapper.apply(item));
            }
        }
        return PageResponse.of(mapped, totalCount, pageSize, pageIndex);
    }

    private static int requireInt(String name, long value, long min) {
        if (value < min || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    name + " 必须在 " + min + " 至 " + Integer.MAX_VALUE + " 之间: " + value);
        }
        return (int) value;
    }
}
