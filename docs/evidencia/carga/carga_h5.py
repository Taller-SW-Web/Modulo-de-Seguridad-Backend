"""Carga FEAT-17 (H5): login, refresh, token de servicio e introspección contra el despliegue.

Reutiliza el enfoque de EP-02-T007 (H3, docs/evidencia/carga/carga.py) y lo amplía:
- número de cuentas configurable (5 para validar el flujo, 50 para la ronda final);
- calentamiento con cada cuenta (nunca con las de la demo), solo si se mide el login;
- escenarios de token de servicio (client_credentials) e introspección (éxito solo con activo=true);
- cierre de cada sesión con /auth/logout, fuera de las métricas;
- parada automática ante timeout, 423, 429 o 5xx, comprobada antes de cada petición.

Secretos y cuentas SOLO por entorno, nunca en git ni en la evidencia:
  G7_CLAVE_CARGA      contraseña común de las cuentas de carga
  G7_CUENTAS_CARGA    (opcional) correos de las cuentas, separados por comas. Si falta, se usan
                      carga.0 … carga.N-1@fixtures.g7.test según --cuentas
  G7_CLIENT_ID        cliente técnico de prueba
  G7_CLIENT_SECRET    secreto del cliente técnico
  G7_SCOPE_SERVICIO   scope pedido al emitir el token (por defecto tokens:introspeccion)

Ejemplos:
  # Ronda corta con las cuentas de G7_CUENTAS_CARGA, solo ingreso y renovación
  python carga_h5.py https://g7-auth-service.onrender.com/api/v1 --concurrencia 5 --escenarios login,refresh
  python carga_h5.py https://g7-auth-service.onrender.com/api/v1 --cuentas 5 --concurrencia 5 --por-hilo 1
  python carga_h5.py https://g7-auth-service.onrender.com/api/v1 --cuentas 50 --concurrencia 50 --por-hilo 10
"""
import argparse
import json
import os
import statistics
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor

UMBRALES_P95_MS = {"login": 800, "refresh": 300, "introspeccion": 200}
# Respuestas que detienen la ronda (acuerdo con el equipo): sin respuesta o tiempo agotado (0),
# bloqueo de cuenta (423), límite de peticiones (429) y servicio caído o saturado (500, 502, 503, 504).
# Seguir con ellas solo empeoraría el servicio.
ESTADOS_DE_PARADA = {0, 423, 429, 500, 502, 503, 504}
DOMINIO = "@fixtures.g7.test"


# CF-Ray de las respuestas de parada (429 o 5xx), para rastrearlas con Render/Cloudflare.
RAYOS = []


def peticion(base, ruta, cuerpo=None, formulario=None, bearer=None):
    """Devuelve (segundos, status, json|None). Nunca imprime tokens ni contraseñas."""
    cabeceras = {}
    if formulario is not None:
        datos = urllib.parse.urlencode(formulario).encode()
        cabeceras["Content-Type"] = "application/x-www-form-urlencoded"
    else:
        datos = json.dumps(cuerpo or {}).encode()
        cabeceras["Content-Type"] = "application/json"
    if bearer:
        cabeceras["Authorization"] = f"Bearer {bearer}"
    pet = urllib.request.Request(base + ruta, data=datos, headers=cabeceras, method="POST")
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(pet, timeout=30) as r:
            crudo = r.read()
            return time.perf_counter() - t0, r.status, json.loads(crudo) if crudo else None
    except urllib.error.HTTPError as e:
        if e.code == 429 or e.code >= 500:
            RAYOS.append(f"{ruta} {e.code} cf-ray={e.headers.get('CF-Ray', '—')} "
                         f"servidor={e.headers.get('Server', '—')} "
                         f"hora={time.strftime('%H:%M:%S')}")
        return time.perf_counter() - t0, e.code, None
    except Exception:
        return time.perf_counter() - t0, 0, None


def lista_cuentas(cantidad):
    """Correos de G7_CUENTAS_CARGA (separados por comas) o, si no está, carga.N@fixtures.g7.test."""
    crudo = os.environ.get("G7_CUENTAS_CARGA", "")
    correos = [c.strip() for c in crudo.split(",") if c.strip()]
    if correos:
        return correos
    return [f"carga.{i}{DOMINIO}" for i in range(cantidad)]


def enmascarar(c):
    """Para imprimir sin exponer el correo: primera letra y dominio."""
    usuario, _, dominio = c.partition("@")
    return f"{usuario[:1]}***@{dominio}"


def cerrar_sesion(base, refresco):
    """POST /auth/logout con el refresco vigente. No se mide; un fallo aquí no afecta la ronda."""
    if refresco:
        peticion(base, "/auth/logout", {"refreshToken": refresco})


def token_servicio(base):
    _, s, d = peticion(base, "/auth/token", formulario={
        "grant_type": "client_credentials",
        "client_id": os.environ["G7_CLIENT_ID"],
        "client_secret": os.environ["G7_CLIENT_SECRET"],
        "scope": os.environ.get("G7_SCOPE_SERVICIO", "tokens:introspeccion"),
    })
    return s, (d or {}).get("access_token") or (d or {}).get("accessToken")


def resumen(nombre, muestras, estados):
    if not muestras:
        print(f"{nombre:<13} sin muestras")
        return None
    ms = sorted(x * 1000 for x in muestras)
    p = lambda q: ms[min(len(ms) - 1, int(round(q * (len(ms) - 1))))]
    errores = sum(v for k, v in estados.items() if k not in (200, 201))
    print(f"{nombre:<13} n={len(ms)} p50={p(0.50):.0f} p95={p(0.95):.0f} p99={p(0.99):.0f} "
          f"media={statistics.mean(ms):.0f} max={ms[-1]:.0f} ms | errores={errores} estados={dict(estados)}")
    return p(0.95)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("base", help="URL base hasta /api/v1, sin barra final")
    ap.add_argument("--cuentas", type=int, default=5,
                    help="cuántas cuentas carga.N usar; se ignora si existe G7_CUENTAS_CARGA")
    ap.add_argument("--concurrencia", type=int, default=5)
    ap.add_argument("--por-hilo", type=int, default=1, help="iteraciones por hilo")
    ap.add_argument("--escenarios", default="login,refresh,token,introspeccion",
                    help="lista separada por comas; refresh e introspeccion dependen de login")
    a = ap.parse_args()
    base = a.base.rstrip("/")
    esc = {e.strip() for e in a.escenarios.split(",") if e.strip()}
    if {"refresh", "introspeccion"} & esc:
        esc.add("login")
    clave = os.environ["G7_CLAVE_CARGA"]
    cuentas = lista_cuentas(a.cuentas)
    correo = lambda i: cuentas[i % len(cuentas)]

    # Calentamiento: solo si se mide el login. Se prueba cada cuenta una vez y se cierra esa sesión; si
    # una falla, se aborta antes de repetir intentos con ella (una clave mala activaría el bloqueo).
    if "login" in esc:
        for n, c in enumerate(cuentas, 1):
            _, s, d = peticion(base, "/auth/login", {"correo": c, "contrasena": clave})
            if s != 200:
                sys.exit(f"El login de calentamiento de la cuenta {n} ({enmascarar(c)}) falló (HTTP {s}); "
                         "se aborta para no bloquear cuentas.")
            cerrar_sesion(base, (d or {}).get("refreshToken"))
    bearer_servicio = None
    if {"token", "introspeccion"} & esc:
        s, bearer_servicio = token_servicio(base)
        if not bearer_servicio:
            sys.exit(f"No se obtuvo token de servicio (HTTP {s}); revisa G7_CLIENT_ID/SECRET y el scope.")

    parada = threading.Event()
    motivo = []

    def vigilar(s, escenario):
        if s in ESTADOS_DE_PARADA and not parada.is_set():
            motivo.append(f"{escenario} respondió {'sin respuesta / timeout' if s == 0 else s}")
            parada.set()

    def trabajo(i):
        m = {k: [] for k in ("login", "refresh", "token", "introspeccion")}
        st = {k: Counter() for k in m}
        for _ in range(a.por_hilo):
            if parada.is_set():
                break
            refresco = None
            try:
                if "login" in esc:
                    t, s, d = peticion(base, "/auth/login", {"correo": correo(i), "contrasena": clave})
                    m["login"].append(t); st["login"][s] += 1
                    vigilar(s, "login")
                    if s != 200 or not d:
                        continue
                    refresco, acceso = d.get("refreshToken"), d.get("accessToken")
                    if "refresh" in esc and not parada.is_set():
                        t, s2, d2 = peticion(base, "/auth/refresh", {"refreshToken": refresco})
                        m["refresh"].append(t); st["refresh"][s2] += 1
                        vigilar(s2, "refresh")
                        if s2 == 200 and d2:
                            # La renovación rota el refresco: el que sigue vivo es el nuevo.
                            refresco = d2.get("refreshToken") or refresco
                            acceso = d2.get("accessToken") or acceso
                    if "introspeccion" in esc and not parada.is_set():
                        t, s3, d3 = peticion(base, "/auth/introspeccion", {"token": acceso},
                                             bearer=bearer_servicio)
                        m["introspeccion"].append(t)
                        # Siempre responde 200; solo cuenta como éxito si el token sale activo.
                        if s3 == 200 and not (d3 or {}).get("activo"):
                            st["introspeccion"]["200-inactivo"] += 1
                        else:
                            st["introspeccion"][s3] += 1
                        vigilar(s3, "introspeccion")
                if "token" in esc and not parada.is_set():
                    t0 = time.perf_counter()
                    s4, _ = token_servicio(base)
                    m["token"].append(time.perf_counter() - t0); st["token"][s4] += 1
                    vigilar(s4, "token")
            finally:
                # Cierra la sesión de la iteración (fuera de las métricas) para no dejar sesiones vivas.
                cerrar_sesion(base, refresco)
        return m, st

    inicio = time.perf_counter()
    with ThreadPoolExecutor(a.concurrencia) as ex:
        resultados = list(ex.map(trabajo, range(a.concurrencia)))
    duracion = time.perf_counter() - inicio

    if parada.is_set():
        print(f"RONDA DETENIDA: {motivo[0]}. Se reportan las muestras tomadas hasta ese momento; "
              "no subir al siguiente tramo.")
    if RAYOS:
        print("Respuestas de parada (primeras 5):")
        for r in RAYOS[:5]:
            print("  " + r)
    print(f"base={base} cuentas={len(cuentas)} concurrencia={a.concurrencia} por_hilo={a.por_hilo} "
          f"duración={duracion:.1f} s")
    for nombre in ("login", "refresh", "token", "introspeccion"):
        if nombre not in esc:
            continue
        muestras = [x for r in resultados for x in r[0][nombre]]
        estados = sum((r[1][nombre] for r in resultados), Counter())
        p95 = resumen(nombre, muestras, estados)
        if p95 is not None and nombre in UMBRALES_P95_MS:
            u = UMBRALES_P95_MS[nombre]
            print(f"{'':<13} objetivo p95<{u} ms: {'CUMPLE' if p95 < u else 'NO CUMPLE'}")


if __name__ == "__main__":
    main()
