# API contracts — planned versus implemented

## Implemented (source code, not runtime verified)

`incrementVersion(version: '1.2.3', type: 'minor') -> '1.3.0'`.

This pure calculation never changes `pom.xml`, commits, tags, pushes or deploys. Supported input versions are `MAJOR.MINOR.PATCH` with optional `-SNAPSHOT`; version bump returns release version without SNAPSHOT.

## Version-management additions (source committed; tests pending)

- `MavenVersion.validate(version)` — strict MAJOR.MINOR.PATCH[-SNAPSHOT] parsing.
- `MavenVersion.next(current, type, snapshot)` — calculate next version without I/O.
- `ReleasePolicy.releaseBranch(branch)` — currently requires `main`.
- `ReleasePolicy.tag(version)` — `vX.Y.Z`, rejecting SNAPSHOT releases.
- `updateMavenVersion(expectedVersion: '1.2.3-SNAPSHOT', newVersion: '1.2.4-SNAPSHOT')` — requires a Jenkins workspace containing a root `pom.xml`, Maven on PATH, and access to pinned Maven plugins. Verifies version before and after change. Does not commit, tag, push or deploy; multi-module projects require additional safeguards and tests.

**Verification:** Unit tests and Jenkins runtime execution for these new functions are pending. Never interpret source commits as passing tests.

## Git release operations (code committed, Jenkins integration pending)

- `commitVersion(branch: 'main', version: '1.2.3', authorName: 'Release Bot', authorEmail: 'ci@example.invalid')`: checks checked-out branch, allows a change to root `pom.xml` only, creates a **local commit** and returns its SHA. Does not push. Caller must authenticate and authorize any later push separately.
- `createReleaseTag(branch: 'main', version: '1.2.3', authorName: 'Release Bot', authorEmail: 'ci@example.invalid')`: checks clean attached `main`, rejects duplicate local tags, creates annotated **local** `v1.2.3` tag. Does not push, and does not check the remote for an existing tag.
- `GitConfig` validates an allowlist of version files (currently root `pom.xml`) and Git inputs. `ReleasePolicy` rejects `-SNAPSHOT` tags.
- Jenkins source/runtime tests of the `vars/` wrappers, remote tag checks, and approval gates are **not yet implemented or verified**. Neither function should be run from an untrusted pull request or in production until those safeguards and integration tests exist.

## Maven CI steps (source committed; runtime validation pending)

- `mavenVerify()`: requires root `pom.xml`; runs `mvn -B -ntp verify` and publishes Surefire/Failsafe XML reports using `junit` even if Maven exits unsuccessfully. Missing test reports fail verification.
- `mavenBuild()`: requires root `pom.xml`; runs `mvn -B -ntp package` and fingerprints/archives the root JAR. `skipTests: true` is supported as an explicit option, intended only after a verified test stage.
- Maven is assumed available on the selected Jenkins agent. Jenkins environment integration has not yet been validated. Multi-module build layouts need additional handling.
- Both functions have mocked test cases; actual execution and test results are **pending**.

## Docker image foundation (source committed, tests pending)

- `new DockerImageRef(repository, tag)`: checks a restricted image reference format and rejects obvious floating aliases such as `latest`. **Tag validation does not guarantee immutability**; registry-enforced immutable tags and image digest deployment remain separate requirements.
- `dockerBuild(repository: 'team/app', tag: 'git-abc123')`: checks root `Dockerfile` and executes `docker build --pull` with a fixed root build context; no registry authentication, pushing or deployment.
- `dockerInspect(repository: 'team/app', tag: 'git-abc123')`: inspects a **local image ID**, requiring `sha256:` plus 64 lowercase hex characters. This ID is **not** an ECR/registry manifest digest.
- This initial version assumes Docker CLI availability and a trusted checked-out workspace. No BuildKit cache/pinning strategy, remote registry inspection, or Jenkins-on-agent validation yet.

## Docker Hub publication (source committed, runtime verification pending)

- `dockerPush(repository: 'team/app', tag: 'git-a1b2c3', credentialsId: 'dockerhub-credentials')`: uses Jenkins username/password credentials, environment expansion in a single-quoted shell script, `docker login --password-stdin`, a temporary `DOCKER_CONFIG`, and an EXIT trap to delete that temporary directory. Accepts Docker Hub `namespace/repository` only; AWS ECR authentication will be a separate implementation. A successful push does **not** guarantee immutable tags unless enforced by registry settings.
- `dockerImageDigest(repository: 'team/app', tag: 'git-a1b2c3')`: queries `docker buildx imagetools inspect` for a manifest digest and returns `repository@sha256:...`. Requires installed Docker buildx and existing registry authentication for private images. **The credentials in `dockerPush` do not persist into a later call.** For private images, a future scoped authentication wrapper or an authenticated ECR-specific implementation is required.
- Neither step is verified against real Jenkins, Docker Hub, or AWS; mocked Groovy tests are included. Docker Hub token credentials should be scoped to the smallest appropriate permission and repository. Do not echo tokens or enable shell tracing.

## AWS ECR (source committed; agent/runtime tests pending)

- `EcrImageRef(accountId, region, repository, tag)`: validates commercial AWS ECR coordinates and returns an image reference or a checked digest reference. Does not validate account ownership or repository existence.
- `ecrPush(accountId: '123456789012', region: 'eu-north-1', repository: 'team/app', tag: 'git-abcdef')`: obtains an ECR login token from the Jenkins agent's AWS identity; Docker authentication uses `--password-stdin` and temporary `DOCKER_CONFIG`, cleaned up on exit. **The target full ECR image must already be built/tagged locally**. The step does not create or retag an image.
- `ecrImageDigest(...)`: executes `aws ecr describe-images` for the target image tag, validates its `sha256` digest and returns a digest-pinned reference. Requires `ecr:DescribeImages` authorization.
- Scope AWS identity to the desired repository and account, preferably an agent IAM role / short-lived credentials. Additional ECR permissions normally include `ecr:GetAuthorizationToken`, `ecr:BatchCheckLayerAvailability`, `ecr:InitiateLayerUpload`, `ecr:UploadLayerPart`, `ecr:CompleteLayerUpload`, `ecr:PutImage`. AWS account, region, and repository must be the intended deployment destination.
- The hardened ecrPush step now verifies the AWS caller account via STS, requires ECR repository imageTagMutability=IMMUTABLE, checks the exact local Docker image exists, and refuses a previously used tag before authenticating/pushing. ECR server-side immutability is required to prevent publication races. The current step still does **not** automatically build/retag images or guarantee consistency with the image built in an earlier stage. End-to-end integration verification is pending.

### ECR publishing integration readiness

Before attempting a real publish, use a disposable test repository with immutable image tags, scoped short-lived AWS credentials, AWS CLI v2 and Docker on the Jenkins agent. Verify expected-account enforcement, repository policy, local image preflight, existing tag refusal, successful push, temporary credentials cleanup and digest resolution. Set Jenkins concurrency controls for release pipelines as an additional guard, not as a replacement for registry immutability. After pushing, deploy only a digest-pinned reference. Never use untrusted pull-request code with cloud publishing credentials.

The new `EcrPreflightTest` checks preflight script content, not live AWS error responses; these tests require local verification.

## Remaining planned functions

| Public step | Planned contract |
| --- | --- |

| `commitVersion` | Stage allowlisted files only, validate clean/dirty state, create local commit |
| `createReleaseTag` | Verify approved branch/release version and create signed or annotated tag under policy |

| `dockerBuild` | Validate image ref and build context; immutable tag strategy |
| `ecrPush` | Credential-scoped AWS auth; push and resolve digest |
| `eksDeploy` | Deploy immutable image digest; assert rollout health |
| `terraformPlan` | fmt/validate and persist a reviewable plan securely |
| `terraformApply` | Explicit approval then apply the exact saved plan |
| `ansibleDeploy` | Syntax/lint/preflight and play execution with secure inventory/credentials |
| `securityScan` | Pinned scanners, explicit severity/failure policy, archived reports |

Add only when tests, documentation, and a real Capstone use case exist.
