package com.yggdrasil.labs.log.field;

import java.util.Optional;

/** 原始异常的只读摘要，不持有原始 Throwable 或堆栈。 */
public interface LogExceptionView {

    /** 异常类型的完整类名。 */
    String type();

    /** 原异常消息全文，尚未脱敏或截断。 */
    Optional<String> message();
}
