package com.yggdrasil.labs.common.page;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页结果
 * 用于业务层和服务层之间的数据传递和返回
 * 同时作为 API 响应的分页数据结构
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Data
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 数据列表
     */
    private List<T> data;

    /**
     * 总记录数
     */
    private Long totalCount;

    /**
     * 页码
     */
    private Long pageIndex;

    /**
     * 页大小
     */
    private Long pageSize;

    /**
     * 总页数
     */
    private Long totalPages;

    /**
     * 是否有下一页
     */
    private Boolean hasNext;

    /**
     * 是否有上一页
     */
    private Boolean hasPrevious;

    /**
     * 构造方法
     */
    public PageResult() {
    }

    /**
     * 构造方法
     *
     * @param data      数据列表
     * @param totalCount 总记录数
     * @param pageIndex 页码
     * @param pageSize  页大小
     */
    public PageResult(List<T> data, Long totalCount, Long pageIndex, Long pageSize) {
        this.data = data;
        this.totalCount = totalCount;
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
        this.totalPages = pageSize == 0 ? 0 : (totalCount + pageSize - 1) / pageSize;
        this.hasNext = pageIndex < totalPages;
        this.hasPrevious = pageIndex > 1;
    }

    /**
     * 创建分页结果
     *
     * @param data      数据列表
     * @param totalCount 总记录数
     * @param pageIndex 页码
     * @param pageSize  页大小
     * @param <T>       数据类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> data, Long totalCount, Long pageIndex, Long pageSize) {
        return new PageResult<>(data, totalCount, pageIndex, pageSize);
    }

    /**
     * 创建空分页结果
     *
     * @param pageIndex 页码
     * @param pageSize  页大小
     * @param <T>       数据类型
     * @return 空分页结果
     */
    public static <T> PageResult<T> empty(Long pageIndex, Long pageSize) {
        return new PageResult<>(List.of(), 0L, pageIndex, pageSize);
    }

    /**
     * 从分页请求创建空分页结果
     *
     * @param pageRequest 分页请求
     * @param <T>         数据类型
     * @return 空分页结果
     */
    public static <T> PageResult<T> empty(PageRequest pageRequest) {
        return empty(pageRequest.getPageIndex(), pageRequest.getPageSize());
    }
}
