package com.yggdrasil.labs.common.page;

import com.yggdrasil.labs.common.constant.CommonConstants;
import com.yggdrasil.labs.common.enums.OrderDirection;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页请求参数
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Data
public class PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 当前页码
     */
    private Long current = CommonConstants.DEFAULT_PAGE_NUMBER;

    /**
     * 每页大小
     */
    private Long size = CommonConstants.DEFAULT_PAGE_SIZE;

    /**
     * 排序字段
     */
    private String orderBy;

    /**
     * 排序方向
     */
    private String orderDirection = OrderDirection.ASC.getCode();

    /**
     * 构造方法
     */
    public PageRequest() {
        // 默认值已在字段初始化时设置，无需额外处理
    }

    /**
     * 构造方法
     *
     * @param current 当前页码
     * @param size    每页大小
     */
    public PageRequest(Long current, Long size) {
        this.current = current;
        this.size = size;
        validateAndCorrect();
    }

    /**
     * 构造方法
     *
     * @param current        当前页码
     * @param size           每页大小
     * @param orderBy        排序字段
     * @param orderDirection 排序方向
     */
    public PageRequest(Long current, Long size, String orderBy, String orderDirection) {
        this.current = current;
        this.size = size;
        this.orderBy = orderBy;
        this.orderDirection = orderDirection;
        validateAndCorrect();
    }

    /**
     * 获取偏移量
     *
     * @return 偏移量
     */
    public Long getOffset() {
        return (current - 1) * size;
    }

    /**
     * 验证并修正分页参数
     * 
     * <p>自动校验分页参数的有效性，如果参数无效则修正为默认值：
     * <ul>
     *   <li>页码必须 >= 1，默认 1</li>
     *   <li>每页大小必须在 1 到 MAX_PAGE_SIZE 之间，默认 10</li>
     *   <li>排序方向必须是 ASC 或 DESC，默认 ASC</li>
     * </ul>
     */
    public void validateAndCorrect() {
        if (this.current == null || this.current < 1) {
            this.current = CommonConstants.DEFAULT_PAGE_NUMBER;
        }
        if (this.size == null || this.size < 1) {
            this.size = CommonConstants.DEFAULT_PAGE_SIZE;
        }
        if (this.size > CommonConstants.MAX_PAGE_SIZE) {
            this.size = CommonConstants.MAX_PAGE_SIZE;
        }
        // 验证排序方向，使用枚举进行校验
        if (this.orderDirection == null || (!OrderDirection.isAsc(this.orderDirection) && !OrderDirection.isDesc(this.orderDirection))) {
            this.orderDirection = OrderDirection.ASC.getCode();
        }
    }

    /**
     * 创建分页请求（自动校验）
     *
     * @param current 当前页码
     * @param size    每页大小
     * @return 分页请求（已校验）
     */
    public static PageRequest of(Long current, Long size) {
        return new PageRequest(current, size);
    }

    /**
     * 创建分页请求（自动校验）
     *
     * @param current        当前页码
     * @param size           每页大小
     * @param orderBy        排序字段
     * @param orderDirection 排序方向
     * @return 分页请求（已校验）
     */
    public static PageRequest of(Long current, Long size, String orderBy, String orderDirection) {
        return new PageRequest(current, size, orderBy, orderDirection);
    }
}
