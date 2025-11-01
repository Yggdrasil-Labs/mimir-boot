package com.yggdrasil.labs.mybatis.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yggdrasil.labs.common.page.PageRequest;
import com.yggdrasil.labs.common.page.PageResult;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 分页转换器测试
 *
 * @author Yggdrasil Labs
 * @since 1.0.0
 */
class PageConvertersTest {

    @Test
    void testToMybatisPageWithNullRequest() {
        Page<String> page = PageConverters.toMybatisPage(null);

        assertNotNull(page);
        assertTrue(page.getCurrent() >= 1);
        assertTrue(page.getSize() > 0);
    }

    @Test
    void testToMybatisPageWithValidRequest() {
        PageRequest request = new PageRequest();
        request.setPageIndex(2L);
        request.setPageSize(20L);

        Page<String> page = PageConverters.toMybatisPage(request);

        assertNotNull(page);
        assertEquals(2L, page.getCurrent());
        assertEquals(20L, page.getSize());
    }

    @Test
    void testToMybatisPageWithInvalidRequest() {
        PageRequest request = new PageRequest();
        request.setPageIndex(0L);  // 无效页码
        request.setPageSize(-1L);   // 无效页大小

        Page<String> page = PageConverters.toMybatisPage(request);

        assertNotNull(page);
        // validateAndCorrect() 应该修正这些值
        assertTrue(page.getCurrent() >= 1);
        assertTrue(page.getSize() > 0);
    }

    @Test
    void testToPageResult() {
        Page<String> page = new Page<>(1, 10);
        List<String> records = Arrays.asList("item1", "item2", "item3");
        page.setRecords(records);
        page.setTotal(100L);

        PageResult<String> result = PageConverters.toPageResult(page);

        assertNotNull(result);
        assertEquals(records, result.getData());
        assertEquals(100L, result.getTotalCount());
        assertEquals(1L, result.getPageIndex());
        assertEquals(10L, result.getPageSize());
    }

    @Test
    void testToPageResultWithEmptyRecords() {
        Page<String> page = new Page<>(1, 10);
        page.setRecords(Collections.emptyList());
        page.setTotal(0L);

        PageResult<String> result = PageConverters.toPageResult(page);

        assertNotNull(result);
        assertTrue(result.getData().isEmpty());
        assertEquals(0L, result.getTotalCount());
    }

    @Test
    void testToPageResultWithNullRecords() {
        Page<String> page = new Page<>(1, 10);
        page.setRecords(null);
        page.setTotal(0L);

        PageResult<String> result = PageConverters.toPageResult(page);

        assertNotNull(result);
        assertNull(result.getData());
    }

    @Test
    void testRoundTripConversion() {
        // 创建 PageRequest
        PageRequest request = new PageRequest();
        request.setPageIndex(3L);
        request.setPageSize(15L);

        // 转换为 MyBatis-Plus Page
        Page<String> mybatisPage = PageConverters.toMybatisPage(request);

        // 模拟查询结果
        List<String> records = Arrays.asList("a", "b", "c");
        mybatisPage.setRecords(records);
        mybatisPage.setTotal(100L);

        // 转换回 PageResult
        PageResult<String> result = PageConverters.toPageResult(mybatisPage);

        // 验证
        assertEquals(3L, result.getPageIndex());
        assertEquals(15L, result.getPageSize());
        assertEquals(records, result.getData());
        assertEquals(100L, result.getTotalCount());
    }

    @Test
    void testToPageResultWithLargePage() {
        Page<String> page = new Page<>(100, 1000);
        List<String> records = Collections.nCopies(1000, "item");
        page.setRecords(records);
        page.setTotal(100000L);

        PageResult<String> result = PageConverters.toPageResult(page);

        assertNotNull(result);
        assertEquals(100000L, result.getTotalCount());
        assertEquals(100L, result.getPageIndex());
        assertEquals(1000L, result.getPageSize());
        assertEquals(1000, result.getData().size());
    }
}

