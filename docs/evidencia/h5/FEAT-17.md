# FEAT-17 — Pruebas de rendimiento contra el despliegue (H5)

Ejecutó: Valery Cristin Gutierrez Bendezu · Revisó: _(Eva)_
Plan: [FEAT-17-plan.md](FEAT-17-plan.md) · Script: [`carga_h5.py`](../carga/carga_h5.py)
Nivel de evidencia: **propia desplegada**.
Estado: **R1 (ronda corta) y ronda de referencia ejecutadas el 23-nov; R2 (50 concurrentes) pendiente**
de la ventana que acuerde el equipo, por el riesgo de afectar la demo (ver «Causas probables»).

## Condiciones

| Elemento | Valor |
|---|---|
| Fecha y hora | 23-nov-2026, entre 14:25 y 14:26 (America/Lima) |
| Backend | `https://g7-auth-service.onrender.com/api/v1` |
| Versión desplegada (SHA) | _(la que muestre Render; anotarla con David)_ |
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

## R2 — Ronda final (50 concurrentes, 10 iteraciones por hilo)

_Pendiente._ Con 5 concurrentes el login ya tarda ~5 s; con 50 podría superar el tiempo de espera de
30 s del script y degradar el servicio para la demo mientras dura la ronda. Se propuso al equipo
correrla en una ventana sin demo o subir de forma escalonada (10, 25, 50).

| Ronda | Concurrencia | Login p50 / p95 / p99 | Refresh p50 / p95 / p99 | Errores |
|---|---|---|---|---|
| _(pendiente)_ | | | | |

## Comparación con los umbrales

| Escenario | Umbral p95 | Sin concurrencia | 5 concurrentes | 50 concurrentes |
|---|---|---|---|---|
| Login | < 800 ms | 1666 ms (p50 581) · **No cumple**, por un valor aislado | 5661 ms · **No cumple** | _(pendiente)_ |
| Refresh | < 300 ms | 239 ms · **Cumple** | 1408 ms · **No cumple** | _(pendiente)_ |
| Introspección | < 200 ms | — | — | _(pendiente del scope)_ |
| Token de servicio | sin umbral | — | — | _(pendiente del scope)_ |

Los umbrales están definidos para 50 concurrentes. Con 5 ya no se cumplen, así que **no se cumplirán
con 50** en el despliegue actual. Se declara.

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

Por verificar con R2 o con las métricas de Render: uso de CPU y memoria del servicio durante la ronda.

## Comparación con H3 (local)

| | H3 local, 50 concurrentes (15-oct) | H5 Render, 5 concurrentes (23-nov) |
|---|---|---|
| Login p95 | 673–820 ms | 5661 ms |
| Refresh p95 | 269–339 ms | 1408 ms |

En H3, backend, base y generador compartían una CPU de escritorio. En Render el backend tiene la CPU
de su plan y se suma la latencia de red hacia Supabase y desde el generador.

## Limitaciones

- Muestras pequeñas (5 por escenario y ronda): los percentiles son orientativos. R2 aporta ~500 por escenario.
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

# Referencia — 1 concurrente, 5 iteraciones
> python docs/evidencia/carga/carga_h5.py https://g7-auth-service.onrender.com/api/v1 --concurrencia 1 --por-hilo 5 --escenarios login,refresh
base=https://g7-auth-service.onrender.com/api/v1 cuentas=5 concurrencia=1 por_hilo=5 duración=5.1 s
login         n=5 p50=581 p95=1666 p99=1666 media=794 max=1666 ms | errores=0 estados={200: 5}
              objetivo p95<800 ms: NO CUMPLE
refresh       n=5 p50=232 p95=239 p99=239 media=230 max=239 ms | errores=0 estados={200: 5}
              objetivo p95<300 ms: CUMPLE
```
