import com.asambataiden.devops.cloud.EcrImageRef

/**
 * Build a Docker image using its final, fully qualified Amazon ECR reference.
 *
 * Building with the destination name eliminates implicit retagging between
 * the Docker build stage and ecrPush. No credentials or registry operations.
 */
def call(Map args = [:]) {
    if (!['accountId', 'region', 'repository', 'tag'].every { args[it] instanceof String }) {
        error('ecrBuild requires accountId, region, repository and tag strings')
    }

    EcrImageRef target = new EcrImageRef(
        args.accountId,
        args.region,
        args.repository,
        args.tag
    )

    if (!fileExists('Dockerfile')) {
        error('Dockerfile missing from workspace root')
    }

    withEnv(["ECR_IMAGE=${target.image()}"]) {
        sh(
            label: 'Build fully qualified ECR image',
            script: '''#!/bin/sh
set -eu
docker build --pull --file Dockerfile --tag "$ECR_IMAGE" .
docker image inspect "$ECR_IMAGE" >/dev/null
'''
        )
    }

    return target.image()
}
