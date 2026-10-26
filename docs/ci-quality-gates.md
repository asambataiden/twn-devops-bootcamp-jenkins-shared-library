# CI quality gates

## Purpose

Validate Jenkins Shared Library Groovy source and mocked Jenkins pipeline tests on every pull request targeting main, without deploying infrastructure or exposing cloud credentials.

## Implemented workflow

- `.github/workflows/ci.yml`: JDK 21 (Temurin), Gradle 9.0.0 wrapper, `./gradlew clean test --no-daemon --console=plain`.
- GitHub Actions execution status is **not yet verified**. Successful local runs do not prove GitHub runners will pass.
- Read-only GitHub token; no cloud credentials; failure test report artifacts.
- No automatic releases, Git pushes, Docker image pushes, or EKS changes.

## Required GitHub repository settings (manual)

After the workflow's first green run, configure a branch ruleset for `main`:

1. Require a pull request before merging.
2. Require the `Gradle tests (Java 21)` check from this workflow.
3. Prevent force pushes and branch deletion.
4. Require at least one reviewer where supported and appropriate; prevent bypass for administrators where supported.
5. Keep GitHub Actions permissions at least privilege.

Do not mark the required check until its exact name is confirmed in GitHub Actions. Avoid setting rules that lock you out of an initial bootstrap.

## Next hardening tasks

- Pin third-party GitHub Actions to reviewed immutable commit SHAs rather than mutable major version tags. The initial workflow uses version tags.
- Obtain and add the official Gradle distribution SHA-256 to `gradle-wrapper.properties` and review committed wrapper JAR provenance.
- Add formatting and lint checks compatible with Groovy/Jenkins CPS.
- Introduce supply-chain and dependency vulnerability scanning with explicit remediation policies.
- Add real Jenkins integration tests before production adoption.
- Investigate Maven/Jenkins pipeline parity and ECR publishing in a disposable AWS test environment.

## Local validation

```bash
git pull --ff-only origin feat/library-foundation
./gradlew clean test --no-daemon --console=plain
git status --short
```

Check GitHub **Actions → Shared Library CI** for a successful run on the feature branch. No claim of green GitHub CI is made before seeing that run.
