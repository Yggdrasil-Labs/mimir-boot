package com.yggdrasil.labs.common.page;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yggdrasil.labs.common.batch.BatchItemResult;
import com.yggdrasil.labs.common.batch.BatchResult;
import com.yggdrasil.labs.common.batch.BatchStatus;
import com.yggdrasil.labs.common.exception.BizException;
import com.yggdrasil.labs.common.exception.ErrorCode;
import com.yggdrasil.labs.common.exception.SystemException;
import com.yggdrasil.labs.common.response.R;

/** 在不含 COLA 的隔离类加载器中执行，验证普通 Common 模型不链接 COLA 类型；由 {@link ColaOptionalDependencyTest} 加载 */
public final class ColaFreeProbe {

    private ColaFreeProbe() {}

    public static String run() throws Exception {
        try {
            Class.forName("com.alibaba.cola.dto.PageResponse");
            throw new IllegalStateException("隔离类加载器仍可见 COLA");
        } catch (ClassNotFoundException expected) {
            // 预期：COLA 不在类路径
        }
        for (Class<?> type :
                List.of(
                        R.class,
                        PageResult.class,
                        BizException.class,
                        SystemException.class,
                        BatchResult.class,
                        BatchItemResult.class,
                        BatchStatus.class)) {
            type.getDeclaredMethods();
            type.getDeclaredFields();
            type.getDeclaredConstructors();
        }

        PageResult<Integer> page = PageResult.of(List.of(1, 2), 2L, 1L, 10L);
        PageResult<String> mapped = page.map(String::valueOf);
        BatchResult<String> batch =
                BatchResult.of(
                        List.of(
                                BatchItemResult.success(0, null, "ok"),
                                BatchItemResult.failure(1, null, ErrorCode.DATA_NOT_FOUND)));
        BizException biz = BizException.fromError(ErrorCode.DATA_NOT_FOUND);
        SystemException sys = SystemException.fromError(ErrorCode.SYSTEM_ERROR, null, biz);

        ObjectMapper mapper = new ObjectMapper();
        StringBuilder json = new StringBuilder();
        json.append(mapper.writeValueAsString(R.success(mapped)));
        json.append(mapper.writeValueAsString(R.<Serializable>failCode(ErrorCode.FAIL)));
        json.append(mapper.writeValueAsString(R.success(batch)));
        json.append(mapper.writeValueAsString(page));
        json.append(mapper.writeValueAsString(biz.getCode() + sys.getCode()));
        return json.toString();
    }
}
