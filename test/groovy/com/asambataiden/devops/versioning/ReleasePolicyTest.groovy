package com.asambataiden.devops.versioning
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*
class ReleasePolicyTest {
    @Test void allowed() {
        assertEquals('main', ReleasePolicy.releaseBranch('main'))
        assertEquals('v1.2.3', ReleasePolicy.tag('1.2.3'))
    }
    @Test void blocked() {
        assertThrows(IllegalArgumentException.class, { ReleasePolicy.tag('1.2.3-SNAPSHOT') })
        assertThrows(IllegalArgumentException.class, { ReleasePolicy.releaseBranch('feat/test') })
    }
}
