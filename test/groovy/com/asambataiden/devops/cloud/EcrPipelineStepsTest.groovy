package com.asambataiden.devops.cloud

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class EcrPipelineStepsTest {
    private static Map runStep(String name, Map args, String output = '') {
        List<Map> commands = []
        List<String> environment = []
        def binding = new Binding([
            error: { String reason -> throw new IllegalStateException(reason) },
            withEnv: { List<String> values, Closure body ->
                environment.addAll(values)
                body.call()
            },
            sh: { Map cmd ->
                commands.add(cmd)
                return cmd.returnStdout ? output : null
            }
        ])
        def script = new GroovyShell(EcrPipelineStepsTest.class.classLoader, binding)
            .parse(new File(System.getProperty('user.dir'), 'vars/' + name + '.groovy'))
        return [result: script.call(args), commands: commands, environment: environment]
    }

    private static Map input() {
        [accountId: '123456789012', region: 'eu-north-1', repository: 'team/app', tag: 'git-abcdef']
    }

    @Test void pushesWithScopedTemporaryDockerConfig() {
        def result = runStep('ecrPush', input())
        assertEquals('123456789012.dkr.ecr.eu-north-1.amazonaws.com/team/app:git-abcdef', result.result)
        assertEquals(1, result.commands.size())
        String script = result.commands[0].script
        assertTrue(script.contains('get-login-password'))
        assertTrue(script.contains('--password-stdin'))
        assertTrue(script.contains('trap '))
        assertTrue(script.contains('docker push "$ECR_IMAGE"'))
        assertFalse(script.contains('git push'))
        assertFalse(script.contains('AKIA'))
    }

    @Test void resolvesPublishedDigestUsingAwsApi() {
        String digest = 'sha256:' + ('b' * 64)
        def result = runStep('ecrImageDigest', input(), digest + '\n')
        assertEquals('123456789012.dkr.ecr.eu-north-1.amazonaws.com/team/app@' + digest, result.result)
        assertTrue(result.commands[0].script.contains('describe-images'))
    }

    @Test void rejectsMalformedDigest() {
        assertThrows(IllegalArgumentException.class, {
            runStep('ecrImageDigest', input(), 'None')
        })
    }

    @Test void rejectsBadInputBeforeShell() {
        def args = input()
        args.region = 'eu-north-1;rm'
        assertThrows(IllegalArgumentException.class, { runStep('ecrPush', args) })
    }
}
