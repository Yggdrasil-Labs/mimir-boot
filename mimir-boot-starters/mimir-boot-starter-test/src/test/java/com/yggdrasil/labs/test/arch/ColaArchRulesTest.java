package com.yggdrasil.labs.test.arch;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;

class ColaArchRulesTest {
    @TempDir Path directory;

    @Test
    void threeLayersPassAllRecommendedRules() throws Exception {
        JavaClasses classes =
                classes(
                        Map.of(
                                "sample.adapter.Endpoint",
                                        FixtureCompiler.type(
                                                "sample.adapter.Endpoint",
                                                "sample.app.Service service;"),
                                "sample.app.Service",
                                        FixtureCompiler.type(
                                                "sample.app.Service", "sample.domain.Order order;"),
                                "sample.domain.Order",
                                        FixtureCompiler.type(
                                                "sample.domain.Order",
                                                "java.time.Instant created;")));
        ColaArchRules.layered("sample").check(classes);
        ColaArchRules.domainIsFrameworkFree("sample").check(classes);
        ColaArchRules.recommended("sample").check(classes);
    }

    @ParameterizedTest
    @CsvSource({
        "adapter,app",
        "adapter,client",
        "app,client",
        "app,domain",
        "infrastructure,domain"
    })
    void allowedColaRelationsPass(String source, String target) throws Exception {
        ColaArchRules.layered("sample").check(dependency(source, target));
    }

    @ParameterizedTest
    @CsvSource({
        "domain,app",
        "app,adapter",
        "client,app",
        "domain,client",
        "adapter,infrastructure",
        "domain,infrastructure",
        "infrastructure,app"
    })
    void forbiddenColaRelationsReportBothClasses(String source, String target) throws Exception {
        assertDependency(
                ColaArchRules.layered("sample"),
                dependency(source, target),
                "sample." + source + ".Source",
                "sample." + target + ".Target");
    }

    @Test
    void recommendedReportsDomainToAppViolation() throws Exception {
        assertDependency(
                ColaArchRules.recommended("sample"),
                dependency("domain", "app"),
                "sample.domain.Source",
                "sample.app.Target");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "org.springframework.context",
                "org.apache.ibatis",
                "com.baomidou.mybatisplus",
                "jakarta.servlet",
                "javax.servlet",
                "jakarta.persistence",
                "javax.persistence"
            })
    void frameworkDependencyReportsBothClasses(String frameworkPackage) throws Exception {
        String target = frameworkPackage + ".FrameworkType";
        JavaClasses classes =
                classes(
                        Map.of(
                                "sample.domain.Order",
                                FixtureCompiler.type("sample.domain.Order", target + " framework;"),
                                target,
                                FixtureCompiler.type(target, "")));
        assertDependency(
                ColaArchRules.domainIsFrameworkFree("sample"),
                classes,
                "sample.domain.Order",
                target);
    }

    @Test
    void absentDomainIsOptionalForFrameworkRule() throws Exception {
        ColaArchRules.domainIsFrameworkFree("sample").check(dependency("adapter", "app"));
    }

    @Test
    void singleLayerFailsLayeredAndRecommendedRules() throws Exception {
        JavaClasses classes =
                classes(
                        Map.of(
                                "sample.domain.Order",
                                FixtureCompiler.type("sample.domain.Order", "")));
        for (ArchRule rule :
                new ArchRule[] {
                    ColaArchRules.layered("sample"), ColaArchRules.recommended("sample")
                }) {
            AssertionError failure = assertThrows(AssertionError.class, () -> rule.check(classes));
            assertTrue(failure.getMessage().contains("少于 2"), failure.getMessage());
        }
    }

    @Test
    void everyPresetRejectsInvalidRootPackage() {
        assertParameter(() -> ColaArchRules.layered(null));
        assertParameter(() -> ColaArchRules.domainIsFrameworkFree(" "));
        assertParameter(() -> ColaArchRules.recommended("sample.."));
    }

    private JavaClasses dependency(String source, String target) throws Exception {
        String sourceClass = "sample." + source + ".Source";
        String targetClass = "sample." + target + ".Target";
        return classes(
                Map.of(
                        sourceClass,
                        FixtureCompiler.type(sourceClass, targetClass + " target;"),
                        targetClass,
                        FixtureCompiler.type(targetClass, "")));
    }

    private JavaClasses classes(Map<String, String> sources) throws Exception {
        return new ClassFileImporter().importPath(FixtureCompiler.compile(directory, sources));
    }

    private static void assertDependency(
            ArchRule rule, JavaClasses classes, String source, String target) {
        AssertionError failure = assertThrows(AssertionError.class, () -> rule.check(classes));
        assertTrue(failure.getMessage().contains(source), failure.getMessage());
        assertTrue(failure.getMessage().contains(target), failure.getMessage());
    }

    private static void assertParameter(org.junit.jupiter.api.function.Executable action) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, action);
        assertTrue(failure.getMessage().contains("rootPackage"), failure.getMessage());
    }
}
