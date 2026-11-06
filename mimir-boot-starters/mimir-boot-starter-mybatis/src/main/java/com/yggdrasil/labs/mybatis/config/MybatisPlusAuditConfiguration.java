package com.yggdrasil.labs.mybatis.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.yggdrasil.labs.mybatis.audit.AuditMetaObjectHandler;
import com.yggdrasil.labs.mybatis.audit.AuditorProvider;

/** 自动提供默认审计人和审计字段填充器。 */
@AutoConfiguration
public class MybatisPlusAuditConfiguration {

    /** 未自定义审计人来源时使用 system。 */
    @Bean
    @ConditionalOnMissingBean(AuditorProvider.class)
    public AuditorProvider defaultAuditorProvider() {
        return () -> "system";
    }

    /** 创建使用指定审计人来源的字段填充器。 */
    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    public MetaObjectHandler auditMetaObjectHandler(AuditorProvider auditorProvider) {
        return new AuditMetaObjectHandler(auditorProvider);
    }
}
