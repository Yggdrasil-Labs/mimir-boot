package com.yggdrasil.labs.web.architecture;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import com.yggdrasil.labs.test.arch.MimirArchClasses;
import com.yggdrasil.labs.test.arch.MimirArchRules;

class WebArchitectureTest {
    @Test
    void productionClassesFollowModuleDependencyDirection() {
        JavaClasses classes = MimirArchClasses.importDirectory(Path.of("target/classes"));
        MimirArchRules.onlyDependOnWithin(
                        "com.yggdrasil.labs",
                        "com.yggdrasil.labs.web",
                        "com.yggdrasil.labs.common",
                        "com.yggdrasil.labs.exception")
                .check(classes);
        // TD-051：只保留已发布构造器/返回类型涉及的配置属性依赖，其他循环仍检查。
        SlicesRuleDefinition.slices()
                .matching("com.yggdrasil.labs.web.(*)..")
                .should()
                .beFreeOfCycles()
                .ignoreDependency(
                        "com.yggdrasil.labs.web.advice.ResponseBodyEnhancer",
                        "com.yggdrasil.labs.web.config.WebProperties")
                .ignoreDependency(
                        "com.yggdrasil.labs.web.advice.ResponseBodyEnhancer",
                        "com.yggdrasil.labs.web.config.WebProperties$Response")
                .check(classes);
    }
}
