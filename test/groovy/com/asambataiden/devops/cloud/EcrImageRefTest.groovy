package com.asambataiden.devops.cloud

import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class EcrImageRefTest {
    @Test void createsValidatedImageAndDigestRef() {
        def image = new EcrImageRef('123456789012', 'eu-north-1', 'team/app', 'git-abcdef')
        assertEquals('123456789012.dkr.ecr.eu-north-1.amazonaws.com/team/app:git-abcdef', image.image())
        assertEquals('123456789012.dkr.ecr.eu-north-1.amazonaws.com/team/app@sha256:' + ('a' * 64),
            image.digestRef('sha256:' + ('a' * 64)))
    }

    @Test void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, {
            new EcrImageRef('123', 'eu-north-1', 'app', 'git-abcdef')
        })
        assertThrows(IllegalArgumentException.class, {
            new EcrImageRef('123456789012', 'bad;region', 'app', 'git-abcdef')
        })
        assertThrows(IllegalArgumentException.class, {
            new EcrImageRef('123456789012', 'eu-north-1', 'bad repository', 'git-abcdef')
        })
        assertThrows(IllegalArgumentException.class, {
            new EcrImageRef('123456789012', 'eu-north-1', 'app', 'latest')
        })
        assertThrows(IllegalArgumentException.class, {
            new EcrImageRef('123456789012', 'eu-north-1', 'app', 'git-abcdef').digestRef('not-a-digest')
        })
    }
}
