package com.asambataiden.devops.cloud

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.*

/**
 * Runs the real POSIX deployment shell with fake AWS/kubectl executables.
 * Never calls AWS or a Kubernetes cluster. Requires /bin/sh on test runner.
 */
class EksShellIntegrationTest {
    @TempDir Path directory

    private String deploymentScript() {
        List<String> captured = []
        def binding = new Binding([
            error: { String message -> throw new IllegalStateException(message) },
            withEnv: { List<String> variables, Closure body -> body.call() },
            sh: { Map command -> captured.add(command.script) }
        ])
        def step = new GroovyShell(getClass().classLoader, binding)
            .parse(new File(System.getProperty('user.dir'), 'vars/eksDeployVerified.groovy'))
        step.call([
            accountId: '123456789012', region: 'eu-north-1', cluster: 'test-cluster',
            namespace: 'staging', deployment: 'app', container: 'app',
            image: 'registry.example/team/app@sha256:' + ('a' * 64)
        ])
        assertEquals(1, captured.size())
        return captured[0]
    }

    private Map execute(String scenario) {
        File binDir = directory.resolve('bin').toFile()
        assertTrue(binDir.mkdirs())
        File calls = directory.resolve('calls.log').toFile()
        File script = directory.resolve('deploy.sh').toFile()
        script.text = deploymentScript()

        File aws = new File(binDir, 'aws')
        aws.text = '''#!/bin/sh
set -eu
printf 'aws %s\\n' "$*" >> "$CALLS_LOG"
case "$1 $2" in
  'sts get-caller-identity')
    if [ "$SCENARIO" = wrong-account ]; then echo 999999999999; else echo 123456789012; fi ;;
  'eks describe-cluster')
    if [ "$SCENARIO" = wrong-cluster ]; then echo arn:aws:eks:eu-north-1:123456789012:cluster/other;
    else echo arn:aws:eks:eu-north-1:123456789012:cluster/test-cluster; fi ;;
  'eks update-kubeconfig')
    printf 'temporary-config\\n' > "$KUBECONFIG" ;;
  *) exit 2 ;;
esac
'''
        File kubectl = new File(binDir, 'kubectl')
        kubectl.text = '''#!/bin/sh
set -eu
printf 'kubectl %s\\n' "$*" >> "$CALLS_LOG"
case " $* " in
  *' -o go-template='*)
    case "$SCENARIO" in
      missing-container) printf 'sidecar\\tsidecar:v1\\n' ;;
      digest-mismatch)
        if [ -f "$STATE_FILE" ]; then printf 'sidecar\\tsidecar:v1\\napp\\twrong-image@sha256:%064d\\n' 0
        else printf 'sidecar\\tsidecar:v1\\napp\\told-image@sha256:%064d\\n' 0; fi ;;
      *)
        if [ -f "$STATE_FILE" ]; then printf 'sidecar\\tsidecar:v1\\napp\\t%s\\n' "$K8S_IMAGE"
        else printf 'sidecar\\tsidecar:v1\\napp\\told-image@sha256:%064d\\n' 0; fi ;;
    esac ;;
  *' get deployment '*)
    [ "$SCENARIO" != missing-deployment ] ;;
  *' set image '*)
    touch "$STATE_FILE" ;;
  *' rollout status '*)
    [ "$SCENARIO" != rollout-failure ] ;;
  *' describe deployment '*)
    exit 0 ;;
  *) exit 2 ;;
esac
'''
        assertTrue(aws.setExecutable(true))
        assertTrue(kubectl.setExecutable(true))
        String image = 'registry.example/team/app@sha256:' + ('a' * 64)
        Map<String, String> env = new HashMap<>(System.getenv())
        env.putAll([
            PATH: binDir.absolutePath + ':' + System.getenv('PATH'),
            SCENARIO: scenario,
            CALLS_LOG: calls.absolutePath,
            STATE_FILE: directory.resolve('mutated').toString(),
            EXPECTED_AWS_ACCOUNT: '123456789012',
            AWS_REGION: 'eu-north-1',
            EKS_CLUSTER: 'test-cluster',
            EXPECTED_EKS_ARN: 'arn:aws:eks:eu-north-1:123456789012:cluster/test-cluster',
            K8S_NAMESPACE: 'staging',
            K8S_DEPLOYMENT: 'app',
            K8S_CONTAINER: 'app',
            K8S_IMAGE: image,
            TMPDIR: directory.toString()
        ])
        def process = new ProcessBuilder('/bin/sh', script.absolutePath)
            .directory(directory.toFile()).redirectErrorStream(true)
        process.environment().putAll(env)
        Process running = process.start()
        String output = running.inputStream.getText('UTF-8')
        int code = running.waitFor()
        return [
            code: code, output: output,
            calls: calls.exists() ? calls.text : '',
            mutated: directory.resolve('mutated').toFile().exists(),
            kubeconfigExists: directory.resolve('kubeconfig').toFile().exists()
        ]
    }

    @Test void wrongAccountPreventsMutation() {
        def result = execute('wrong-account')
        assertNotEquals(0, result.code)
        assertFalse(result.mutated)
        assertFalse(result.calls.contains('kubectl'))
    }

    @Test void wrongClusterPreventsMutation() {
        def result = execute('wrong-cluster')
        assertNotEquals(0, result.code)
        assertFalse(result.mutated)
        assertFalse(result.calls.contains('kubectl'))
    }

    @Test void missingDeploymentPreventsMutation() {
        def result = execute('missing-deployment')
        assertNotEquals(0, result.code)
        assertFalse(result.mutated)
    }

    @Test void missingNamedContainerPreventsMutation() {
        def result = execute('missing-container')
        assertNotEquals(0, result.code)
        assertFalse(result.mutated)
        assertTrue(result.output.contains('target container not found'))
    }

    @Test void failedRolloutPreservesPreviousImageEvidence() {
        def result = execute('rollout-failure')
        assertNotEquals(0, result.code)
        assertTrue(result.mutated)
        assertTrue(result.output.contains('previous image for app'))
        assertTrue(result.calls.contains('describe deployment'))
    }

    @Test void mismatchAfterRolloutFailsVerification() {
        def result = execute('digest-mismatch')
        assertNotEquals(0, result.code)
        assertTrue(result.output.contains('observed Deployment image does not match'))
    }

    @Test void successfulRolloutVerifiesDigest() {
        def result = execute('success')
        assertEquals(0, result.code, result.output)
        assertTrue(result.mutated)
        assertTrue(result.output.contains('Deployment verified'))
    }
}
