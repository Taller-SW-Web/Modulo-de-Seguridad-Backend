#!/usr/bin/env python3
"""Pruebas con respuestas simuladas para el smoke test del despliegue."""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))

import smoke_test

BASE_URL = "https://example.test"
EMAIL = "cliente.a@fixtures.g7.test"
PASSWORD = "ClaveSecreta123!"
ACCESS_TOKEN = "access-token-de-prueba"
REFRESH_TOKEN = "refresh-token-de-prueba"


class FakeClock:
    def __init__(self) -> None:
        self.now = 0.0

    def __call__(self) -> float:
        return self.now

    def sleep(self, seconds: float) -> None:
        self.now += seconds


def jwks_body(*, extra: dict | None = None) -> str:
    key = {
        "kty": "RSA",
        "use": "sig",
        "alg": "RS256",
        "kid": "2026-10",
        "n": "modulo-publico",
        "e": "AQAB",
    }
    if extra:
        key.update(extra)
    return json.dumps({"keys": [key]})


def login_body(*, extra: dict | None = None) -> str:
    payload = {
        "accessToken": ACCESS_TOKEN,
        "refreshToken": REFRESH_TOKEN,
        "tokenType": "Bearer",
        "expiresIn": 900,
        "usuario": {
            "id": "00000000-0000-4000-8000-00000000f00a",
            "nombreCompleto": "Cliente A",
            "correo": EMAIL,
            "roles": ["CLIENTE"],
        },
    }
    if extra:
        payload.update(extra)
    return json.dumps(payload)


class HealthTest(unittest.TestCase):
    def test_health_up_ok(self) -> None:
        with patch.object(
            smoke_test,
            "_request",
            return_value=(200, json.dumps({"status": "UP"})),
        ):
            smoke_test.check_health(BASE_URL, 30, 1)

    def test_health_down_fails_after_timeout(self) -> None:
        clock = FakeClock()
        with patch.object(
            smoke_test,
            "_request",
            return_value=(200, json.dumps({"status": "DOWN"})),
        ) as request:
            with self.assertRaisesRegex(smoke_test.SmokeError, "Timeout"):
                smoke_test.check_health(
                    BASE_URL,
                    30,
                    10,
                    clock=clock,
                    sleep=clock.sleep,
                )

        self.assertGreaterEqual(request.call_count, 2)


class JwksTest(unittest.TestCase):
    def test_valid_public_jwks_ok(self) -> None:
        with patch.object(
            smoke_test,
            "_request",
            return_value=(200, jwks_body()),
        ):
            self.assertEqual(smoke_test.check_jwks(BASE_URL), 1)

    def test_private_material_fails(self) -> None:
        with patch.object(
            smoke_test,
            "_request",
            return_value=(200, jwks_body(extra={"d": "material-privado"})),
        ):
            with self.assertRaisesRegex(smoke_test.SmokeError, "privado"):
                smoke_test.check_jwks(BASE_URL)

    def test_empty_keys_fails(self) -> None:
        with patch.object(
            smoke_test,
            "_request",
            return_value=(200, json.dumps({"keys": []})),
        ):
            with self.assertRaisesRegex(smoke_test.SmokeError, "no contiene claves"):
                smoke_test.check_jwks(BASE_URL)

    def test_wrong_algorithm_fails(self) -> None:
        with patch.object(
            smoke_test,
            "_request",
            return_value=(200, jwks_body(extra={"alg": "RS512"})),
        ):
            with self.assertRaisesRegex(smoke_test.SmokeError, "RS256"):
                smoke_test.check_jwks(BASE_URL)

    def test_jwks_http_error_fails(self) -> None:
        with patch.object(
            smoke_test,
            "_request",
            return_value=(503, "{}"),
        ):
            with self.assertRaisesRegex(smoke_test.SmokeError, "HTTP 503"):
                smoke_test.check_jwks(BASE_URL)


class LoginTest(unittest.TestCase):
    def test_valid_login_ok(self) -> None:
        with patch.object(
            smoke_test,
            "_request",
            return_value=(200, login_body()),
        ) as request:
            self.assertEqual(
                smoke_test.check_login(BASE_URL, EMAIL, PASSWORD),
                900,
            )

        sent = request.call_args.args[0]
        self.assertEqual(sent.get_method(), "POST")
        self.assertIn(EMAIL.encode("utf-8"), sent.data)
        self.assertIn(PASSWORD.encode("utf-8"), sent.data)

    def test_mfa_required_fails(self) -> None:
        with patch.object(
            smoke_test,
            "_request",
            return_value=(
                200,
                json.dumps(
                    {
                        "mfaRequerido": True,
                        "challengeToken": "chg-1",
                        "expiraEn": 300,
                        "canal": "EMAIL",
                    }
                ),
            ),
        ):
            with self.assertRaisesRegex(smoke_test.SmokeError, "MFA"):
                smoke_test.check_login(BASE_URL, EMAIL, PASSWORD)

    def test_invalid_credentials_fail_without_leaking_password(self) -> None:
        with patch.object(
            smoke_test,
            "_request",
            return_value=(
                401,
                json.dumps(
                    {
                        "code": "CREDENCIALES_INVALIDAS",
                        "detail": "El correo o la contraseña no son correctos.",
                    }
                ),
            ),
        ):
            with self.assertRaises(smoke_test.SmokeError) as error:
                smoke_test.check_login(BASE_URL, EMAIL, PASSWORD)

        message = str(error.exception)
        self.assertIn("401", message)
        self.assertIn("CREDENCIALES_INVALIDAS", message)
        self.assertNotIn(PASSWORD, message)
        self.assertNotIn(EMAIL, message)

    def test_missing_access_token_fails(self) -> None:
        payload = json.loads(login_body())
        payload.pop("accessToken")
        with patch.object(
            smoke_test,
            "_request",
            return_value=(200, json.dumps(payload)),
        ):
            with self.assertRaisesRegex(smoke_test.SmokeError, "accessToken"):
                smoke_test.check_login(BASE_URL, EMAIL, PASSWORD)

    def test_wrong_account_fails(self) -> None:
        payload = json.loads(login_body())
        payload["usuario"]["correo"] = "otra@fixtures.g7.test"
        with patch.object(
            smoke_test,
            "_request",
            return_value=(200, json.dumps(payload)),
        ):
            with self.assertRaisesRegex(smoke_test.SmokeError, "correo distinto"):
                smoke_test.check_login(BASE_URL, EMAIL, PASSWORD)


if __name__ == "__main__":
    unittest.main()
