package com.yggdrasil.labs.exception.architecture;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import com.yggdrasil.labs.test.arch.MimirArchClasses;
import com.yggdrasil.labs.test.arch.MimirArchRules;

class ExceptionArchitectureTest {
    @Test
    void productionClassesFollowModuleDependencyDirection() {
        JavaClasses classes = MimirArchClasses.importDirectory(Path.of("target/classes"));
        MimirArchRules.onlyDependOnWithin(
                        "com.yggdrasil.labs",
                        "com.yggdrasil.labs.exception",
                        "com.yggdrasil.labs.common")
                .check(classes);
        // TD-051：既有公开构造器使用配置属性类型，保留接口；仅忽略已确认的两条类型依赖。
        SlicesRuleDefinition.slices()
                .matching("com.yggdrasil.labs.exception.(*)..")
                .should()
                .beFreeOfCycles()
                .ignoreDependency(
                        "com.yggdrasil.labs.exception.handler.MimirExceptionHandler",
                        "com.yggdrasil.labs.exception.config.ExceptionProperties")
                .ignoreDependency(
                        "com.yggdrasil.labs.exception.handler.MimirExceptionHandler",
                        "com.yggdrasil.labs.exception.config.ExceptionProperties$Validation")
                .check(classes);
    }
}
