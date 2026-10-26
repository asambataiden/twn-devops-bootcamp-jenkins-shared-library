package com.asambataiden.devops.versioning
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*
class MavenVersionTest {
    @Test void bump() {
        assertEquals('2.0.0', MavenVersion.next('1.2.3-SNAPSHOT', 'major'))
        assertEquals('1.3.0-SNAPSHOT', MavenVersion.next('1.2.3', 'minor', true))
        assertEquals('1.2.4', MavenVersion.next('1.2.3', 'patch'))
    }
    @Test void invalid() {
        assertThrows(IllegalArgumentException.class, { MavenVersion.validate('1.2.3;bad') })
        assertThrows(IllegalStateException.class, { MavenVersion.requireExpected('1.2.4', '1.2.3') })
    }
}
