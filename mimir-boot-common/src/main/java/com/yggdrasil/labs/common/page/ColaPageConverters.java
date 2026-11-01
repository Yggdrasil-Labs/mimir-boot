package com.yggdrasil.labs.common.page;

import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

import com.alibaba.cola.dto.PageResponse;

/**
 * {@link PageResult} 到 COLA {@link PageResponse} 的转换工具
 *
 * <p>COLA 为 optional 依赖：使用本类的消费者需自行声明 {@code com.alibaba.cola:cola-component-dto}。转换前先校验三个必需元数据，
 * 全部合法后才调用映射函数；输出 success=true，总页数由 COLA 自行计算。
 *
 * @author Yggdrasil Labs
 * @since 2.4.0
 */
public final class ColaPageConverters {

    private ColaPageConverters() {}

    /**
     * 保持元素原样转换为 COLA 分页响应
     *
     * @param source 源分页结果，不能为 null
     * @param <T> 数据类型
     * @return COLA 分页响应
     */
    public static <T extends Serializable> PageResponse<T> toCola(PageResult<T> source) {
        return toCola(source, Function.identity());
    }

    /**
     * 逐条映射后转换为 COLA 分页响应
     *
     * <p>null 元素不交给映射函数并原位保留，函数返回的 null 同样保留；函数异常原样传播。
     *
     * @param source 源分页结果，不能为 null
     * @param mapper 映射函数，不能为 null
     * @param <T> 源数据类型
     * @param <R> 目标数据类型
     * @return COLA 分页响应
     * @throws IllegalArgumentException source、mapper 为 null，或总数、页码、页大小为 null、越界
     */
    public static <T extends Serializable, R> PageResponse<R> toCola(
            PageResult<T> source, Function<? super T, ? extends R> mapper) {
        if (source == null) {
            throw new IllegalArgumentException("source 不能为 null");
        }
        if (mapper == null) {
            throw new IllegalArgumentException("mapper 不能为 null");
        }
        int totalCount = requireInt("totalCount", source.getTotalCount(), 0);
        int pageIndex = requireInt("pageIndex", source.getPageIndex(), 1);
        int pageSize = requireInt("pageSize", source.getPageSize(), 1);

        List<R> mapped = PageItems.map(source.getData(), mapper);
        // COLA 参数顺序为 data、totalCount、pageSize、pageIndex
        return PageResponse.of(mapped, totalCount, pageSize, pageIndex);
    }

    private static int requireInt(String name, Long value, long min) {
        if (value == null) {
            throw new IllegalArgumentException(name + " 不能为 null");
        }
        if (value < min || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    name + " 必须在 " + min + " 至 " + Integer.MAX_VALUE + " 之间: " + value);
        }
        return value.intValue();
    }
}
