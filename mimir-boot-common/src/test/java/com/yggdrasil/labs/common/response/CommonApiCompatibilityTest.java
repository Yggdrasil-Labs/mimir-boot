package com.yggdrasil.labs.common.response;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;

import com.yggdrasil.labs.common.exception.ErrorCode;

/** 以 v2.3.0 公共签名 fixture 预编译旧消费者，删除历史 API 字节码后链接当前 Common 运行 */
class CommonApiCompatibilityTest {

    private static final String FIXTURE_ROOT = "/compatibility/common-api-v2.3.0/";

    private static final List<String> LEGACY_API =
            List.of(
                    "com/yggdrasil/labs/common/response/R",
                    "com/yggdrasil/labs/common/exception/IException",
                    "com/yggdrasil/labs/common/exception/ErrorCode",
                    "com/yggdrasil/labs/common/exception/BaseException",
                    "com/yggdrasil/labs/common/exception/BizException",
                    "com/yggdrasil/labs/common/exception/SystemException");

    private static final String CONSUMER =
            "com/yggdrasil/labs/common/compat/PrecompiledCommonApiConsumer";

    @Test
    void legacy_consumer_links_against_current_common() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "兼容性测试需要 JDK JavaCompiler");
        Path directory = Files.createTempDirectory("common-api-legacy-");
        Path sources = directory.resolve("sources");
        Path classes = directory.resolve("classes");
        Files.createDirectories(classes);
        List<Path> files = new ArrayList<>();
        for (String type : Stream.concat(LEGACY_API.stream(), Stream.of(CONSUMER)).toList()) {
            Path target = sources.resolve(type + ".java");
            copyFixture(type + ".java", target);
            files.add(target);
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, null, null)) {
            boolean compiled =
                    compiler.getTask(
                                    null,
                                    fileManager,
                                    diagnostics,
                                    List.of("-proc:none", "-d", classes.toString()),
                                    null,
                                    fileManager.getJavaFileObjects(files.toArray(Path[]::new)))
                            .call();
            assertTrue(compiled, diagnostics.getDiagnostics().toString());
        }
        for (String type : LEGACY_API) {
            Files.delete(classes.resolve(type + ".class"));
        }

        try (URLClassLoader loader =
                new URLClassLoader(new URL[] {classes.toUri().toURL()}, R.class.getClassLoader())) {
            Class<?> consumerType = Class.forName(CONSUMER.replace('/', '.'), true, loader);
            Object consumer = consumerType.getDeclaredConstructor().newInstance();

            @SuppressWarnings("unchecked")
            List<String> result = (List<String>) consumerType.getMethod("run").invoke(consumer);

            assertEquals(
                    List.of(
                            "E100/oops/true",
                            "00001/bad",
                            "00001/null",
                            "00000/true/true",
                            "d/m",
                            "C/t",
                            "BizException:B1/biz/false",
                            "BizException:B2/biz-cause/true",
                            "BizException:20001/数据不存在/false",
                            "BizException:20001/数据不存在/true",
                            "SystemException:S1/sys/false",
                            "SystemException:S2/sys-cause/true",
                            "SystemException:10000/系统错误/false",
                            "SystemException:10000/系统错误/true"),
                    result);
        }
    }

    @Test
    void legacy_r_factories_keep_descriptors() throws Exception {
        assertStaticPublic(R.class.getMethod("success"));
        assertStaticPublic(R.class.getMethod("success", Serializable.class));
        assertStaticPublic(R.class.getMethod("success", String.class, Serializable.class));
        assertStaticPublic(R.class.getMethod("fail", String.class, String.class));
        assertStaticPublic(R.class.getMethod("fail", String.class));
        assertTrue(
                Modifier.isPublic(
                        R.class
                                .getConstructor(String.class, String.class, Serializable.class)
                                .getModifiers()));
        assertEquals(
                0,
                Stream.of(R.class.getMethods())
                        .filter(m -> m.getName().equals("fail"))
                        .filter(m -> m.getParameterCount() == 1)
                        .filter(m -> m.getParameterTypes()[0] != String.class)
                        .count(),
                "不得新增 fail 单参重载，避免 R.fail(null) 歧义");
        assertNotNull(ErrorCode.class.getMethod("fromCode", String.class));
        assertNotNull(ErrorCode.class.getMethod("fromCodeOrNull", String.class));
    }

    private static void assertStaticPublic(Method method) {
        assertTrue(Modifier.isPublic(method.getModifiers()), method.toString());
        assertTrue(Modifier.isStatic(method.getModifiers()), method.toString());
    }

    private static void copyFixture(String resourcePath, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        try (InputStream input =
                CommonApiCompatibilityTest.class.getResourceAsStream(FIXTURE_ROOT + resourcePath)) {
            assertNotNull(input, "缺少兼容性 fixture: " + resourcePath);
            Files.copy(input, destination);
        }
    }
}
