package com.asambataiden.devops.container
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class DockerImageRefTest {
    @Test void validReferences() {
        assertEquals('team/app:1.2.3', new DockerImageRef('team/app', '1.2.3').toString())
        assertEquals('registry.example.com/team/app:git-a1234', new DockerImageRef('registry.example.com/team/app', 'git-a1234').toString())
    }

    @Test void invalidReferences() {
        ['latest','stable','main','bad tag','1.2.3;echo'].each { tag ->
            assertThrows(IllegalArgumentException.class, { new DockerImageRef('team/app', tag) })
        }
        ['UPPER/app', 'team/app;ls', 'team/app with spaces', '../app'].each { name ->
            assertThrows(IllegalArgumentException.class, { new DockerImageRef(name, '1.2.3') })
        }
    }
}
