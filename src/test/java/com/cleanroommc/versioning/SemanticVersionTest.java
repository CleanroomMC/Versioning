package com.cleanroommc.versioning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticVersionTest {

    @Test
    void parsesAndBumpsNumericVersion() {
        assertEquals("0.1.10", SemanticVersion.parse("0.1.9").nextPatch().toString());
        assertEquals("1.2.1", SemanticVersion.parse("1.2.0").nextPatch().toString());
    }

    @ParameterizedTest
    @CsvSource({"1.4, 1.4.0", "1.4.0, 1.4.0", "v2.0, 2.0.0", "v2.0.0, 2.0.0"})
    void parsesADevelopmentTargetWithAnOptionalPatch(String value, String expected) {
        assertEquals(expected, SemanticVersion.parseTarget(value).toString());
    }

    @Test
    void rejectsADevelopmentTargetCarryingAPatch() {
        var exception = assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parseTarget("1.4.3"));
        assertTrue(exception.getMessage().contains("patch versions are cut on the release branch"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1", "1.2.3.4", "01.2", "1.x", "next"})
    void rejectsMalformedDevelopmentTargets(String value) {
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parseTarget(value));
    }

    @Test
    void comparesComponentsNumerically() {
        assertTrue(SemanticVersion.parse("10.0.0").compareTo(SemanticVersion.parse("2.99.99")) > 0);
        assertTrue(SemanticVersion.parse("1.10.0").compareTo(SemanticVersion.parse("1.2.99")) > 0);
        assertTrue(SemanticVersion.parse("1.2.10").compareTo(SemanticVersion.parse("1.2.9")) > 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1", "1.2", "01.2.3", "1.02.3", "1.2.03", "1.2.3-alpha", " 1.2.3", "V1.2.3", "release-1.2.3"})
    void rejectsNonNumericTags(String value) {
        assertFalse(SemanticVersion.isValid(value));
        assertThrows(IllegalArgumentException.class, () -> SemanticVersion.parse(value));
    }

    // The prefix is accepted on the tag and dropped from the version.
    @Test
    void acceptsAnOptionalVPrefix() {
        assertTrue(SemanticVersion.isValid("v1.2.3"));
        assertEquals("1.2.3", SemanticVersion.parse("v1.2.3").toString());
        assertEquals(SemanticVersion.parse("1.2.3"), SemanticVersion.parse("v1.2.3"));
        assertTrue(SemanticVersion.parse("2.0.0").compareTo(SemanticVersion.parse("v1.2.3")) > 0);
    }

}
