package com.yggdrasil.labs.mybatis.processor;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;

import static com.google.testing.compile.CompilationSubject.assertThat;

/**
 * 针对 {@link AutoMybatisProcessor} 的编译期测试：
 * 编译一个带有 @AutoMybatis 的实体类，断言生成 Mapper / Service / ServiceImpl。
 */
@SuppressWarnings("deprecation")
class AutoMybatisProcessorTest {

    @Test
    void generatesMapperServiceAndImpl() {
        String entitySrc = """
                package demo.entity;
                import com.yggdrasil.labs.mybatis.annotation.AutoMybatis;
                @AutoMybatis(mapperPackage="mapper", servicePackage="service", serviceImplPackage="service.impl")
                public class User {
                  private Long id;
                  private String name;
                }
                """;

        JavaFileObject entity = JavaFileObjects.forSourceString("demo.entity.User", entitySrc);

        // 提供最小桩类型，避免引入外部 mybatis-plus 依赖
        JavaFileObject baseMapperStub = JavaFileObjects.forSourceString(
                "com.baomidou.mybatisplus.core.mapper.BaseMapper",
                """
                        package com.baomidou.mybatisplus.core.mapper;
                        public interface BaseMapper<T> {}
                        """);

        JavaFileObject iServiceStub = JavaFileObjects.forSourceString(
                "com.baomidou.mybatisplus.extension.service.IService",
                """
                        package com.baomidou.mybatisplus.extension.service;
                        public interface IService<T> {}
                        """);

        JavaFileObject serviceImplStub = JavaFileObjects.forSourceString(
                "com.baomidou.mybatisplus.extension.service.impl.ServiceImpl",
                """
                        package com.baomidou.mybatisplus.extension.service.impl;
                        public class ServiceImpl<M, T> {}
                        """);

        // 提供 @Mapper 注解的桩类型
        JavaFileObject mapperAnnotationStub = JavaFileObjects.forSourceString(
                "org.apache.ibatis.annotations.Mapper",
                """
                        package org.apache.ibatis.annotations;
                        public @interface Mapper {}
                        """);

        Compilation compilation = Compiler.javac()
                .withClasspathFrom(this.getClass().getClassLoader())
                .withProcessors(new AutoMybatisProcessor())
                .compile(entity, baseMapperStub, iServiceStub, serviceImplStub, mapperAnnotationStub);

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
        String entitySrc = """
                package demo.entity;
                import com.yggdrasil.labs.mybatis.annotation.AutoMybatis;
                @AutoMybatis(mapperPackage="mapper", servicePackage="service", serviceImplPackage="service.impl")
                public class UserDO {
                  private Long id;
                  private String name;
                }
                """;

        JavaFileObject entity = JavaFileObjects.forSourceString("demo.entity.UserDO", entitySrc);

        // 提供最小桩类型，避免引入外部 mybatis-plus 依赖
        JavaFileObject baseMapperStub = JavaFileObjects.forSourceString(
                "com.baomidou.mybatisplus.core.mapper.BaseMapper",
                """
                        package com.baomidou.mybatisplus.core.mapper;
                        public interface BaseMapper<T> {}
                        """);

        JavaFileObject iServiceStub = JavaFileObjects.forSourceString(
                "com.baomidou.mybatisplus.extension.service.IService",
                """
                        package com.baomidou.mybatisplus.extension.service;
                        public interface IService<T> {}
                        """);

        JavaFileObject serviceImplStub = JavaFileObjects.forSourceString(
                "com.baomidou.mybatisplus.extension.service.impl.ServiceImpl",
                """
                        package com.baomidou.mybatisplus.extension.service.impl;
                        public class ServiceImpl<M, T> {}
                        """);

        // 提供 @Mapper 注解的桩类型
        JavaFileObject mapperAnnotationStub = JavaFileObjects.forSourceString(
                "org.apache.ibatis.annotations.Mapper",
                """
                        package org.apache.ibatis.annotations;
                        public @interface Mapper {}
                        """);

        Compilation compilation = Compiler.javac()
                .withClasspathFrom(this.getClass().getClassLoader())
                .withProcessors(new AutoMybatisProcessor())
                .compile(entity, baseMapperStub, iServiceStub, serviceImplStub, mapperAnnotationStub);

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
}


