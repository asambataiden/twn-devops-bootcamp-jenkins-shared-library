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
