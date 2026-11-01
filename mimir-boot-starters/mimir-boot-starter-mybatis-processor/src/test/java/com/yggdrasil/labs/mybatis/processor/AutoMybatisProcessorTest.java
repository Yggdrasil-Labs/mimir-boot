package com.yggdrasil.labs.mybatis.processor;

import static com.google.testing.compile.CompilationSubject.assertThat;

import java.util.ArrayList;
import java.util.List;

import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import com.yggdrasil.labs.test.base.BaseUnitTest;

/**
 * 针对 {@link AutoMybatisProcessor} 的编译期测试： 编译一个带有 @AutoMybatis 的实体类，断言生成 Mapper / Service /
 * ServiceImpl。
 */
@SuppressWarnings("deprecation")
class AutoMybatisProcessorTest extends BaseUnitTest {

    @Test
    void generatesMapperServiceAndImpl() {
        String entitySrc =
                """
package demo.entity;
import com.yggdrasil.labs.mybatis.annotation.AutoMybatis;
@AutoMybatis(mapperPackage="mapper", servicePackage="service", serviceImplPackage="service.impl")
public class User {
  private Long id;
  private String name;
}
""";

        JavaFileObject entity = JavaFileObjects.forSourceString("demo.entity.User", entitySrc);

        // BaseMapper 与 @Mapper 使用最小桩类型；Service 类型由 mybatis-plus-spring 测试依赖提供。
        JavaFileObject baseMapperStub =
                JavaFileObjects.forSourceString(
                        "com.baomidou.mybatisplus.core.mapper.BaseMapper",
                        """
                        package com.baomidou.mybatisplus.core.mapper;
                        public interface BaseMapper<T> {}
                        """);

        // 提供 @Mapper 注解的桩类型
        JavaFileObject mapperAnnotationStub =
                JavaFileObjects.forSourceString(
                        "org.apache.ibatis.annotations.Mapper",
                        """
                        package org.apache.ibatis.annotations;
                        public @interface Mapper {}
                        """);

        Compilation compilation =
                Compiler.javac()
                        .withClasspathFrom(this.getClass().getClassLoader())
                        .withProcessors(new AutoMybatisProcessor())
                        .compile(entity, baseMapperStub, mapperAnnotationStub);

        assertThat(compilation).succeeded();

        // 断言生成的类型存在，且包含 @Mapper 注解
        assertThat(compilation)
                .generatedSourceFile("demo.entity.mapper.UserMapper")
                .contentsAsUtf8String()
                .contains("@Mapper");

        assertThat(compilation)
                .generatedSourceFile("demo.entity.mapper.UserMapper")
                .contentsAsUtf8String()
                .contains("interface UserMapper");

        assertThat(compilation)
                .generatedSourceFile("demo.entity.service.UserService")
                .contentsAsUtf8String()
                .contains("interface UserService");

        assertThat(compilation)
                .generatedSourceFile("demo.entity.service.impl.UserServiceImpl")
                .contentsAsUtf8String()
                .contains("class UserServiceImpl");
    }

    @Test
    void generatesMapperServiceAndImpl_WithDoSuffix() {
        // 测试实体类名为 UserDO 时，生成的类名应该是 UserMapper、UserService 等（去掉 DO）
        String entitySrc =
                """
package demo.entity;
import com.yggdrasil.labs.mybatis.annotation.AutoMybatis;
@AutoMybatis(mapperPackage="mapper", servicePackage="service", serviceImplPackage="service.impl")
public class UserDO {
  private Long id;
  private String name;
}
""";

        JavaFileObject entity = JavaFileObjects.forSourceString("demo.entity.UserDO", entitySrc);

        // BaseMapper 与 @Mapper 使用最小桩类型；Service 类型由 mybatis-plus-spring 测试依赖提供。
        JavaFileObject baseMapperStub =
                JavaFileObjects.forSourceString(
                        "com.baomidou.mybatisplus.core.mapper.BaseMapper",
                        """
                        package com.baomidou.mybatisplus.core.mapper;
                        public interface BaseMapper<T> {}
                        """);

        // 提供 @Mapper 注解的桩类型
        JavaFileObject mapperAnnotationStub =
                JavaFileObjects.forSourceString(
                        "org.apache.ibatis.annotations.Mapper",
                        """
                        package org.apache.ibatis.annotations;
                        public @interface Mapper {}
                        """);

        Compilation compilation =
                Compiler.javac()
                        .withClasspathFrom(this.getClass().getClassLoader())
                        .withProcessors(new AutoMybatisProcessor())
                        .compile(entity, baseMapperStub, mapperAnnotationStub);

        assertThat(compilation).succeeded();

        // 断言生成的类型存在，且类名去掉了 DO 后缀，且包含 @Mapper 注解
        // 验证生成的类名是 UserMapper（不是 UserDOMapper）
        assertThat(compilation)
                .generatedSourceFile("demo.entity.mapper.UserMapper")
                .contentsAsUtf8String()
                .contains("interface UserMapper");

        assertThat(compilation)
                .generatedSourceFile("demo.entity.mapper.UserMapper")
                .contentsAsUtf8String()
                .contains("@Mapper");

        // 验证实体类引用保持原样（UserDO），JavaPoet 可能使用导入或完全限定名
        assertThat(compilation)
                .generatedSourceFile("demo.entity.mapper.UserMapper")
                .contentsAsUtf8String()
                .contains("BaseMapper<");
        assertThat(compilation)
                .generatedSourceFile("demo.entity.mapper.UserMapper")
                .contentsAsUtf8String()
                .contains("UserDO");

        // 验证生成的类名是 UserService（不是 UserDOService）
        assertThat(compilation)
                .generatedSourceFile("demo.entity.service.UserService")
                .contentsAsUtf8String()
                .contains("interface UserService");
        assertThat(compilation)
                .generatedSourceFile("demo.entity.service.UserService")
                .contentsAsUtf8String()
                .contains("IService<");
        assertThat(compilation)
                .generatedSourceFile("demo.entity.service.UserService")
                .contentsAsUtf8String()
                .contains("UserDO");

        // 验证生成的类名是 UserServiceImpl（不是 UserDOServiceImpl）
        assertThat(compilation)
                .generatedSourceFile("demo.entity.service.impl.UserServiceImpl")
                .contentsAsUtf8String()
                .contains("class UserServiceImpl");
        assertThat(compilation)
                .generatedSourceFile("demo.entity.service.impl.UserServiceImpl")
                .contentsAsUtf8String()
                .contains("ServiceImpl<");
        assertThat(compilation)
                .generatedSourceFile("demo.entity.service.impl.UserServiceImpl")
                .contentsAsUtf8String()
                .contains("UserMapper");
        assertThat(compilation)
                .generatedSourceFile("demo.entity.service.impl.UserServiceImpl")
                .contentsAsUtf8String()
                .contains("UserDO");
    }

    @Test
    void supportedSourceVersionFollowsRunningCompiler() {
        String entitySrc =
                """
package demo.entity;
import com.yggdrasil.labs.mybatis.annotation.AutoMybatis;
@AutoMybatis
public class UserDO {
  private Long id;
}
""";

        // 以当前编译器支持的最新 release 编译，不应出现“支持的源版本低于当前源版本”的处理器告警
        String latestRelease = String.valueOf(SourceVersion.latestSupported().ordinal());
        Compilation compilation =
                compile(
                        List.of("--release", latestRelease),
                        JavaFileObjects.forSourceString("demo.entity.UserDO", entitySrc));

        assertThat(compilation).succeeded();
        Assertions.assertThat(compilation.diagnostics())
                .filteredOn(d -> d.getKind() == Diagnostic.Kind.WARNING)
                .extracting(d -> d.getMessage(null))
                .noneMatch(message -> message.contains("AutoMybatisProcessor"));
        Assertions.assertThat(new AutoMybatisProcessor().getSupportedSourceVersion())
                .isEqualTo(SourceVersion.latestSupported());
        assertThat(compilation).generatedSourceFile("demo.entity.mapper.UserMapper");
        assertThat(compilation).generatedSourceFile("demo.entity.service.UserService");
        assertThat(compilation).generatedSourceFile("demo.entity.service.impl.UserServiceImpl");
    }

    @Test
    void differentEntitiesInSamePackageDoNotConflict() {
        Compilation compilation =
                compile(List.of(), entity("User", "@AutoMybatis"), entity("Order", "@AutoMybatis"));

        assertThat(compilation).succeeded();
        for (String name : List.of("User", "Order")) {
            assertThat(compilation).generatedSourceFile("demo.entity.mapper." + name + "Mapper");
            assertThat(compilation).generatedSourceFile("demo.entity.service." + name + "Service");
            assertThat(compilation)
                    .generatedSourceFile("demo.entity.service.impl." + name + "ServiceImpl");
        }
    }

    @Test
    void sameGeneratedQualifiedNameFailsCompilation() {
        // UserDO 去除 DO 后缀后与 User 同名，生成包与后缀相同，产物全限定名重复
        Compilation compilation =
                compile(
                        List.of(),
                        entity("UserDO", "@AutoMybatis"),
                        entity("User", "@AutoMybatis"));

        assertThat(compilation).failed();
    }

    @Test
    void conflictIsAvoidedOnlyWhenAllThreeArtifactsAreRenamed() {
        Compilation mapperOnly =
                compile(
                        List.of(),
                        entity("UserDO", "@AutoMybatis(mapperSuffix = \"DoMapper\")"),
                        entity("User", "@AutoMybatis"));
        assertThat(mapperOnly).failed();

        Compilation allRenamed =
                compile(
                        List.of(),
                        entity(
                                "UserDO",
                                "@AutoMybatis(mapperPackage = \"legacy.mapper\","
                                        + " serviceSuffix = \"LegacyService\","
                                        + " serviceImplSuffix = \"LegacyServiceImpl\")"),
                        entity("User", "@AutoMybatis"));
        assertThat(allRenamed).succeeded();
        assertThat(allRenamed).generatedSourceFile("demo.entity.legacy.mapper.UserMapper");
        assertThat(allRenamed).generatedSourceFile("demo.entity.service.UserLegacyService");
        assertThat(allRenamed)
                .generatedSourceFile("demo.entity.service.impl.UserLegacyServiceImpl");
        assertThat(allRenamed).generatedSourceFile("demo.entity.mapper.UserMapper");
        assertThat(allRenamed).generatedSourceFile("demo.entity.service.UserService");
        assertThat(allRenamed).generatedSourceFile("demo.entity.service.impl.UserServiceImpl");
    }

    private static JavaFileObject entity(String simpleName, String annotation) {
        return JavaFileObjects.forSourceString(
                "demo.entity." + simpleName,
                "package demo.entity;\n"
                        + "import com.yggdrasil.labs.mybatis.annotation.AutoMybatis;\n"
                        + annotation
                        + "\npublic class "
                        + simpleName
                        + " {\n  private Long id;\n}\n");
    }

    private Compilation compile(List<String> options, JavaFileObject... entities) {
        List<JavaFileObject> sources = new ArrayList<>(List.of(entities));
        // BaseMapper 与 @Mapper 使用最小桩类型；Service 类型由 mybatis-plus-spring 测试依赖提供。
        sources.add(
                JavaFileObjects.forSourceString(
                        "com.baomidou.mybatisplus.core.mapper.BaseMapper",
                        """
                        package com.baomidou.mybatisplus.core.mapper;
                        public interface BaseMapper<T> {}
                        """));
        sources.add(
                JavaFileObjects.forSourceString(
                        "org.apache.ibatis.annotations.Mapper",
                        """
                        package org.apache.ibatis.annotations;
                        public @interface Mapper {}
                        """));
        return Compiler.javac()
                .withClasspathFrom(this.getClass().getClassLoader())
                .withOptions(options.toArray())
                .withProcessors(new AutoMybatisProcessor())
                .compile(sources);
    }
}
