package com.yggdrasil.labs.test.arch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ArchPackagesTest {
    @Test
    void requirePackageAcceptsLongMultiSegmentPackageNames() {
        String packageName = "a.".repeat(20_000) + "a";

        assertEquals(packageName, ArchPackages.requirePackage(packageName, "packageName"));
    }

    @Test
    void requirePackagePreservesJavaIdentifierCompatibility() {
        String packageName = "π.$nested._.class.\uD801\uDC00";

        assertEquals(packageName, ArchPackages.requirePackage(packageName, "packageName"));
    }

    @Test
    void requirePackageRejectsInvalidSegmentsAndTrailingDot() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ArchPackages.requirePackage("valid.1invalid", "packageName"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ArchPackages.requirePackage("valid.", "packageName"));
    }
}
