# FEAT-17 — Pruebas de rendimiento contra el despliegue (H5)

Ejecutó: Valery Cristin Gutierrez Bendezu · Revisó: _(Eva)_
Plan: [FEAT-17-plan.md](FEAT-17-plan.md) · Script: [`carga_h5.py`](../carga/carga_h5.py)
Nivel de evidencia: **propia desplegada**.

> Plantilla: los campos entre `_( )_` se completan con la salida real del script.
> No se rellenan con valores estimados.

## Condiciones

| Elemento | Valor |
|---|---|
| Fecha y hora | _(dd-nov-2026 hh:mm, hora de Lima)_ |
| Backend | `https://g7-auth-service.onrender.com/api/v1` |
| Versión desplegada (SHA) | _(sha)_ |
| Generador | _(equipo / red)_ |
| Cuentas | carga.0 … carga._(N)_ @fixtures.g7.test (CLIENTE) |
| Cliente técnico | scope `tokens:introspeccion` |

## R1 — Ronda corta (5 cuentas, 5 concurrentes)

| Escenario | n | p50 | p95 | p99 | Errores | Estados |
|---|---|---|---|---|---|---|
| Login | | | | | | |
| Refresh | | | | | | |
| Token de servicio | | | | | | |
| Introspección | | | | | | |

Resultado: _(sin bloqueos / detalle)_

## R2 — Ronda final (50 cuentas, 50 concurrentes, 10 iteraciones por hilo)

Tiempos en ms.

| Ronda | Login p50 / p95 / p99 | Refresh p50 / p95 / p99 | Token p50 / p95 / p99 | Introspección p50 / p95 / p99 | Errores |
|---|---|---|---|---|---|
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |

## Comparación con los umbrales

| Escenario | Umbral p95 | Mejor p95 medido | Resultado |
|---|---|---|---|
| Login | < 800 ms | | _(Cumple / No cumple)_ |
| Refresh | < 300 ms | | |
| Introspección | < 200 ms | | |
| Token de servicio | sin umbral | | Se reporta |

## Causas probables de lo que no se cumple

_(Completar solo si algún umbral no se cumple. Ejemplos de causas a verificar: CPU del plan
de Render con Argon2id, latencia de red hacia Supabase, tamaño del pool de conexiones,
arranque en frío.)_

## Comparación con H3 (local)

En H3 (15-oct, local, misma CPU para backend, base y generador) el login p95 fue 673–820 ms
y el refresh p95 269–339 ms ([EP-02-T007](../EP-02-T007-carga.md)).
_(Diferencias observadas en el despliegue.)_

## Limitaciones

- Generador en una red doméstica: la latencia incluye esa red.
- Plan gratuito de Render: CPU compartida y arranque en frío.
- No se mide el login con MFA.

## Salida de las rondas

Salida textual del script (sin secretos):

```text
_(pegar aquí la salida de R1 y de las tres rondas de R2)_
```
