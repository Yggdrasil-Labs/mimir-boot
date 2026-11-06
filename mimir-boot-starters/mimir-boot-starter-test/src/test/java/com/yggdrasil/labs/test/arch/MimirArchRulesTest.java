package com.yggdrasil.labs.test.arch;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

class MimirArchRulesTest {
    @TempDir Path directory;

    @Test
    void permittedDependenciesIncludeOwnPackageAndExternalLibraries() throws Exception {
        JavaClasses classes =
                classes(
                        Map.of(
                                "sample.own.Order",
                                        FixtureCompiler.type(
                                                "sample.own.Order",
                                                "sample.common.Shared shared; java.time.Instant"
                                                    + " created; sample.own.internal.State state;"),
                                "sample.common.Shared",
                                        FixtureCompiler.type("sample.common.Shared", ""),
                                "sample.own.internal.State",
                                        FixtureCompiler.type("sample.own.internal.State", "")));
        MimirArchRules.onlyDependOnWithin("sample", "sample.own", "sample.common").check(classes);
        MimirArchRules.noDependencyOn("sample.own", "sample.other").check(classes);
    }

    @Test
    void namespacePrefixDoesNotIncludeSiblingNamespace() throws Exception {
        JavaClasses classes =
                classes(
                        Map.of(
                                "sample.own.Order",
                                        FixtureCompiler.type(
                                                "sample.own.Order", "samplex.other.Shared shared;"),
                                "samplex.other.Shared",
                                        FixtureCompiler.type("samplex.other.Shared", "")));
        MimirArchRules.onlyDependOnWithin("sample", "sample.own").check(classes);
    }

    @Test
    void unlistedNamespaceDependencyReportsBothClasses() throws Exception {
        JavaClasses classes =
                classes(
                        Map.of(
                                "sample.own.Order",
                                        FixtureCompiler.type(
                                                "sample.own.Order", "sample.other.Shared shared;"),
                                "sample.other.Shared",
                                        FixtureCompiler.type("sample.other.Shared", "")));
        assertDependency(
                MimirArchRules.onlyDependOnWithin("sample", "sample.own"),
                classes,
                "sample.own.Order",
                "sample.other.Shared");
    }

    @Test
    void namespaceRuleRejectsInvalidPackages() {
        assertParameter("namespace", () -> MimirArchRules.onlyDependOnWithin(null, "sample.own"));
        assertParameter("ownPackage", () -> MimirArchRules.onlyDependOnWithin("sample", " "));
        assertParameter(
                "allowedPackages",
                () -> MimirArchRules.onlyDependOnWithin("sample", "sample.own", (String[]) null));
        assertParameter(
                "allowedPackages",
                () -> MimirArchRules.onlyDependOnWithin("sample", "sample.own", ""));
        assertParameter("forbiddenPackages", () -> MimirArchRules.noDependencyOn("sample.own"));
        assertParameter(
                "forbiddenPackages",
                () -> MimirArchRules.noDependencyOn("sample.own", (String[]) null));
        assertParameter(
                "forbiddenPackages",
                () -> MimirArchRules.noDependencyOn("sample.own", "sample..other"));
    }

    @Test
    void customLayersAllowDownwardDependenciesAndMissingLayers() throws Exception {
        JavaClasses classes =
                classes(
                        Map.of(
                                "sample.controller.Controller",
                                        FixtureCompiler.type(
                                                "sample.controller.Controller",
                                                "sample.repository.Repository repository;"),
                                "sample.repository.Repository",
                                        FixtureCompiler.type("sample.repository.Repository", "")));
        MimirArchRules.layered(
                        LayerSpec.of("sample")
                                .layer("controller")
                                .layer("service")
                                .layer("repository"))
                .check(classes);
    }

    @Test
    void customLayerSupportsSeveralCompletePackageNames() throws Exception {
        JavaClasses classes =
                classes(
                        Map.of(
                                "external.ui.Controller",
                                        FixtureCompiler.type(
                                                "external.ui.Controller",
                                                "external.data.Repository repository;"),
                                "external.data.Repository",
                                        FixtureCompiler.type("external.data.Repository", "")));
        MimirArchRules.layered(
                        LayerSpec.of("sample")
                                .layer("ui", "external.ui", "external.api")
                                .layer("data", "external.data"))
                .check(classes);
    }

    @Test
    void customLayersReportUpwardDependency() throws Exception {
        JavaClasses classes =
                classes(
                        Map.of(
                                "sample.controller.Controller",
                                        FixtureCompiler.type("sample.controller.Controller", ""),
                                "sample.repository.Repository",
                                        FixtureCompiler.type(
                                                "sample.repository.Repository",
                                                "sample.controller.Controller controller;")));
        assertDependency(
                MimirArchRules.layered(
                        LayerSpec.of("sample").layer("controller").layer("repository")),
                classes,
                "sample.repository.Repository",
                "sample.controller.Controller");
    }

    @Test
    void customLayersRejectSinglePopulatedLayerAndEmptyImports() throws Exception {
        com.tngtech.archunit.lang.ArchRule rule =
                MimirArchRules.layered(
                        LayerSpec.of("sample").layer("controller").layer("repository"));
        AssertionError failure =
                assertThrows(
                        AssertionError.class,
                        () ->
                                rule.check(
                                        classes(
                                                Map.of(
                                                        "sample.controller.Controller",
                                                        FixtureCompiler.type(
                                                                "sample.controller.Controller",
                                                                "")))));
        assertTrue(failure.getMessage().contains("少于 2"), failure.getMessage());
        assertThrows(
                AssertionError.class, () -> rule.check(new ClassFileImporter().importClasses()));
    }

    @Test
    void layerDeclarationsRejectInvalidArguments() {
        assertParameter("spec", () -> MimirArchRules.layered(null));
        assertParameter("spec", () -> MimirArchRules.layered(LayerSpec.of("sample").layer("only")));
        assertParameter("rootPackage", () -> LayerSpec.of(" "));
        assertParameter("name", () -> LayerSpec.of("sample").layer(""));
        assertParameter(
                "name", () -> LayerSpec.of("sample").layer("one").layer("one", "sample.other"));
        assertParameter("packageNames", () -> LayerSpec.of("sample").layer("one", ""));
        assertParameter("packageNames", () -> LayerSpec.of("sample").layer("one", new String[0]));
        assertParameter("packageNames", () -> LayerSpec.of("sample").layer("one", (String[]) null));
    }

    private static void assertParameter(
            String name, org.junit.jupiter.api.function.Executable action) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, action);
        assertTrue(failure.getMessage().contains(name), failure.getMessage());
    }

    @Test
    void cyclesAcrossDirectSubpackagesReportBothClasses() throws Exception {
        JavaClasses classes =
                classes(
                        Map.of(
                                "sample.first.One",
                                        FixtureCompiler.type(
                                                "sample.first.One", "sample.second.Two two;"),
                                "sample.second.Two",
                                        FixtureCompiler.type(
                                                "sample.second.Two", "sample.first.One one;")));
        assertDependency(
                MimirArchRules.noCycles("sample"),
                classes,
                "sample.first.One",
                "sample.second.Two");
    }

    @Test
    void cyclesInsideOneDirectSubpackageAreAllowed() throws Exception {
        JavaClasses classes =
                classes(
                        Map.of(
                                "sample.first.inner.One",
                                        FixtureCompiler.type(
                                                "sample.first.inner.One",
                                                "sample.first.other.Two two;"),
                                "sample.first.other.Two",
                                        FixtureCompiler.type(
                                                "sample.first.other.Two",
                                                "sample.first.inner.One one;")));
        MimirArchRules.noCycles("sample").check(classes);
    }

    @Test
    void cyclesRejectBlankRootPackage() {
        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> MimirArchRules.noCycles(" "));
        assertTrue(failure.getMessage().contains("rootPackage"));
    }

    private JavaClasses classes(Map<String, String> sources) throws Exception {
        return new ClassFileImporter().importPath(FixtureCompiler.compile(directory, sources));
    }

    private static void assertDependency(
            com.tngtech.archunit.lang.ArchRule rule,
            JavaClasses classes,
            String source,
            String target) {
        AssertionError failure = assertThrows(AssertionError.class, () -> rule.check(classes));
        assertTrue(failure.getMessage().contains(source), failure.getMessage());
        assertTrue(failure.getMessage().contains(target), failure.getMessage());
    }

    @Test
    void forbiddenDependencyRejectsMissingSourcePackage() {
        IllegalArgumentException failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> MimirArchRules.noDependencyOn(null, "sample.app"));
        assertTrue(failure.getMessage().contains("sourcePackage"));
    }

    @Test
    void forbiddenDependencyReportsBothClasses() throws Exception {
        JavaClasses classes =
                new ClassFileImporter()
                        .importPath(
                                FixtureCompiler.compile(
                                        directory,
                                        Map.of(
                                                "sample.domain.Order",
                                                FixtureCompiler.type(
                                                        "sample.domain.Order",
                                                        "sample.app.Service service;"),
                                                "sample.app.Service",
                                                FixtureCompiler.type("sample.app.Service", ""))));
        AssertionError failure =
                assertThrows(
                        AssertionError.class,
                        () ->
                                MimirArchRules.noDependencyOn("sample.domain", "sample.app")
                                        .check(classes));
        assertTrue(failure.getMessage().contains("sample.domain.Order"));
        assertTrue(failure.getMessage().contains("sample.app.Service"));
    }
}
