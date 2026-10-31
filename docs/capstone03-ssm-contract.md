# Capstone 03 — SSM Deployment Contract

## Purpose

Deploy an immutable ECR image to an existing EC2 instance using
AWS Systems Manager Run Command.

## Shared Library step

Name: ssmDeploy

Required inputs:
- accountId: expected 12-digit AWS account
- region: AWS deployment region
- instanceId: target EC2 instance
- imageRef: ECR image pinned to sha256 digest
- appVersion: deployed application source revision
- composeFile: workspace-relative Docker Compose file
- deployScript: workspace-relative deployment script

## Preconditions

- Jenkins agent has AWS CLI v2, jq and base64.
- Jenkins AWS identity is explicitly configured.
- AWS account identity matches accountId.
- Target EC2 instance is managed by SSM and online.
- Deployment files exist in the Jenkins workspace.
- EC2 instance role has ECR image-pull permissions.

## Execution requirements

1. Validate all input values before invoking AWS.
2. Verify the expected AWS account.
3. Verify that the target instance is available through SSM.
4. Transfer the deployment files without SSH.
5. Send the deployment command with a bounded timeout.
6. Wait for remote execution to finish.
7. Retrieve remote stdout, stderr and exit code.
8. Fail the Jenkins build unless remote execution succeeded.
9. Confirm application health and deployed image identity.
10. Return deployment evidence, including the SSM command ID.

## Security constraints

- No static AWS credentials in source control.
- No administrator credentials in Jenkins pipelines.
- No SSH access required.
- No unpinned container images.
- No secrets written to build logs.
- No Terraform apply or destroy inside the deployment helper.
- No successful deployment status before remote verification.

## Testing

Unit tests must cover:
- Valid parameters
- Invalid AWS account, region and instance ID
- Invalid image digest
- Unsafe workspace paths
- AWS account mismatch
- Offline SSM target
- SSM submission failure
- Remote execution timeout
- Remote execution failure
- Successful deployment

## Current implementation scope

- Remote deployment directory: `/opt/capstone03`.
- Application deployment uses Docker Compose.
- The Jenkins Shared Library wrapper returns the SSM command ID only
  after successful remote execution.
- SSM parameters have a conservative project-level limit of 16 KiB.
- The deployment implementation is currently specific to Capstone 03.
- General-purpose remote directory configuration, concurrent deployment
  protection, and interruption recovery are future hardening tasks.
- Offline mocks do not substitute for live IAM and SSM verification.
