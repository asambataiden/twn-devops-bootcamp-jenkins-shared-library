#!/usr/bin/env python3
"""Offline contract tests for the Jenkins SSM deployment resource."""

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEPLOY_SCRIPT = ROOT / "resources/com/asambataiden/devops/ssm/deploy.sh"
COMMAND_ID = "123e4567-e89b-12d3-a456-426614174000"

FAKE_AWS = r"""#!/usr/bin/env python3
import json
import os
import sys

args = sys.argv[1:]
scenario = os.environ.get("SSM_TEST_SCENARIO", "success")

if args[:2] == ["sts", "get-caller-identity"]:
    print("123456789012" if scenario == "account_mismatch"
          else "591132357918")
elif args[:2] == ["ssm", "describe-instance-information"]:
    print("Offline" if scenario == "offline" else "Online")
elif args[:2] == ["ssm", "send-command"]:
    print("123e4567-e89b-12d3-a456-426614174000")
elif args[:2] == ["ssm", "get-command-invocation"]:
    if scenario == "polling_api_failure":
        print("Simulated AWS API error", file=sys.stderr)
        sys.exit(1)
    if scenario == "timeout":
        status, code = "InProgress", -1
    elif scenario == "remote_failure":
        status, code = "Failed", 1
    else:
        status, code = "Success", 0
    print(json.dumps({
        "Status": status,
        "ResponseCode": code,
        "StandardOutputContent": "",
        "StandardErrorContent": "Mock deployment error"
            if scenario == "remote_failure" else ""
    }))
else:
    print("Unexpected AWS CLI operation", file=sys.stderr)
    sys.exit(2)
"""


@unittest.skipUnless(shutil.which("jq"), "jq is required")
class SsmDeployIntegrationTest(unittest.TestCase):

    def run_scenario(self, scenario):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bin_dir = root / "bin"
            bin_dir.mkdir()

            aws = bin_dir / "aws"
            aws.write_text(FAKE_AWS)
            aws.chmod(0o755)

            # Avoid real waiting during the timeout test.
            sleep = bin_dir / "sleep"
            sleep.write_text("#!/bin/sh\nexit 0\n")
            sleep.chmod(0o755)

            compose = root / "compose.yaml"
            compose.write_text("services: {}\n")

            deploy = root / "deploy.sh"
            deploy.write_text(
                "#!/usr/bin/env bash\n" + ("# large payload\n" * 2500)
                if scenario == "oversized_payload"
                else "#!/usr/bin/env bash\nexit 0\n"
            )

            env = os.environ.copy()
            env.update({
                "PATH": str(bin_dir) + os.pathsep + env["PATH"],
                "SSM_TEST_SCENARIO": scenario,
                "EXPECTED_ACCOUNT": "591132357918",
                "AWS_REGION": "eu-north-1",
                "TARGET_INSTANCE": "i-058aa03bdbb014833",
                "IMAGE_REF": (
                    "591132357918.dkr.ecr.eu-north-1.amazonaws.com/"
                    "twn-capstone-03-app@sha256:" + "a" * 64
                ),
                "APP_VERSION": "bee47c0a7414",
                "COMPOSE_SOURCE": str(compose),
                "DEPLOY_SOURCE": str(deploy),
            })

            return subprocess.run(
                ["bash", str(DEPLOY_SCRIPT)],
                env=env,
                text=True,
                capture_output=True,
                timeout=20,
                check=False,
            )

    def test_success_returns_only_command_id(self):
        result = self.run_scenario("success")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(result.stdout.strip(), COMMAND_ID)

    def test_rejects_account_mismatch(self):
        result = self.run_scenario("account_mismatch")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("AWS account does not match", result.stderr)
        self.assertEqual(result.stdout, "")

    def test_rejects_offline_instance(self):
        result = self.run_scenario("offline")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("not online", result.stderr)

    def test_rejects_remote_failure(self):
        result = self.run_scenario("remote_failure")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Deployment verification failed", result.stderr)
        self.assertIn("Mock deployment error", result.stderr)

    def test_fails_after_repeated_aws_api_errors(self):
        result = self.run_scenario("polling_api_failure")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn(
            "SSM polling failed after repeated AWS API errors",
            result.stderr,
        )
        self.assertEqual(result.stdout, "")

    def test_rejects_oversized_payload(self):
        result = self.run_scenario("oversized_payload")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("payload exceeds 16384-byte", result.stderr)
        self.assertEqual(result.stdout, "")

    def test_fails_on_polling_timeout(self):
        result = self.run_scenario("timeout")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Deployment verification failed", result.stderr)


if __name__ == "__main__":
    unittest.main(verbosity=2)
