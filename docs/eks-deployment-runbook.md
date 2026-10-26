# EKS digest-based rollout: integration runbook

## Verified EKS-specific entry point (new, unverified in Jenkins)

Prefer `eksDeployVerified(accountId: '123456789012', region: 'eu-north-1', cluster: 'test-cluster', namespace: 'staging', deployment: 'app', container: 'app', image: 'registry/repo@sha256:...')` for controlled EKS use. It verifies AWS account and exact EKS cluster ARN before creating an isolated, temporary kubeconfig, checks Deployment and container existence, updates the digest-pinned image, then waits for rollout status. It has no automatic rollback. Its implementation and mocked tests have been committed but not locally executed yet.

The existing `eksDeploy` is a basic context-based helper, retained for backwards compatibility; it does not independently prove target EKS identity. Do not select it for production-like release flows until additional verification is implemented.

**Security boundary:** Runtime AWS identity still depends on the trusted Jenkins agent or approved short-lived credentials. Jenkins must restrict which jobs may run this step and ensure isolated workspaces/agents. Never accept destination inputs from untrusted PR code. The Kubernetes cluster ARN check authenticates the intended AWS cluster resource; EKS/Kubernetes authorization and workload-level readiness remain separate checks.


## Intent and boundaries

`eksDeploy(context, namespace, deployment, container, image)` updates a **pre-existing** Kubernetes Deployment to an immutable OCI digest reference, then waits up to five minutes for rollout status. Source-code tests are mocked. The step does **not** create a cluster, apply arbitrary manifests, grant RBAC, approve releases, or perform automatic rollback.

## Prerequisites

- Trusted Jenkins job using a reviewed Shared Library revision, with `kubectl` installed and compatible with the target cluster version.
- An explicitly approved kubeconfig and context pointing to the intended EKS cluster/account. **Always verify the context and credentials separately**: syntactically valid context names are not proof of cluster identity.
- Least-privilege Kubernetes RBAC allowing `get` and `patch` on the intended Deployment, plus permission to observe rollout status. Grant only the necessary verbs/resources/namespaces.
- An existing namespace and Deployment whose container name matches the configured argument.
- Image already pushed to ECR and resolved to a verified registry manifest digest; cluster nodes must be authorized to pull it.
- A reviewed rollout strategy, readiness and liveness probes, resource requests/limits, replica settings and PodDisruptionBudget where appropriate. Configure these in manifests/IaC, not from an ad hoc Jenkins script.
- Clear release approval and change window for the environment. No automatic AWS action should be triggered from an untrusted pull request.

## Read-only preflight

```bash
kubectl --context YOUR_APPROVED_CONTEXT --namespace YOUR_NAMESPACE get deployment YOUR_DEPLOYMENT
kubectl --context YOUR_APPROVED_CONTEXT --namespace YOUR_NAMESPACE get deployment YOUR_DEPLOYMENT -o jsonpath='{.spec.template.spec.containers[*].name}'
kubectl --context YOUR_APPROVED_CONTEXT --namespace YOUR_NAMESPACE auth can-i patch deployment/YOUR_DEPLOYMENT
```

Use placeholders deliberately; do not guess a real cluster or namespace.

## Controlled rollout

1. Confirm reviewed source revision, CI validation, release authorization and exact ECR image digest.
2. Record the currently deployed image digest for rollback preparation.
3. Call `eksDeploy` with the approved context/namespace/deployment/container/image parameters.
4. Inspect deployment rollout status, Kubernetes events and application health after rollout.
5. Capture Jenkins run URL, source SHA, digest, target namespace and Deployment, and a non-sensitive health-check summary.
6. If rollout fails, investigate first. Roll back using the previously approved **digest** or a separately approved Kubernetes rollout undo command; avoid blind retry loops.

## Known limitations

- This is **Kubernetes Deployment orchestration for EKS**, not EKS cluster provisioning or Kubernetes context authentication.
- A successful `kubectl rollout status` means Kubernetes considers the rollout complete; it does not prove business-level availability, security posture or successful external traffic.
- Context naming and image-reference shape are validated but target cluster identity, ECR repository ownership and OCI image trust/provenance are not yet attested.
- No cluster actions or integration tests have been performed during development of this step.

## Phase 9.2: named-container recovery evidence

The verified step now selects the **requested container by name**, rather than assuming the first container is the application. It captures the previous image reference before mutation, checks rollout completion, and reads the Deployment template afterward to confirm it matches the requested digest. On rollout failure, the step records the previous image and describes the Deployment, then fails. It does **not** automatically roll back or guarantee business-level health.

The previous image currently appears in Jenkins build logs, not a protected artifact or structured recovery record. For recovery, an operator must independently confirm the image and authorize redeployment; pod readiness and externally observed service health remain separate gates. Shell command structure tests do not simulate Kubernetes runtime failures, and no live EKS deployment has been performed.

