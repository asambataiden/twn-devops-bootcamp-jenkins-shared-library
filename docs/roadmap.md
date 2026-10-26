# Incremental delivery roadmap

## Milestone 0.1 — foundation (this scaffold)

- [x] Version parsing and major/minor/patch calculations
- [x] Basic unit tests and Gradle project
- [x] Jenkins `vars/incrementVersion.groovy` entry point (source only)
- [ ] Verify Gradle tests in developer environment or CI
- [ ] Verify Jenkins loading and Groovy CPS compatibility

## Milestone 0.2 — Maven & release

- [ ] `updateMavenVersion`, `mavenBuild`, `mavenVerify`
- [ ] Commit only approved files; separate commit from push
- [ ] Approval-gated Git release tagging; avoid recursive CI triggers
- [ ] Unit and Jenkins Pipeline Unit tests

## Milestone 0.3 — container pipeline

- [ ] `dockerBuild`, `dockerPush`, `securityScan`, `ecrPush`
- [ ] Scoped credentials and ephemeral Docker config
- [ ] Digest resolution and scanning policy

## Milestone 0.4 — deployment

- [ ] `eksDeploy`, Kubernetes readiness validation and rollback
- [ ] Terraform plan artifact + approval-gated apply
- [ ] `ansibleDeploy` including syntax and idempotency validation

Never implement a placeholder which silently reports a successful deployment.
