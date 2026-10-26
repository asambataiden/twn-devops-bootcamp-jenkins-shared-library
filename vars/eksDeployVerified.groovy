import com.asambataiden.devops.cloud.EksTargetSpec
import com.asambataiden.devops.cloud.EksDeploymentSpec

/**
 * Secure EKS-only deployment path for a pre-existing Deployment.
 *
 * Jenkins agent must use an approved short-lived AWS identity with EKS
 * describe permissions and a Kubernetes RBAC identity scoped to the target.
 * No implicit ~/.kube/config, no cluster creation, no automatic rollback.
 */
def call(Map args = [:]) {
    List<String> required = [
        'accountId', 'region', 'cluster',
        'namespace', 'deployment', 'container', 'image'
    ]
    if (!required.every { args[it] instanceof String }) {
        error('eksDeployVerified requires accountId, region, cluster, namespace, deployment, container and image')
    }

    EksTargetSpec target = new EksTargetSpec(args.accountId, args.region, args.cluster)
    EksDeploymentSpec deployment = new EksDeploymentSpec(
        'verified-context', args.namespace, args.deployment, args.container, args.image
    )

    withEnv([
        "EXPECTED_AWS_ACCOUNT=${target.accountId}",
        "AWS_REGION=${target.region}",
        "EKS_CLUSTER=${target.cluster}",
        "EXPECTED_EKS_ARN=${target.arn()}",
        "K8S_NAMESPACE=${deployment.namespace}",
        "K8S_DEPLOYMENT=${deployment.deployment}",
        "K8S_CONTAINER=${deployment.container}",
        "K8S_IMAGE=${deployment.image}"
    ]) {
        sh(
            label: 'Verify EKS identity and deploy immutable digest',
            script: '''#!/bin/sh
set -eu
umask 077
export AWS_PAGER=""

tmp_dir="$(mktemp -d)"
cleanup() {
    rm -rf -- "$tmp_dir"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

export KUBECONFIG="$tmp_dir/kubeconfig"
actual_account="$(aws sts get-caller-identity --query Account --output text)"
if [ "$actual_account" != "$EXPECTED_AWS_ACCOUNT" ]; then
    echo 'ERROR: unexpected AWS account' >&2
    exit 1
fi

actual_arn="$(aws eks describe-cluster \
    --region "$AWS_REGION" \
    --name "$EKS_CLUSTER" \
    --query 'cluster.arn' --output text)"
if [ "$actual_arn" != "$EXPECTED_EKS_ARN" ]; then
    echo 'ERROR: unexpected EKS cluster ARN' >&2
    exit 1
fi

aws eks update-kubeconfig \
    --region "$AWS_REGION" \
    --name "$EKS_CLUSTER" \
    --alias verified-context \
    --kubeconfig "$KUBECONFIG" >/dev/null

kubectl --context verified-context --namespace "$K8S_NAMESPACE" \
    get deployment "$K8S_DEPLOYMENT" >/dev/null

# Emit name and image pairs; select the exact named container, not index 0.
# Tab-delimited output supports sidecars without assuming container order.
container_images() {
    kubectl --context verified-context --namespace "$K8S_NAMESPACE" \
        get deployment "$K8S_DEPLOYMENT" \
        -o go-template='{{range .spec.template.spec.containers}}{{.name}}{{printf "\\t"}}{{.image}}{{printf "\\n"}}{{end}}'
}

previous_image="$(container_images | awk -F '\\t' -v name="$K8S_CONTAINER" '$1 == name { print $2 }')"
if [ -z "$previous_image" ]; then
    echo 'ERROR: target container not found in Deployment' >&2
    exit 1
fi
echo "Previous image for container $K8S_CONTAINER: $previous_image"

kubectl --context verified-context --namespace "$K8S_NAMESPACE" \
    set image "deployment/$K8S_DEPLOYMENT" "$K8S_CONTAINER=$K8S_IMAGE"

if ! kubectl --context verified-context --namespace "$K8S_NAMESPACE" \
    rollout status "deployment/$K8S_DEPLOYMENT" --timeout=300s; then
    echo "ERROR: rollout failed; previous image for $K8S_CONTAINER: $previous_image" >&2
    kubectl --context verified-context --namespace "$K8S_NAMESPACE" \
        describe deployment "$K8S_DEPLOYMENT" || true
    exit 1
fi

observed_image="$(container_images | awk -F '\\t' -v name="$K8S_CONTAINER" '$1 == name { print $2 }')"
if [ "$observed_image" != "$K8S_IMAGE" ]; then
    echo 'ERROR: observed Deployment image does not match requested digest' >&2
    exit 1
fi
echo "Deployment verified: $K8S_DEPLOYMENT / $K8S_CONTAINER -> $observed_image"
'''
        )
    }

    return deployment.image
}
