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

## Remaining planned functions

| Public step | Planned contract |
| --- | --- |

| `commitVersion` | Stage allowlisted files only, validate clean/dirty state, create local commit |
| `createReleaseTag` | Verify approved branch/release version and create signed or annotated tag under policy |
| `mavenBuild` | Verify Maven tool and run controlled goals; publish unit-test results |
| `dockerBuild` | Validate image ref and build context; immutable tag strategy |
| `ecrPush` | Credential-scoped AWS auth; push and resolve digest |
| `eksDeploy` | Deploy immutable image digest; assert rollout health |
| `terraformPlan` | fmt/validate and persist a reviewable plan securely |
| `terraformApply` | Explicit approval then apply the exact saved plan |
| `ansibleDeploy` | Syntax/lint/preflight and play execution with secure inventory/credentials |
| `securityScan` | Pinned scanners, explicit severity/failure policy, archived reports |

Add only when tests, documentation, and a real Capstone use case exist.
