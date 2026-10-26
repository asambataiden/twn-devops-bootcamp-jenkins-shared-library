# Shared Library architecture

- `vars/`: CPS-aware Jenkins entrypoints for declarative and scripted pipelines.
- `src/com/asambataiden/devops/`: testable configuration, versioning, and validation logic. Avoid direct Jenkins step calls in pure domain types.
- `resources/`: future static templates accessed by `libraryResource`, if appropriate.
- `test/groovy/`: domain tests; add Jenkins Pipeline Unit tests and Jenkins runtime integration checks before consuming deploy steps.
- `examples/`: consumer pipelines, labeled as examples until run and verified.

## Security boundaries

The library can execute privileged build/deploy operations when approved by Jenkins administrators. Never load unreviewed pull-request library code with production secrets. Restrict agents and credentials, pin an approved commit/tag, and keep Terraform apply, Git push, and deployment approval separate from read-only checks.

## Version policy

The library is independently versioned. Application SemVer, Docker image digests, Terraform plans, and Jenkins build numbers are different identities; do not equate them.
