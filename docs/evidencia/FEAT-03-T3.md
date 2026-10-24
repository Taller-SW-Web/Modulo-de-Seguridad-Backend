# FEAT-03-T3 — Restablecimiento de contraseña

## Alcance y fuentes

- Tarea: `FEAT-03-T3`, Hito 4, responsable Juan Cano.
- Fuentes H4 consultadas en `Modulo-de-Seguridad`, revisión `aeb477c491826dd4801f95c2e3d996f9d25ac5f7`: `AGENTS.md`, `PLAN.md`, `TAREAS.md`, SPEC-07, SPEC-08, SPEC-14, SPEC-15, contratos de API y acuerdos de arquitectura.
- Implementación sobre `feat/EP-03-recuperacion`, cuya base local antes del cambio era `59c14fa8d21bb024ad8b18e982192a94055e2fff`.

## Implementado

- `POST /api/v1/password/restablecer`, público y sin exigir Bearer; responde 204 al completar, 401 `TOKEN_RECUPERACION_INVALIDO`, 410 `TOKEN_RECUPERACION_EXPIRADO` y 422 `POLITICA_INCUMPLIDA`.
- El token se busca por SHA-256, se revalida dentro de la transacción y se consume una sola vez. Un rechazo de política no lo consume.
- La nueva contraseña pasa por la política central, datos personales e historial; la credencial e historial se actualizan en la misma transacción.
- Se revocan todas las familias de sesión. Protección bloquea la cuenta y sus filas en el orden cuenta → bloqueo/racha → token; el restablecimiento quita solo el bloqueo automático, conserva el manual, reinicia intentos fallidos y preserva el contador de bloqueos consecutivos.
- Si se quita un bloqueo automático, auditoría y evento `usuario.desbloqueado` registran `via: RESTABLECIMIENTO`. El cambio de contraseña también se audita y se encola un aviso de forma transaccional.
- El cuerpo HTTP, los secretos efímeros y las representaciones de solicitud mantienen token y contraseña redactados.

## Verificación real

- JDK 21.0.12.101; `gradlew.bat --no-daemon --console=plain clean build`: `BUILD SUCCESSFUL`; 827 pruebas, 0 fallos, 0 errores, 0 omitidas.
- Testcontainers con PostgreSQL 17.6 en los casos T3: token consumido, hash e historial actualizados, revocación de ambas familias, bloqueo manual conservado e intentos reiniciados, bloqueo automático eliminado y auditoría con `RESTABLECIMIENTO`.
- Pruebas unitarias del caso de uso: token vencido/inexistente y política rechazada no escriben ni consumen; el camino correcto aplica todos los efectos.
- Prueba HTTP: ruta pública devuelve 204 aun con Bearer inválido.
- `git diff --check`: sin errores de whitespace. Git avisó que un archivo de prueba LF se convertirá a CRLF en el siguiente checkout.

## Revisión pendiente

La API de Protección se extendió con operaciones transaccionales para fijar el orden de locks y distinguir `RESTABLECIMIENTO` en los efectos. Debe revisarse ese contrato con Luis, dueño de la capacidad. La suite usa fixtures PostgreSQL locales; no se conectó a Supabase ni se aplicaron migraciones allí. No se hicieron commits ni push.
