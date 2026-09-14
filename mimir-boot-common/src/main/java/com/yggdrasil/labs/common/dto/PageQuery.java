package com.yggdrasil.labs.common.dto;

import java.io.Serial;

import com.yggdrasil.labs.common.page.PageRequest;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分页查询对象
 *
 * <p>采用组合 PageRequest，避免与 PageRequest 字段重复。
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class PageQuery extends Query {

    @Serial private static final long serialVersionUID = 1L;

    /** 分页参数 */
    private PageRequest page = new PageRequest();

    /**
     * 转换为 PageRequest
     *
     * @return PageRequest
     */
    public PageRequest toPageRequest() {
        return page;
    }
}
