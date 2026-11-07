# FEAT-03 — retorno por canal en recuperación (issue #76)

**Fecha:** 30-oct-2026  
**Rama:** `feat/EP-03-retorno-canal`  
**Base backend:** `8664989bf98f414412f4c95870d29aa7599125a4` (`origin/develop`)  
**Fuentes SDD:** `Modulo-de-Seguridad` `origin/main` en `6045c013fd454cff998169bd1b114323324cda72`; issue [#76](https://github.com/Taller-SW-Web/Modulo-de-Seguridad/issues/76).

## Alcance aplicado

- `POST /api/v1/password/recuperar` acepta `canalOrigen` opcional y limita su valor a `WEB`, `CHATBOT`, `RETAIL` o `MARKETPLACE`. Si se omite, usa `WEB`.
- El canal se propaga por el caso de uso y el puerto de correo. El enlace añade `&canal=<CANAL>` para los canales distintos de `WEB`; no recibe ni construye destinos desde una URL enviada por el solicitante.
- La respuesta neutra, cuota, vigencia del token y envío mediante outbox mantienen su comportamiento previo.

Fuentes consultadas: SPEC-08 RF-08.9/ESC-08.8, FRONT-05 «Retorno por canal» y OpenAPI para `POST /password/recuperar`, publicados en el SHA indicado. No se modificó el contrato, que pertenece al PO.

## Verificación local

Con Microsoft OpenJDK 21.0.12.1:

- Pruebas HTTP, servicio y enlace de recuperación: exitosas.
- `TokenUnUsoJpaPostgresTest` con PostgreSQL/Testcontainers: 11/11, sin fallos, errores u omisiones.
- `gradlew.bat --no-daemon --console=plain clean build`: `BUILD SUCCESSFUL`; 1007 pruebas, 0 fallos, 0 errores, 0 omisiones.
- `git diff --check`: sin errores de whitespace.

No se desplegó el backend ni se probó contra Supabase o un servidor de correo real. Esta evidencia cubre la implementación backend local del issue; la adaptación de FRONT-05 sigue siendo parte frontend del mismo alcance.
