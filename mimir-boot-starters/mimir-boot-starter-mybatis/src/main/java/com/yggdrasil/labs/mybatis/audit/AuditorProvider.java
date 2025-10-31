package com.yggdrasil.labs.mybatis.audit;

/**
 * 获取当前审计主体（例如当前登录用户名/用户ID）的提供器接口。
 */
@FunctionalInterface
public interface AuditorProvider {
    /**
     * 返回当前审计主体标识，为空时由调用方自行处理降级策略。
     */
    String currentAuditor();
}


