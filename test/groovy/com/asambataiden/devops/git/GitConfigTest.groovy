package com.asambataiden.devops.git
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class GitConfigTest {
    @Test void validInputs() {
        assertEquals('main', GitConfig.requireReleaseBranch('main'))
        assertEquals(['pom.xml'], GitConfig.requireVersionFiles(['pom.xml']))
        assertEquals('Release Bot', GitConfig.requireIdentity('Release Bot'))
    }
    @Test void invalidInputs() {
        ['feat/foo', 'main;echo unsafe', '../main', 'foo.lock'].each { branch ->
            assertThrows(IllegalArgumentException.class, { GitConfig.requireReleaseBranch(branch) })
        }
        [['pom.xml', 'README.md'], ['pom.xml','pom.xml'], []].each { paths ->
            assertThrows(IllegalArgumentException.class, { GitConfig.requireVersionFiles(paths) })
        }
        assertThrows(IllegalArgumentException.class, { GitConfig.requireIdentity('bad\nname') })
    }
}
