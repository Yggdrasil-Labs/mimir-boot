package com.yggdrasil.labs.mybatis.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;

/**
 * 针对 {@link AutoMybatisProcessor} 的编译期测试：
 * 编译一个带有 @AutoMybatis 的实体类，断言生成 Mapper / Service / ServiceImpl。
 */
@SuppressWarnings("deprecation")
class AutoMybatisProcessorTest {

    @Test
    void generatesMapperServiceAndImpl() {
        String entitySrc = "package demo.entity;\n" +
                "import com.yggdrasil.labs.mybatis.annotation.AutoMybatis;\n" +
                "@AutoMybatis(mapperPackage=\"mapper\", servicePackage=\"service\", serviceImplPackage=\"service.impl\")\n" +
                "public class User {\n" +
                "  private Long id;\n" +
                "  private String name;\n" +
                "}\n";

        JavaFileObject entity = JavaFileObjects.forSourceString("demo.entity.User", entitySrc);

        // 提供最小桩类型，避免引入外部 mybatis-plus 依赖
        JavaFileObject iServiceStub = JavaFileObjects.forSourceString(
                "com.baomidou.mybatisplus.extension.service.IService",
                "package com.baomidou.mybatisplus.extension.service;\n" +
                        "public interface IService<T> {}\n");

        JavaFileObject serviceImplStub = JavaFileObjects.forSourceString(
                "com.baomidou.mybatisplus.extension.service.impl.ServiceImpl",
                "package com.baomidou.mybatisplus.extension.service.impl;\n" +
                        "public class ServiceImpl<M, T> {}\n");

        Compilation compilation = Compiler.javac()
                .withClasspathFrom(this.getClass().getClassLoader())
                .withProcessors(new AutoMybatisProcessor())
                .compile(entity, iServiceStub, serviceImplStub);

        assertThat(compilation).succeeded();

        // 断言生成的类型存在
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
}


