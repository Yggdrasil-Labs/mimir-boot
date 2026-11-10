package com.yggdrasil.labs.web.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yggdrasil.labs.common.constant.HttpHeaderConstants;
import com.yggdrasil.labs.common.exception.ErrorCode;

/** 上传限额由容器执行，使用真实随机端口验证。 */
@SpringBootTest(
        classes = ExistingExceptionTraceMvcIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "logging.level.root=OFF",
            "server.address=127.0.0.1",
            "spring.servlet.multipart.max-file-size=1KB",
            "spring.servlet.multipart.max-request-size=8KB",
            "spring.servlet.multipart.resolve-lazily=true"
        })
class ExistingMultipartTraceMvcIntegrationTest {
    private static final String TRACE_ID = "existing-upload-trace";
    private static final String REQUEST_ID = "existing-upload-request";
    @Autowired private ObjectMapper objectMapper;
    @LocalServerPort private int port;

    @Test
    void enhancesRealServletMultipartSizeFailure() throws Exception {
        HttpResponse<String> response =
                OversizedUploadRequests.send(
                        port,
                        Map.of(
                                HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID,
                                HttpHeaderConstants.REQUEST_ID_HEADER, REQUEST_ID));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue("content-type"))
                .hasValueSatisfying(
                        value ->
                                assertThat(
                                                MediaType.parseMediaType(value)
                                                        .isCompatibleWith(
                                                                MediaType.APPLICATION_JSON))
                                        .isTrue());
        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.path("code").asText()).isEqualTo(ErrorCode.PARAM_INVALID.getCode());
        assertThat(json.path("traceId").asText()).isEqualTo(TRACE_ID);
        // 懒解析时 413 发生在拦截器之后，响应带本次 requestId
        assertThat(response.headers().allValues(HttpHeaderConstants.REQUEST_ID_HEADER))
                .containsExactly(REQUEST_ID);
    }
}
