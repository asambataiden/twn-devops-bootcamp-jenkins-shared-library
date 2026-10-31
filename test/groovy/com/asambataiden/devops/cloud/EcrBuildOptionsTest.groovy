package com.asambataiden.devops.cloud

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class EcrBuildOptionsTest {

    private static Map baseArgs() {
        [
            accountId : '123456789012',
            region    : 'eu-north-1',
            repository: 'team/app',
            tag       : 'git-abcdef'
        ]
    }

    private static Map execute(Map args) {
        List<Map> commands = []
        List<String> environment = []

        def binding = new Binding([
            error: { String message ->
                throw new IllegalStateException(message)
            },
            fileExists: { String path ->
                path in ['Dockerfile', '.', 'app/Dockerfile', 'app']
            },
            withEnv: { List<String> values, Closure body ->
                environment.addAll(values.collect { it.toString() })
                body.call()
            },
            sh: { Map command ->
                commands.add(command)
            }
        ])

        def script = new GroovyShell(
            EcrBuildOptionsTest.class.classLoader,
            binding
        ).parse(new File(
            System.getProperty('user.dir'),
            'vars/ecrBuild.groovy'
        ))

        [
            result     : script.call(args),
            commands   : commands,
            environment: environment
        ]
    }

    @Test
    void supportsCapstone03BuildxConfiguration() {
        Map args = baseArgs() + [
            dockerfile: 'app/Dockerfile',
            context   : 'app',
            platform  : 'linux/amd64'
        ]

        def run = execute(args)
        String command = run.commands[0].script

        assertEquals(
            '123456789012.dkr.ecr.eu-north-1.amazonaws.com/team/app:git-abcdef',
            run.result
        )

        assertTrue(run.environment.contains('BUILD_DOCKERFILE=app/Dockerfile'))
        assertTrue(run.environment.contains('BUILD_CONTEXT=app'))
        assertTrue(run.environment.contains('BUILD_PLATFORM=linux/amd64'))

        assertTrue(command.contains('docker buildx build'))
        assertTrue(command.contains('--platform "$BUILD_PLATFORM"'))
        assertTrue(command.contains('--load'))
        assertTrue(command.contains('actual_platform='))
        assertFalse(command.contains('docker push'))
    }

    @Test
    void preservesLegacyBuildBehavior() {
        def run = execute(baseArgs())

        assertTrue(run.environment.contains('BUILD_PLATFORM='))
        assertTrue(run.commands[0].script.contains('docker build'))
        assertTrue(run.commands[0].script.contains('docker image inspect'))
    }

    @Test
    void rejectsUnsafePaths() {
        [
            [dockerfile: '../Dockerfile'],
            [dockerfile: '/tmp/Dockerfile'],
            [dockerfile: 'app/../Dockerfile'],
            [context: '../other'],
            [context: 'app;touch /tmp/unsafe']
        ].each { invalid ->
            assertThrows(IllegalStateException.class, {
                execute(baseArgs() + invalid)
            })
        }
    }

    @Test
    void rejectsUnsupportedPlatform() {
        assertThrows(IllegalStateException.class, {
            execute(baseArgs() + [platform: 'linux/windows'])
        })
    }
}
