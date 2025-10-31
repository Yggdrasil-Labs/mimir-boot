package com.yggdrasil.labs.mybatis.audit;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * 通用审计字段自动填充处理器。
 *
 * <p>支持字段：createdBy、createdTime、updatedBy、updatedTime。</p>
 */
public class AuditMetaObjectHandler implements MetaObjectHandler {

    private final AuditorProvider auditorProvider;

    public AuditMetaObjectHandler(AuditorProvider auditorProvider) {
        this.auditorProvider = auditorProvider;
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        String auditor = safeAuditor();
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdBy", String.class, auditor);
        strictInsertFill(metaObject, "createdTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedBy", String.class, auditor);
        strictInsertFill(metaObject, "updatedTime", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        String auditor = safeAuditor();
        LocalDateTime now = LocalDateTime.now();
        strictUpdateFill(metaObject, "updatedBy", String.class, auditor);
        strictUpdateFill(metaObject, "updatedTime", LocalDateTime.class, now);
    }

    private String safeAuditor() {
        try {
            String v = auditorProvider.currentAuditor();
            return v == null || v.isBlank() ? "system" : v;
        } catch (Exception e) {
            return "system";
        }
    }
}


