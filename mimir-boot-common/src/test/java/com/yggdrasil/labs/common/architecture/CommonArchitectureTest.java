package com.yggdrasil.labs.common.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

class CommonArchitectureTest {
    @Test
    void productionClassesFollowModuleDependencyDirection() {
        JavaClasses classes = new ClassFileImporter().importPath(Path.of("target/classes"));
        assertFalse(classes.isEmpty(), "Common 生产类导入不能为空");
        noClasses()
                .that()
                .resideInAPackage("com.yggdrasil.labs.common..")
                .should()
                .dependOnClassesThat(
                        resideInAPackage("com.yggdrasil.labs..")
                                .and(resideOutsideOfPackage("com.yggdrasil.labs.common..")))
                .check(classes);
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .check(classes);
        SlicesRuleDefinition.slices()
                .matching("com.yggdrasil.labs.common.(*)..")
                .should()
                .beFreeOfCycles()
                .check(classes);
    }
}
