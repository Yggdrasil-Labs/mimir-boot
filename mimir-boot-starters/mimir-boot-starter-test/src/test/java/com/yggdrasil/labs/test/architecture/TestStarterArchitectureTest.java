package com.yggdrasil.labs.test.architecture;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.yggdrasil.labs.test.arch.MimirArchClasses;
import com.yggdrasil.labs.test.arch.MimirArchRules;

class TestStarterArchitectureTest {
    @Test
    void productionClassesFollowModuleDependencyDirection() {
        JavaClasses classes = MimirArchClasses.importDirectory(Path.of("target/classes"));
        MimirArchRules.onlyDependOnWithin(
                        "com.yggdrasil.labs",
                        "com.yggdrasil.labs.test",
                        "com.yggdrasil.labs.common")
                .check(classes);
        MimirArchRules.noCycles("com.yggdrasil.labs.test").check(classes);
    }
}
