package com.yggdrasil.labs.log.architecture;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.yggdrasil.labs.test.arch.MimirArchClasses;
import com.yggdrasil.labs.test.arch.MimirArchRules;

class LogArchitectureTest {
    @Test
    void productionClassesFollowModuleDependencyDirection() {
        JavaClasses classes = MimirArchClasses.importDirectory(Path.of("target/classes"));
        MimirArchRules.onlyDependOnWithin(
                        "com.yggdrasil.labs", "com.yggdrasil.labs.log", "com.yggdrasil.labs.common")
                .check(classes);
        MimirArchRules.noCycles("com.yggdrasil.labs.log").check(classes);
    }

    @Test
    void textAndFieldContractsDoNotLinkOptionalJsonLibraries() {
        JavaClasses classes = MimirArchClasses.importDirectory(Path.of("target/classes"));
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("..log.field..")
                .or()
                .haveNameMatching(
                        ".*\\.(?:LogFieldCaptureAppender|FieldSnapshot|ValueNormalizer|LogFieldProviderRegistry|LogJsonKeys|LogJsonSettings|FieldSnapshotKeyValuePair|JsonPointer|LogJsonSettingsCodec|FormatDelegatingEncoder|StructuredMaskSupport)")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("net.logstash..", "com.fasterxml..")
                .check(classes);
    }
}
