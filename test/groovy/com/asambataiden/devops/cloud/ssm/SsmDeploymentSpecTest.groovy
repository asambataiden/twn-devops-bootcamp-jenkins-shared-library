package com.asambataiden.devops.cloud.ssm

import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.*

class SsmDeploymentSpecTest {

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

    @Test
    void acceptsValidCapstone03Deployment() {
        def spec = new SsmDeploymentSpec(validArgs())

        assertEquals('eu-north-1', spec.region)
        assertEquals('i-058aa03bdbb014833', spec.instanceId)
        assertEquals('bee47c0a7414', spec.appVersion)
        assertTrue(spec.imageRef.contains('@sha256:'))
    }

    @Test
    void exportsExpectedEnvironment() {
        def environment = new SsmDeploymentSpec(
            validArgs()
        ).toEnvironment()

        assertTrue(environment.contains('EXPECTED_ACCOUNT=591132357918'))
        assertTrue(environment.contains('AWS_REGION=eu-north-1'))
        assertTrue(environment.contains('APP_VERSION=bee47c0a7414'))
        assertTrue(environment.contains(
            'COMPOSE_SOURCE=deploy/compose.ec2.yaml'
        ))
    }

    @Test
    void rejectsMissingArguments() {
        def args = validArgs()
        args.remove('instanceId')

        assertThrows(IllegalArgumentException.class, {
            new SsmDeploymentSpec(args)
        })
    }

    @Test
    void rejectsInvalidAwsTargets() {
        [
            [accountId: '123'],
            [region: 'eu-north-1;exit'],
            [instanceId: 'i-invalid']
        ].each { invalid ->
            assertThrows(IllegalArgumentException.class, {
                new SsmDeploymentSpec(validArgs() + invalid)
            })
        }
    }

    @Test
    void rejectsWrongEcrAccountAndRegion() {
        [
            [accountId: '123456789012'],
            [region: 'eu-west-1']
        ].each { invalid ->
            assertThrows(IllegalArgumentException.class, {
                new SsmDeploymentSpec(validArgs() + invalid)
            })
        }
    }

    @Test
    void rejectsFloatingImageTags() {
        def args = validArgs()
        args.imageRef =
            '591132357918.dkr.ecr.eu-north-1.amazonaws.com/' +
            'twn-capstone-03-app:latest'

        assertThrows(IllegalArgumentException.class, {
            new SsmDeploymentSpec(args)
        })
    }

    @Test
    void rejectsInvalidDigest() {
        def args = validArgs()
        args.imageRef = args.imageRef.replace(
            'a' * 64,
            'not-a-valid-digest'
        )

        assertThrows(IllegalArgumentException.class, {
            new SsmDeploymentSpec(args)
        })
    }

    @Test
    void rejectsUnsafeWorkspacePaths() {
        [
            [composeFile: '../compose.yaml'],
            [composeFile: '/tmp/compose.yaml'],
            [deployScript: 'scripts/../deploy.sh'],
            [deployScript: 'scripts/deploy.sh;id']
        ].each { invalid ->
            assertThrows(IllegalArgumentException.class, {
                new SsmDeploymentSpec(validArgs() + invalid)
            })
        }
    }

    @Test
    void rejectsInvalidApplicationVersion() {
        assertThrows(IllegalArgumentException.class, {
            new SsmDeploymentSpec(
                validArgs() + [appVersion: 'latest']
            )
        })
    }
}
