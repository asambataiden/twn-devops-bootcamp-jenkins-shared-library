package com.asambataiden.devops.cloud

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class EksDeployStepTest {
    private static Map valid() {
        [context: 'test-context', namespace: 'staging', deployment: 'app',
         container: 'app', image: 'team/app@sha256:' + ('a' * 64)]
    }

    private static Map invoke(Map args) {
        List<String> scripts = []
        List<String> environment = []
        def bindings = new Binding([
            error: { String msg -> throw new IllegalStateException(msg) },
            withEnv: { List<String> vals, Closure body ->
                environment.addAll(vals.collect { it.toString() })
                body.call()
            },
            sh: { Map params -> scripts.add(params.script) }
        ])
        def script = new GroovyShell(EksDeployStepTest.class.classLoader, bindings)
            .parse(new File(System.getProperty('user.dir'), 'vars/eksDeploy.groovy'))
        return [value: script.call(args), scripts: scripts, environment: environment]
    }

    @Test void verifiesBeforeDeployAndWaitsForRollout() {
        def result = invoke(valid())
        assertEquals(valid().image, result.value)
        assertEquals(1, result.scripts.size())
        String body = result.scripts[0]
        assertTrue(body.indexOf('get deployment') < body.indexOf('set image'))
        assertTrue(body.indexOf('set image') < body.indexOf('rollout status'))
        assertTrue(body.contains('--timeout=300s'))
        assertTrue(body.contains('--context "$K8S_CONTEXT"'))
        assertFalse(body.contains('rollout undo'))
    }

    @Test void refusesUnpinnedTag() {
        Map args = valid()
        args.image = 'team/app:latest'
        assertThrows(IllegalArgumentException.class, { invoke(args) })
    }
}
