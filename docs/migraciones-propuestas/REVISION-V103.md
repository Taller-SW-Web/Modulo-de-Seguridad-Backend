# Entrega V103 — EP-04-T001

## Contraste real (14-oct-2026)
Metadatos consultados en Supabase `sghqhbakbnaapxdapmbc` por el conector,
solo SELECT a information_schema/pg_catalog: columnas, constraints e índices.
No se consultaron hashes, códigos, usuarios ni otras filas de negocio.
Resultado reproducible: `INVENTARIO-V103-20261014.json`.

| Objeto observado | Tratamiento |
|---|---|
| otp.desafio_id y FK compuesta ya presentes | Reutilizados, sin ADD ni FK duplicada |
| otp.expiracion, canal_otp (EMAIL/SMS) | Mapping JPA ajustado; no renombrar columnas |
| destino/motivo/hash_key_id/invalidado_en ausentes | V103 añade en tabla propia |
| credencial PK(usuario_id), sin id ni version | FK de desafío por usuario; identidad de revisión opaca |
| idx_otp_usuario_fecha ya presente | Reutilizado, no índice duplicado por usuario/fecha |

## Revisión de credenciales y límites entre dueños
`credencial_id` es la identidad opaca entregada por CredencialesPort, no una
suposición sobre su PK física. Se conserva UUID/version en el desafío.
La FK por usuario exige credencial para nuevas escrituras; la confirmación de
id/version vigente y pertenencia al usuario corresponde al port de Juan José
(T002/T004). Una FK no puede sustituir esa comprobación.
V103 NO añade id, version ni cambia hashes/PK de credencial. El PR #18 aún usa
id/password_hash/algoritmo/actualizado_en de la base histórica; Juan José debe
contrastar sus mappings con el inventario, por su propia tarea. V101 pertenece
a él. V103 no presupone que esa migración cree una columna id.

## Históricos y aplicación
No rellenar revisiones, destino, propósito ni key id de filas previas.
Constraints NOT VALID preservan esas filas y controlan nuevas escrituras;
el mapper rechaza históricos como OTP de login y el modelo rechaza desafíos
sin revisión. No VALIDATE las constraints de contexto/FK sobre históricos a
ciegas. Las filas ficticias del test incluyen una cuenta sin credencial.
No drop/rename de objetos presentes, grants, baseline ni repair.

## Prueba y fuente
Copia de `docs/arquitectura/schema.sql`, SHA-256 `5f771377b423b3e201044706902b1e2313adeb3e3f4d1b8ace35cddead93b62c`,
en recursos `mfa/base-observada/schema.sql`. Las tablas relevantes se contrastaron
con el inventario; no es un dump ni certifica que todas las 22 tablas remotas sean
idénticas. Solo se ejecuta en un contenedor PostgreSQL 17.6 desechable.
La evidencia de ejecución se registra en `docs/evidencia/EP-04-T001.md`.

## Qué recibe Christian
Revisar el diff y el SHA de este PR, junto a V103, inventario y evidencia.
Repetir el contraste antes de la ventana para detectar cambios posteriores.
Aplicar como parte del paquete coordinado, con backup confirmado y registro de
historial acordado: NO ejecutar V1–V4 sobre la base manual ni activar Flyway sin
ese procedimiento. La migración permanece fuera de db/migration hasta entonces.
Postaplicación: validar mappings con ddl-auto=validate, columnas nuevas, FK
existente y CHECK; comprobar grants para g7_runtime. V103 aún NO aplicada en nube.
