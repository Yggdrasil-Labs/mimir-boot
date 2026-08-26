package com.yggdrasil.labs.mybatis.audit;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * 通用审计字段自动填充处理器。
 *
 * <p>支持字段：createBy、createTime、updateBy、updateTime。</p>
 */
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditMetaObjectHandler.class);

    private final AuditorProvider auditorProvider;

    public AuditMetaObjectHandler(AuditorProvider auditorProvider) {
        this.auditorProvider = auditorProvider;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        String auditor = safeAuditor();
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createBy", String.class, auditor);
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateBy", String.class, auditor);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        String auditor = safeAuditor();
        LocalDateTime now = LocalDateTime.now();
        strictUpdateFill(metaObject, "updateBy", String.class, auditor);
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    private String safeAuditor() {
        try {
            String v = auditorProvider.currentAuditor();
            return v == null || v.isBlank() ? "system" : v;
        } catch (Exception e) {
            LOGGER.warn("获取审计人失败，使用 system 作为审计人");
            return "system";
        }
    }
}
