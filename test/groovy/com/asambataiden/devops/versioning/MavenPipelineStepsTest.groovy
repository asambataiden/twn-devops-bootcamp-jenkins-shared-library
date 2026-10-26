package com.asambataiden.devops.versioning

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test
import static org.junit.jupiter.api.Assertions.*

class MavenPipelineStepsTest {
    static Map execute(String name, Map args = [:], boolean pomExists = true, boolean failBuild = false) {
        List<String> executed = []
        Map reports = [:]
        Map archives = [:]
        def bindings = new Binding([
            fileExists: { String p -> pomExists && p == 'pom.xml' },
            error: { String reason -> throw new IllegalStateException(reason) },
            sh: { String command ->
                executed.add(command)
                if (failBuild) throw new IllegalStateException('Simulated Maven failure')
            },
            junit: { Map parameters -> reports.putAll(parameters) },
            archiveArtifacts: { Map parameters -> archives.putAll(parameters) }
        ])
        def script = new GroovyShell(MavenPipelineStepsTest.class.classLoader, bindings)
            .parse(new File(System.getProperty('user.dir'), 'vars/' + name + '.groovy'))
        try { script.call(args) } finally { }
        return [commands: executed, reports: reports, archives: archives]
    }

    @Test void verifiesAndPublishesTestResults() {
        def result = execute('mavenVerify')
        assertEquals(['mvn -B -ntp verify'], result.commands)
        assertEquals(false, result.reports.allowEmptyResults)
        assertTrue(result.reports.testResults.contains('surefire-reports'))
    }

    @Test void verifiesEvenWhenBuildFailsAttemptingToPublishResults() {
        List<String> records = []
        def shell = new Binding([
            fileExists: { String p -> true },
            error: { String m -> throw new IllegalStateException(m) },
            sh: { String s -> throw new IllegalStateException('Simulated Maven error') },
            junit: { Map m -> records.add('junit') }
        ])
        def groovy = new GroovyShell(MavenPipelineStepsTest.class.classLoader, shell)
            .parse(new File(System.getProperty('user.dir'), 'vars/mavenVerify.groovy'))
        assertThrows(IllegalStateException.class, { groovy.call([:]) })
        assertEquals(['junit'], records)
    }

    @Test void buildsAndArchivesJar() {
        def result = execute('mavenBuild')
        assertEquals(['mvn -B -ntp package'], result.commands)
        assertEquals('target/*.jar', result.archives.artifacts)
        assertEquals(true, result.archives.fingerprint)
    }

    @Test void canSkipTestsExplicitly() {
        def result = execute('mavenBuild', [skipTests: true])
        assertEquals(['mvn -B -ntp -DskipTests package'], result.commands)
    }

    @Test void rejectsInvalidOptionsAndMissingPom() {
        assertThrows(IllegalStateException.class, { execute('mavenBuild', [skipTests: 'true']) })
        assertThrows(IllegalStateException.class, { execute('mavenBuild', [foo: true]) })
        assertThrows(IllegalStateException.class, { execute('mavenBuild', [:], false) })
        assertThrows(IllegalStateException.class, { execute('mavenVerify', [:], false) })
    }
}
