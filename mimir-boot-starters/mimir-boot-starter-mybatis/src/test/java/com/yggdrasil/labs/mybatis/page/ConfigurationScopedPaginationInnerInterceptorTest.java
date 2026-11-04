package com.yggdrasil.labs.mybatis.page;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ConfigurationScopedPaginationInnerInterceptorTest {

    @Test
    void sharedInterceptorKeepsBothCountPathsInTheCallingConfigurationUnderConcurrency()
            throws Exception {
        Configuration first = new Configuration();
        Configuration second = new Configuration();
        MappedStatement firstQuery = query(first, "sample.Mapper.selectPage");
        MappedStatement secondQuery = query(second, firstQuery.getId());
        MappedStatement firstCount = query(first, "sample.Mapper.countPage");
        MappedStatement secondCount = query(second, firstCount.getId());
        first.addMappedStatement(firstCount);
        second.addMappedStatement(secondCount);
        var interceptor = new ConfigurationScopedPaginationInnerInterceptor();
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Void>> checks =
                    List.of(
                            () -> {
                                verifyCounts(start, interceptor, firstQuery, firstCount);
                                return null;
                            },
                            () -> {
                                verifyCounts(start, interceptor, secondQuery, secondCount);
                                return null;
                            });
            var firstCheck = pool.submit(checks.get(0));
            var secondCheck = pool.submit(checks.get(1));
            start.countDown();
            firstCheck.get(10, TimeUnit.SECONDS);
            secondCheck.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void missingExplicitCountNeverFallsBackToAnotherConfigurationsStatement() {
        var interceptor = new ConfigurationScopedPaginationInnerInterceptor();
        Configuration first = new Configuration();
        MappedStatement count = query(first, "sample.Mapper.countPage");
        first.addMappedStatement(count);
        assertSame(
                count,
                interceptor.buildCountMappedStatement(
                        query(first, "sample.Mapper.selectPage"), "countPage"));
        MappedStatement secondQuery = query(new Configuration(), "sample.Mapper.selectPage");
        assertNull(interceptor.buildCountMappedStatement(secondQuery, "countPage"));
        assertNull(interceptor.buildCountMappedStatement(secondQuery, "sample.Mapper.countPage"));
        assertNull(interceptor.buildCountMappedStatement(secondQuery, " "));
    }

    private static void verifyCounts(
            CountDownLatch start,
            ConfigurationScopedPaginationInnerInterceptor interceptor,
            MappedStatement query,
            MappedStatement count)
            throws InterruptedException {
        assertTrue(start.await(10, TimeUnit.SECONDS));
        for (int i = 0; i < 500; i++) {
            MappedStatement automatic = interceptor.buildAutoCountMappedStatement(query);
            assertSame(query.getConfiguration(), automatic.getConfiguration());
            assertEquals(query.getId() + "_mpCount", automatic.getId());
            assertSame(query.getSqlSource(), automatic.getSqlSource());
            assertSame(query.getParameterMap(), automatic.getParameterMap());
            assertEquals(Long.class, automatic.getResultMaps().get(0).getType());
            assertSame(count, interceptor.buildCountMappedStatement(query, "countPage"));
            assertSame(count, interceptor.buildCountMappedStatement(query, count.getId()));
        }
    }

    private static MappedStatement query(Configuration configuration, String id) {
        return new MappedStatement.Builder(
                        configuration,
                        id,
                        new StaticSqlSource(configuration, "SELECT 1"),
                        SqlCommandType.SELECT)
                .resultMaps(Collections.emptyList())
                .build();
    }
}
