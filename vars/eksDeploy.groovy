import com.asambataiden.devops.cloud.EksDeploymentSpec

/**
 * Update a pre-existing Kubernetes Deployment to a digest-pinned image.
 * Does not create namespaces/clusters, apply unreviewed manifests, or roll back automatically.
 * Caller must supply an approved kubeconfig/context and least-privilege Kubernetes identity.
 */
def call(Map args = [:]) {
    if (!['context', 'namespace', 'deployment', 'container', 'image'].every {
        args[it] instanceof String
    }) {
        error('eksDeploy requires context, namespace, deployment, container and image')
    }

    EksDeploymentSpec spec = new EksDeploymentSpec(
        args.context, args.namespace, args.deployment, args.container, args.image
    )

    withEnv([
        "K8S_CONTEXT=${spec.context}",
        "K8S_NAMESPACE=${spec.namespace}",
        "K8S_DEPLOYMENT=${spec.deployment}",
        "K8S_CONTAINER=${spec.container}",
        "K8S_IMAGE=${spec.image}"
    ]) {
        sh(
            label: 'Deploy pinned digest and verify rollout',
            script: '''#!/bin/sh
set -eu

kubectl --context "$K8S_CONTEXT" --namespace "$K8S_NAMESPACE" \
    get deployment "$K8S_DEPLOYMENT" >/dev/null

kubectl --context "$K8S_CONTEXT" --namespace "$K8S_NAMESPACE" \
    set image "deployment/$K8S_DEPLOYMENT" "$K8S_CONTAINER=$K8S_IMAGE"

kubectl --context "$K8S_CONTEXT" --namespace "$K8S_NAMESPACE" \
    rollout status "deployment/$K8S_DEPLOYMENT" --timeout=300s
'''
        )
    }
    return spec.image
}
