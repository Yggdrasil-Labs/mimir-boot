package com.yggdrasil.labs.test.arch;

import java.nio.file.Files;
import java.nio.file.Path;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/** 为架构规则导入生产类，拒绝空导入结果。 */
public final class MimirArchClasses {
    private MimirArchClasses() {}

    /**
     * 从类路径的目录与 jar 导入完整根包下的生产类，并排除测试输出目录。
     *
     * @param rootPackage 要导入的完整根包名，包含全部子包
     * @return 非空的生产类集合，适用于测试位于启动模块的多模块工程
     * @throws IllegalArgumentException 根包名为空或无效时抛出
     * @throws IllegalStateException 根包下未导入任何类时抛出
     */
    public static JavaClasses importMain(String rootPackage) {
        String root = ArchPackages.requirePackage(rootPackage, "rootPackage");
        JavaClasses classes =
                new ClassFileImporter()
                        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                        .importPackages(root);
        return requireClasses(classes, "rootPackage=" + root);
    }

    /**
     * 只导入指定编译输出目录中的类，隔离不同模块中名称相同的包。
     *
     * @param classesDirectory 生产类编译输出目录，通常为本模块的 target/classes
     * @return 非空的类集合
     * @throws IllegalArgumentException 目录参数为 null 时抛出
     * @throws IllegalStateException 路径不是现有目录或目录中没有类时抛出
     */
    public static JavaClasses importDirectory(Path classesDirectory) {
        if (classesDirectory == null) {
            throw new IllegalArgumentException("classesDirectory 不能为空");
        }
        if (!Files.isDirectory(classesDirectory)) {
            throw new IllegalStateException("classesDirectory 不是现有目录：" + classesDirectory);
        }
        return requireClasses(
                new ClassFileImporter().importPath(classesDirectory),
                "classesDirectory=" + classesDirectory);
    }

    /**
     * 拒绝空导入，防止架构测试因路径或包名错误而空跑。
     *
     * @param classes 导入结果
     * @param source 供错误定位的导入来源
     * @return 非空的原始类集合
     * @throws IllegalStateException 没有导入到类时抛出
     */
    private static JavaClasses requireClasses(JavaClasses classes, String source) {
        if (classes.isEmpty()) {
            throw new IllegalStateException("没有导入到类，请检查 " + source);
        }
        return classes;
    }
}
