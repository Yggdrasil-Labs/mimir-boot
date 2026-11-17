package com.yggdrasil.labs.log.field;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** 日志事件的不可变视图；不暴露原始事件或异常对象，扩展间不共享补充结果。 */
public interface LogEventView {

    /** 事件产生时间。 */
    Instant timestamp();

    /** 事件日志级别。 */
    String level();

    /** 应用服务名称。 */
    String service();

    /** 可选部署环境。 */
    Optional<String> environment();

    /** 可选实例标识。 */
    Optional<String> instance();

    /** 完整 Logger 名称。 */
    String logger();

    /** 产生事件的线程名称。 */
    String thread();

    /** 格式化后的原始消息，尚未脱敏或截断。 */
    Optional<String> message();

    /** 原始 MDC 中的链路标识。 */
    Optional<String> traceId();

    /** 原始 MDC 中的 Span 标识。 */
    Optional<String> spanId();

    /** 全量 MDC 的不可变副本，尚未排除或脱敏。 */
    Map<String, String> context();

    /** 仅含事件自带且通过类型检查的键值对，嵌套容器也不可变。 */
    Map<String, Object> attributes();

    /** 可选原始异常摘要，不暴露异常对象。 */
    Optional<LogExceptionView> exception();
}
