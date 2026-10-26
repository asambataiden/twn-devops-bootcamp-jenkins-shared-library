package com.asambataiden.devops.container

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class DockerPipelineStepsTest {
    private static Map step(String name, Map args, Map config = [:]) {
        List<String> commands = []
        def binding = new Binding([
            error: { String text -> throw new IllegalStateException(text) },
            fileExists: { String path -> config.get('dockerfile', true) && path == 'Dockerfile' },
            sh: { Object input ->
                String command = input instanceof Map ? input.script : input.toString()
                commands.add(command)
                return input instanceof Map && input.returnStdout ? config.get('inspection', '') : null
            }
        ])
        def script = new GroovyShell(DockerPipelineStepsTest.class.classLoader, binding)
            .parse(new File(System.getProperty('user.dir'), 'vars/' + name + '.groovy'))
        return [result: script.call(args), commands: commands]
    }

    @Test void buildOnlyInvokesDockerBuild() {
        def result = step('dockerBuild', [repository: 'team/app', tag: 'git-abc123'])
        assertEquals('team/app:git-abc123', result.result)
        assertEquals(['docker build --pull --tag team/app:git-abc123 --file Dockerfile .'], result.commands)
    }

    @Test void buildRejectsMissingDockerfile() {
        assertThrows(IllegalStateException.class, {
            step('dockerBuild', [repository: 'team/app', tag: '1.2.3'], [dockerfile: false])
        })
    }

    @Test void inspectReturnsLocalImageId() {
        String id = 'sha256:' + ('a' * 64)
        def result = step('dockerInspect', [repository: 'team/app', tag: '1.2.3'], [inspection: id + '\n'])
        assertEquals(id, result.result)
        assertTrue(result.commands[0].startsWith('docker image inspect '))
    }

    @Test void inspectRejectsBadOutput() {
        assertThrows(IllegalStateException.class, {
            step('dockerInspect', [repository: 'team/app', tag: '1.2.3'], [inspection: 'not-a-digest'])
        })
    }
}
