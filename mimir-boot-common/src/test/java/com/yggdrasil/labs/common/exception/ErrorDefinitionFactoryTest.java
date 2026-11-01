package com.yggdrasil.labs.common.exception;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ErrorDefinitionFactoryTest {

    /** 模拟业务错误枚举 */
    enum OrderError implements ErrorDefinition {
        ORDER_NOT_FOUND("ORDER_NOT_FOUND", "订单不存在"),
        NULL_MESSAGE("ORDER_NULL_MESSAGE", null),
        PADDED_SUCCESS(" 00000 ", "带空白的成功码");

        private final String code;
        private final String message;

        OrderError(String code, String message) {
            this.code = code;
            this.message = message;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }

    private interface Factory {
        BaseException create(ErrorDefinition error, String message, Throwable cause);
    }

    static Stream<Arguments> factories() {
        return Stream.of(
                Arguments.of(BizException.class, (Factory) BizException::fromError),
                Arguments.of(SystemException.class, (Factory) SystemException::fromError));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void three_arg_factory_keeps_type_code_message_and_cause_identity(
            Class<?> type, Factory factory) {
        IllegalStateException cause = new IllegalStateException("db");

        BaseException ex = factory.create(OrderError.ORDER_NOT_FOUND, "订单 123 不存在", cause);

        assertSame(type, ex.getClass());
        assertEquals("ORDER_NOT_FOUND", ex.getCode());
        assertEquals("订单 123 不存在", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void one_and_two_arg_factories_use_default_message_without_cause() {
        BizException biz = BizException.fromError(OrderError.ORDER_NOT_FOUND);
        SystemException sys = SystemException.fromError(OrderError.ORDER_NOT_FOUND, null);
        BizException overridden = BizException.fromError(OrderError.ORDER_NOT_FOUND, "{} %s");

        assertEquals("订单不存在", biz.getMessage());
        assertNull(biz.getCause());
        assertEquals("订单不存在", sys.getMessage());
        assertNull(sys.getCause());
        assertEquals("{} %s", overridden.getMessage());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void default_message_with_cause_uses_null_message(Class<?> type, Factory factory) {
        RuntimeException cause = new RuntimeException("root");

        BaseException ex = factory.create(OrderError.ORDER_NOT_FOUND, null, cause);

        assertEquals("订单不存在", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void null_default_and_override_message_is_allowed(Class<?> type, Factory factory) {
        BaseException ex = factory.create(OrderError.NULL_MESSAGE, null, null);

        assertNull(ex.getMessage());
        assertNull(ex.getCause());
        assertEquals("ORDER_NULL_MESSAGE", ex.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void blank_override_message_is_kept_as_is(String message) {
        assertEquals(
                message, BizException.fromError(OrderError.ORDER_NOT_FOUND, message).getMessage());
        assertEquals(
                message,
                SystemException.fromError(OrderError.ORDER_NOT_FOUND, message).getMessage());
    }

    @Test
    void padded_success_code_is_not_trimmed() {
        assertEquals(" 00000 ", BizException.fromError(OrderError.PADDED_SUCCESS).getCode());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void invalid_definition_is_rejected(Class<?> type, Factory factory) {
        assertThrows(IllegalArgumentException.class, () -> factory.create(null, "m", null));
        for (String code : new String[] {null, "", " \t", "00000"}) {
            ErrorDefinition invalid = definition(code, "m");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> factory.create(invalid, null, null),
                    "code=" + code);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(ErrorCode.SUCCESS, null, null));
    }

    @Test
    void builtin_error_code_is_an_error_definition() {
        ErrorDefinition definition = ErrorCode.DATA_NOT_FOUND;

        BizException ex = BizException.fromError(definition);

        assertEquals("20001", ex.getCode());
        assertEquals("数据不存在", ex.getMessage());
    }

    @Test
    void definition_is_read_once_and_default_message_only_when_needed() {
        AtomicInteger codeReads = new AtomicInteger();
        AtomicInteger messageReads = new AtomicInteger();
        ErrorDefinition counting =
                new ErrorDefinition() {
                    @Override
                    public String getCode() {
                        codeReads.incrementAndGet();
                        return "E1";
                    }

                    @Override
                    public String getMessage() {
                        messageReads.incrementAndGet();
                        return "default";
                    }
                };

        BizException.fromError(counting, "override");
        assertEquals(1, codeReads.get());
        assertEquals(0, messageReads.get());

        SystemException.fromError(counting);
        assertEquals(2, codeReads.get());
        assertEquals(1, messageReads.get());
    }

    @Test
    void getter_exception_propagates_unchanged() {
        IllegalStateException boom = new IllegalStateException("boom");
        ErrorDefinition failing =
                new ErrorDefinition() {
                    @Override
                    public String getCode() {
                        throw boom;
                    }

                    @Override
                    public String getMessage() {
                        return null;
                    }
                };

        assertSame(
                boom,
                assertThrows(IllegalStateException.class, () -> BizException.fromError(failing)));
    }

    @Test
    void legacy_public_constructors_are_preserved() throws Exception {
        for (Class<?> type : List.of(BizException.class, SystemException.class)) {
            assertTrue(
                    Modifier.isPublic(
                            type.getConstructor(String.class, String.class).getModifiers()));
            assertNotNull(type.getConstructor(String.class, String.class, Throwable.class));
            assertNotNull(type.getConstructor(ErrorCode.class));
            assertNotNull(type.getConstructor(ErrorCode.class, Throwable.class));
        }
        for (Class<?>[] params :
                List.of(
                        new Class<?>[] {String.class, String.class},
                        new Class<?>[] {String.class, String.class, Throwable.class},
                        new Class<?>[] {ErrorCode.class},
                        new Class<?>[] {ErrorCode.class, Throwable.class})) {
            assertTrue(
                    Modifier.isProtected(
                            BaseException.class.getDeclaredConstructor(params).getModifiers()));
        }
    }

    @Test
    void error_definition_and_exception_contract_are_separate() {
        assertFalse(IException.class.isAssignableFrom(ErrorDefinition.class));
        assertFalse(ErrorDefinition.class.isAssignableFrom(IException.class));
        assertFalse(ErrorDefinition.class.isAssignableFrom(BaseException.class));
    }

    private static ErrorDefinition definition(String code, String message) {
        return new ErrorDefinition() {
            @Override
            public String getCode() {
                return code;
            }

            @Override
            public String getMessage() {
                return message;
            }
        };
    }
}
