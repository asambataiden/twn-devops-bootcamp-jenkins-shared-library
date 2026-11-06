# TWN DevOps Bootcamp — Jenkins Shared Library

**Branch status:** `main` contains the v0.1.0 foundation scaffold. The separately maintained [`feat/capstone03-ecr-ssm-integration`](https://github.com/asambataiden/twn-devops-bootcamp-jenkins-shared-library/tree/feat/capstone03-ecr-ssm-integration) branch contains implemented Maven, Docker, ECR, EKS and SSM pipeline steps; it was used at pinned revision `c514371` in the verified Capstone 03 Jenkins release workflow. Do not assume those APIs are available on `main`.

Reusable CI/CD building blocks for the six TechWorld with Nana Capstones. Original training repositories remain intact. This new library will evolve incrementally with tests and reviewed changes.

## Foundation implemented

- `SemanticVersion`: pure major/minor/patch parser and bumping logic (no Jenkins dependency).
- `InputValidator`: basic validation of version bump and supported Maven coordinates.
- `incrementVersion(Map)`: Jenkins global step that calculates a new version; **does not change files or push to Git**.
- Gradle-based unit test scaffolding.

**Scope:** The following describes the `main` foundation branch only. Additional APIs exist on the integration feature branch; review that branch's source and the capstone-specific evidence before making implementation or validation claims. See `docs/roadmap.md` and `docs/api-contracts.md` for the foundation plan.

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

## Public portfolio and tested integration

- [Dedicated DevOps Capstone Portfolio](https://github.com/asambataiden/twn-devops-capstone-portfolio)
- [Capstone 03 implementation and verification](https://github.com/asambataiden/twn-capstone-03-complete-cicd-terraform/blob/main/README.md)
- [Pinned integration revision `c514371`](https://github.com/asambataiden/twn-devops-bootcamp-jenkins-shared-library/tree/c5143712fd0e762cf3c9e19114c894dfae214efe)

## Existing references

- [Earlier Shared Library](https://github.com/asambataiden/jenkins-shared-library)
- [Module 12 Shared Library](https://github.com/asambataiden/twn-devops-bootcamp-12-terraform-jenkins-shared-library)
- Historical bootcamp module portfolio (private; not a reviewer-accessible link)

See `docs/architecture.md`, `docs/roadmap.md`, and `docs/api-contracts.md`.
