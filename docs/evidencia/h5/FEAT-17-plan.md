# FEAT-17-T1 — Plan de pruebas de rendimiento (H5)

Dueña: Valery Cristin Gutierrez Bendezu · Revisión cruzada: Eva Lucía Moreno Zevallos
Rama: `feat/TEC-INFRA-rendimiento` · Script: [`docs/evidencia/carga/carga_h5.py`](../carga/carga_h5.py)
Base: evidencia local de H3 ([EP-02-T007](../EP-02-T007-carga.md)), que no se repitió en la nube.

## 1. Objetivo

Medir la latencia y los errores de las operaciones de identidad **en el entorno desplegado**
(Render + Supabase) y compararlas con los umbrales ya documentados. Un umbral que no se
cumpla se declara en el informe con su causa probable; no se oculta.

## 2. Alcance

| ID | Escenario | Operación | Umbral (p95) | Concurrencia |
|---|---|---|---|---|
| E1 | Ingreso | `POST /api/v1/auth/login` | < 800 ms | 50 |
| E2 | Renovación | `POST /api/v1/auth/refresh` | < 300 ms | 50 |
| E3 | Token de servicio | `POST /api/v1/auth/token` (`client_credentials`) | sin umbral documentado; se reporta | 50 |
| E4 | Introspección | `POST /api/v1/auth/introspeccion` | < 200 ms | 50 |

**Fuera de alcance:** login con MFA (`/auth/otp/verificar`), pantallas del frontend, pruebas
de estrés hasta el punto de quiebre y pruebas de seguridad (FEAT-15).

## 3. Entorno

| Elemento | Valor |
|---|---|
| Backend | `https://g7-auth-service.onrender.com/api/v1` |
| Versión medida | SHA desplegado en Render al momento de la ronda (se anota en el informe) |
| Base de datos | Supabase (PostgreSQL) del despliegue |
| Generador de carga | Equipo de Valery, fuera de Render; red doméstica (se anota en el informe) |

Diferencia con H3: allí backend, base y generador compartían la misma CPU; aquí cada uno
tiene la suya y se suma la latencia de red. Los resultados no son directamente comparables.

## 4. Datos de prueba

- **Cuentas:** rol CLIENTE, verificadas y sin MFA. **No se usan las cuentas de la demo**, porque el
  bloqueo automático podría dejarlas inutilizables.
  - Ronda corta: 5 cuentas creadas por Valery desde la SPA con correos o alias que controla
    (acuerdo con David), pasadas al script por `G7_CUENTAS_CARGA`.
  - Ronda final: `carga.0` … `carga.49@fixtures.g7.test` (David, OPS-T2), o la lista que se acuerde.
- **Cliente técnico de prueba:** `client_id` y `client_secret` con el scope
  `tokens:introspeccion` (confirmación de Sergio).
- **Secretos:** solo por variables de entorno (`G7_CLAVE_CARGA`, `G7_CLIENT_ID`,
  `G7_CLIENT_SECRET`) y la lista de correos en `G7_CUENTAS_CARGA`. Nunca en git, capturas ni
  informe; el script solo imprime correos enmascarados.

## 5. Rondas

| Ronda | Cuentas | Concurrencia | Iteraciones por hilo | Peticiones aprox. | Propósito |
|---|---|---|---|---|---|
| R0 calentamiento | 1 (`carga.0`) | 1 | 1 | 2 | Despertar el servicio de Render y validar la clave |
| R1 corta | 5 | 5 | 1 | 20 | Comprobar que nada se bloquea y que los 4 escenarios responden 200 |
| R2 escalonada | según `G7_CUENTAS_CARGA` | 10 → 25 → 50 | 10 | 200 / 500 / 1000 por escenario | Medición contra umbrales, subiendo de tramo solo si el anterior terminó sin paradas |

- Cada iteración de un hilo hace E1 → E2 → E4 con la sesión obtenida, más un E3.
- R2 se corre de forma escalonada (acuerdo con David, 23-nov): 10, 25 y 50 concurrentes, en una ventana
  sin demo ni pruebas de otros grupos, revisando cada tramo antes de subir.
- **Parada:** si aparece un timeout o sin respuesta, un 423 (bloqueo) o un 429, el script detiene la
  ronda («RONDA DETENIDA») y no se sube al siguiente tramo; se reporta el último tramo completo.
- Si R1 tiene cualquier error 401/423/429 en el login, **no se ejecuta R2** hasta revisar la causa.

Comandos:

```powershell
# R1 (cuentas por G7_CUENTAS_CARGA; introspección y token se agregan cuando se confirme el scope)
python docs/evidencia/carga/carga_h5.py https://g7-auth-service.onrender.com/api/v1 --concurrencia 5 --por-hilo 1 --escenarios login,refresh
# R2 escalonada: un tramo por vez, revisando la salida antes de subir
python docs/evidencia/carga/carga_h5.py https://g7-auth-service.onrender.com/api/v1 --concurrencia 10 --por-hilo 10 --escenarios login,refresh
python docs/evidencia/carga/carga_h5.py https://g7-auth-service.onrender.com/api/v1 --concurrencia 25 --por-hilo 10 --escenarios login,refresh
python docs/evidencia/carga/carga_h5.py https://g7-auth-service.onrender.com/api/v1 --concurrencia 50 --por-hilo 10 --escenarios login,refresh
```

## 6. Métricas

Por escenario: número de muestras, p50, p95, p99, media, máximo y errores por código HTTP.
Se registran también la duración de la ronda, la fecha y hora y el SHA desplegado.

## 7. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Bloqueo automático de cuentas por logins fallidos | Cuentas exclusivas de carga; el script aborta si falla el login de calentamiento |
| Afectar la demo o el trabajo de otros grupos | Ventana avisada al equipo con anticipación; rondas cortas (R2 dura pocos segundos) |
| Arranque en frío de Render (plan gratuito) | R0 de calentamiento y tres rondas de R2 |
| Límite de CPU del plan de Render con Argon2 | Se declara como causa probable si el login no cumple |
| Variación por la red del generador | Se anota la red usada; las tres rondas muestran la dispersión |

## 8. Ventana

Por acordar con el equipo dentro de S2 (lun 16 a vie 20-nov, antes del congelamiento).
Aviso en el grupo al menos 1 hora antes, indicando inicio y fin.

## 9. Criterio de salida

- R1 sin errores y R2 ejecutada tres veces contra la versión desplegada.
- Informe publicado en [`FEAT-17.md`](FEAT-17.md) con la comparación contra los umbrales
  y la causa probable de lo que no se cumpla.
