package com.yggdrasil.labs.test.arch;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.tngtech.archunit.core.domain.JavaClasses;

class MimirArchClassesTest {
    @TempDir Path directory;

    @Test
    void mainImportsProductionClassesAndExcludesTestClasses() {
        JavaClasses classes = MimirArchClasses.importMain("com.yggdrasil.labs.test.arch");
        assertTrue(classes.contain(MimirArchRules.class));
        assertFalse(classes.contain(MimirArchClassesTest.class));
    }

    @Test
    void mainRejectsEmptyOrInvalidPackages() {
        IllegalStateException missing =
                assertThrows(
                        IllegalStateException.class,
                        () -> MimirArchClasses.importMain("no.such.archunit.fixture"));
        assertTrue(missing.getMessage().contains("no.such.archunit.fixture"));
        IllegalArgumentException invalid =
                assertThrows(
                        IllegalArgumentException.class, () -> MimirArchClasses.importMain(null));
        assertTrue(invalid.getMessage().contains("rootPackage"));
        assertThrows(IllegalArgumentException.class, () -> MimirArchClasses.importMain(" "));
    }

    @Test
    void directoryImportsOnlyTheSpecifiedOutput() throws Exception {
        Path classesDirectory =
                FixtureCompiler.compile(
                        directory,
                        Map.of(
                                "sample.domain.Order",
                                FixtureCompiler.type("sample.domain.Order", "")));
        JavaClasses classes = MimirArchClasses.importDirectory(classesDirectory);
        assertTrue(classes.contain("sample.domain.Order"));
        assertFalse(classes.contain(MimirArchRules.class));
    }

    @Test
    void directoryRejectsMissingEmptyAndRegularFilePaths() throws Exception {
        assertThrows(
                IllegalStateException.class,
                () -> MimirArchClasses.importDirectory(directory.resolve("absent")));
        assertThrows(
                IllegalStateException.class, () -> MimirArchClasses.importDirectory(directory));
        Path file = Files.writeString(directory.resolve("not-a-directory"), "");
        assertThrows(IllegalStateException.class, () -> MimirArchClasses.importDirectory(file));
        IllegalArgumentException invalid =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> MimirArchClasses.importDirectory(null));
        assertTrue(invalid.getMessage().contains("classesDirectory"));
    }
}
