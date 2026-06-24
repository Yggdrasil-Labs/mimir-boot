package com.yggdrasil.labs.exception.handler;

import com.yggdrasil.labs.common.response.R;
import com.yggdrasil.labs.test.base.BaseUnitTest;
import com.yggdrasil.labs.test.util.AssertUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultExceptionResponseFactory 单元测试
 *
 * @author Yggdrasil Labs
 * @since 2.1.0
 */
class DefaultExceptionResponseFactoryTest extends BaseUnitTest {

    private final DefaultExceptionResponseFactory factory = new DefaultExceptionResponseFactory();

    @Test
    void shouldReturnRWithDataWhenSerializable() {
        Object result = factory.createResponse("500", "error", "detail");

        assertInstanceOf(R.class, result);
        R<?> r = (R<?>) result;
        AssertUtils.assertEquals("500", r.getCode());
        AssertUtils.assertEquals("error", r.getMessage());
        AssertUtils.assertEquals("detail", r.getData());
    }

    @Test
    void shouldReturnRFailWhenDataNotSerializable() {
        Object result = factory.createResponse("500", "error", new Object());

        assertInstanceOf(R.class, result);
        R<?> r = (R<?>) result;
        AssertUtils.assertEquals("500", r.getCode());
        AssertUtils.assertEquals("error", r.getMessage());
        assertNull(r.getData());
    }

    @Test
    void shouldReturnRFailWhenDataIsNull() {
        Object result = factory.createResponse("500", "error", null);

        assertInstanceOf(R.class, result);
        R<?> r = (R<?>) result;
        AssertUtils.assertEquals("500", r.getCode());
        AssertUtils.assertEquals("error", r.getMessage());
        assertNull(r.getData());
    }

    @Test
    void shouldReturnRWithArrayListData() {
        java.util.ArrayList<String> errors = new java.util.ArrayList<>(java.util.List.of("field1: 不能为空", "field2: 格式错误"));
        Object result = factory.createResponse("PARAM_001", "参数校验失败", errors);

        assertInstanceOf(R.class, result);
        R<?> r = (R<?>) result;
        AssertUtils.assertEquals("PARAM_001", r.getCode());
        assertEquals(errors, r.getData());
    }
}
