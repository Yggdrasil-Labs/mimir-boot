package com.yggdrasil.labs.web.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

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
    @Autowired private ObjectMapper objectMapper;
    @LocalServerPort private int port;

    @Test
    void enhancesRealServletMultipartSizeFailure() throws Exception {
        String boundary = "mimir-existing-exception-boundary";
        String body =
                "--"
                        + boundary
                        + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\";"
                        + " filename=\"large.txt\"\r\n"
                        + "Content-Type: text/plain\r\n\r\n"
                        + "x".repeat(2048)
                        + "\r\n--"
                        + boundary
                        + "--\r\n";
        HttpRequest request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + port
                                                + "/existing-exception-trace/upload"))
                        .timeout(Duration.ofSeconds(10))
                        .header(HttpHeaderConstants.TRACE_ID_HEADER, TRACE_ID)
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();
        HttpResponse<String> response =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()
                        .send(request, HttpResponse.BodyHandlers.ofString());

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
    }
}
