package com.asambataiden.devops.cloud.ssm

/**
 * Immutable, validated specification for an EC2 SSM deployment.
 *
 * No Jenkins dependency, AWS calls, shell execution or credentials.
 */
final class SsmDeploymentSpec implements Serializable {

    final String accountId
    final String region
    final String instanceId
    final String imageRef
    final String appVersion
    final String composeFile
    final String deployScript

    SsmDeploymentSpec(Map args) {
        if (args == null) {
            throw new IllegalArgumentException('Deployment arguments required')
        }

        def required = [
            'accountId', 'region', 'instanceId',
            'imageRef', 'appVersion', 'composeFile', 'deployScript'
        ]

        required.each { name ->
            if (!(args[name] instanceof String) || !args[name]) {
                throw new IllegalArgumentException(
                    "Invalid or missing deployment parameter: ${name}"
                )
            }
        }

        if (!(args.accountId ==~ /[0-9]{12}/)) {
            throw new IllegalArgumentException('Invalid AWS account ID')
        }

        if (!(args.region ==~ /[a-z]{2}-[a-z0-9-]+-[1-9][0-9]*/)) {
            throw new IllegalArgumentException('Invalid AWS region')
        }

        if (!(args.instanceId ==~ /i-(?:[a-f0-9]{8}|[a-f0-9]{17})/)) {
            throw new IllegalArgumentException('Invalid EC2 instance ID')
        }

        String registry =
            "${args.accountId}.dkr.ecr.${args.region}.amazonaws.com"

        String prefix = "${registry}/"

        if (!args.imageRef.startsWith(prefix)) {
            throw new IllegalArgumentException(
                'ECR image account or region mismatch'
            )
        }

        String imagePath = args.imageRef.substring(prefix.length())

        if (!(imagePath ==~ /[a-z0-9][a-z0-9._\/-]*@sha256:[a-f0-9]{64}/)) {
            throw new IllegalArgumentException(
                'Image must use a valid immutable SHA-256 digest'
            )
        }

        if (!(args.appVersion ==~ /[a-f0-9]{7,40}/)) {
            throw new IllegalArgumentException(
                'Application version must be a Git commit hash'
            )
        }

        [args.composeFile, args.deployScript].each { path ->
            if (!(path ==~ /[A-Za-z0-9_.\/-]+/) ||
                path.startsWith('/') ||
                path.split('/').any {
                    it == '.' || it == '..' || it.isEmpty()
                }) {
                throw new IllegalArgumentException(
                    'Unsafe workspace-relative deployment path'
                )
            }
        }

        accountId = args.accountId
        region = args.region
        instanceId = args.instanceId
        imageRef = args.imageRef
        appVersion = args.appVersion
        composeFile = args.composeFile
        deployScript = args.deployScript
    }

    List<String> toEnvironment() {
        return [
            "EXPECTED_ACCOUNT=${accountId}".toString(),
            "AWS_REGION=${region}".toString(),
            "TARGET_INSTANCE=${instanceId}".toString(),
            "IMAGE_REF=${imageRef}".toString(),
            "APP_VERSION=${appVersion}".toString(),
            "COMPOSE_SOURCE=${composeFile}".toString(),
            "DEPLOY_SOURCE=${deployScript}".toString()
        ]
    }
}
