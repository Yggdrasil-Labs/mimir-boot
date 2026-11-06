package com.yggdrasil.labs.test.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tngtech.archunit.ArchConfiguration;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.freeze.FreezingArchRule;

class FreezingArchRulesTest {
    @TempDir Path directory;

    @Test
    void storedBaselineReportsOnlyTheNewDependency() throws Exception {
        JavaClasses before =
                MimirArchClasses.importDirectory(
                        FixtureCompiler.compile(
                                directory.resolve("before"),
                                sources("sample.app.Existing existing;")));
        JavaClasses after =
                MimirArchClasses.importDirectory(
                        FixtureCompiler.compile(
                                directory.resolve("after"),
                                sources("sample.app.Existing existing; sample.app.Added added;")));
        ArchConfiguration.withThreadLocalScope(
                configuration -> {
                    configuration.setProperty(
                            "freeze.store.default.path", directory.resolve("store").toString());
                    configuration.setProperty("freeze.store.default.allowStoreCreation", "true");
                    ArchRule rule = MimirArchRules.noDependencyOn("sample.domain", "sample.app");
                    FreezingArchRule.freeze(rule).check(before);
                    configuration.setProperty("freeze.store.default.allowStoreCreation", "false");
                    List<String> violations =
                            FreezingArchRule.freeze(rule)
                                    .evaluate(after)
                                    .getFailureReport()
                                    .getDetails();
                    assertEquals(1, violations.size(), violations.toString());
                    String violation = violations.get(0);
                    assertTrue(violation.contains("sample.domain.Order"), violation);
                    assertTrue(violation.contains("sample.app.Added"), violation);
                    assertFalse(violation.contains("sample.app.Existing"), violation);
                });
    }

    private static Map<String, String> sources(String body) {
        return Map.of(
                "sample.domain.Order",
                FixtureCompiler.type("sample.domain.Order", body),
                "sample.app.Existing",
                FixtureCompiler.type("sample.app.Existing", ""),
                "sample.app.Added",
                FixtureCompiler.type("sample.app.Added", ""));
    }
}
