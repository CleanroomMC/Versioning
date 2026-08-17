package com.cleanroommc.versioning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersioningTest {

    @Test
    void localAlphaAppendsDistance() {
        var computed = Versioning.compute("0.6.10", "alpha", "0.6.10-alpha-48-gf5e9227e", false, null);
        assertEquals("0.6.10-alpha+local.48", computed.version());
        assertEquals("0.6.10-alpha", computed.baseVersion());
        assertEquals(Stage.ALPHA, computed.stage());
        assertEquals(48, computed.git().distance());
        assertEquals("0.6.10-alpha", computed.git().tag());
        assertFalse(computed.publish());
        assertEquals("0.6.10-alpha+local.48", computed.toString());
    }

    @Test
    void localReleaseStageHasNoPrereleaseSuffix() {
        var computed = Versioning.compute("0.6.10", "release", "0.6.10-3-gabcdef0", false, null);
        assertEquals("0.6.10+local.3", computed.version());
        assertEquals("0.6.10", computed.baseVersion());
        assertEquals(Stage.RELEASE, computed.stage());
    }

    @Test
    void ciUsesRunNumber() {
        var computed = Versioning.compute("0.6.10", "alpha", "0.6.10-alpha-48-gf5e9227e", false, "123");
        assertEquals("0.6.10-alpha+build.48.run.123", computed.version());
        assertEquals("0.6.10-alpha", computed.baseVersion());
        assertFalse(computed.publish());
    }

    @Test
    void releaseOnMatchingTag() {
        var computed = Versioning.compute("0.6.10", "alpha", "0.6.10-alpha-0-gf5e9227e", true, null);
        assertEquals("0.6.10-alpha", computed.version());
        assertEquals("0.6.10-alpha", computed.baseVersion());
        assertTrue(computed.publish());
    }

    @Test
    void releaseIgnoresRunNumber() {
        var computed = Versioning.compute("0.6.10", "alpha", "0.6.10-alpha-0-gf5e9227e", true, "99");
        assertEquals("0.6.10-alpha", computed.version());
        assertTrue(computed.publish());
    }

    @Test
    void releaseRejectsTagMismatch() {
        var ex = assertThrows(VersioningException.class, () -> Versioning.compute("0.6.10", "alpha", "0.6.9-alpha-0-gf5e9227e", true, null));
        assertEquals("Git tag '0.6.9-alpha' does not match gradle.properties version '0.6.10-alpha'", ex.getMessage());
    }

    @Test
    void releaseRejectsDistance() {
        var ex = assertThrows(VersioningException.class, () -> Versioning.compute("0.6.10", "alpha", "0.6.10-alpha-48-gf5e9227e", true, null));
        assertEquals("Release must be on a tagged commit (48 commits ahead of '0.6.10-alpha')", ex.getMessage());
    }

    @Test
    void releaseRejectsUnparseableDescribe() {
        var ex = assertThrows(VersioningException.class, () -> Versioning.compute("0.6.10", "alpha", "f5e9227e", true, null));
        assertEquals("Git tag '' does not match gradle.properties version '0.6.10-alpha'", ex.getMessage());
    }

    @ParameterizedTest
    @CsvSource({
            "alpha, 0.6.10-alpha+local.0",
            "beta, 0.6.10-beta+local.0",
            "rc, 0.6.10-rc+local.0",
            "release, 0.6.10+local.0",
    })
    void stages(String stage, String expected) {
        assertEquals(expected, Versioning.compute("0.6.10", stage, "0.6.10-alpha-0-gabc", false, null).version());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "nightly", "snapshot", "final"})
    void rejectsInvalidStage(String stage) {
        var ex = assertThrows(VersioningException.class, () ->
                Versioning.compute("0.6.10", stage, "0.6.10-0-gabc", false, null));
        assertTrue(ex.getMessage().contains("versioning.stage must be one of"), ex.getMessage());
        assertTrue(ex.getMessage().contains(stage), ex.getMessage());
    }

    @Test
    void rejectsMissingVersion() {
        assertThrows(VersioningException.class, () ->
                Versioning.compute("unspecified", "alpha", "0.6.10-alpha-0-gabc", false, null));
        assertThrows(VersioningException.class, () ->
                Versioning.compute("  ", "alpha", "0.6.10-alpha-0-gabc", false, null));
    }

    @Test
    void requestBaseVersion() {
        var request = new VersionRequest("0.6.10", Stage.ALPHA, GitDescribe.missing(), false, null);
        assertEquals("0.6.10-alpha", request.baseVersion());
        assertEquals("0.6.10", new VersionRequest("0.6.10", Stage.RELEASE, GitDescribe.missing(), false, null).baseVersion());
    }
}
