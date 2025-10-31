package com.yggdrasil.labs.mybatis.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.yggdrasil.labs.mybatis.audit.AuditMetaObjectHandler;
import com.yggdrasil.labs.mybatis.audit.AuditorProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class MybatisPlusAuditConfiguration {

    @Bean
    @ConditionalOnMissingBean(AuditorProvider.class)
    public AuditorProvider defaultAuditorProvider() {
        return () -> "system";
    }

    @Bean
    @ConditionalOnMissingBean(MetaObjectHandler.class)
    public MetaObjectHandler auditMetaObjectHandler(AuditorProvider auditorProvider) {
        return new AuditMetaObjectHandler(auditorProvider);
    }
}


