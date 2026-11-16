package com.yggdrasil.labs.log.field;

import java.util.Map;

/**
 * 补充日志的业务字段，在产生日志的线程执行。
 *
 * <p>实现必须轻量，不得阻塞或执行 I/O。返回值仅支持 JSON 标量及字符串键 Map、List；null 或空 Map
 * 表示无补充。输入为排除、脱敏之前的只读视图，不得将敏感内容发送至外部系统。
 */
@FunctionalInterface
public interface LogFieldProvider {

    /** 返回合并至 attributes 的字段。 */
    Map<String, ?> provide(LogEventView event);
}
