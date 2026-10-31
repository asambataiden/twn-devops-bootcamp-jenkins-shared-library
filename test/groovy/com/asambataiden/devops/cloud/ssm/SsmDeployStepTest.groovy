package com.asambataiden.devops.cloud.ssm

import groovy.lang.Binding
import groovy.lang.GroovyShell
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class SsmDeployStepTest {

    private static final String COMMAND_ID =
        '123e4567-e89b-12d3-a456-426614174000'

    private static Map validArgs() {
        [
            accountId   : '591132357918',
            region      : 'eu-north-1',
            instanceId  : 'i-058aa03bdbb014833',
            imageRef    :
                '591132357918.dkr.ecr.eu-north-1.amazonaws.com/' +
                'twn-capstone-03-app@sha256:' + ('a' * 64),
            appVersion  : 'bee47c0a7414',
            composeFile : 'deploy/compose.ec2.yaml',
            deployScript: 'scripts/deploy-ec2.sh'
        ]
    }

    private static Map fixture(Map options = [:]) {
        List<String> events = []
        List<String> environment = []
        Map result = [events: events, environment: environment]

        Binding binding = new Binding([
            fileExists: { String path ->
                events << "fileExists:${path}".toString()
                path != options.missingFile
            },
            libraryResource: { String path ->
                events << "libraryResource:${path}".toString()
                '#!/usr/bin/env bash\nexit 0\n'
            },
            withEnv: { List<String> variables, Closure body ->
                events << 'withEnv'
                environment.addAll(variables)
                body.call()
            },
            sh: { Map arguments ->
                events << 'sh'
                result.shArguments = arguments
                options.containsKey('output')
                    ? options.output
                    : COMMAND_ID + '\n'
            },
            error: { String message ->
                throw new IllegalStateException(message)
            }
        ])

        def shell = new GroovyShell(
            SsmDeployStepTest.class.classLoader,
            binding
        )

        def file = new File(
            System.getProperty('user.dir'),
            'vars/ssmDeploy.groovy'
        )

        result.step = shell.parse(file)
        return result
    }

    @Test
    void deploysWithValidatedEnvironment() {
        def context = fixture()

        def result = context.step.call(validArgs())

        assertEquals(COMMAND_ID, result)
        assertTrue(context.environment.contains(
            'EXPECTED_ACCOUNT=591132357918'
        ))
        assertTrue(context.environment.contains(
            'AWS_REGION=eu-north-1'
        ))
        assertTrue(context.environment.contains(
            'TARGET_INSTANCE=i-058aa03bdbb014833'
        ))
        assertTrue(context.events.contains(
            'libraryResource:com/asambataiden/devops/ssm/deploy.sh'
        ))
        assertEquals(true, context.shArguments.returnStdout)
        assertEquals(
            '#!/usr/bin/env bash\nexit 0\n',
            context.shArguments.script
        )
    }

    @Test
    void rejectsInvalidParametersBeforePipelineExecution() {
        def context = fixture()
        def args = validArgs()
        args.instanceId = 'invalid-instance'

        assertThrows(IllegalArgumentException.class, {
            context.step.call(args)
        })

        assertTrue(context.events.isEmpty())
    }

    @Test
    void rejectsMissingComposeFile() {
        def context = fixture(
            missingFile: 'deploy/compose.ec2.yaml'
        )

        assertThrows(IllegalStateException.class, {
            context.step.call(validArgs())
        })

        assertFalse(context.events.contains('sh'))
    }

    @Test
    void rejectsMissingDeploymentScript() {
        def context = fixture(
            missingFile: 'scripts/deploy-ec2.sh'
        )

        assertThrows(IllegalStateException.class, {
            context.step.call(validArgs())
        })

        assertFalse(context.events.contains('sh'))
    }

    @Test
    void rejectsInvalidSsmCommandId() {
        def context = fixture(output: 'not-a-command-id\n')

        assertThrows(IllegalStateException.class, {
            context.step.call(validArgs())
        })

        assertTrue(context.events.contains('sh'))
    }

    @Test
    void rejectsUnexpectedStandardOutput() {
        def context = fixture(
            output: 'Debug logging\n' + COMMAND_ID + '\n'
        )

        assertThrows(IllegalStateException.class, {
            context.step.call(validArgs())
        })
    }

    @Test
    void propagatesShellFailure() {
        def context = fixture()

        context.step.binding.setVariable('sh', { Map args ->
            throw new IllegalStateException('AWS SSM command failed')
        })

        def exception = assertThrows(
            IllegalStateException.class,
            { context.step.call(validArgs()) }
        )

        assertEquals('AWS SSM command failed', exception.message)
    }
}
