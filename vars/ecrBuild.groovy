import com.asambataiden.devops.cloud.EcrImageRef

/**
 * Build an image directly under its immutable ECR destination tag.
 *
 * Existing callers without optional arguments retain docker build behavior.
 * Optional arguments:
 *   dockerfile: relative path, default Dockerfile
 *   context:    relative build context, default .
 *   platform:   supported Linux target architecture, optional
 *
 * Does not authenticate, publish, or deploy.
 */
def call(Map args = [:]) {
    if (!['accountId', 'region', 'repository', 'tag'].every {
        args[it] instanceof String
    }) {
        error('ecrBuild requires accountId, region, repository and tag strings')
    }

    EcrImageRef target = new EcrImageRef(
        args.accountId,
        args.region,
        args.repository,
        args.tag
    )

    String dockerfile = args.get('dockerfile', 'Dockerfile')
    String context = args.get('context', '.')
    String platform = args.get('platform', '')

    if (!(dockerfile instanceof String) ||
        !(context instanceof String) ||
        !(platform instanceof String)) {
        error('dockerfile, context and platform must be strings')
    }

    // Only allow workspace-relative paths without traversal.
    def safePath = { String path ->
        path == '.' || (
            path ==~ /[A-Za-z0-9_.\/-]+/ &&
            !path.startsWith('/') &&
            !path.split('/').any { it == '..' || it == '.' || it.isEmpty() }
        )
    }

    if (!safePath(dockerfile) || dockerfile == '.') {
        error('Invalid Dockerfile path')
    }

    if (!safePath(context)) {
        error('Invalid Docker build context')
    }

    if (platform && !(platform in ['linux/amd64', 'linux/arm64'])) {
        error('Unsupported Docker build platform')
    }

    if (!fileExists(dockerfile)) {
        error("Dockerfile not found: ${dockerfile}")
    }

// The workspace root is the established default build context.
// Preserve compatibility with existing pipeline-step contracts.
    if (context != '.' && !fileExists(context)) {
        error("Docker build context not found: ${context}")
    }

    withEnv([
        "ECR_IMAGE=${target.image()}",
        "BUILD_DOCKERFILE=${dockerfile}",
        "BUILD_CONTEXT=${context}",
        "BUILD_PLATFORM=${platform}"
    ]) {
        sh(
            label: 'Build fully qualified ECR image',
            script: '''#!/bin/sh
set -eu

if [ -n "$BUILD_PLATFORM" ]; then
    docker buildx build \
        --pull \
        --platform "$BUILD_PLATFORM" \
        --provenance=false \
        --load \
        --file "$BUILD_DOCKERFILE" \
        --tag "$ECR_IMAGE" \
        "$BUILD_CONTEXT"
else
    docker build \
        --pull \
        --file "$BUILD_DOCKERFILE" \
        --tag "$ECR_IMAGE" \
        "$BUILD_CONTEXT"
fi

docker image inspect "$ECR_IMAGE" >/dev/null

if [ -n "$BUILD_PLATFORM" ]; then
    actual_platform="$(docker image inspect \
        --format '{{.Os}}/{{.Architecture}}' "$ECR_IMAGE")"

    if [ "$actual_platform" != "$BUILD_PLATFORM" ]; then
        echo "ERROR: Built image architecture mismatch" >&2
        exit 1
    fi
fi
'''
        )
    }

    return target.image()
}
