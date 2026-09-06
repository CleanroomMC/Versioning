package com.cleanroommc.versioning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VersioningTest {

    @Test
    void exactTagIsTheTagItself() {
        assertEquals("1.1.1", Versioning.compute(onTag("1.1.1", false, true), "dev", Map.of()));
    }

    // The patch only ever moves once off a tag, no matter how many commits follow.
    @Test
    void offTagLeadsToTheNextPatch() {
        assertEquals("1.1.2-dev.3", compute("1.1.1", null, 3));
        assertEquals("1.1.2-dev.9", compute("1.1.1", null, 9));
    }

    @Test
    void developmentTargetPinsTheNumber() {
        assertEquals("1.4.0-dev.7", compute("1.1.1", "1.4.0", 7));
        assertEquals("2.0.0-dev.20", compute("1.1.1", "2.0.0", 20));
    }

    @Test
    void noTagCountsFromTheInitialBaseline() {
        assertEquals("0.0.1-dev.7", Versioning.compute(new GitState(null, null, 7, false, false, true), "dev", Map.of()));
    }

    // The back-merge case: once the target has been tagged the branch would compute below its own release.
    @ParameterizedTest
    @CsvSource({"1.4.0, 1.4.0", "1.5.0, 1.4.0"})
    void targetAtOrBelowTheTagIsRejected(String tag, String target) {
        var exception = assertThrows(IllegalArgumentException.class, () -> compute(tag, target, 3));
        assertEquals("Branch leads to " + target + ", which tag " + tag
                + " has already reached; retarget or delete the branch", exception.getMessage());
    }

    @Test
    void metadataFollowsTheLabelInIterationOrder() {
        var metadata = new LinkedHashMap<String, String>();
        metadata.put("run", "24");
        metadata.put("commit", "a3f9c2");
        assertEquals("1.1.2-dev.1.run.24.commit.a3f9c2",
                Versioning.compute(state("1.1.1", null, 1, false, true), "dev", metadata));
    }

    @Test
    void exactTagDropsTheLabelAndMetadata() {
        assertEquals("1.1.1", Versioning.compute(onTag("1.1.1", false, true), "dev", Map.of("run", "24")));
    }

    @Test
    void labelIsConfigurable() {
        assertEquals("1.1.2-nightly.3", Versioning.compute(state("1.1.1", null, 3, false, true), "nightly", Map.of()));
    }

    @Test
    void unpushedCommitIsMarkedLocal() {
        assertEquals("1.1.2-dev.3.local", Versioning.compute(state("1.1.1", null, 3, false, false), "dev", Map.of()));
    }

    @Test
    void dirtyWorktreeIsMarkedLocalAndDirtyAfterTheMetadata() {
        assertEquals("1.1.2-dev.3.run.24.local.dirty",
                Versioning.compute(state("1.1.1", null, 3, true, true), "dev", Map.of("run", "24")));
    }

    // A bare or single-branch checkout of a released tag has no remote-tracking ref containing HEAD.
    @ParameterizedTest
    @CsvSource({"false, false, 1.1.1", "false, true, 1.1.1", "true, false, 1.1.1-local.dirty", "true, true, 1.1.1-local.dirty"})
    void exactTagIsNeverLocalUnlessDirty(boolean dirty, boolean pushed, String expected) {
        assertEquals(expected, Versioning.compute(onTag("1.1.1", dirty, pushed), "dev", Map.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "dev build", "dev.1", "01"})
    void invalidLabelIsRejected(String label) {
        assertThrows(IllegalArgumentException.class,
                () -> Versioning.compute(state("1.1.1", null, 3, false, true), label, Map.of()));
    }

    @Test
    void numericLabelIsRejected() {
        var exception = assertThrows(IllegalArgumentException.class,
                () -> Versioning.compute(state("1.1.1", null, 3, false, true), "1", Map.of()));
        assertEquals("versioning.label must not be numeric (got '1')", exception.getMessage());
    }

    @ParameterizedTest
    @CsvSource({"run number, 24", "run, 0024", "run, a3f9 c2"})
    void invalidMetadataIsRejected(String key, String value) {
        assertThrows(IllegalArgumentException.class,
                () -> Versioning.compute(state("1.1.1", null, 3, false, true), "dev", Map.of(key, value)));
    }

    private static String compute(String tag, String target, long commits) {
        return Versioning.compute(state(tag, target, commits, false, true), "dev", Map.of());
    }

    private static GitState state(String tag, String target, long commits, boolean dirty, boolean pushed) {
        return new GitState(SemanticVersion.parse(tag), target == null ? null : SemanticVersion.parse(target),
                commits, false, dirty, pushed);
    }

    // A non-zero counter, because a tag build has to ignore it rather than happen to render zero.
    private static GitState onTag(String tag, boolean dirty, boolean pushed) {
        return new GitState(SemanticVersion.parse(tag), null, 12, true, dirty, pushed);
    }

}
