"""Carga EP-02-T007: login y refresh con 50 concurrentes contra el backend local (PG local,
g7_runtime). Mide latencias de extremo a extremo HTTP y reporta p50/p95/p99 y errores."""
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

BASE = sys.argv[1]
CONCURRENCIA = int(sys.argv[2]) if len(sys.argv) > 2 else 50
POR_HILO = int(sys.argv[3]) if len(sys.argv) > 3 else 10
CUENTAS = [f"carga.{i}" for i in range(50)]
CLAVE = os.environ["G7_CLAVE_CARGA"]  # contraseña de las cuentas de carga: solo por entorno, nunca en git


def post(ruta, cuerpo):
    pet = urllib.request.Request(BASE + ruta, data=json.dumps(cuerpo).encode(),
                                 headers={"Content-Type": "application/json"})
    t0 = time.perf_counter()
    try:
        with urllib.request.urlopen(pet, timeout=30) as r:
            datos = json.loads(r.read())
            return time.perf_counter() - t0, r.status, datos
    except urllib.error.HTTPError as e:
        return time.perf_counter() - t0, e.code, None
    except Exception:
        return time.perf_counter() - t0, 0, None


def trabajo(i):
    logins, refrescos, errores = [], [], 0
    for j in range(POR_HILO):
        cuenta = CUENTAS[i % len(CUENTAS)] + "@fixtures.g7.test"
        t, s, d = post("/auth/login", {"correo": cuenta, "contrasena": CLAVE})
        logins.append(t)
        if s != 200:
            errores += 1
            continue
        t, s, _ = post("/auth/refresh", {"refreshToken": d["refreshToken"]})
        refrescos.append(t)
        errores += s != 200
    return logins, refrescos, errores


def resumen(nombre, muestras):
    ms = sorted(x * 1000 for x in muestras)
    p = lambda q: ms[min(len(ms) - 1, int(round(q * (len(ms) - 1))))]
    print(f"{nombre}: n={len(ms)} p50={p(0.50):.0f} ms p95={p(0.95):.0f} ms p99={p(0.99):.0f} ms "
          f"media={statistics.mean(ms):.0f} ms max={ms[-1]:.0f} ms")
    return p(0.95)


post("/auth/login", {"correo": "cliente.a@fixtures.g7.test", "contrasena": CLAVE})  # calentamiento
inicio = time.perf_counter()
with ThreadPoolExecutor(CONCURRENCIA) as ex:
    resultados = list(ex.map(trabajo, range(CONCURRENCIA)))
duracion = time.perf_counter() - inicio
logins = [t for r in resultados for t in r[0]]
refrescos = [t for r in resultados for t in r[1]]
errores = sum(r[2] for r in resultados)
print(f"concurrencia={CONCURRENCIA} peticiones={len(logins) + len(refrescos)} duración={duracion:.1f} s errores={errores}")
p95_login = resumen("login  ", logins)
p95_refresh = resumen("refresh", refrescos)
print(f"objetivo login p95<800 ms: {'CUMPLE' if p95_login < 800 else 'NO CUMPLE'}; "
      f"refresh p95<300 ms: {'CUMPLE' if p95_refresh < 300 else 'NO CUMPLE'}")
