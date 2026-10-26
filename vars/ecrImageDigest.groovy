import com.asambataiden.devops.cloud.EcrImageRef

/** Query ECR for the pushed manifest digest. Return an immutable image reference. */
def call(Map args = [:]) {
    if (!['accountId', 'region', 'repository', 'tag'].every { args[it] instanceof String }) {
        error('ecrImageDigest requires accountId, region, repository and tag strings')
    }
    EcrImageRef target = new EcrImageRef(args.accountId, args.region, args.repository, args.tag)
    String registryDigest
    withEnv(["AWS_REGION=${target.region}", "ECR_REPOSITORY=${target.repository}",
             "ECR_TAG=${target.tag}"]) {
        registryDigest = sh(
            script: '''#!/bin/sh
set -eu
aws ecr describe-images --region "$AWS_REGION" --repository-name "$ECR_REPOSITORY" --image-ids imageTag="$ECR_TAG" --query 'imageDetails[0].imageDigest' --output text
''',
            returnStdout: true
        ).trim()
    }
    return target.digestRef(registryDigest)
}
