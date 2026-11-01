#!/usr/bin/env python3
"""Smoke test del entorno desplegado: salud, JWKS y login de fixtures."""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.request
from typing import Any, Callable

DEFAULT_BASE_URL = "https://g7-auth-service.onrender.com"
PRIVATE_JWK_FIELDS = frozenset({"d", "p", "q", "dp", "dq", "qi", "oth"})
USER_AGENT = "g7-ci-smoke/1.0"


class SmokeError(RuntimeError):
    """Error controlado del smoke test."""


def _env(name: str) -> str:
    value = os.environ.get(name)
    if value is None or value.strip() == "":
        raise SmokeError(f"Falta la variable o secreto {name}.")
    return value


def _int_env(name: str, default: int) -> int:
    raw = os.environ.get(name)
    if raw is None or raw.strip() == "":
        raw = str(default)
    try:
        value = int(raw)
    except ValueError as exc:
        raise SmokeError(f"{name} debe ser un entero positivo.") from exc
    if value <= 0:
        raise SmokeError(f"{name} debe ser un entero positivo.")
    return value


def _url(base_url: str, path: str) -> str:
    return f"{base_url.rstrip('/')}{path}"


def _request(request: urllib.request.Request, timeout: int) -> tuple[int, str]:
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", errors="replace")
    except (urllib.error.URLError, TimeoutError) as exc:
        raise SmokeError("No se pudo conectar con el servicio desplegado.") from exc


def _problem_code(body: str) -> str | None:
    """Extrae solo el código público de un Problem; nunca el detalle."""

    try:
        payload = json.loads(body)
    except ValueError:
        return None
    if not isinstance(payload, dict):
        return None
    code = payload.get("code")
    return code if isinstance(code, str) and code else None


def check_health(
    base_url: str,
    timeout: int,
    interval: int,
    *,
    request_timeout: int = 30,
    clock: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
) -> None:
    """Espera a que la versión desplegada responda salud UP."""

    url = _url(base_url, "/actuator/health")
    deadline = clock() + timeout
    last_error = "sin respuesta"
    while True:
        try:
            request = urllib.request.Request(
                url,
                headers={
                    "Accept": "application/json",
                    "Cache-Control": "no-cache",
                    "User-Agent": USER_AGENT,
                },
            )
            status, body = _request(request, request_timeout)
            if status == 200:
                try:
                    payload = json.loads(body)
                except ValueError:
                    payload = {}
                if isinstance(payload, dict) and payload.get("status") == "UP":
                    return
                estado = payload.get("status", "sin estado") if isinstance(payload, dict) else "sin estado"
                last_error = f"health respondió {estado}"
            else:
                last_error = f"health respondió HTTP {status}"
        except SmokeError as exc:
            last_error = str(exc)
        if clock() >= deadline:
            raise SmokeError(f"Timeout esperando salud UP: {last_error}.")
        sleep(interval)


def validate_jwks(payload: Any) -> int:
    """Valida que el JWKS publique solo claves RSA RS256 de verificación."""

    if not isinstance(payload, dict):
        raise SmokeError("JWKS no devolvió un objeto JSON.")
    keys = payload.get("keys")
    if not isinstance(keys, list) or not keys:
        raise SmokeError("JWKS no contiene claves.")
    for index, key in enumerate(keys, start=1):
        if not isinstance(key, dict):
            raise SmokeError(f"JWKS: la clave {index} no es un objeto JSON.")
        if key.get("kty") != "RSA":
            raise SmokeError(f"JWKS: la clave {index} no es RSA.")
        if key.get("alg") != "RS256":
            raise SmokeError(f"JWKS: la clave {index} no usa RS256.")
        if key.get("use") != "sig":
            raise SmokeError(f"JWKS: la clave {index} no está declarada para firma.")
        for field in ("kid", "n", "e"):
            value = key.get(field)
            if not isinstance(value, str) or not value.strip():
                raise SmokeError(f"JWKS: la clave {index} no tiene {field}.")
        if PRIVATE_JWK_FIELDS.intersection(key):
            raise SmokeError(f"JWKS: la clave {index} contiene material privado.")
    return len(keys)


def check_jwks(base_url: str, request_timeout: int = 30) -> int:
    """Comprueba que el JWKS sea público, válido y no exponga la clave privada."""

    url = _url(base_url, "/api/v1/auth/.well-known/jwks.json")
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/json",
            "Cache-Control": "no-cache",
            "User-Agent": USER_AGENT,
        },
    )
    status, body = _request(request, request_timeout)
    if status != 200:
        raise SmokeError(f"JWKS respondió HTTP {status}.")
    try:
        payload = json.loads(body)
    except ValueError as exc:
        raise SmokeError("JWKS devolvió JSON inválido.") from exc
    return validate_jwks(payload)


def validate_login(payload: Any, expected_email: str) -> int:
    """Valida la respuesta de login sin conservar ni mostrar los tokens."""

    if not isinstance(payload, dict):
        raise SmokeError("Login no devolvió un objeto JSON.")
    if payload.get("mfaRequerido") is True:
        raise SmokeError(
            "La cuenta de smoke requiere MFA; usar una cuenta CLIENTE sin MFA."
        )
    access_token = payload.get("accessToken")
    refresh_token = payload.get("refreshToken")
    if not isinstance(access_token, str) or not access_token.strip():
        raise SmokeError("Login no devolvió accessToken.")
    if not isinstance(refresh_token, str) or not refresh_token.strip():
        raise SmokeError("Login no devolvió refreshToken.")
    if payload.get("tokenType") != "Bearer":
        raise SmokeError("Login no devolvió tokenType Bearer.")
    expires_in = payload.get("expiresIn")
    if not isinstance(expires_in, int) or isinstance(expires_in, bool) or expires_in <= 0:
        raise SmokeError("Login no devolvió un expiresIn positivo.")
    usuario = payload.get("usuario")
    if not isinstance(usuario, dict):
        raise SmokeError("Login no devolvió el usuario.")
    user_id = usuario.get("id")
    if not isinstance(user_id, str) or not user_id.strip():
        raise SmokeError("Login no devolvió el id del usuario.")
    correo = usuario.get("correo")
    if not isinstance(correo, str) or correo.casefold() != expected_email.casefold():
        raise SmokeError("Login devolvió un correo distinto al de la cuenta de smoke.")
    roles = usuario.get("roles")
    if not isinstance(roles, list):
        raise SmokeError("Login no devolvió la lista de roles.")
    return expires_in


def check_login(
    base_url: str,
    email: str,
    password: str,
    request_timeout: int = 30,
) -> int:
    """Ejecuta un login real y devuelve el expiresIn sin exponer secretos."""

    body = json.dumps({"correo": email, "contrasena": password}).encode("utf-8")
    request = urllib.request.Request(
        _url(base_url, "/api/v1/auth/login"),
        data=body,
        method="POST",
        headers={
            "Accept": "application/json",
            "Content-Type": "application/json",
            "Cache-Control": "no-cache",
            "User-Agent": USER_AGENT,
        },
    )
    status, response_body = _request(request, request_timeout)
    if status != 200:
        code = _problem_code(response_body)
        suffix = f" ({code})" if code else ""
        raise SmokeError(f"Login de smoke rechazado con HTTP {status}{suffix}.")
    try:
        payload = json.loads(response_body)
    except ValueError as exc:
        raise SmokeError("Login devolvió JSON inválido.") from exc
    return validate_login(payload, email)


def main() -> None:
    base_url = os.environ.get("SMOKE_BASE_URL", DEFAULT_BASE_URL).strip() or DEFAULT_BASE_URL
    email = _env("SMOKE_LOGIN_EMAIL")
    password = _env("SMOKE_LOGIN_PASSWORD")
    request_timeout = _int_env("SMOKE_REQUEST_TIMEOUT_SECONDS", 30)
    health_timeout = _int_env("SMOKE_HEALTH_TIMEOUT_SECONDS", 120)
    poll_interval = _int_env("SMOKE_POLL_INTERVAL_SECONDS", 10)

    print(f"Smoke: comprobando salud en {base_url}/actuator/health ...")
    check_health(
        base_url,
        health_timeout,
        poll_interval,
        request_timeout=request_timeout,
    )
    print("Smoke: salud UP.")

    print("Smoke: comprobando JWKS ...")
    key_count = check_jwks(base_url, request_timeout)
    print(f"Smoke: JWKS válido ({key_count} clave(s) RSA RS256 pública(s)).")

    print("Smoke: probando login de la cuenta fixture ...")
    expires_in = check_login(base_url, email, password, request_timeout)
    print(
        "Smoke: login OK "
        f"(tokenType=Bearer, expiresIn={expires_in}s). No se registran tokens."
    )


if __name__ == "__main__":
    try:
        main()
    except SmokeError as exc:
        print(f"::error::{exc}")
        sys.exit(1)
