package com.yggdrasil.labs.log.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 在独立子 JVM 中运行需要不同类路径的场景
 *
 * <p>项目质量门禁只允许一个 Failsafe execution，因此 legacy bootstrap（需 spring-cloud-context）与替代日志后端（去掉
 * Logback、改用 Log4j2）两组场景由本测试按场景组装类路径后拉起子 JVM 执行；所需 jar 路径由 maven-dependency-plugin 的 properties
 * 目标传入。
 */
class LogIsolatedJvmIntegrationTest {

    @TempDir Path workDir;

    @Test
    void legacy_bootstrap_scenarios_with_spring_cloud_context() throws Exception {
        List<String> classpath = baseClasspath();
        classpath.addAll(isolatedJars("mimir.isolated.legacy-bootstrap", "spring-cloud-context-"));

        runScenarios(LegacyBootstrapScenarios.class, classpath, 6);
    }

    @Test
    void backend_compatibility_scenarios_without_logback() throws Exception {
        List<String> classpath = new ArrayList<>();
        for (String entry : baseClasspath()) {
            String name = new File(entry).getName();
            if (!name.startsWith("logback-classic-")
                    && !name.startsWith("logback-core-")
                    && !name.startsWith("log4j-to-slf4j-")) {
                classpath.add(entry);
            }
        }
        assertThat(classpath).hasSizeLessThan(baseClasspath().size());
        classpath.addAll(
                isolatedJars("mimir.isolated.backend", "log4j-core-", "log4j-slf4j2-impl-"));

        runScenarios(BackendCompatibilityScenarios.class, classpath, 5);
    }

    private void runScenarios(Class<?> scenarios, List<String> classpath, int expectedTests)
            throws Exception {
        Path output = workDir.resolve(scenarios.getSimpleName() + ".log");
        Process process =
                new ProcessBuilder(
                                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                                "-cp",
                                String.join(File.pathSeparator, classpath),
                                IsolatedScenarioRunner.class.getName(),
                                scenarios.getName())
                        .directory(workDir.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(output.toFile())
                        .start();
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
        }
        String log = Files.readString(output);
        assertThat(finished).as("子 JVM 超时").isTrue();
        assertThat(process.exitValue()).as(log).isZero();
        assertThat(log)
                .contains(
                        "ISOLATED-SUMMARY found="
                                + expectedTests
                                + " succeeded="
                                + expectedTests
                                + " failed=0");
    }

    private static List<String> baseClasspath() {
        return new ArrayList<>(
                Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));
    }

    /** 读取构建时复制到 target 的子 JVM 专用 jar，并确认必需的 jar 均已存在 */
    private static List<String> isolatedJars(String property, String... requiredPrefixes)
            throws Exception {
        String directory = System.getProperty(property);
        assertThat(directory).as(property + " 未由 Failsafe 传入").isNotBlank().doesNotContain("${");
        List<String> jars;
        try (var files = Files.list(Path.of(directory))) {
            jars =
                    files.filter(f -> f.toString().endsWith(".jar"))
                            .map(Path::toString)
                            .sorted()
                            .toList();
        }
        for (String prefix : requiredPrefixes) {
            assertThat(jars)
                    .as(prefix)
                    .anyMatch(jar -> Path.of(jar).getFileName().toString().startsWith(prefix));
        }
        return jars;
    }
}
