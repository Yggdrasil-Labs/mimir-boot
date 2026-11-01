package com.yggdrasil.labs.common.page;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.function.Function;

import lombok.Data;

/**
 * 分页结果 用于业务层和服务层之间的数据传递和返回 同时作为 API 响应的分页数据结构
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Data
public class PageResult<T extends Serializable> implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 数据列表 */
    private List<T> data;

    /** 总记录数 */
    private Long totalCount;

    /** 页码 */
    private Long pageIndex;

    /** 页大小 */
    private Long pageSize;

    /** 总页数 */
    private Long totalPages;

    /** 是否有下一页 */
    private Boolean hasNext;

    /** 是否有上一页 */
    private Boolean hasPrevious;

    /** 构造方法 */
    public PageResult() {}

    /**
     * 构造方法
     *
     * @param data 数据列表
     * @param totalCount 总记录数
     * @param pageIndex 页码
     * @param pageSize 页大小
     */
    public PageResult(List<T> data, Long totalCount, Long pageIndex, Long pageSize) {
        validatePageParameters(totalCount, pageIndex, pageSize);
        this.data = data;
        this.totalCount = totalCount;
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
        this.totalPages = totalCount / pageSize + (totalCount % pageSize == 0 ? 0 : 1);
        this.hasNext = pageIndex < totalPages;
        this.hasPrevious = pageIndex > 1;
    }

    private static void validatePageParameters(Long totalCount, Long pageIndex, Long pageSize) {
        if (totalCount == null
                || pageIndex == null
                || pageSize == null
                || totalCount < 0
                || pageIndex < 1
                || pageSize < 1) {
            throw new IllegalArgumentException("分页参数无效");
        }
    }

    /**
     * 创建分页结果
     *
     * @param data 数据列表
     * @param totalCount 总记录数
     * @param pageIndex 页码
     * @param pageSize 页大小
     * @param <T> 数据类型
     * @return 分页结果
     */
    public static <T extends Serializable> PageResult<T> of(
            List<T> data, Long totalCount, Long pageIndex, Long pageSize) {
        return new PageResult<>(data, totalCount, pageIndex, pageSize);
    }

    /**
     * 创建空分页结果
     *
     * @param pageIndex 页码
     * @param pageSize 页大小
     * @param <T> 数据类型
     * @return 空分页结果
     */
    public static <T extends Serializable> PageResult<T> empty(Long pageIndex, Long pageSize) {
        return new PageResult<>(List.of(), 0L, pageIndex, pageSize);
    }

    /**
     * 从分页请求创建空分页结果
     *
     * @param pageRequest 分页请求
     * @param <T> 数据类型
     * @return 空分页结果
     */
    public static <T extends Serializable> PageResult<T> empty(PageRequest pageRequest) {
        return empty(pageRequest.getPageIndex(), pageRequest.getPageSize());
    }

    /**
     * 逐条映射数据并返回新的分页结果
     *
     * <p>六个分页元数据按原值复制（包括 null 或不一致的值），不重新计算；源数据为 null 时结果数据仍为 null，否则返回新的可变列表。 null
     * 元素不交给映射函数并原位保留，函数返回的 null 同样保留；函数抛出的异常原样传播，后续记录不再处理。
     *
     * @param mapper 映射函数，不能为 null
     * @param <U> 目标数据类型
     * @return 新的分页结果
     * @since 2.4.0
     */
    public <U extends Serializable> PageResult<U> map(Function<? super T, ? extends U> mapper) {
        if (mapper == null) {
            throw new IllegalArgumentException("mapper 不能为 null");
        }
        PageResult<U> result = new PageResult<>();
        result.data = PageItems.map(data, mapper);
        result.totalCount = totalCount;
        result.pageIndex = pageIndex;
        result.pageSize = pageSize;
        result.totalPages = totalPages;
        result.hasNext = hasNext;
        result.hasPrevious = hasPrevious;
        return result;
    }
}
