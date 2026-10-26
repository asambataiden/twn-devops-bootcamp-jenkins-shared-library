package com.asambataiden.devops.cloud

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class EcrBuildStepTest {
    private static Map input() {
        [
            accountId: '123456789012',
            region: 'eu-north-1',
            repository: 'team/app',
            tag: 'git-abcdef'
        ]
    }

    private static Map execute(Map args, boolean dockerfilePresent = true) {
        List<String> environment = []
        List<String> commands = []
        def binding = new Binding([
            fileExists: { String path -> dockerfilePresent && path == 'Dockerfile' },
            error: { String message -> throw new IllegalStateException(message) },
            withEnv: { List<String> values, Closure body ->
                environment.addAll(values.collect { it.toString() })
                body.call()
            },
            sh: { Map command -> commands.add(command.script) }
        ])
        def step = new GroovyShell(EcrBuildStepTest.class.classLoader, binding)
            .parse(new File(System.getProperty('user.dir'), 'vars/ecrBuild.groovy'))
        return [image: step.call(args), environment: environment, commands: commands]
    }

    @Test void buildsExactEcrDestinationReference() {
        def run = execute(input())
        String fullImage = '123456789012.dkr.ecr.eu-north-1.amazonaws.com/team/app:git-abcdef'

        assertEquals(fullImage, run.image)
        assertTrue(run.environment.contains('ECR_IMAGE=' + fullImage))
        assertEquals(1, run.commands.size())
        assertTrue(run.commands[0].contains('docker build --pull --file Dockerfile --tag "$ECR_IMAGE" .'))
        assertTrue(run.commands[0].contains('docker image inspect "$ECR_IMAGE"'))
        assertFalse(run.commands[0].contains('docker push'))
    }

    @Test void missingDockerfileFailsWithoutShellInvocation() {
        assertThrows(IllegalStateException.class, { execute(input(), false) })
    }

    @Test void rejectsFloatingTagBeforeShellInvocation() {
        def invalid = input()
        invalid.tag = 'latest'
        assertThrows(IllegalArgumentException.class, { execute(invalid) })
    }
}
