# TEC-INFRA-T005 — preparación del despliegue (avance parcial)

Responsable de la tarea: Christian. Avance de Sergio Osorio el 15-oct-2026 por
asignación del equipo mientras Christian no está disponible. **No se desplegó
nada**: falta la ventana T002, la clave persistente y elegir la plataforma.

## Qué se entrega

| Pieza | Archivo |
|---|---|
| Imagen multi-etapa, usuario sin privilegios, sin secretos | `Dockerfile`, `.dockerignore` |
| Puerto de la plataforma, cabeceras del proxy, apagado ordenado | `application.properties` |
| CORS solo para orígenes acordados, `Retry-After` expuesta | `configuration/CorsConfig.java`, `SeguridadConfig` (`.cors`) |
| Salud pública sin detalles; broker/correo fuera de la salud hasta T004 | `SeguridadConfig`, `application.properties` |
| Build de la imagen en CI (usuario `g7`, Java 21, sin `.pem`/`.env`/fuentes) | `.github/workflows/ci.yml`, job «Backend - imagen Docker» |
| Guía de variables, archivos secretos y pasos | `docs/despliegue/DESPLIEGUE-T005.md` |

## Evidencia (local, 15-oct-2026)

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

## Pendiente (dueño Christian)

Elegir plataforma, ventana T002, clave persistente, CA y lista común como archivos
secretos, desplegar desde `main` y verificar con los `curl` de la guía.
