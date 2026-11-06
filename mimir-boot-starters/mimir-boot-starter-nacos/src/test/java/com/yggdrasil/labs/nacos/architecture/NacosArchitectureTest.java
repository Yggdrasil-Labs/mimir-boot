package com.yggdrasil.labs.nacos.architecture;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import com.yggdrasil.labs.test.arch.MimirArchClasses;
import com.yggdrasil.labs.test.arch.MimirArchRules;

class NacosArchitectureTest {
    @Test
    void productionClassesFollowModuleDependencyDirection() {
        JavaClasses classes = MimirArchClasses.importDirectory(Path.of("target/classes"));
        MimirArchRules.onlyDependOnWithin(
                        "com.yggdrasil.labs",
                        "com.yggdrasil.labs.nacos",
                        "com.yggdrasil.labs.common")
                .check(classes);
        // TD-051：保留 ConfigDecryptProcessor 既有公开构造器使用的配置类型。
        SlicesRuleDefinition.slices()
                .matching("com.yggdrasil.labs.nacos.(*)..")
                .should()
                .beFreeOfCycles()
                .ignoreDependency(
                        "com.yggdrasil.labs.nacos.decrypt.ConfigDecryptProcessor",
                        "com.yggdrasil.labs.nacos.config.NacosEncryptProperties")
                .check(classes);
    }
}
