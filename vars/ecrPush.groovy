import com.asambataiden.devops.cloud.EcrImageRef

/**
 * Push a locally built image to Amazon ECR with the Jenkins agent's AWS identity.
 * Requires AWS CLI v2, Docker and IAM permissions for the target repository.
 * AWS credentials must be provided by a trusted agent role or a scoped Jenkins binding.
 * No static access keys, no Git changes, no EKS deployment.
 */
def call(Map args = [:]) {
    if (!['accountId', 'region', 'repository', 'tag'].every { args[it] instanceof String }) {
        error('ecrPush requires accountId, region, repository and tag strings')
    }
    EcrImageRef target = new EcrImageRef(args.accountId, args.region, args.repository, args.tag)
    String fullImage = target.image()
    String registry = target.registry()

    withEnv(["AWS_REGION=${target.region}", "ECR_REGISTRY=${registry}",
             "ECR_REPOSITORY=${target.repository}", "ECR_TAG=${target.tag}",
             "ECR_IMAGE=${fullImage}"]) {
        sh(label: 'Authenticate and push ECR image', script: '''#!/bin/sh
set -eu
DOCKER_CONFIG="$(mktemp -d)"
export DOCKER_CONFIG
trap 'rm -rf "$DOCKER_CONFIG"' EXIT
aws ecr get-login-password --region "$AWS_REGION" |
  docker login --username AWS --password-stdin "$ECR_REGISTRY"
docker push "$ECR_IMAGE"
''')
    }
    return fullImage
}
