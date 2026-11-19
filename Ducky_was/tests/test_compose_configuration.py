"""Render deployment configuration with synthetic inputs; never start containers."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


WAS = Path(__file__).resolve().parents[1]
CLOUD = {
    "DOCUMENT_MODE": "cloud",
    "DOCUMENT_REGION": "ap-southeast-2",
    "DOCUMENT_BUCKET": "synthetic-config-test",
    "DOCUMENT_COMPLETION_QUEUE_URL": "https://example.invalid/completion",
    "RAG_PROCESSOR_REVISION": "synthetic-release-revision",
    "DOCUMENT_AWS_CREDENTIALS": "instance-role",
    "DOCUMENT_COMPLETION_POLL_ENABLED": "false",
}


class DeploymentComposeTest(unittest.TestCase):
    def render(self, settings):
        env = os.environ.copy()
        for key in CLOUD:
            env.pop(key, None)
        env.update({
            "DB_USERNAME": "synthetic-user",
            "DB_PASSWORD": "synthetic-password",
            "JWT_SECRET": "synthetic-jwt-secret",
            "OPENAI_API_KEY": "synthetic-not-an-api-key",
            "CORS_ALLOWED_ORIGINS": "https://example.invalid",
        })
        env.update(settings)
        with tempfile.TemporaryDirectory() as directory:
            empty_env = Path(directory) / "empty.env"
            empty_env.write_text("", encoding="utf-8")
            result = subprocess.run([
                "docker", "compose", "--project-directory", str(WAS),
                "--env-file", str(empty_env), "-f", str(WAS / "compose.yaml"),
                "config", "--format", "json",
            ], env=env, capture_output=True, text=True, encoding="utf-8", check=True)
        return json.loads(result.stdout)["services"]["backend"]["environment"]

    def test_cloud_configuration_reaches_backend(self):
        environment = self.render(CLOUD)
        self.assertEqual({key: environment.get(key) for key in CLOUD}, CLOUD)

    def test_default_configuration_keeps_cloud_disabled(self):
        environment = self.render({})
        self.assertEqual(environment["DOCUMENT_MODE"], "local")
        self.assertEqual(environment["DOCUMENT_REGION"], "ap-southeast-2")
        self.assertEqual(environment["DOCUMENT_COMPLETION_POLL_ENABLED"], "true")
        for key in ("DOCUMENT_BUCKET", "DOCUMENT_COMPLETION_QUEUE_URL",
                    "RAG_PROCESSOR_REVISION", "DOCUMENT_AWS_CREDENTIALS"):
            self.assertEqual(environment[key], "")


if __name__ == "__main__":
    unittest.main()
