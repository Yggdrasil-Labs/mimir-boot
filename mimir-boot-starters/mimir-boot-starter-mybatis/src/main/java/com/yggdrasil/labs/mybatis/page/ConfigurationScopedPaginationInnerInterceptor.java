package com.yggdrasil.labs.mybatis.page;

import java.util.Collections;

import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

/**
 * 使用当前工厂配置创建 COUNT 语句的默认分页拦截器。
 *
 * <p>MyBatis-Plus 3.5.17 按语句 ID 静态缓存 COUNT MappedStatement，会把首个工厂的 Configuration
 * 带入其他工厂。这里仅替换两处语句获取入口，保留上游分页、方言及 SQL 优化流程。自动 COUNT 逐次构建，不缓存工厂或语句，避免跨工厂引用及并发清理竞态。
 */
public class ConfigurationScopedPaginationInnerInterceptor extends PaginationInnerInterceptor {

    @Override
    protected MappedStatement buildCountMappedStatement(MappedStatement ms, String countId) {
        if (StringUtils.isNotBlank(countId)) {
            if (!countId.contains(".")) {
                String id = ms.getId();
                countId = id.substring(0, id.lastIndexOf('.') + 1) + countId;
            }
            try {
                return ms.getConfiguration().getMappedStatement(countId, false);
            } catch (Exception e) {
                // 与上游一致：找不到显式 countId 时警告，并交由分页流程生成自动 COUNT。
                logger.warn(String.format("can not find this countId: [\"%s\"]", countId));
            }
        }
        return null;
    }

    @Override
    protected MappedStatement buildAutoCountMappedStatement(MappedStatement ms) {
        Configuration configuration = ms.getConfiguration();
        // 字段复制与 MyBatis-Plus 3.5.17 保持一致；升级上游时需复核该扩展点。
        return new MappedStatement.Builder(
                        configuration,
                        ms.getId() + "_mpCount",
                        ms.getSqlSource(),
                        ms.getSqlCommandType())
                .resource(ms.getResource())
                .fetchSize(ms.getFetchSize())
                .statementType(ms.getStatementType())
                .timeout(ms.getTimeout())
                .parameterMap(ms.getParameterMap())
                .resultMaps(
                        Collections.singletonList(
                                new ResultMap.Builder(
                                                configuration,
                                                "mybatis-plus",
                                                Long.class,
                                                Collections.emptyList())
                                        .build()))
                .resultSetType(ms.getResultSetType())
                .cache(ms.getCache())
                .flushCacheRequired(ms.isFlushCacheRequired())
                .useCache(ms.isUseCache())
                .build();
    }
}
