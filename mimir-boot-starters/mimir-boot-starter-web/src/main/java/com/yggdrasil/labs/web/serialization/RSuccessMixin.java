package com.yggdrasil.labs.web.serialization;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** 受管 JSON 默认只输出成功状态。 */
public abstract class RSuccessMixin {
    @JsonIgnore
    public abstract boolean isFail();
}
