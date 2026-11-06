package com.yggdrasil.labs.web.serialization;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** 受管 JSON 默认只输出成功状态。 */
public abstract class RSuccessMixin {
    /** 标记失败状态派生属性不参与受管 JSON 输出。 */
    @JsonIgnore
    public abstract boolean isFail();
}
