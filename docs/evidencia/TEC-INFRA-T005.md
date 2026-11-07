# TEC-INFRA-T005 — despliegue del backend y la SPA en nube

Responsable de la tarea: Christian. Estado: **desplegado y comprobado en sus
puntos públicos; cierre pendiente** (reinicio, persistencia de claves y
reversión sin acreditar). Ficha del entorno:
[DESPLIEGUE-T005.md § Entorno real H3](../despliegue/DESPLIEGUE-T005.md#entorno-real-h3).

En cada dato se distingue la **revisión examinada** (leída en GitHub), la
**revisión desplegada** (la que sirve la plataforma) y la **revisión probada**
(contra la que se ejecutó un recorrido).

## 1. Preparación (15-oct-2026, B#36)

Avance de Sergio Osorio por asignación del equipo mientras Christian no estaba
disponible. Ese PR **no desplegó nada**; dejó listas la imagen y la configuración.

| Pieza | Archivo |
|---|---|
| Imagen multi-etapa, usuario sin privilegios, sin secretos | `Dockerfile`, `.dockerignore` |
| Puerto de la plataforma, cabeceras del proxy, apagado ordenado | `application.properties` |
| CORS solo para orígenes acordados, `Retry-After` expuesta | `configuration/CorsConfig.java`, `SeguridadConfig` (`.cors`) |
| Salud pública sin detalles; broker/correo fuera de la salud hasta T004 | `SeguridadConfig`, `application.properties` |
| Build de la imagen en CI (usuario `g7`, Java 21, sin `.pem`/`.env`/fuentes) | `.github/workflows/ci.yml`, job «Backend - imagen Docker» |
| Guía de variables, archivos secretos y pasos | `docs/despliegue/DESPLIEGUE-T005.md` |

Evidencia local de ese día:

- `DespliegueHttpTest` (servidor HTTP real): 5/5. `FiltroAccesoHttpTest` 11/11 y
  arquitectura en verde con el cambio en `SeguridadConfig`.
- Mutación: quitar `.cors(...)` y el `permitAll` de salud → 3 de 5 fallan.
- Jar ejecutado como lo haría el contenedor (`PORT=18081`, variables de entorno,
  PostgreSQL 16 local con el esquema del ensayo de la ventana — base observada +
  V101–V106 + R__ + grants del #35 — y conectado como **`g7_runtime`**, no superusuario):
  arranca en 7 s; Hibernate valida el esquema con los privilegios mínimos.
  - `GET /actuator/health` → 200 `{"status":"UP"}`; `/actuator/env` → 401.
  - JWKS y política públicas → 200.
  - Preflight desde `http://localhost:5173` → 200 con `Allow-Origin` exacto y
    `Expose-Headers: Retry-After, Location`; desde `https://malo.test` → 403 sin cabeceras CORS.
  - `POST /api/v1/usuarios/{id}/roles` sin token → 401 `TOKEN_INVALIDO`.
- Contra una base sin V101–V106 el arranque falla en la validación
  (`missing table [acceso_sesion]`): confirma que la ventana T002 es precondición.

La imagen Docker no se construyó en local (sin Docker); la construye el job de CI.

## 2. Despliegue en Render (ronda del 16-oct-2026, informado por el equipo)

| Componente | Alojamiento | URL pública |
|---|---|---|
| SPA G7 | Render Static Site | https://g7-seguridad-spa.onrender.com |
| Backend G7 (Spring Boot, Docker) | Render Web Service | https://g7-auth-service.onrender.com |
| Base de datos | PostgreSQL en Supabase | (privada) |
| Correo | Brevo SMTP, puerto 2525 en el entorno probado | (privado) |

| Revisión | Backend | Frontend |
|---|---|---|
| Examinada | `4bd7b61165ff2e4940c68a8b0f050ba395b7b7ad` (punta de `develop` el 16-oct; PR #54) | `82c94149c8e2326d3d6013a167026a6b38d75b4a` (punta de `develop`; PR #9) |
| Desplegada | **Reportada** por el equipo: `4bd7b61`, rama `develop`. No hay registro de despliegue en GitHub y el servicio no publica su versión: **sin verificar** | **No obtenida del panel de Render.** Indicio fuerte (§3.3): el bundle servido es idéntico byte a byte al build del árbol de `82c9414` |
| Probada | La de la ronda del 16-oct (reportada `4bd7b61`) | La servida durante la ronda del 16-oct |

El PR #55 (EP-07, bloqueo vigente en el detalle) seguía **abierto** sin fusionar
el 16-oct: no forma parte de la revisión desplegada ni de la probada.
`main` está 21 commits por detrás de `develop`; la guía proponía desplegar desde
`main`, pero el entorno se informó desde `develop`. No se cambió la rama de Render.

## 3. Comprobación pública independiente (17-oct-2026 UTC = noche del 16-oct, Lima)

Solo peticiones de lectura y un preflight CORS. No se ejecutó login, ni pruebas
que bloqueen cuentas, ni reinicios, ni cambios de datos.

### 3.1 Backend

| Hora (UTC) | Petición | HTTP | Resultado |
|---|---|---|---|
| 2026-10-17T04:45:55Z | `GET /actuator/health` | — | Sin respuesta en 90 s (servicio dormido del plan gratuito); **no concluyente**, no indica ausencia de despliegue |
| 2026-10-17T04:47:25Z | `GET /api/v1/auth/.well-known/openid-configuration` | 200 | Respondió en 64 s (arranque en frío) |
| 2026-10-17T04:48:30Z | `GET /api/v1/auth/.well-known/jwks.json` | 200 | 0,2 s |
| 2026-10-17T04:48:56Z | `GET /actuator/health` | 200 | `{"status":"UP","groups":["liveness","readiness"]}` |
| 2026-10-17T04:48:56Z | `GET /api/v1/auth/.well-known/openid-configuration` | 200 | Ver abajo |
| ≈2026-10-17T04:51Z | `GET /actuator/env` | 401 | El resto de actuator no se publica |

Descubrimiento publicado (completo):

```json
{"issuer":"https://g7-auth-service.onrender.com/api/v1/auth",
 "jwks_uri":"https://g7-auth-service.onrender.com/api/v1/auth/.well-known/jwks.json",
 "id_token_signing_alg_values_supported":["RS256"]}
```

- `issuer` coincide **exactamente** con el `AUTH_ISSUER` informado.
- `jwks_uri` apunta al JWKS del mismo servicio.
- JWKS: una sola clave `RSA`, `alg=RS256`, `use=sig`,
  `kid=12VnSlgesm0okVRMAdltXyR-wJBxsRI44QiOcnm1XeQ`, módulo de 3072 bits, **sin
  componente privado `d`**. El `kid` queda anotado para comparar tras un reinicio.

### 3.2 CORS (preflight `OPTIONS /api/v1/auth/login`, 2026-10-17T04:51Z)

| Origen | HTTP | Cabeceras |
|---|---|---|
| `https://g7-seguridad-spa.onrender.com` | 200 | `Allow-Origin` exacto, `Expose-Headers: Retry-After, Location`, sin `Allow-Credentials` |
| `https://malo.test` | 403 | Sin cabeceras CORS |

Coherente con `G7_CORS_ORIGENES=https://g7-seguridad-spa.onrender.com`; el
preflight no revela si hay otros orígenes en la lista.

### 3.3 SPA

| Hora (UTC) | Petición | HTTP | Resultado |
|---|---|---|---|
| 2026-10-17T04:48:30Z | `GET /` | 200 | `<title>G7 — Seguridad</title>`, `<meta name="referrer" content="no-referrer">`, bundle `index-BIAtdqE9.js` y `index-Dhk6WyMq.css`; `last-modified: Fri, 16 Oct 2026 08:48:16 UTC` |
| ≈2026-10-17T04:49Z | `GET /login` | 200 | La reescritura de rutas de la SPA funciona |

**Revisión servida.** Se compiló el árbol de `82c9414` (`npm ci` +
`vite build`, Node 24) con la única variable pública
`VITE_API_BASE_URL=https://g7-auth-service.onrender.com/api/v1`. Los dos
archivos coinciden con los servidos:

| Archivo | SHA-256 (local = servido) |
|---|---|
| `index-BIAtdqE9.js` | `89d597c52a042d076f15c8464d8d20210cbf461bca1bf7cee0210cf42b3a4415` |
| `index-Dhk6WyMq.css` | `3ac8e726b3662fa727f6b1797a8618f6547a86361dc220c9e25e01e1117ab741` |

Esto acredita que se sirve el contenido de ese árbol, no el SHA del despliegue:
`a7e9947` (merge de `develop` a `main`, PR #10) tiene los mismos archivos y
produciría el mismo bundle. El SHA efectivo debe tomarse del panel de Render.
El valor efectivo de `VITE_API_BASE_URL` **incluye `/api/v1`** (así lo pide
`.env.example`); el bundle no contiene otra variable de entorno.

## 4. Recorridos ya ejecutados en nube (evidencia histórica)

Ejecución manual del equipo el 16-oct-2026, documentada en
`G7_Evidencias_Pruebas_H3.pdf` (8 páginas) y `G7_G5_Integracion_API_H3.pdf`
(6 páginas), adjuntos por el equipo. Acreditan la ejecución en esa fecha, no la
disponibilidad continua.

| Recorrido | Resultado observado | Referencia |
|---|---|---|
| OTP por correo de ADM: código incorrecto, reemplazado tras reenvío, nuevo | Rechazo, rechazo, acceso al panel | Evidencias E01–E03 |
| Desafío vencido por API | 401 `TOKEN_INVALIDO` | E04 |
| Reúso del mismo desafío y código | 200 la primera vez, 401 la segunda | E04 |
| Cambio de contraseña de C | Confirmación y aviso real recibido (Brevo) | E05 |
| Segunda sesión de C | Redirección al login con aviso | E06 |
| Desbloqueo de L | Pantalla con confirmación explícita, acceso posterior, enlace reutilizado rechazado | E07–E10 |
| Identidades A/B/V | Tres cuentas `ACTIVO` (consulta SQL de solo lectura) | E11 |
| Integración con G5 | Siete casos; ver [hito-3-ventas.md](https://github.com/Taller-SW-Web/Modulo-de-Seguridad/blob/main/docs/integracion/hito-3-ventas.md) (incluye que G5, ya redesplegado, respondía 200 sin token el 17-oct 04:54 UTC) | Integración E01–E08 |

No acreditado por estas evidencias:

- El caso específico **410 `OTP_EXPIRADO`**: el desafío vence antes y la API
  responde 401 `TOKEN_INVALIDO`; ese 401 no equivale al 410.
- La revocación instantánea de la segunda sesión: se observó la redirección,
  no el instante en que dejó de valer el acceso.

## 5. Criterios de cierre de T005

| Criterio (tablero de Christian) | Estado | Base |
|---|---|---|
| Imagen del backend construida y publicada | Acreditado | CI de imagen; servicio responde |
| SPA publicada | Acreditado | §3.3 |
| HTTPS; base URL, issuer y JWKS coherentes | Acreditado | §3.1 |
| Rutas de la SPA | Acreditado (`/login`) | §3.3 |
| CORS solo para orígenes concretos, `Retry-After` expuesta | Acreditado | §3.2 |
| Arranque con runtime real (DB, SMTP) | Acreditado por la ronda del 16-oct | §4: login, OTP y avisos por correo |
| Sin secretos en el build de la SPA | Acreditado | Build reproducido con una sola variable pública |
| Enlaces de correo al entorno y ruta propios; `no-referrer`; sin token de login en URL | Acreditado (histórico + meta) | E07, §3.3 |
| Claves, familias y mensajes **persisten tras reinicio** | **Pendiente de comprobación** | No se reinició el servicio; comparar `kid` del §3.1 tras un reinicio |
| Procedimiento de despliegue reproducible | **Parcial** | Guía existente; falta registrar la configuración aplicada en Render por su dueño |
| Reversión: revisión anterior conservada, sin fingir que revierte el esquema | **Pendiente de comprobación** | No documentado ni probado |
| SHA desplegado identificado | **Parcial** | Backend reportado; frontend por contenido, no por panel |

## 6. Pendiente (dueño Christian)

1. Anotar en la ficha los SHA desplegados tal como los muestra Render (ambos servicios).
2. Reiniciar el backend en una ventana acordada y comprobar que el `kid` se
   conserva y que las sesiones/mensajes persisten.
3. Documentar el procedimiento de reversión (redeploy de la revisión anterior)
   sin tocar la base.
4. Confirmar la configuración de despliegue automático de Render antes de fusionar
   en la rama que sigue el servicio.
