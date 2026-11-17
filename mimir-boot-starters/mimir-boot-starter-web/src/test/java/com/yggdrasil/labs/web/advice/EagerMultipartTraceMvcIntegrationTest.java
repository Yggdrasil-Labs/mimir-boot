package com.yggdrasil.labs.web.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.yggdrasil.labs.common.constant.HttpHeaderConstants;

/** 默认非懒解析时 multipart 超限在 Trace 拦截器之前失败，响应不回传 requestId；MockMvc 无法复现，使用真实端口。 */
@SpringBootTest(
        classes = ExistingExceptionTraceMvcIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "logging.level.root=OFF",
            "server.address=127.0.0.1",
            "spring.servlet.multipart.max-file-size=1KB",
            "spring.servlet.multipart.max-request-size=8KB",
            "spring.servlet.multipart.resolve-lazily=false"
        })
class EagerMultipartTraceMvcIntegrationTest {

    @LocalServerPort private int port;

    @Test
    void eagerMultipartSizeFailureHappensBeforeTraceInterceptor() throws Exception {
        HttpResponse<String> response =
                OversizedUploadRequests.send(
                        port,
                        Map.of(HttpHeaderConstants.REQUEST_ID_HEADER, "eager-upload-request"));

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue(HttpHeaderConstants.REQUEST_ID_HEADER)).isEmpty();
    }
}
