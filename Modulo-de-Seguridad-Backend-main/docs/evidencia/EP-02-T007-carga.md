# EP-02-T007 — Prueba de carga de login y refresh (p95, 50 concurrentes)

Fecha: 15-oct-2026. Ejecutó: Sergio Osorio.
Objetivos del plan: login p95 < 800 ms y refresh p95 < 300 ms con 50 concurrentes.

## Condiciones

- Backend `develop` (jar), Java 21, en el mismo PC que PostgreSQL 16 local y el
  generador de carga (20 hilos lógicos). Usuario de base `g7_runtime` con los grants de la
  ventana T002; **pool Hikari de 5** (valor de diseño).
- 50 cuentas CLIENTE distintas (`carga.N@fixtures.g7.test`), creadas solo en la base
  local de prueba. Cada hilo: 10 × (login + refresh). 1000 peticiones por ronda.
- Script: `docs/evidencia/carga/carga.py` (la contraseña llega por `G7_CLAVE_CARGA`).
- Argon2id con los parámetros de producción (m = 19 MiB, t = 2, p = 1).

## Hallazgos que la carga destapó (y su corrección)

| # | Síntoma | Causa | Corrección |
|---|---|---|---|
| 1 | 441/587 errores, p50 de login 10 s, `Connection is not available, request timed out` | La auditoría no crítica abría una transacción nueva en `afterCommit`, en el mismo hilo, cuando la conexión del productor aún no se había liberado: cada login retenía 1 conexión y pedía otra → interbloqueo del pool de 5 hasta el timeout de 10 s | `TareasTrasCommitSpring` ejecuta la tarea en un ejecutor aparte (2 hilos, cola acotada) después del commit |
| 2 | Conexiones retenidas mientras se calculaba Argon2 | `CredencialesTransaccional.preverificar` y `validarNueva` abrían una transacción de solo lectura que cubría el hash (lento a propósito) | Propagación `SUPPORTS`: la lectura usa la transacción corta del repositorio y el hash corre sin conexión |

Con una sola petición el login ya tardaba 63 ms: el problema era de concurrencia, no de lentitud.

## Resultados (después de las correcciones)

| Ronda | Errores | Login p50 / p95 / p99 | Refresh p50 / p95 / p99 | Objetivos |
|---|---|---|---|---|
| 1 (JVM en frío) | 0 | 489 / 820 / 960 ms | 158 / 339 / 435 ms | No (por poco) |
| 2 | 0 | 431 / **693** / 864 ms | 138 / **269** / 392 ms | **Cumple** |
| 3 | 0 | 416 / **673** / 792 ms | 130 / **283** / 359 ms | **Cumple** |

Con pool de 10 los números son casi iguales: el límite es la CPU que comparten Argon2,
PostgreSQL y el generador en el mismo equipo, no el pool. Antes de la demo conviene
calentar el servicio con unas peticiones.

## Limitaciones

Medición local, no en la nube: en el despliegue el backend tendrá su propia CPU y
latencia de red hacia Supabase; repetir la ronda allí cuando exista el entorno.
