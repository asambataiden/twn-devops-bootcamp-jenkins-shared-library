package com.asambataiden.devops.cloud

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Contract tests for the ECR preflight script. Does not access AWS or Docker.
 * Actual CLI failure behavior must be tested in a controlled integration environment.
 */
class EcrPreflightTest {
    private static Map invokePush(Map inputs) {
        List<String> scripts = []
        List<String> variables = []
        def binding = new Binding([
            error: { String message -> throw new IllegalStateException(message) },
            withEnv: { List<String> env, Closure body ->
                variables.addAll(env)
                body.call()
            },
            sh: { Map cmd -> scripts.add(cmd.script) }
        ])
        def step = new GroovyShell(EcrPreflightTest.class.classLoader, binding)
            .parse(new File(System.getProperty('user.dir'), 'vars/ecrPush.groovy'))
        return [result: step.call(inputs), script: scripts.single(), env: variables]
    }

    private static Map valid() {
        [
            accountId: '123456789012',
            region: 'eu-north-1',
            repository: 'team/app',
            tag: 'git-abcdef'
        ]
    }

    @Test void requiresCorrectAccountAndImmutableRepository() {
        def run = invokePush(valid())
        assertTrue(run.env.contains('EXPECTED_AWS_ACCOUNT=123456789012'))
        assertTrue(run.script.contains('aws sts get-caller-identity'))
        assertTrue(run.script.contains('actual_account" != "$EXPECTED_AWS_ACCOUNT"'))
        assertTrue(run.script.contains('aws ecr describe-repositories'))
        assertTrue(run.script.contains('"$mutability" != "IMMUTABLE"'))
        assertTrue(run.script.contains('set -eu'))
    }

    @Test void checksLocalImageAndRejectsExistingTagBeforeLogin() {
        def run = invokePush(valid())
        String script = run.script
        assertTrue(script.contains('docker image inspect "$ECR_IMAGE"'))
        assertTrue(script.contains('aws ecr list-images'))
        assertTrue(script.contains('existing_tags'))
        assertTrue(script.indexOf('docker image inspect') < script.indexOf('aws ecr get-login-password'))
        assertTrue(script.indexOf('aws ecr list-images') < script.indexOf('aws ecr get-login-password'))
        assertTrue(script.contains('--password-stdin'))
        assertTrue(script.contains('trap '))
        assertFalse(script.contains('git push'))
    }

    @Test void rejectsUnsafeReferencesBeforeShell() {
        def binding = new Binding([
            error: { String message -> throw new IllegalStateException(message) },
            withEnv: { List<String> env, Closure body ->
                fail('Credentials or AWS must not be accessed for malformed input')
            },
            sh: { Map cmd -> fail('No shell invocation expected') }
        ])
        def step = new GroovyShell(EcrPreflightTest.class.classLoader, binding)
            .parse(new File(System.getProperty('user.dir'), 'vars/ecrPush.groovy'))
        def malformed = valid()
        malformed.tag = 'latest'
        assertThrows(IllegalArgumentException.class, { step.call(malformed) })
    }
}
