# FEAT-17 — Pruebas de rendimiento contra el despliegue (H5)

Ejecutó: Valery Cristin Gutierrez Bendezu · Revisó: _(Eva)_
Plan: [FEAT-17-plan.md](FEAT-17-plan.md) · Script: [`carga_h5.py`](../carga/carga_h5.py)
Nivel de evidencia: **propia desplegada**.
Estado: **R1 (ronda corta) y ronda de referencia completadas el 23-nov. R2: el tramo de 10 concurrentes se
ejecutó a las 22:01 y se detuvo solo por un 429 de la verificación anti-bots de Cloudflare de Render, no de
nuestro servicio. Los tramos de 25 y 50 quedan pendientes por ese límite; el token de servicio y la
introspección no se midieron** (acuerdo con David y Alejandro, 22:14).
**Último tramo completado: R1 (5 concurrentes).**

## Condiciones

| Elemento | Valor |
|---|---|
| Fecha y hora | 23-nov-2026, entre 14:25 y 14:26 (America/Lima) |
| Backend | `https://g7-auth-service.onrender.com/api/v1` |
| Versión desplegada (SHA) | `05ad567d16d1323c1b13711aeb83f6ee5f041254` (último confirmado en Render por David, 23-nov) |
| Generador | Equipo de Valery (Windows, Python 3.14), red doméstica, fuera de Render |
| Cuentas | 5 cuentas CLIENTE creadas desde la SPA con alias de un correo de Valery, verificadas y sin MFA (acuerdo con David); pasadas por `G7_CUENTAS_CARGA`. No son cuentas de la demo |
| Escenarios | Ingreso y renovación (`--escenarios login,refresh`). Token de servicio e introspección esperan la confirmación del scope `tokens:introspeccion` del cliente técnico |

Antes de cada ronda, el script hace un login de calentamiento con cada cuenta (no se mide) y aborta
si alguno falla, para no activar el bloqueo automático. Ninguno falló.

## R1 — Ronda corta (5 cuentas, 5 concurrentes, 1 iteración)

Tiempos en ms.

| Escenario | n | p50 | p95 | p99 | media | máx. | Errores | Estados |
|---|---|---|---|---|---|---|---|---|
| Login | 5 | 4769 | 5661 | 5661 | 4765 | 5661 | 0 | 200 ×5 |
| Refresh | 5 | 695 | 1408 | 1408 | 842 | 1408 | 0 | 200 ×5 |

Duración de la ronda: 6,3 s. **Flujo validado:** las cinco cuentas ingresan y renuevan sin errores y
ninguna se bloquea.

## Ronda de referencia (1 concurrente, 5 iteraciones)

Sin concurrencia (un solo hilo, con la primera cuenta en las cinco iteraciones), para separar la
latencia propia de una petición del efecto de la concurrencia.

| Escenario | n | p50 | p95 | p99 | media | máx. | Errores | Estados |
|---|---|---|---|---|---|---|---|---|
| Login | 5 | 581 | 1666 | 1666 | 794 | 1666 | 0 | 200 ×5 |
| Refresh | 5 | 232 | 239 | 239 | 230 | 239 | 0 | 200 ×5 |

Duración de la ronda: 5,1 s.

## R2 — Ronda escalonada (10 → 25 → 50)

Acordada con David: 10, 25 y 50 concurrentes en una ventana sin demo ni pruebas de otros grupos, subiendo
de tramo solo si el anterior termina sin timeouts, 429 ni bloqueos. Valery corre ingreso y renovación; David,
después y por separado, token de servicio e introspección con el secreto del cliente técnico.

### Tramo de 10 concurrentes — detenido

23-nov-2026, 22:01 (America/Lima). 5 cuentas (2 hilos por cuenta), 10 iteraciones por hilo, `login,refresh`.

| Escenario | n | p50 | p95 | p99 | media | máx. | Errores | Estados |
|---|---|---|---|---|---|---|---|---|
| Login | 56 | 176 | 8348 | 8354 | 1470 | 8360 | 54 | 200 ×2, 502 ×38, 503 ×8, 429 ×8 |
| Refresh | 2 | 4075 | 4590 | 4590 | 4332 | 4590 | 2 | 502 ×2 |

Duración: 9,2 s. El script detuvo la ronda al recibir el primer 429 en `POST /api/v1/auth/login`
(«RONDA DETENIDA: login respondió 429»); de las 100 iteraciones previstas se hicieron 56.

**Origen del 429.** Minutos después, desde la misma conexión, `GET /actuator/health` mostró la página de
verificación anti-bots de Cloudflare («Verificación de seguridad en curso», HTTP 429) delante del servicio:
el corte vino de la protección perimetral de Render y no del backend de G7. David comprobó desde otra
conexión que el servicio respondía `200 UP` a las 22:12, así que el bloqueo afectó solo al origen de la carga.
**CF-Ray:** no registrado. El script de ese momento no guardaba cabeceras y, al revisar, la verificación ya
había pasado. Desde esta versión el script imprime el CF-Ray de cada 429 o 5xx.

**502 y 503.** Llegaron antes del 429 y son respuestas del borde de Render (el servicio no contestó a
tiempo o estaba saturado). Con los datos del script no se puede separar si los produjo el backend
saturado o la propia protección. Esta versión del script también se detiene ante cualquier 5xx.

### Tramos de 25 y 50 — pendientes

No se ejecutaron. Quedan pendientes por el límite de la protección perimetral de Render: el tramo de 10 ya
fue cortado por la verificación anti-bots de Cloudflare (429), así que subir la concurrencia desde el mismo
origen solo repetiría el corte. Además, solo hay 5 cuentas de carga.

**Token de servicio e introspección: no medidos.** La concesión temporal de `tokens:introspeccion` para
`modulo-prueba-h5` se cerró el 23-nov al terminar la ventana (David quitó el scope y Alejandro la revocó en la
matriz). David había validado antes una sesión de prueba con `activo: true`.

## Comparación con los umbrales

| Escenario | Umbral p95 | Sin concurrencia | 5 concurrentes | 10 concurrentes (detenido) | 50 concurrentes |
|---|---|---|---|---|---|
| Login | < 800 ms | 1666 ms (p50 581) · **No cumple**, por un valor aislado | 5661 ms · **No cumple** | 8348 ms; 54 de 56 con error · **No cumple** | _(pendiente)_ |
| Refresh | < 300 ms | 239 ms · **Cumple** | 1408 ms · **No cumple** | 2 muestras, ambas 502 · no medible | _(pendiente)_ |
| Introspección | < 200 ms | — | — | — | no medido (concesión revocada el 23-nov) |
| Token de servicio | sin umbral | — | — | — | no medido (concesión revocada el 23-nov) |

Los umbrales están definidos para 50 concurrentes: **pendiente de medir con 50**. Con 5 concurrentes
los p95 de login y refresh ya superan los valores objetivo; se declara.

## Causas probables

1. **CPU del plan de Render compartida por los logins concurrentes.** Un login solo tarda ~0,6 s
   (p50 581 ms), dentro del umbral; con 5 a la vez la mediana sube a 4,8 s (≈8×). El ingreso verifica
   la contraseña con Argon2id, con los parámetros de producción de [EP-02-T007](../EP-02-T007-carga.md)
   (m = 19 MiB, t = 2, p = 1), costoso en CPU y memoria a propósito: varias verificaciones simultáneas
   se encolan en la poca CPU del servicio. En H3, en local con CPU de escritorio, el mismo flujo cumplía
   con 50 concurrentes.
2. **La renovación se resiente por la misma CPU.** Sola cumple (p95 239 ms), pero en R1 corre a la par
   de los logins y sube a 1,4 s. No usa Argon2: su demora viene de esperar CPU, no de su propio costo.
3. **Valor aislado en la referencia.** El máximo de login sin concurrencia (1666 ms) es un único valor
   entre cinco, y la mediana es 581 ms. Puede deberse a la red o al arranque del servicio.

4. **Protección perimetral de Render (Cloudflare) ante la ráfaga de un solo origen.** Con 10 concurrentes
   desde una misma conexión, el borde de Render devolvió 502/503 y luego una verificación anti-bots (429),
   que cortó la ronda. Sin cambiar ese límite (o sin generar la carga desde varios orígenes) no se puede
   medir el despliegue actual con 25 o 50 concurrentes.

Por verificar con las métricas de Render: uso de CPU y memoria del servicio a las 22:01 del 23-nov, para
saber si los 502/503 vinieron del backend saturado o del borde.

## Comparación con H3 (local)

| | H3 local, 50 concurrentes (15-oct) | H5 Render, 5 concurrentes (23-nov) |
|---|---|---|
| Login p95 | 673–820 ms | 5661 ms |
| Refresh p95 | 269–339 ms | 1408 ms |

En H3, backend, base y generador compartían una CPU de escritorio. En Render el backend tiene la CPU
de su plan y se suma la latencia de red hacia Supabase y desde el generador.

## Limitaciones

- Muestras pequeñas (5 por escenario en R1 y la referencia): los percentiles son orientativos.
- El tramo de 10 se cortó a las 56 iteraciones y con casi todo en error: sus percentiles describen el
  servicio saturado o bloqueado, no su latencia normal.
- Generador en una red doméstica: la latencia incluye esa red.
- No se midió el login con MFA ni, por ahora, el token de servicio y la introspección.

## Salida de las rondas

Salida textual del script, sin secretos: las cuentas y la clave se pasan por entorno y no se imprimen.

```text
# R1 — 5 concurrentes
> python docs/evidencia/carga/carga_h5.py https://g7-auth-service.onrender.com/api/v1 --concurrencia 5 --escenarios login,refresh
base=https://g7-auth-service.onrender.com/api/v1 cuentas=5 concurrencia=5 por_hilo=1 duración=6.3 s
login         n=5 p50=4769 p95=5661 p99=5661 media=4765 max=5661 ms | errores=0 estados={200: 5}
              objetivo p95<800 ms: NO CUMPLE
refresh       n=5 p50=695 p95=1408 p99=1408 media=842 max=1408 ms | errores=0 estados={200: 5}
              objetivo p95<300 ms: NO CUMPLE

# R2, tramo de 10 — 23-nov 22:01, detenido
> python docs/evidencia/carga/carga_h5.py https://g7-auth-service.onrender.com/api/v1 --concurrencia 10 --por-hilo 10 --escenarios login,refresh
RONDA DETENIDA: login respondió 429. Se reportan las muestras tomadas hasta ese momento; no subir al siguiente tramo.
base=https://g7-auth-service.onrender.com/api/v1 cuentas=5 concurrencia=10 por_hilo=10 duración=9.2 s
login         n=56 p50=176 p95=8348 p99=8354 media=1470 max=8360 ms | errores=54 estados={200: 2, 502: 38, 503: 8, 429: 8}
              objetivo p95<800 ms: NO CUMPLE
refresh       n=2 p50=4075 p95=4590 p99=4590 media=4332 max=4590 ms | errores=2 estados={502: 2}
              objetivo p95<300 ms: NO CUMPLE

# Referencia — 1 concurrente, 5 iteraciones
> python docs/evidencia/carga/carga_h5.py https://g7-auth-service.onrender.com/api/v1 --concurrencia 1 --por-hilo 5 --escenarios login,refresh
base=https://g7-auth-service.onrender.com/api/v1 cuentas=5 concurrencia=1 por_hilo=5 duración=5.1 s
login         n=5 p50=581 p95=1666 p99=1666 media=794 max=1666 ms | errores=0 estados={200: 5}
              objetivo p95<800 ms: NO CUMPLE
refresh       n=5 p50=232 p95=239 p99=239 media=230 max=239 ms | errores=0 estados={200: 5}
              objetivo p95<300 ms: CUMPLE
```
