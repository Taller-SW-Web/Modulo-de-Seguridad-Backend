#!/usr/bin/env python3
"""Dispara y verifica un deploy de Render para el SHA que ejecuta el CI."""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any, Callable

RENDER_API_BASE = "https://api.render.com/v1"
DEFAULT_HEALTH_URL = "https://g7-auth-service.onrender.com/actuator/health"
LIVE_STATUS = "live"
FAILED_STATUSES = frozenset(
    {
        "build_failed",
        "update_failed",
        "canceled",
        "pre_deploy_failed",
        "deactivated",
    }
)


class DeployError(RuntimeError):
    """Error controlado del despliegue."""


def _env(name: str, default: str | None = None) -> str:
    value = os.environ.get(name, default)
    if value is None or value.strip() == "":
        raise DeployError(f"Falta la variable o secreto {name}.")
    return value


def _int_env(name: str, default: int) -> int:
    raw = os.environ.get(name, str(default))
    try:
        value = int(raw)
    except ValueError as exc:
        raise DeployError(f"{name} debe ser un entero positivo.") from exc
    if value <= 0:
        raise DeployError(f"{name} debe ser un entero positivo.")
    return value


def build_deploy_url(hook_url: str, sha: str) -> str:
    """Añade ref=<sha> conservando la autenticación y los demás parámetros."""

    parts = urllib.parse.urlsplit(hook_url)
    query = [
        (key, value)
        for key, value in urllib.parse.parse_qsl(parts.query, keep_blank_values=True)
        if key != "ref"
    ]
    query.append(("ref", sha))
    return urllib.parse.urlunsplit(
        (
            parts.scheme,
            parts.netloc,
            parts.path,
            urllib.parse.urlencode(query),
            parts.fragment,
        )
    )


def derive_service_id(hook_url: str, explicit: str | None = None) -> str:
    """Obtiene el service ID del path del hook o del valor explícito."""

    if explicit:
        return explicit
    path = urllib.parse.urlsplit(hook_url).path
    for part in reversed([segment for segment in path.split("/") if segment]):
        if part.startswith("srv-"):
            return part
    raise DeployError(
        "No se pudo derivar el service ID de Render desde el deploy hook; "
        "define RENDER_SERVICE_ID."
    )


def _request(request: urllib.request.Request, timeout: int) -> tuple[int, str]:
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", errors="replace")
    except urllib.error.URLError as exc:
        raise DeployError("No se pudo conectar con el servicio externo.") from exc


def trigger_deploy(hook_url: str, sha: str, timeout: int) -> str:
    """Dispara el hook fijando el commit y devuelve el deploy ID verificable."""

    request = urllib.request.Request(
        build_deploy_url(hook_url, sha),
        data=b"",
        method="POST",
        headers={"Accept": "application/json"},
    )
    status, body = _request(request, timeout)
    if status == 202:
        raise DeployError(
            "Render respondió 202: ya hay otro deploy en curso y no devolvió un id "
            "verificable. Reintentar cuando no haya un deploy solapado."
        )
    if status != 200:
        raise DeployError(f"Render rechazó el deploy hook con HTTP {status}.")
    try:
        payload = json.loads(body)
        deploy_id = payload["deploy"]["id"]
    except (ValueError, KeyError, TypeError) as exc:
        raise DeployError("Render no devolvió un id de deploy verificable.") from exc
    if not isinstance(deploy_id, str) or not deploy_id:
        raise DeployError("Render no devolvió un id de deploy verificable.")
    return deploy_id


def fetch_deploy(
    service_id: str,
    deploy_id: str,
    api_key: str,
    timeout: int,
) -> dict[str, Any]:
    """Consulta el deploy por ID en la API de Render."""

    url = (
        f"{RENDER_API_BASE}/services/"
        f"{urllib.parse.quote(service_id, safe='')}/deploys/"
        f"{urllib.parse.quote(deploy_id, safe='')}"
    )
    request = urllib.request.Request(
        url,
        headers={"Accept": "application/json", "Authorization": f"Bearer {api_key}"},
    )
    status, body = _request(request, timeout)
    if status != 200:
        raise DeployError(f"Render API respondió HTTP {status} al consultar el deploy.")
    try:
        payload = json.loads(body)
    except ValueError as exc:
        raise DeployError("Render API devolvió JSON inválido.") from exc
    if not isinstance(payload, dict):
        raise DeployError("Render API devolvió una respuesta inesperada.")
    return payload


def _commit_id(deploy: dict[str, Any]) -> str:
    commit = deploy.get("commit")
    actual = commit.get("id") if isinstance(commit, dict) else None
    return actual if isinstance(actual, str) and actual else "desconocido"


def _commit_matches(deploy: dict[str, Any], expected_sha: str) -> bool:
    actual = _commit_id(deploy)
    if actual == "desconocido":
        return False
    return actual == expected_sha or (
        len(actual) >= 7 and expected_sha.startswith(actual)
    )


def wait_for_deploy(
    service_id: str,
    deploy_id: str,
    expected_sha: str,
    api_key: str,
    timeout: int,
    interval: int,
    *,
    request_timeout: int = 30,
    clock: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
) -> dict[str, Any]:
    """Espera a que el deploy esperado esté live; falla si no coincide o vence."""

    deadline = clock() + timeout
    last_status: str | None = None
    while True:
        deploy = fetch_deploy(service_id, deploy_id, api_key, request_timeout)
        status = deploy.get("status")
        if status == LIVE_STATUS:
            if not _commit_matches(deploy, expected_sha):
                raise DeployError(
                    f"El deploy {deploy_id} está live pero corresponde al commit "
                    f"{_commit_id(deploy)}, no a {expected_sha}."
                )
            return deploy
        if status in FAILED_STATUSES:
            raise DeployError(f"El deploy {deploy_id} terminó en estado {status}.")
        if clock() >= deadline:
            raise DeployError(
                f"Timeout esperando el deploy {deploy_id}; "
                f"último estado: {last_status or status or 'desconocido'}."
            )
        if status != last_status:
            print(f"Deploy {deploy_id}: estado {status or 'desconocido'}; esperando...")
            last_status = status
        sleep(interval)


def check_health(
    health_url: str,
    timeout: int,
    interval: int,
    *,
    request_timeout: int = 30,
    clock: Callable[[], float] = time.monotonic,
    sleep: Callable[[float], None] = time.sleep,
) -> None:
    """Comprueba que la versión ya publicada responde salud UP."""

    deadline = clock() + timeout
    last_error = "sin respuesta"
    while True:
        try:
            request = urllib.request.Request(
                health_url,
                headers={"Accept": "application/json"},
            )
            status, body = _request(request, request_timeout)
            if status == 200:
                try:
                    payload = json.loads(body)
                except ValueError:
                    payload = {}
                if isinstance(payload, dict) and payload.get("status") == "UP":
                    return
                last_error = f"health respondió {payload.get('status', 'sin estado')}"
            else:
                last_error = f"health respondió HTTP {status}"
        except DeployError as exc:
            last_error = str(exc)
        if clock() >= deadline:
            raise DeployError(f"Timeout esperando salud UP: {last_error}.")
        sleep(interval)


def main() -> None:
    expected_sha = _env("GITHUB_SHA")
    hook_url = _env("RENDER_DEPLOY_HOOK_URL")
    api_key = _env("RENDER_API_KEY")
    service_id = derive_service_id(
        hook_url,
        os.environ.get("RENDER_SERVICE_ID") or None,
    )
    health_url = os.environ.get("RENDER_HEALTH_URL") or DEFAULT_HEALTH_URL
    request_timeout = _int_env("RENDER_REQUEST_TIMEOUT_SECONDS", 30)
    deploy_timeout = _int_env("RENDER_DEPLOY_TIMEOUT_SECONDS", 1800)
    health_timeout = _int_env("RENDER_HEALTH_TIMEOUT_SECONDS", 120)
    poll_interval = _int_env("RENDER_POLL_INTERVAL_SECONDS", 10)

    print(f"Disparando deploy de {expected_sha} en Render...")
    deploy_id = trigger_deploy(hook_url, expected_sha, request_timeout)
    print(f"Deploy aceptado: {deploy_id}. Esperando estado live...")
    wait_for_deploy(
        service_id,
        deploy_id,
        expected_sha,
        api_key,
        deploy_timeout,
        poll_interval,
        request_timeout=request_timeout,
    )
    print(f"Deploy {deploy_id} live para {expected_sha}. Comprobando salud...")
    check_health(
        health_url,
        health_timeout,
        poll_interval,
        request_timeout=request_timeout,
    )
    print(f"Deploy {deploy_id} verificado: {expected_sha} live y salud UP.")


if __name__ == "__main__":
    try:
        main()
    except DeployError as exc:
        print(f"::error::{exc}")
        sys.exit(1)
