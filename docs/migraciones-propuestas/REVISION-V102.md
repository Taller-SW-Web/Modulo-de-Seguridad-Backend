# Entrega V102 — EP-02-T002

## Contenido
`V102__sesion_familia_acceso.sql`: crea `sesion_familia` (raíz estable, UNIQUE
compuesta, CHECK de coherencia revocada) y `acceso_sesion` (jti PK, FK
compuesta a la familia, CHECK de expiración, índices por familia y expiración);
backfill de una raíz por `familia_id` existente con revocación histórica
conservada; FK compuesta `fk_token_refresco_familia` con VALIDATE posterior.
Reutiliza `uq_token_refresco_hash` e índices por familia/usuario existentes.
Agrega índice de familias por usuario, habilita RLS en las dos tablas nuevas
y revoca acceso de PUBLIC/anon/authenticated si estos roles existen. No crea
roles ni concede permisos al runtime.

## Límites entre dueños
- `sesion_familia.usuario_id` referencia `usuario(id)` en SQL; en Java viaja
  como UUID escalar. La cuenta se consulta solo mediante Eva: ningún
  repositorio/entidad de `usuario` en la capacidad `sesion`.
- V102 no toca `credencial`, `desafio_mfa`, `otp`, auditoría ni outbox
  (V101/V103–V106 pertenecen a sus dueños). Sin grants, baseline, repair,
  DROP/rename ni semillas.

## Históricos y aplicación
- Backfill exige un solo usuario por familia: con dos usuarios la migración
  aborta con error (`familia_id con más de un usuario_id`) sin dejar raíz
  parcial. No se elige usuario arbitrario ni se infiere familia por sub.
- Una revocación histórica cualquiera de la familia deja la raíz revocada
  (`revocada_en = now()` como marca de migración, no como fecha del hecho).
- Raíz `creado_en` = mínimo histórico de la familia; no se renumeran familias.
- Validación previa bajo lock `SHARE ROW EXCLUSIVE` de `token_refresco`;
  agrupación por `(familia_id, usuario_id)` después de descartar inconsistencias.
  PostgreSQL no necesita un agregado `min(uuid)` para seleccionar al titular.
- Ejecutar el archivo completo en una transacción, conservando los bloques `DO`.
  Flyway PostgreSQL debe mantener ejecución transaccional. Ante cualquier error
  se revierte también el DDL; no continuar con una migración parcialmente aplicada.
- No fabricar asociaciones para tokens de acceso históricos: `acceso_sesion`
  empieza vacía. Esos accesos no prueban su familia y requieren reautenticación
  para operaciones que la exijan.

## Prueba y fuente
- Esquema completo de referencia en `sesion/base-observada/schema.sql`, copia
  de `docs/arquitectura/schema.sql`; SHA-256
  `5f771377b423b3e201044706902b1e2313adeb3e3f4d1b8ace35cddead93b62c`.
  Históricos sintéticos coherentes/incoherentes, sin datos de Supabase.
- `SesionPersistenciaPostgresTest`: backfill (revocada/activa), roundtrip del
  adaptador, FK compuesta, CHECKs, jti único, objetos del diseño y MANDATORY.
- `MigracionV102PostgresTest`: backfill sin datos/con históricos, revocaciones,
  rechazo de familias mixtas y rollback completo incluso después de crear tablas;
  índices/FK validados y RLS/privilegios públicos.
- Pruebas PostgreSQL 17.6 obligatorias, sin condición de omisión. Locks, commit,
  rollback y MANDATORY se comprueban sobre el bean real del adaptador JPA.
  Resultados finales en `docs/evidencia/EP-02-T002.md`.

## Qué recibe Christian
Cadena de borrado del borrador corregida: usuario → sesion_familia y familia →
acceso_sesion/token_refresco mediante FK compuestas ON DELETE CASCADE.
No basta la primera cascada. La eliminación física la decide el dueño de usuario;
sesiones no agrega un endpoint de borrado ni elimina por rotación/logout.
Pruebas aisladas cubren borrado de familia, de usuario, conservación de otra
cuenta y rollback del borrado, además de backfill/integridad anteriores.

Antes de usar este diff, comprobar historial/checksum real. Si V102 ya fue
aplicada, NO sustituir el archivo aplicado ni hacer repair: trasladar estas
acciones a una nueva migración revisada con Christian. Esta sesión solo
modifica un borrador local y no confirma nuevamente el estado de Supabase.

Revisar el diff y esta revisión junto a V102 y la evidencia `EP-02-T002`.
Repetir el contraste contra el historial real antes de la ventana (V101 puede
mover el orden; no reemplazar versiones aplicadas). Aplicar como parte del
paquete coordinado, con backup confirmado: NO ejecutar V1–V4 sobre la base
manual ni activar Flyway sin ese procedimiento. La migración permanece fuera
de `db/migration` hasta entonces. V102 aún NO aplicada en nube.

Para el runtime, revisar políticas y permisos mínimos de SELECT/INSERT/UPDATE
en familia/refresco y SELECT/INSERT en acceso; no conceder owner, BYPASSRLS o
permisos públicos. Limpieza automática y su DELETE quedan para un incremento
posterior. Sin políticas/grants del rol real, RLS deniega acceso a las tablas nuevas.

La revisión local consume el esquema de referencia ya contrastado el 14-oct;
no constituye una nueva inspección del esquema remoto ni confirma que siga igual.
