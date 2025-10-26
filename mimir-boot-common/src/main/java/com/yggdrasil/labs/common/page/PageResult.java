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
    private List<T> records;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Long current;

    /**
     * 每页大小
     */
    private Long size;

    /**
     * 总页数
     */
    private Long pages;

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
     * @param records 数据列表
     * @param total   总记录数
     * @param current 当前页码
     * @param size    每页大小
     */
    public PageResult(List<T> records, Long total, Long current, Long size) {
        this.records = records;
        this.total = total;
        this.current = current;
        this.size = size;
        this.pages = (total + size - 1) / size;
        this.hasNext = current < pages;
        this.hasPrevious = current > 1;
    }

    /**
     * 创建分页结果
     *
     * @param records 数据列表
     * @param total   总记录数
     * @param current 当前页码
     * @param size    每页大小
     * @param <T>     数据类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> records, Long total, Long current, Long size) {
        return new PageResult<>(records, total, current, size);
    }

    /**
     * 创建空分页结果
     *
     * @param current 当前页码
     * @param size    每页大小
     * @param <T>     数据类型
     * @return 空分页结果
     */
    public static <T> PageResult<T> empty(Long current, Long size) {
        return new PageResult<>(List.of(), 0L, current, size);
    }

    /**
     * 从分页请求创建空分页结果
     *
     * @param pageRequest 分页请求
     * @param <T>         数据类型
     * @return 空分页结果
     */
    public static <T> PageResult<T> empty(PageRequest pageRequest) {
        return empty(pageRequest.getCurrent(), pageRequest.getSize());
    }
}
