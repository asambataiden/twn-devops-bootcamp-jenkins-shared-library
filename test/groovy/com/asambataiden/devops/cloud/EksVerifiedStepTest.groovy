package com.asambataiden.devops.cloud

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class EksVerifiedStepTest {
    private static Map inputs() {
        [
            accountId: '123456789012', region: 'eu-north-1', cluster: 'test-cluster',
            namespace: 'staging', deployment: 'app', container: 'app',
            image: '123456789012.dkr.ecr.eu-north-1.amazonaws.com/team/app@sha256:' + ('a' * 64)
        ]
    }

    private static Map invoke(Map args) {
        List<String> scripts = []
        List<String> env = []
        def binding = new Binding([
            error: { String msg -> throw new IllegalStateException(msg) },
            withEnv: { List values, Closure body ->
                env.addAll(values.collect { it.toString() })
                body.call()
            },
            sh: { Map params -> scripts.add(params.script) }
        ])
        def script = new GroovyShell(EksVerifiedStepTest.class.classLoader, binding)
            .parse(new File(System.getProperty('user.dir'), 'vars/eksDeployVerified.groovy'))
        return [value: script.call(args), scripts: scripts, env: env]
    }

    @Test void verifiesAwsIdentityAndIsolatesKubeconfig() {
        def result = invoke(inputs())
        String script = result.scripts[0]
        assertTrue(result.env.contains('EXPECTED_AWS_ACCOUNT=123456789012'))
        assertTrue(result.env.contains('EXPECTED_EKS_ARN=arn:aws:eks:eu-north-1:123456789012:cluster/test-cluster'))
        assertTrue(script.contains('aws sts get-caller-identity'))
        assertTrue(script.contains('aws eks describe-cluster'))
        assertTrue(script.contains('export KUBECONFIG="$tmp_dir/kubeconfig"'))
        assertTrue(script.contains('trap cleanup EXIT'))
        assertTrue(script.indexOf('describe-cluster') < script.indexOf('update-kubeconfig'))
        assertTrue(script.indexOf('get deployment') < script.indexOf('set image'))
        assertTrue(script.contains('rollout status'))
        assertTrue(script.contains('describe deployment'))
        assertFalse(script.contains('rollout undo'))
    }

    @Test void rejectsFloatingImageBeforeShell() {
        def args = inputs()
        args.image = 'team/app:latest'
        assertThrows(IllegalArgumentException.class, { invoke(args) })
    }

    @Test void rejectsWrongAccountFormatBeforeShell() {
        def args = inputs()
        args.accountId = 'bad'
        assertThrows(IllegalArgumentException.class, { invoke(args) })
    }

    @Test void validatesEksTargetArn() {
        def spec = new EksTargetSpec('123456789012', 'eu-north-1', 'test-cluster')
        assertEquals('arn:aws:eks:eu-north-1:123456789012:cluster/test-cluster', spec.arn())
        assertThrows(IllegalArgumentException.class, {
            new EksTargetSpec('123456789012', 'eu-north-1', 'unsafe;name')
        })
    }
}
