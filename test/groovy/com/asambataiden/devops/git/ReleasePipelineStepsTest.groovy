package com.asambataiden.devops.git

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

/** Lightweight Jenkins step fakes. Never invokes a real shell or changes Git. */
class ReleasePipelineStepsTest {
    private static File pipelineFile(String name) {
        new File(System.getProperty('user.dir'), "vars/${name}.groovy")
    }

    private static Map runStep(String name, Map args, Map state = [:]) {
        List<String> commands = []
        Map replies = state.get('replies', [:])
        Closure shell = { Object input ->
            String command = input instanceof Map ? input.script : input.toString()
            commands << command
            if (input instanceof Map && input.returnStatus) return replies.get(command, 1)
            if (input instanceof Map && input.returnStdout) return replies.get(command, '')
            return null
        }
        def binding = new Binding([
            sh: shell,
            error: { String msg -> throw new IllegalStateException(msg) },
            fileExists: { String p -> state.get('files', ['pom.xml']).contains(p) },
            withEnv: { List<String> vars, Closure body -> body.call() }
        ])
        def script = new GroovyShell(ReleasePipelineStepsTest.class.classLoader, binding).parse(pipelineFile(name))
        try {
            return [result: script.call(args), commands: commands]
        } catch (Exception ex) {
            state.lastCommands = commands
            throw ex
        }
    }

    private static Map gitReadyState(String porcelain) {
        return [replies: [
            'git symbolic-ref --quiet --short HEAD': 'main',
            'git status --porcelain --untracked-files=normal': porcelain,
            'git rev-parse HEAD': 'abcdef123456'
        ]]
    }

    @Test void commitAllowsOnlyPomAndNeverPushes() {
        Map result = runStep('commitVersion', [
            branch: 'main', version: '1.2.3',
            authorName: 'Release Bot', authorEmail: 'ci@example.invalid'
        ], gitReadyState(' M pom.xml'))
        assertEquals('abcdef123456', result.result)
        assertTrue(result.commands.any { it.startsWith('git add -- pom.xml') })
        assertTrue(result.commands.any { it.startsWith('git commit ') })
        assertFalse(result.commands.any { it.contains('push') })
    }

    @Test void commitRejectsUnrelatedWorkspaceChanges() {
        def state = gitReadyState(' M pom.xml\n M README.md')
        assertThrows(IllegalStateException.class, {
            runStep('commitVersion', [
                branch: 'main', version: '1.2.3',
                authorName: 'Release Bot', authorEmail: 'ci@example.invalid'
            ], state)
        })
        assertFalse(state.lastCommands.any { it.startsWith('git add ') || it.startsWith('git commit ') })
    }

    @Test void commitRejectsFeatureBranchBeforeShell() {
        def state = gitReadyState(' M pom.xml')
        assertThrows(IllegalArgumentException.class, {
            runStep('commitVersion', [
                branch: 'feat/test', version: '1.2.3',
                authorName: 'Release Bot', authorEmail: 'ci@example.invalid'
            ], state)
        })
        assertEquals([], state.lastCommands)
    }

    @Test void tagIsLocalAnnotatedOnly() {
        Map result = runStep('createReleaseTag', [
            branch: 'main', version: '1.2.3',
            authorName: 'Release Bot', authorEmail: 'ci@example.invalid'
        ], gitReadyState(''))
        assertEquals('v1.2.3', result.result)
        assertTrue(result.commands.any { it == "git tag -a v1.2.3 -m 'Release v1.2.3'" })
        assertFalse(result.commands.any { it.contains('push') })
    }

    @Test void tagRejectsSnapshotBeforeGitCommands() {
        def state = gitReadyState('')
        assertThrows(IllegalArgumentException.class, {
            runStep('createReleaseTag', [
                branch: 'main', version: '1.2.3-SNAPSHOT',
                authorName: 'Release Bot', authorEmail: 'ci@example.invalid'
            ], state)
        })
        assertEquals([], state.lastCommands)
    }

    @Test void tagRejectsDirtyWorkspace() {
        def state = gitReadyState(' M pom.xml')
        assertThrows(IllegalStateException.class, {
            runStep('createReleaseTag', [
                branch: 'main', version: '1.2.3',
                authorName: 'Release Bot', authorEmail: 'ci@example.invalid'
            ], state)
        })
        assertFalse(state.lastCommands.any { it.startsWith('git tag ') })
    }
}
