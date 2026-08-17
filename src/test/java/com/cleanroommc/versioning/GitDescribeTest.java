package com.cleanroommc.versioning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitDescribeTest {

    @Test
    void parsesLongDescribe() {
        var info = GitDescribe.parse("0.6.10-alpha-48-gf5e9227e");
        assertEquals("0.6.10-alpha", info.tag());
        assertEquals(48, info.distance());
        assertFalse(info.isMissing());
    }

    @Test
    void parsesOnTag() {
        var info = GitDescribe.parse("0.6.10-alpha-0-gabcdef0");
        assertEquals("0.6.10-alpha", info.tag());
        assertEquals(0, info.distance());
    }

    @Test
    void parsesNumericTag() {
        var info = GitDescribe.parse("0.6.10-3-gabcdef0");
        assertEquals("0.6.10", info.tag());
        assertEquals(3, info.distance());
    }

    @Test
    void hashOnlyIsMissing() {
        var info = GitDescribe.parse("f5e9227e");
        assertEquals(GitDescribe.missing(), info);
        assertTrue(info.isMissing());
        assertEquals("", info.tag());
        assertEquals(0, info.distance());
    }

    @Test
    void blankIsMissing() {
        assertEquals(GitDescribe.missing(), GitDescribe.parse(""));
        assertEquals(GitDescribe.missing(), GitDescribe.parse("   "));
        assertEquals(GitDescribe.missing(), GitDescribe.parse(null));
    }

    @Test
    void trimsWhitespace() {
        var info = GitDescribe.parse("  0.6.10-alpha-1-gabc1234\n");
        assertEquals("0.6.10-alpha", info.tag());
        assertEquals(1, info.distance());
    }
}
