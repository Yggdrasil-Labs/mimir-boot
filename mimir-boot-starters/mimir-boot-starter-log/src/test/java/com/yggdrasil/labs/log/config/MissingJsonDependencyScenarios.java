package com.yggdrasil.labs.log.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

import ch.qos.logback.classic.LoggerContext;

/** 在真正移除 Encoder 与全部 Jackson jar 的子 JVM 中验证 optional 依赖边界。 */
@ExtendWith(OutputCaptureExtension.class)
class MissingJsonDependencyScenarios {

    private static final String CONTAINER =
            "classpath:com/yggdrasil/labs/log/logback/container.xml";
    private static final String SERVER = "classpath:com/yggdrasil/labs/log/logback/server.xml";

    @TempDir Path logPath;
    private String marker;

    @BeforeEach
    void setUp() {
        MaskTestSupport.reset();
        marker = UUID.randomUUID().toString();
        assertThat(ClassUtils.isPresent("net.logstash.logback.encoder.LogstashEncoder", null))
                .isFalse();
        assertThat(ClassUtils.isPresent("com.fasterxml.jackson.databind.ObjectMapper", null))
                .isFalse();
        assertThat(ClassUtils.isPresent("com.fasterxml.jackson.core.JsonFactory", null)).isFalse();
    }

    @AfterEach
    void tearDown() {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).stop();
        MaskTestSupport.reset();
    }

    @Test
    void container_text_works_without_json_dependencies(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext ignored = start(CONTAINER)) {
            emitEvents();
        }
        assertThat(output.getOut())
                .contains("info " + marker, "error " + marker, "access " + marker);
        assertNoMissingClassErrors(output);
        assertNoFiles();
    }

    @Test
    void server_text_works_without_json_dependencies(CapturedOutput output) throws Exception {
        try (ConfigurableApplicationContext ignored =
                start(SERVER, "--mimir.boot.log.format=text")) {
            emitEvents();
        }
        ((LoggerContext) LoggerFactory.getILoggerFactory()).stop();
        Path directory = logPath.resolve("optional-app");
        assertThat(Files.readString(directory.resolve("info.log")))
                .contains("info " + marker, "error " + marker);
        assertThat(Files.readString(directory.resolve("error.log"))).contains("error " + marker);
        assertThat(Files.readString(directory.resolve("access.log"))).contains("access " + marker);
        assertThat(Files.readString(directory.resolve("sql.log"))).contains("sql " + marker);
        assertThat(output.getOut()).doesNotContain(marker);
        assertNoMissingClassErrors(output);
    }

    @Test
    void container_json_fails_before_creating_files() throws IOException {
        assertMissingDependency(CONTAINER);
    }

    @Test
    void server_json_fails_before_creating_files() throws IOException {
        assertMissingDependency(SERVER);
    }

    @Test
    void custom_xml_json_encoder_fails_with_missing_logstash_class() throws IOException {
        assertThatThrownBy(() -> start("classpath:mimir-json-capture.xml"))
                .hasStackTraceContaining("net/logstash/logback");
        assertNoFiles();
    }

    private void assertMissingDependency(String config) throws IOException {
        assertThatThrownBy(() -> start(config, "--mimir.boot.log.format=json"))
                .hasStackTraceContaining("net.logstash.logback:logstash-logback-encoder");
        assertNoFiles();
    }

    private void assertNoFiles() throws IOException {
        try (var files = Files.walk(logPath)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    private static void assertNoMissingClassErrors(CapturedOutput output) {
        assertThat(output.getAll())
                .doesNotContain(
                        "ClassNotFoundException",
                        "NoClassDefFoundError",
                        "ERROR in ch.qos.logback");
    }

    private void emitEvents() {
        LoggerFactory.getLogger("demo.optional").info("info {}", marker);
        LoggerFactory.getLogger("demo.optional").error("error {}", marker);
        LoggerFactory.getLogger("access.log").info("access {}", marker);
        LoggerFactory.getLogger("SQL.JSON").info("sql {}", marker);
    }

    private ConfigurableApplicationContext start(String config, String... extra) {
        SpringApplication application = new SpringApplication(App.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        String[] args = new String[extra.length + 4];
        args[0] = "--logging.config=" + config;
        args[1] = "--logging.file.path=" + logPath;
        args[2] = "--spring.application.name=optional-app";
        args[3] = "--spring.main.banner-mode=off";
        System.arraycopy(extra, 0, args, 4, extra.length);
        return application.run(args);
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({LogMaskAutoConfiguration.class, LogFieldAutoConfiguration.class})
    static class App {}
}
