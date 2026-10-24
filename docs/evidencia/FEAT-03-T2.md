# FEAT-03-T2 — Solicitar recuperación de contraseña

**Estado:** implementación local en `feat/EP-03-recuperacion`; sin commit ni push.

## Base y fuentes

- Backend sobre `develop` en `813b8099a59974c172d95a0b45b23d125e58caa6` (incluye el merge de T1, PR #59).
- Documentación H4 consultada en `Modulo-de-Seguridad`, revisión `aeb477c491826dd4801f95c2e3d996f9d25ac5f7`: plan H4 v1.2, `tareas/hito-4/juan-cano/TAREAS.md` FEAT-03-T2, SPEC-08, FRONT-05, OpenAPI, catálogo de errores y diseño hexagonal.
- El contrato HTTP ya publicado define `POST /password/recuperar`, 202 sin cuerpo y 429 con `Retry-After`; no se modificó OpenAPI.

## Implementación

- El endpoint es público, valida el correo y devuelve 202 vacío sin distinguir cuenta existente, bloqueada o inexistente.
- La cuota se reserva antes de consultar cuentas: tres solicitudes por hora y correo normalizado; la cuarta responde 429 `DEMASIADAS_SOLICITUDES` con `Retry-After`. La persistencia guarda HMAC-SHA-256 del correo, nunca el correo en la tabla de cuota, y serializa reservas concurrentes con advisory lock de PostgreSQL.
- Para cuentas elegibles —incluido estado efectivo `BLOQUEADO`— reutiliza `TokensUnUsoPort` con propósito `RECUPERACION` y TTL de 30 minutos. Entrega el enlace por `MensajeriaPort` al outbox cifrado y asíncrono; no envía correo sincrónicamente ni registra token/correo en auditoría.
- Se aplica un piso asíncrono de 300 ms a respuestas válidas para reducir la diferencia de latencia sin retener el hilo HTTP. No es una garantía de tiempo constante si la operación supera ese piso; se debe medir y ajustar bajo carga.
- La clave estable de HMAC se lee de `AUTH_SOLICITUD_LIMITADA_HMAC_KEY`, codificada en Base64 y con mínimo 32 bytes. Si falta o es inválida, el límite falla cerrado y la solicitud responde 503.

## Verificación ejecutada

Con Microsoft JDK 21.0.12.1 y Docker Desktop/Testcontainers:

```powershell
.\gradlew.bat --no-daemon --console=plain clean build
```

Resultado: `BUILD SUCCESSFUL`, 817 pruebas en 109 suites; 0 fallos, 0 errores y 0 omitidas. Incluye la prueba PostgreSQL 16.6 de cuota, ventana y concurrencia, así como pruebas del servicio, HMAC, endpoint público, latencia mínima y arquitectura hexagonal.

La ejecución focalizada de T2 más arquitectura también pasó 17/17. `git diff --check` no reportó errores.

Revalidación local del 22-oct-2026, después de confirmar Docker Engine 29.9.1 y Microsoft JDK 21.0.12.1: `clean build` volvió a terminar `BUILD SUCCESSFUL`, 817/817 pruebas, 0 fallos, 0 errores y 0 omitidas (109 suites). `git diff --check` volvió a pasar.

## Límites de la evidencia

- PostgreSQL 16.6 fue un contenedor local inicializado con `src/test/resources/mfa/base-observada/schema.sql`; no se conectó a Supabase ni se inspeccionaron sus migraciones o grants.
- El esquema documentado contiene `solicitud_limitada` y el enum `RECUPERACION`, pero el estado real de la base compartida debe confirmarse antes de desplegar. No se añadió una migración especulativa.
- Debe provisionarse la misma clave HMAC persistente y secreta en todas las instancias. No se incluyó ningún secreto en el repositorio.
- No se implementó `POST /password/restablecer`, que corresponde a T3, ni pantallas frontend.
