package com.asambataiden.devops.cloud
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class EksDeploymentSpecTest {
    private static final String IMAGE = '123456789012.dkr.ecr.eu-north-1.amazonaws.com/team/app@sha256:' + ('a' * 64)

    @Test void permitsPinnedImage() {
        def spec = new EksDeploymentSpec('arn:aws:eks:eu-north-1:123456789012:cluster/test',
            'staging', 'java-app', 'application', IMAGE)
        assertEquals(IMAGE, spec.image)
    }

    @Test void rejectsFloatingTagAndShellInjection() {
        assertThrows(IllegalArgumentException.class, {
            new EksDeploymentSpec('test', 'staging', 'app', 'app', 'team/app:latest')
        })
        assertThrows(IllegalArgumentException.class, {
            new EksDeploymentSpec('test;echo unsafe', 'staging', 'app', 'app', IMAGE)
        })
        assertThrows(IllegalArgumentException.class, {
            new EksDeploymentSpec('test', 'bad name', 'app', 'app', IMAGE)
        })
    }
}
