# TWN DevOps Bootcamp — Jenkins Shared Library

**Status:** v0.1.0 foundation scaffold; no Jenkins integration has been executed.

Reusable CI/CD building blocks for the six TechWorld with Nana Capstones. Original training repositories remain intact. This new library will evolve incrementally with tests and reviewed changes.

## Foundation implemented

- `SemanticVersion`: pure major/minor/patch parser and bumping logic (no Jenkins dependency).
- `InputValidator`: basic validation of version bump and supported Maven coordinates.
- `incrementVersion(Map)`: Jenkins global step that calculates a new version; **does not change files or push to Git**.
- Gradle-based unit test scaffolding.

All other APIs are **planned**, not implemented. Refer to `docs/roadmap.md` and `docs/api-contracts.md`.

## Quick start

```bash
./gradlew test   # after Gradle wrapper is generated and committed
# Alternatively, with Gradle installed:
gradle test
```

To add a wrapper on a trusted workstation: `gradle wrapper --gradle-version <reviewed-compatible-version>`, review resulting files, then commit them. Run builds with a supported JDK/Gradle/Groovy combination; the CI compatibility matrix remains to be verified against your Jenkins controller.

## Example Jenkins use

```groovy
@Library('twn-devops-lib@<approved-commit-or-tag>') _
pipeline {
  agent any
  stages {
    stage('Calculate Version') {
      steps {
        script {
          echo incrementVersion(version: '1.2.3', type: 'minor') // 1.3.0
        }
      }
    }
  }
}
```

Replace `<approved-commit-or-tag>` with a real reviewed revision after configuring a Jenkins Global Pipeline Library with that name. Avoid marking arbitrary external libraries as trusted.

## Engineering rules

- No secret values in source, shell args, or logs; bind Jenkins credentials in the narrowest possible scope.
- No infrastructure changes, Git push, or release tags on library import.
- Release and deployment operations must have explicit approval and scoped permissions.
- Jenkins orchestration belongs in `vars/`; testable domain logic in `src/`.
- Verify Jenkins CPS and sandbox behavior with integration tests before releasing.
- Prefer immutable image digests and approved library tags.

## Existing references

- [Earlier Shared Library](https://github.com/asambataiden/jenkins-shared-library)
- [Module 12 Shared Library](https://github.com/asambataiden/twn-devops-bootcamp-12-terraform-jenkins-shared-library)
- [Bootcamp portfolio](https://github.com/asambataiden/twn-devops-bootcamp-portfolio)

See `docs/architecture.md`, `docs/roadmap.md`, and `docs/api-contracts.md`.
