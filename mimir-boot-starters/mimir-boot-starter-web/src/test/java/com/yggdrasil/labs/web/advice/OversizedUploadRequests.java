package com.yggdrasil.labs.web.advice;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.springframework.http.MediaType;

/** 向真实端口发送超过 1KB 文件限额的 multipart 上传，供容器级上传限额测试共用 */
final class OversizedUploadRequests {

    private static final String BOUNDARY = "mimir-oversized-upload-boundary";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private OversizedUploadRequests() {}

    static HttpResponse<String> send(int port, Map<String, String> headers)
            throws IOException, InterruptedException {
        String body =
                "--"
                        + BOUNDARY
                        + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\";"
                        + " filename=\"large.txt\"\r\n"
                        + "Content-Type: text/plain\r\n\r\n"
                        + "x".repeat(2048)
                        + "\r\n--"
                        + BOUNDARY
                        + "--\r\n";
        HttpRequest.Builder request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + port
                                                + "/existing-exception-trace/upload"))
                        .timeout(TIMEOUT)
                        .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                        .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(request::header);
        return HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build()
                .send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
