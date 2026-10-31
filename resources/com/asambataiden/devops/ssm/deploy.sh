#!/usr/bin/env bash
set -euo pipefail

export AWS_PAGER=""

: "${EXPECTED_ACCOUNT:?Required}"
: "${AWS_REGION:?Required}"
: "${TARGET_INSTANCE:?Required}"
: "${IMAGE_REF:?Required}"
: "${APP_VERSION:?Required}"
: "${COMPOSE_SOURCE:?Required}"
: "${DEPLOY_SOURCE:?Required}"

log() {
    printf '[ssm-deploy] %s\n' "$*" >&2
}

fail() {
    log "ERROR: $*"
    exit 1
}

for tool in aws jq base64; do
    command -v "$tool" >/dev/null 2>&1 ||
        fail "Missing executable: $tool"
done

actual_account="$(
    aws sts get-caller-identity \
        --query Account \
        --output text
)"

[[ "$actual_account" == "$EXPECTED_ACCOUNT" ]] ||
    fail "AWS account does not match expected account"

ping_status="$(
    aws ssm describe-instance-information \
        --region "$AWS_REGION" \
        --filters "Key=InstanceIds,Values=$TARGET_INSTANCE" \
        --query 'InstanceInformationList[0].PingStatus' \
        --output text
)"

[[ "$ping_status" == "Online" ]] ||
    fail "EC2 target is not online in Systems Manager"

log "AWS identity and SSM target verified"

compose_b64="$(base64 < "$COMPOSE_SOURCE" | tr -d '\n')"
deploy_b64="$(base64 < "$DEPLOY_SOURCE" | tr -d '\n')"

parameters="$(
    jq -n \
        --arg compose "$compose_b64" \
        --arg deploy "$deploy_b64" \
        --arg image "$IMAGE_REF" \
        --arg version "$APP_VERSION" \
        --arg region "$AWS_REGION" '
    {
        executionTimeout: ["480"],
        commands: [
            "set -e",
            "install -d -m 0700 /opt/capstone03",
            ("printf %s " + ($compose | @sh) +
              " | base64 -d > /opt/capstone03/compose.yaml"),
            ("printf %s " + ($deploy | @sh) +
              " | base64 -d > /opt/capstone03/deploy.sh"),
            "chmod 0600 /opt/capstone03/compose.yaml",
            "chmod 0700 /opt/capstone03/deploy.sh",
            ("export IMAGE_REF=" + ($image | @sh)),
            ("export APP_VERSION=" + ($version | @sh)),
            ("export AWS_REGION=" + ($region | @sh)),
            "bash -e /opt/capstone03/deploy.sh"
        ]
    }'
)"

# Conservative project-level payload guard (16 KiB).
# Larger artifacts should use an integrity-verified S3 transfer.
payload_bytes="$(LC_ALL=C printf '%s' "$parameters" | wc -c | tr -d '[:space:]')"

if ((payload_bytes > 16384)); then
    fail "SSM deployment payload exceeds 16384-byte project limit"
fi

command_id="$(
    aws ssm send-command \
        --region "$AWS_REGION" \
        --instance-ids "$TARGET_INSTANCE" \
        --document-name AWS-RunShellScript \
        --comment "Jenkins digest-pinned application deployment" \
        --timeout-seconds 600 \
        --parameters "$parameters" \
        --query 'Command.CommandId' \
        --output text
)"

[[ "$command_id" =~ ^[a-f0-9-]{36}$ ]] ||
    fail "SSM returned an invalid command ID"

log "SSM command submitted: $command_id"

status="Pending"
response_code="-1"
invocation=""
consecutive_errors=0

for ((attempt=1; attempt<=120; attempt++)); do
    if invocation="$(
        aws ssm get-command-invocation \
            --region "$AWS_REGION" \
            --command-id "$command_id" \
            --instance-id "$TARGET_INSTANCE" \
            --output json 2>/dev/null
    )"; then
        consecutive_errors=0

        status="$(jq -er '.Status | strings' <<< "$invocation")" ||
            fail "Invalid SSM invocation status"

        response_code="$(jq -er '.ResponseCode | numbers' <<< "$invocation")" ||
            fail "Invalid SSM response code"

        case "$status" in
            Success|Failed|Cancelled|TimedOut|DeliveryTimedOut|ExecutionTimedOut|Undeliverable|Terminated|InvalidPlatform|AccessDenied)
                break
                ;;
            Pending|InProgress|Delayed|Cancelling)
                ;;
            *)
                fail "Unexpected SSM status: $status"
                ;;
        esac
    else
        consecutive_errors=$((consecutive_errors + 1))
        log "Unable to retrieve SSM invocation (attempt $consecutive_errors)"

        if ((consecutive_errors >= 5)); then
            fail "SSM polling failed after repeated AWS API errors"
        fi
    fi

    if ((attempt < 120)); then
        sleep 5
    fi
done

log "Final SSM status: $status"
log "Remote exit code: $response_code"

if [[ "$status" != "Success" || "$response_code" != "0" ]]; then
    log "Remote command did not complete successfully"

    if [[ -n "${invocation:-}" ]]; then
        jq '{
            Status,
            ResponseCode,
            StandardOutputContent,
            StandardErrorContent
        }' <<< "$invocation" >&2
    fi

    fail "Deployment verification failed"
fi

log "Deployment command succeeded"

# Only this value is returned to the Jenkins wrapper.
printf '%s\n' "$command_id"
