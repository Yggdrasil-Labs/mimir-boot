package com.yggdrasil.labs.common.page;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** 分页数据逐条映射，供 {@link PageResult#map} 与 {@link ColaPageConverters} 共用，保证两者语义一致；仅包内使用 */
final class PageItems {

    private PageItems() {}

    /** 源为 null 时返回 null，否则返回新的可变列表；null 元素不交给函数并原位保留，函数返回 null 同样保留，函数异常原样传播 */
    static <S, R> List<R> map(List<S> data, Function<? super S, ? extends R> mapper) {
        if (data == null) {
            return null;
        }
        List<R> mapped = new ArrayList<>(data.size());
        for (S item : data) {
            mapped.add(item == null ? null : mapper.apply(item));
        }
        return mapped;
    }
}
