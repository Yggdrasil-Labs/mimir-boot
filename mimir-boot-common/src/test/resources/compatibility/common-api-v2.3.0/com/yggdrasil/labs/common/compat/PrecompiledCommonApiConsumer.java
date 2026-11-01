// 兼容性 fixture：针对 v2.3.0 公共 API 预编译的旧消费者，运行时链接当前 Common。
package com.yggdrasil.labs.common.compat;

import java.util.ArrayList;
import java.util.List;

import com.yggdrasil.labs.common.exception.BaseException;
import com.yggdrasil.labs.common.exception.BizException;
import com.yggdrasil.labs.common.exception.ErrorCode;
import com.yggdrasil.labs.common.exception.SystemException;
import com.yggdrasil.labs.common.response.R;

public class PrecompiledCommonApiConsumer {

    public List<String> run() {
        List<String> out = new ArrayList<>();
        R<String> failWithCode = R.fail("E100", "oops");
        R<String> failWithMessage = R.fail("bad");
        R<String> failNull = R.fail(null);
        R<String> success = R.success();
        R<String> successData = R.success("d");
        R<String> successMessage = R.success("m", "d");
        R<String> constructed = new R<>("C", "M", "D");
        constructed.setTraceId("t");
        out.add(failWithCode.getCode() + "/" + failWithCode.getMessage() + "/" + failWithCode.isFail());
        out.add(failWithMessage.getCode() + "/" + failWithMessage.getMessage());
        out.add(failNull.getCode() + "/" + failNull.getMessage());
        out.add(success.getCode() + "/" + success.isSuccess() + "/" + (success.getTimestamp() != null));
        out.add(successData.getData() + "/" + successMessage.getMessage());
        out.add(constructed.getCode() + "/" + constructed.getTraceId());

        Throwable cause = new IllegalStateException("cause");
        List<BaseException> exceptions =
                List.of(
                        new BizException("B1", "biz"),
                        new BizException("B2", "biz-cause", cause),
                        new BizException(ErrorCode.DATA_NOT_FOUND),
                        new BizException(ErrorCode.DATA_NOT_FOUND, cause),
                        new SystemException("S1", "sys"),
                        new SystemException("S2", "sys-cause", cause),
                        new SystemException(ErrorCode.SYSTEM_ERROR),
                        new SystemException(ErrorCode.SYSTEM_ERROR, cause));
        for (BaseException e : exceptions) {
            out.add(
                    e.getClass().getSimpleName()
                            + ":"
                            + e.getCode()
                            + "/"
                            + e.getMessage()
                            + "/"
                            + (e.getCause() == cause));
        }
        return out;
    }
}
