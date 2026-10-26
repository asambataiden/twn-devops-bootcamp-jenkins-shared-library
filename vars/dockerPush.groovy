import com.asambataiden.devops.container.DockerImageRef

/**
 * Docker Hub push using Jenkins username/password credentials.
 * The password is piped via stdin; Docker config is isolated and cleaned up.
 * No permanent login, no Git operation, no deployment.
 */
def call(Map args = [:]) {
    if (!(args.repository instanceof String) || !(args.tag instanceof String)) {
        error('dockerPush requires repository and tag strings')
    }
    if (!(args.credentialsId instanceof String) ||
        !(args.credentialsId ==~ /[A-Za-z0-9_.-]{1,128}/)) {
        error('dockerPush requires a safe Jenkins credentialsId')
    }
    String image = new DockerImageRef(args.repository, args.tag).toString()
    // Docker Hub only: reject fully-qualified registries; ECR gets a separate step.
    if (!(args.repository ==~ /[a-z0-9_-]+\/[a-z0-9][a-z0-9._-]*/)) {
        error('dockerPush supports Docker Hub namespace/repository only')
    }
    withCredentials([usernamePassword(
        credentialsId: args.credentialsId,
        usernameVariable: 'DOCKERHUB_USERNAME',
        passwordVariable: 'DOCKERHUB_PASSWORD'
    )]) {
        withEnv(["IMAGE_REF=${image}"]) {
            sh(
                label: 'Docker Hub authenticated push',
                script: '''#!/bin/sh
set -eu
DOCKER_CONFIG="$(mktemp -d)"
export DOCKER_CONFIG
trap 'rm -rf "$DOCKER_CONFIG"' EXIT
printf '%s' "$DOCKERHUB_PASSWORD" | docker login --username "$DOCKERHUB_USERNAME" --password-stdin
docker push "$IMAGE_REF"
'''
            )
        }
    }
    return image
}
