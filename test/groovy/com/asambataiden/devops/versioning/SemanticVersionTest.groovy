package com.asambataiden.devops.versioning

import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class SemanticVersionTest {
    @Test void bumpTypes() {
        assertEquals('2.0.0', SemanticVersion.parse('1.2.3').bump('major').toString())
        assertEquals('1.3.0', SemanticVersion.parse('1.2.3').bump('minor').toString())
        assertEquals('1.2.4', SemanticVersion.parse('1.2.3').bump('patch').toString())
    }

    @Test void snapshotAndRelease() {
        def v = SemanticVersion.parse('1.2.3-SNAPSHOT')
        assertEquals('1.2.3', v.asRelease().toString())
        assertEquals('1.2.4-SNAPSHOT', v.bump('patch').asSnapshot().toString())
    }

    @Test void rejectsUnsupportedVersions() {
        for (String value : ['01.2.3', '1.2', '1.2.3-beta', '', '1.2.3;echo pwn']) {
            assertThrows(IllegalArgumentException.class, { SemanticVersion.parse(value) })
        }
        assertThrows(IllegalArgumentException.class, { SemanticVersion.parse('1.2.3').bump('build') })
    }
}
