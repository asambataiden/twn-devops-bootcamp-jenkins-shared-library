package com.asambataiden.devops.versioning

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

/**
 * Runs vars scripts with stubbed Jenkins primitives.
 * Does not invoke Maven, a shell, Git, or a real Jenkins instance.
 */
class VersionPipelineStepsTest {
    private static Map executeStep(String name, Map arguments, Map options = [:]) {
        List<String> commands = []
        List<String> versions = new ArrayList<String>(options.get('versions', []))
        def binding = new Binding([
            fileExists: { String path -> options.get('pomExists', true) && path == 'pom.xml' },
            error: { String message -> throw new IllegalStateException(message) },
            sh: { Object optionsOrCommand ->
                String command = optionsOrCommand instanceof Map ? optionsOrCommand.script : optionsOrCommand.toString()
                commands.add(command)
                if (optionsOrCommand instanceof Map && optionsOrCommand.returnStdout) {
                    if (versions.isEmpty()) throw new IllegalStateException('Missing mocked Maven output')
                    return versions.remove(0)
                }
                return null
            }
        ])
        File scriptFile = new File(System.getProperty('user.dir'), 'vars/' + name + '.groovy')
        def step = new GroovyShell(VersionPipelineStepsTest.class.classLoader, binding).parse(scriptFile)
        try {
            return [value: step.call(arguments), commands: commands]
        } finally {
            options.commands = commands
        }
    }

    @Test void incrementCalculatesWithoutShellCommands() {
        def options = [:]
        def result = executeStep('incrementVersion', [version: '2.3.4', type: 'minor'], options)
        assertEquals('2.4.0', result.value)
        assertEquals([], result.commands)
    }

    @Test void incrementRejectsInvalidInput() {
        assertThrows(IllegalStateException.class, {
            executeStep('incrementVersion', [version: 123, type: 'patch'])
        })
        assertThrows(IllegalArgumentException.class, {
            executeStep('incrementVersion', [version: '1.2.3', type: 'unsafe'])
        })
    }

    @Test void mavenUpdatesOnlyAfterCheckingExpectedVersion() {
        def options = [versions: ['1.2.3-SNAPSHOT', '1.2.4-SNAPSHOT']]
        def result = executeStep('updateMavenVersion',
            [expectedVersion: '1.2.3-SNAPSHOT', newVersion: '1.2.4-SNAPSHOT'], options)
        assertEquals('1.2.4-SNAPSHOT', result.value)
        assertEquals(3, result.commands.size())
        assertTrue(result.commands[0].contains('maven-help-plugin'))
        assertTrue(result.commands[1].contains('versions-maven-plugin'))
        assertTrue(result.commands[2].contains('maven-help-plugin'))
        assertFalse(result.commands.any { it.contains('git ') || it.contains('terraform ') || it.contains('kubectl ') })
    }

    @Test void mavenRejectsUnexpectedCurrentVersionBeforeWrite() {
        def options = [versions: ['1.0.0']]
        assertThrows(IllegalStateException.class, {
            executeStep('updateMavenVersion',
                [expectedVersion: '1.2.3', newVersion: '1.2.4'], options)
        })
        assertEquals(1, options.commands.size())
        assertFalse(options.commands.any { it.contains('versions-maven-plugin') })
    }

    @Test void mavenRejectsMissingPomBeforeShell() {
        def options = [pomExists: false]
        assertThrows(IllegalStateException.class, {
            executeStep('updateMavenVersion',
                [expectedVersion: '1.2.3', newVersion: '1.2.4'], options)
        })
        assertEquals([], options.commands)
    }

    @Test void mavenRejectsUnsupportedOrIdenticalVersions() {
        def options = [:]
        assertThrows(IllegalArgumentException.class, {
            executeStep('updateMavenVersion',
                [expectedVersion: '1.2.3', newVersion: '1.2.4;echo bad'], options)
        })
        assertThrows(IllegalStateException.class, {
            executeStep('updateMavenVersion',
                [expectedVersion: '1.2.3', newVersion: '1.2.3'], options)
        })
        assertEquals([], options.commands)
    }

    @Test void mavenFailsWhenVerificationDoesNotMatch() {
        def options = [versions: ['1.2.3', '1.2.3']]
        assertThrows(IllegalStateException.class, {
            executeStep('updateMavenVersion',
                [expectedVersion: '1.2.3', newVersion: '1.2.4'], options)
        })
        assertEquals(3, options.commands.size())
    }
}
