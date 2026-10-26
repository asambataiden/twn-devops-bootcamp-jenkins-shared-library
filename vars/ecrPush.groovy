import com.asambataiden.devops.cloud.EcrImageRef

/**
 * Publish an already-built, fully qualified local image to an immutable ECR repository.
 *
 * The Jenkins agent must have short-lived AWS credentials, AWS CLI v2, and Docker.
 * Does not build or retag, persist Docker credentials, push Git, or deploy.
 */
def call(Map args = [:]) {
    if (!['accountId', 'region', 'repository', 'tag'].every { args[it] instanceof String }) {
        error('ecrPush requires accountId, region, repository and tag strings')
    }

    EcrImageRef target = new EcrImageRef(
        args.accountId,
        args.region,
        args.repository,
        args.tag
    )

    withEnv([
        "AWS_REGION=${target.region}",
        "EXPECTED_AWS_ACCOUNT=${target.accountId}",
        "ECR_REGISTRY=${target.registry()}",
        "ECR_REPOSITORY=${target.repository}",
        "ECR_TAG=${target.tag}",
        "ECR_IMAGE=${target.image()}"
    ]) {
        sh(
            label: 'Preflight and push immutable ECR image',
            script: '''#!/bin/sh
set -eu
export AWS_PAGER=""

actual_account="$(aws sts get-caller-identity \
    --query Account --output text)"
if [ "$actual_account" != "$EXPECTED_AWS_ACCOUNT" ]; then
    echo "ERROR: AWS caller account differs from expected account" >&2
    exit 1
fi

mutability="$(aws ecr describe-repositories \
    --region "$AWS_REGION" \
    --repository-names "$ECR_REPOSITORY" \
    --query 'repositories[0].imageTagMutability' \
    --output text)"
if [ "$mutability" != "IMMUTABLE" ]; then
    echo "ERROR: ECR repository must enforce immutable tags" >&2
    exit 1
fi

docker image inspect "$ECR_IMAGE" >/dev/null

# Registry-side immutability protects against concurrent publishers.
existing_tags="$(aws ecr list-images \
    --region "$AWS_REGION" \
    --repository-name "$ECR_REPOSITORY" \
    --filter tagStatus=TAGGED \
    --query "imageIds[?imageTag=='$ECR_TAG'].imageTag" \
    --output text)"
if [ -n "$existing_tags" ] && [ "$existing_tags" != "None" ]; then
    echo "ERROR: ECR image tag already exists; refusing overwrite" >&2
    exit 1
fi

DOCKER_CONFIG="$(mktemp -d)"
export DOCKER_CONFIG
trap 'rm -rf "$DOCKER_CONFIG"' EXIT HUP INT TERM

aws ecr get-login-password --region "$AWS_REGION" |
    docker login --username AWS --password-stdin "$ECR_REGISTRY"
docker push "$ECR_IMAGE"
'''
        )
    }
    return target.image()
}
