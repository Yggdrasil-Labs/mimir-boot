package com.yggdrasil.labs.common.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 命令对象抽象类（Command）
 *
 * <p>Command 用于封装写操作（CQRS 模式的命令端）。 所有写操作的命令对象应继承此类。
 *
 * <p>使用场景：
 *
 * <ul>
 *   <li>创建、更新、删除等写操作
 *   <li>执行状态变更的操作
 * </ul>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Data
public abstract class Command implements Serializable {

    @Serial private static final long serialVersionUID = 1L;

    /** 操作人ID（可选，可通过上下文获取） */
    private String operatorId;

    /** 操作人名称（可选，可通过上下文获取） */
    private String operatorName;

    /** 请求追踪ID（可选，可用于分布式追踪） */
    private String traceId;
}
