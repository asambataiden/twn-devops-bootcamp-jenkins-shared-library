package com.asambataiden.devops.cloud

/** Pure Kubernetes deployment-input validation; no cluster access. */
final class EksDeploymentSpec implements Serializable {
    final String context
    final String namespace
    final String deployment
    final String container
    final String image

    EksDeploymentSpec(String context, String namespace, String deployment,
                      String container, String image) {
        if (!(context ==~ /[A-Za-z0-9][A-Za-z0-9._:@\/=-]{0,252}/) ||
            context.startsWith('-')) {
            throw new IllegalArgumentException('Invalid Kubernetes context')
        }
        [namespace, deployment, container].each { String name ->
            if (!(name ==~ /[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?/)) {
                throw new IllegalArgumentException('Invalid Kubernetes resource name')
            }
        }
        if (!(image ==~ /[a-z0-9][a-z0-9._:\/-]*@sha256:[a-f0-9]{64}/)) {
            throw new IllegalArgumentException('Deployment requires digest-pinned image')
        }
        this.context = context
        this.namespace = namespace
        this.deployment = deployment
        this.container = container
        this.image = image
    }
}
