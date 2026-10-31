import com.asambataiden.devops.cloud.ssm.SsmDeploymentSpec

/**
 * Jenkins Pipeline step for digest-pinned EC2 deployment via SSM.
 *
 * Validates deployment configuration, loads a reusable script,
 * and returns the verified SSM command ID.
 */
def call(Map args = [:]) {
    SsmDeploymentSpec spec = new SsmDeploymentSpec(args)

    if (!fileExists(spec.composeFile)) {
        error("Compose file not found: ${spec.composeFile}")
    }

    if (!fileExists(spec.deployScript)) {
        error("Deployment script not found: ${spec.deployScript}")
    }

    String deploymentScript = libraryResource(
        'com/asambataiden/devops/ssm/deploy.sh'
    )

    String commandId

    withEnv(spec.toEnvironment()) {
        commandId = sh(
            label: 'Deploy immutable image through AWS SSM',
            returnStdout: true,
            script: deploymentScript
        ).trim()
    }

    if (!(commandId ==~ /[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}/)) {
        error('SSM deployment did not return a valid command ID')
    }

    return commandId
}
