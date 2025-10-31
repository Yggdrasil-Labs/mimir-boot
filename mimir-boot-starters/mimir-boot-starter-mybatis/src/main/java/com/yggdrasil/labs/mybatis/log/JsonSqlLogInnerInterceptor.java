package com.yggdrasil.labs.mybatis.log;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.yggdrasil.labs.mybatis.util.SqlLogMaskUtils;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

/**
 * 结构化 SQL 日志拦截器，输出 JSON 格式 SQL 与参数。
 *
 * <p>参数输出将结合 {@code SensitiveField} 做脱敏处理。</p>
 */
public class JsonSqlLogInnerInterceptor implements InnerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger("SQL.JSON");

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        try {
            BoundSql boundSql = sh.getBoundSql();
            Map<String, Object> payload = new HashMap<>();
            payload.put("sql", boundSql.getSql());
            Object params = boundSql.getParameterObject();
            payload.put("params", SqlLogMaskUtils.maskParams(params));
            LOGGER.info(JSON.toJSONString(payload));
        } catch (Exception ignore) {
            // no-op
        }
    }
}


