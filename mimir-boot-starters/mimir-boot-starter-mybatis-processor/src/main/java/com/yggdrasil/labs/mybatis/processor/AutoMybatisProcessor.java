package com.yggdrasil.labs.mybatis.processor;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;
import com.yggdrasil.labs.mybatis.annotation.AutoMybatis;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Set;

@SupportedAnnotationTypes("com.yggdrasil.labs.mybatis.annotation.AutoMybatis")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class AutoMybatisProcessor extends AbstractProcessor {

    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(AutoMybatis.class)) {
            if (!(element instanceof TypeElement entityElement)) {
                continue;
            }
            AutoMybatis config = entityElement.getAnnotation(AutoMybatis.class);
            generateForEntity(entityElement, config);
        }
        return false;
    }

    private void generateForEntity(TypeElement entityElement, AutoMybatis config) {
        String entityQualifiedName = entityElement.getQualifiedName().toString();
        String entitySimpleName = entityElement.getSimpleName().toString();
        String basePackage = getPackageName(entityElement);

        String mapperPkg = joinPackage(basePackage, config.mapperPackage());
        String servicePkg = joinPackage(basePackage, config.servicePackage());
        String serviceImplPkg = joinPackage(basePackage, config.serviceImplPackage());

        // 去除实体类名末尾的 DO 后缀（如果存在），用于生成 Mapper、Service 类名
        String baseName = removeDoSuffix(entitySimpleName);
        String mapperName = baseName + config.mapperSuffix();
        String serviceName = baseName + config.serviceSuffix();
        String serviceImplName = baseName + config.serviceImplSuffix();

        ClassName entityClass = ClassName.bestGuess(entityQualifiedName);

        // Mapper: interface XxxMapper extends BaseMapper<Xxx>
        ClassName baseMapper = ClassName.get("com.baomidou.mybatisplus.core.mapper", "BaseMapper");
        ClassName mapperAnnotation = ClassName.get("org.apache.ibatis.annotations", "Mapper");
        AnnotationSpec mapperAnno = AnnotationSpec.builder(mapperAnnotation).build();
        
        TypeSpec mapperType = TypeSpec.interfaceBuilder(mapperName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(mapperAnno)
            .addSuperinterface(ParameterizedTypeName.get(baseMapper, entityClass))
            .build();
        writeJavaFile(mapperPkg, mapperType);

        // Service: interface XxxService extends IService<Xxx>
        ClassName iService = ClassName.get("com.baomidou.mybatisplus.extension.service", "IService");
        TypeSpec serviceType = TypeSpec.interfaceBuilder(serviceName)
            .addModifiers(Modifier.PUBLIC)
            .addSuperinterface(ParameterizedTypeName.get(iService, entityClass))
            .build();
        writeJavaFile(servicePkg, serviceType);

        // ServiceImpl: class XxxServiceImpl extends ServiceImpl<XxxMapper, Xxx> implements XxxService
        ClassName serviceImpl = ClassName.get("com.baomidou.mybatisplus.extension.service.impl", "ServiceImpl");
        ClassName mapperClass = ClassName.get(mapperPkg, mapperName);
        ClassName serviceInterface = ClassName.get(servicePkg, serviceName);

        AnnotationSpec serviceAnno = AnnotationSpec.builder(ClassName.get("org.springframework.stereotype", "Service")).build();

        TypeSpec serviceImplType = TypeSpec.classBuilder(serviceImplName)
            .addModifiers(Modifier.PUBLIC)
            .addAnnotation(serviceAnno)
            .superclass(ParameterizedTypeName.get(serviceImpl, mapperClass, entityClass))
            .addSuperinterface(serviceInterface)
            .build();
        writeJavaFile(serviceImplPkg, serviceImplType);
    }

    private void writeJavaFile(String pkg, TypeSpec type) {
        try {
            JavaFile.builder(pkg, type)
                .skipJavaLangImports(true)
                .build()
                .writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Failed generating " + type.name + ": " + e.getMessage());
        }
    }

    private String getPackageName(TypeElement type) {
        return processingEnv.getElementUtils().getPackageOf(type).getQualifiedName().toString();
    }

    private static String joinPackage(String base, String sub) {
        if (base == null || base.isEmpty()) return sub;
        if (sub == null || sub.isEmpty()) return base;
        return base + "." + sub;
    }

    /**
     * 去除类名末尾的 DO 后缀（如果存在）。
     * 例如：UserDO -> User, OrderDO -> Order, User -> User
     *
     * @param className 原始类名
     * @return 去除 DO 后缀后的类名
     */
    private static String removeDoSuffix(String className) {
        if (className != null && className.endsWith("DO") && className.length() > 2) {
            return className.substring(0, className.length() - 2);
        }
        return className;
    }
}


