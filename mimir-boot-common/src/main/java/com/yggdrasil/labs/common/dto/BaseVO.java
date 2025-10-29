package com.yggdrasil.labs.common.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础视图对象（VO）
 * <p>
 * VO 用于前端展示，包含必要的展示字段。
 * 所有领域 VO 应继承此类以保持一致性。
 * </p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Data
public class BaseVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 备注
     */
    private String remark;
}

