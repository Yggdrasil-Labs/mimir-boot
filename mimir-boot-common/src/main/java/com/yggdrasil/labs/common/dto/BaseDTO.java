package com.yggdrasil.labs.common.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础数据传输对象（DTO）
 * <p>
 * DTO 用于应用层之间传输数据，不包含业务逻辑。
 * 所有领域 DTO 应继承此类以保持一致性。
 * </p>
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
@Data
public class BaseDTO implements Serializable {

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

