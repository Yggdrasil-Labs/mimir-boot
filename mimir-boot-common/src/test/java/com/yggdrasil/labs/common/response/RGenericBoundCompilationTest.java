package com.yggdrasil.labs.common.response;

import static com.yggdrasil.labs.common.CompilationSupport.compile;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.yggdrasil.labs.common.CompilationSupport.CompilationResult;

class RGenericBoundCompilationTest {

    @Test
    void serializable_payload_compiles_against_r(@TempDir Path outputDirectory) throws Exception {
        CompilationResult result =
                compile(
                        "example.SerializableConsumer",
                        """
package example;

import com.yggdrasil.labs.common.response.R;
import java.io.Serializable;

final class SerializablePayload implements Serializable {}

final class SerializableConsumer {
    private final R<SerializablePayload> response = R.success(new SerializablePayload());
}
""",
                        outputDirectory);

        assertTrue(result.success(), result::diagnostics);
    }

    @Test
    void non_serializable_payload_does_not_compile_against_r(@TempDir Path outputDirectory)
            throws Exception {
        CompilationResult result =
                compile(
                        "example.NonSerializableConsumer",
                        """
package example;

import com.yggdrasil.labs.common.response.R;

final class NonSerializablePayload {}

final class NonSerializableConsumer {
    private final R<NonSerializablePayload> response = R.success(new NonSerializablePayload());
}
""",
                        outputDirectory);

        assertFalse(result.success(), "non-Serializable payload must remain a compile-time error");
    }

    @Test
    void legacy_fail_null_and_new_failCode_sources_compile(@TempDir Path outputDirectory)
            throws Exception {
        CompilationResult result =
                compile(
                        "example.FailCodeConsumer",
                        """
package example;

import com.yggdrasil.labs.common.exception.BizException;
import com.yggdrasil.labs.common.exception.ErrorCode;
import com.yggdrasil.labs.common.exception.ErrorDefinition;
import com.yggdrasil.labs.common.exception.SystemException;
import com.yggdrasil.labs.common.response.R;
import java.io.Serializable;

enum OrderError implements ErrorDefinition {
    ORDER_NOT_FOUND;

    @Override
    public String getCode() {
        return "ORDER_NOT_FOUND";
    }

    @Override
    public String getMessage() {
        return "订单不存在";
    }
}

final class FailCodeConsumer {
    private final R<Serializable> legacyNull = R.fail(null);
    private final R<String> byEnum = R.failCode(OrderError.ORDER_NOT_FOUND);
    private final R<String> byBuiltin = R.failCode(ErrorCode.FAIL, null);
    private final BizException biz = BizException.fromError(OrderError.ORDER_NOT_FOUND, null);
    private final SystemException sys =
            SystemException.fromError(OrderError.ORDER_NOT_FOUND, null, null);
}
""",
                        outputDirectory);

        assertTrue(result.success(), result::diagnostics);
    }
}
