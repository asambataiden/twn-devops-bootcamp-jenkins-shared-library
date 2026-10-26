package com.asambataiden.devops.container

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class DockerRegistryStepsTest {
    static Map execute(String name, Map args, Map config = [:]) {
        List<Map> commands = []
        List<String> scopes = []
        def binding = new Binding([
            error: { String message -> throw new IllegalStateException(message) },
            usernamePassword: { Map m ->
                scopes << 'credential-binding'
                return m
            },
            withCredentials: { List spec, Closure body ->
                scopes << 'withCredentials'
                assertEquals('dockerhub-credentials', spec[0].credentialsId)
                body.call()
            },
            withEnv: { List values, Closure body ->
                scopes << 'withEnv'
                body.call()
            },
            sh: { Object input ->
                commands << (input instanceof Map ? input : [script: input.toString()])
                return config.get('stdout', '')
            }
        ])
        def script = new GroovyShell(DockerRegistryStepsTest.class.classLoader, binding)
            .parse(new File(System.getProperty('user.dir'), 'vars/' + name + '.groovy'))
        def output = script.call(args)
        return [output: output, commands: commands, scopes: scopes]
    }

    @Test void pushUsesScopedCredentialsAndCleanup() {
        def result = execute('dockerPush', [
            repository: 'team/app', tag: 'git-a1b2c3',
            credentialsId: 'dockerhub-credentials'
        ])
        assertEquals('team/app:git-a1b2c3', result.output)
        assertEquals(['credential-binding', 'withCredentials', 'withEnv'], result.scopes)
        assertEquals(1, result.commands.size())
        String script = result.commands[0].script
        assertTrue(script.contains('docker login'))
        assertTrue(script.contains('--password-stdin'))
        assertTrue(script.contains('trap '))
        assertTrue(script.contains('docker push "$IMAGE_REF"'))
        assertFalse(script.contains('git push'))
        assertFalse(script.contains('dockerhub-credentials'))
    }

    @Test void pushRejectsBadInputsBeforeUsingCredentials() {
        assertThrows(IllegalStateException.class, {
            execute('dockerPush', [repository: 'team/app', tag: 'git-a1b2c3', credentialsId: 'bad id'])
        })
        assertThrows(IllegalArgumentException.class, {
            execute('dockerPush', [repository: 'team/app', tag: 'latest', credentialsId: 'dockerhub-credentials'])
        })
        assertThrows(IllegalStateException.class, {
            execute('dockerPush', [repository: 'other.registry.io/team/app', tag: 'git-a1b2c3', credentialsId: 'dockerhub-credentials'])
        })
    }

    @Test void digestExtractsRegistryManifestDigest() {
        String hash = 'a' * 64
        def result = execute('dockerImageDigest', [repository: 'team/app', tag: 'git-abc123'],
            [stdout: 'sha256:' + hash + '\n'])
        assertEquals('team/app@sha256:' + hash, result.output)
        assertTrue(result.commands[0].script.contains('buildx imagetools inspect'))
    }

    @Test void digestRejectsMalformedOutput() {
        assertThrows(IllegalStateException.class, {
            execute('dockerImageDigest', [repository: 'team/app', tag: 'git-abc123'],
                [stdout: 'no matching digest'])
        })
    }
}
