package com.yggdrasil.labs.test.arch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.tools.ToolProvider;

/** 在临时目录编译真实类文件，隔离每个架构场景。 */
final class FixtureCompiler {
    private FixtureCompiler() {}

    static Path compile(Path directory, Map<String, String> sources) throws IOException {
        Path output = Files.createDirectories(directory.resolve("classes"));
        List<String> arguments =
                new ArrayList<>(
                        List.of(
                                "-proc:none",
                                "-g",
                                "-classpath",
                                System.getProperty("java.class.path"),
                                "-d",
                                output.toString()));
        for (Map.Entry<String, String> source : sources.entrySet()) {
            Path file =
                    directory.resolve("src").resolve(source.getKey().replace('.', '/') + ".java");
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            arguments.add(file.toString());
        }
        int status =
                ToolProvider.getSystemJavaCompiler()
                        .run(null, null, null, arguments.toArray(String[]::new));
        if (status != 0) {
            throw new IllegalStateException("架构夹具编译失败：" + status);
        }
        return output;
    }

    static String type(String name, String body) {
        int separator = name.lastIndexOf('.');
        return "package "
                + name.substring(0, separator)
                + "; public class "
                + name.substring(separator + 1)
                + " { "
                + body
                + " }";
    }
}
