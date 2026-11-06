package com.yggdrasil.labs.mybatis.page;

import java.io.Serializable;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yggdrasil.labs.common.page.PageRequest;
import com.yggdrasil.labs.common.page.PageResult;

/** 在公共分页模型与 MyBatis-Plus 分页对象之间转换。 */
public final class PageConverters {

    private PageConverters() {}

    /** 校正分页请求并创建 MyBatis-Plus 分页对象。 */
    public static <T> Page<T> toMybatisPage(PageRequest request) {
        if (request == null) {
            request = new PageRequest();
        }
        request.validateAndCorrect();
        long current = request.getPageIndex();
        long size = request.getPageSize();
        return new Page<>(current, size);
    }

    /** 保留记录及页码信息，转换为公共分页结果。 */
    public static <T extends Serializable> PageResult<T> toPageResult(IPage<T> page) {
        List<T> records = page.getRecords();
        long total = page.getTotal();
        long current = page.getCurrent();
        long size = page.getSize();
        return new PageResult<>(records, total, current, size);
    }
}
