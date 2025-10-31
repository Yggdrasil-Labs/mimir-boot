package com.yggdrasil.labs.mybatis.page;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yggdrasil.labs.common.page.PageRequest;
import com.yggdrasil.labs.common.page.PageResult;

import java.util.List;

public final class PageConverters {

    private PageConverters() {}

    public static <T> Page<T> toMybatisPage(PageRequest request) {
        if (request == null) {
            request = new PageRequest();
        }
        request.validateAndCorrect();
        long current = request.getPageIndex();
        long size = request.getPageSize();
        return new Page<>(current, size);
    }

    public static <T> PageResult<T> toPageResult(IPage<T> page) {
        List<T> records = page.getRecords();
        long total = page.getTotal();
        long current = page.getCurrent();
        long size = page.getSize();
        return new PageResult<>(records, total, current, size);
    }
}


