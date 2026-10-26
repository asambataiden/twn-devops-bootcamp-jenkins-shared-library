# API contracts — planned versus implemented

## Implemented (source code, not runtime verified)

`incrementVersion(version: '1.2.3', type: 'minor') -> '1.3.0'`.

This pure calculation never changes `pom.xml`, commits, tags, pushes or deploys. Supported input versions are `MAJOR.MINOR.PATCH` with optional `-SNAPSHOT`; version bump returns release version without SNAPSHOT.

## Planned functions (NOT included yet)

| Public step | Planned contract |
| --- | --- |
| `updateMavenVersion` | Read expected current version, update Maven root version safely and verify results |
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
