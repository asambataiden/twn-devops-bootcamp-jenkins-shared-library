# ECR integration validation runbook

## Scope and acceptance criteria

This runbook covers a **controlled** Jenkins-agent test of `ecrBuild`, `ecrPush`, and `ecrImageDigest`. Source unit tests and mocked Jenkins steps do **not** prove AWS integration readiness.

Do not execute a real push without reviewing AWS cost, account, region, repository ownership, IAM permissions, Docker context and release policy. Do not run on untrusted pull requests.

## Prerequisites

- A dedicated test AWS account or approved sandbox with an ECR repository and `imageTagMutability=IMMUTABLE`.
- Trusted Jenkins agent with Docker Engine, AWS CLI v2, sufficient disk space and least-privilege short-lived credentials (prefer an assumed IAM role).
- A reviewed Dockerfile and checked-out trusted source revision. The CI image tag should be derived from the exact Git commit SHA and should not be reused.
- IAM: `sts:GetCallerIdentity`, `ecr:GetAuthorizationToken`, `ecr:DescribeRepositories`, `ecr:ListImages`, `ecr:DescribeImages`, `ecr:BatchCheckLayerAvailability`, `ecr:InitiateLayerUpload`, `ecr:UploadLayerPart`, `ecr:CompleteLayerUpload`, and `ecr:PutImage` as appropriate. `ecr:GetAuthorizationToken` typically requires resource `*`; scope repository actions to its ARN.
- Build and push must use the **same Jenkins agent Docker daemon**, or the image must be explicitly transferred via an approved artifact mechanism.

## Preflight (read-only)

```bash
aws sts get-caller-identity --query Account --output text
aws ecr describe-repositories --region eu-north-1 \
  --repository-names YOUR_TEST_REPOSITORY \
  --query 'repositories[0].imageTagMutability' --output text
docker version
aws --version
```

Never place AWS access keys or ECR login passwords in logs. Verify actual account and repository; do not assume values from the example.

## Controlled end-to-end execution

1. Verify GitHub CI on Java 21 and 25 is green and load the Shared Library in a **trusted test Jenkins job** at a reviewed commit SHA or tag.
2. Build with `ecrBuild(accountId: ..., region: ..., repository: ..., tag: ...)`. The return value is the fully qualified image name.
3. Invoke `ecrPush(...)` using the same coordinates, in the same Docker daemon, with approved AWS credentials.
4. Invoke `ecrImageDigest(...)` and confirm the returned reference ends in a registry manifest `sha256` digest.
5. Verify the exact tag/digest in ECR with `aws ecr describe-images` and compare the result with the step output. This is a manifest digest, **not** the local Docker image ID.
6. Archive non-sensitive evidence: Jenkins run URL, source commit, final image reference, registry digest, timestamps, test output and AWS region/account (non-secret metadata).
7. Review cleanup of temporary Docker credentials and remove the test image/tag through an approved cleanup process.

## Negative tests (sandbox only)

- Wrong expected AWS account must abort before login.
- Mutable repository must abort before login.
- Missing locally tagged image must abort before login.
- Existing tag must abort before publishing.
- Failed AWS login-password retrieval must propagate failure, never proceed to Docker login or push.
- Docker login/push failures must propagate failure and delete the temporary `DOCKER_CONFIG`.
- Invalid or absent registry manifest digest must prevent EKS deployment.

## Release gates and limitations

- ECR repository-wide immutability is checked, but registry policy and repository configuration still require independent operational review.
- Tag inspection is advisory; registry-enforced immutability is the real concurrency guard.
- The step intentionally uses a private temporary file for the short-lived ECR token to avoid `sh` pipelines with masked failure. `umask 077` restricts file permissions; the EXIT trap removes the temporary directory. Keep the build on an isolated agent with secure disk and workspace policies.
- The token is not included in shell command arguments or source. No `set -x`.
- Base images should be digest-pinned for reproducibility; additional SBOM, signature and vulnerability-scanning gates are future work.
- Real CI execution, IAM boundary tests, and Jenkins CPS/sandbox behavior are **unverified** until evidence from the controlled environment is recorded.
