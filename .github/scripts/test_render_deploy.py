#!/usr/bin/env python3
"""Pruebas con respuestas simuladas para la verificación del deploy de Render."""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

import render_deploy


EXPECTED_SHA = "a" * 40
OTHER_SHA = "b" * 40
HOOK_URL = "https://api.render.com/deploy/srv-test?key=secret"


class FakeClock:
    def __init__(self) -> None:
        self.now = 0.0

    def __call__(self) -> float:
        return self.now

    def sleep(self, seconds: float) -> None:
        self.now += seconds


def fake_request(deploy_status: str, commit_id: str, health_status: str = "UP"):
    calls: list[str] = []

    def _fake(request, timeout):
        url = request.full_url
        calls.append(url)
        if url.startswith("https://api.render.com/deploy/"):
            return 200, json.dumps({"deploy": {"id": "dep-1"}})
        if "/services/srv-test/deploys/dep-1" in url:
            return 200, json.dumps(
                {
                    "id": "dep-1",
                    "status": deploy_status,
                    "commit": {"id": commit_id},
                }
            )
        if url == "https://example.test/actuator/health":
            return 200, json.dumps({"status": health_status})
        raise AssertionError(f"URL inesperada: {url}")

    return _fake, calls


class RenderDeployTest(unittest.TestCase):
    def test_build_deploy_url_adds_ref_and_preserves_authentication(self) -> None:
        url = render_deploy.build_deploy_url(HOOK_URL, EXPECTED_SHA)
        self.assertIn("key=secret", url)
        self.assertIn(f"ref={EXPECTED_SHA}", url)

    def test_derive_service_id_from_hook_url(self) -> None:
        self.assertEqual(render_deploy.derive_service_id(HOOK_URL), "srv-test")

    def test_previous_healthy_version_does_not_approve(self) -> None:
        request, calls = fake_request("live", OTHER_SHA)

        with patch.object(render_deploy, "_request", side_effect=request):
            deploy_id = render_deploy.trigger_deploy(HOOK_URL, EXPECTED_SHA, 5)
            with self.assertRaisesRegex(render_deploy.DeployError, "corresponde al commit"):
                render_deploy.wait_for_deploy(
                    "srv-test",
                    deploy_id,
                    EXPECTED_SHA,
                    "api-key",
                    30,
                    1,
                )

        self.assertFalse(
            any("actuator/health" in url for url in calls),
            "No debe consultar health si el deploy live no es del SHA esperado.",
        )

    def test_failed_deploy_fails(self) -> None:
        request, _ = fake_request("build_failed", EXPECTED_SHA)

        with patch.object(render_deploy, "_request", side_effect=request):
            deploy_id = render_deploy.trigger_deploy(HOOK_URL, EXPECTED_SHA, 5)
            with self.assertRaisesRegex(render_deploy.DeployError, "build_failed"):
                render_deploy.wait_for_deploy(
                    "srv-test",
                    deploy_id,
                    EXPECTED_SHA,
                    "api-key",
                    30,
                    1,
                )

    def test_timeout_fails(self) -> None:
        request, _ = fake_request("build_in_progress", EXPECTED_SHA)
        clock = FakeClock()

        with patch.object(render_deploy, "_request", side_effect=request):
            with self.assertRaisesRegex(render_deploy.DeployError, "Timeout"):
                render_deploy.wait_for_deploy(
                    "srv-test",
                    "dep-1",
                    EXPECTED_SHA,
                    "api-key",
                    30,
                    10,
                    clock=clock,
                    sleep=clock.sleep,
                )

    def test_expected_version_live_and_healthy_approves(self) -> None:
        request, calls = fake_request("live", EXPECTED_SHA)

        with patch.object(render_deploy, "_request", side_effect=request):
            deploy_id = render_deploy.trigger_deploy(HOOK_URL, EXPECTED_SHA, 5)
            render_deploy.wait_for_deploy(
                "srv-test",
                deploy_id,
                EXPECTED_SHA,
                "api-key",
                30,
                1,
            )
            render_deploy.check_health(
                "https://example.test/actuator/health",
                30,
                1,
            )

        self.assertTrue(any("actuator/health" in url for url in calls))

    def test_hook_202_without_deploy_id_fails(self) -> None:
        def request(_request, _timeout):
            return 202, "{}"

        with patch.object(render_deploy, "_request", side_effect=request):
            with self.assertRaisesRegex(render_deploy.DeployError, "202"):
                render_deploy.trigger_deploy(HOOK_URL, EXPECTED_SHA, 5)


if __name__ == "__main__":
    unittest.main()
