package com.yggdrasil.labs.common.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 查询对象抽象类（Query）
 *
 * <p>Query 用于封装查询操作（CQRS 模式的查询端）。 所有查询操作的查询对象应继承此类。
 *
 * <p>使用场景：
 *
 * <ul>
 *   <li>单条查询、列表查询、分页查询等
 *   <li>统计数据查询
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Data
public abstract class Query implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 请求追踪ID（可选，可用于分布式追踪） */
    private String traceId;
}
