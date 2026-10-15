# Entrega V102 — EP-02-T002

## Contenido
`V102__sesion_familia_acceso.sql`: crea `sesion_familia` (raíz estable, UNIQUE
compuesta, CHECK de coherencia revocada) y `acceso_sesion` (jti PK, FK
compuesta a la familia, CHECK de expiración, índices por familia y expiración);
backfill de una raíz por `familia_id` existente con revocación histórica
conservada; FK compuesta `fk_token_refresco_familia` con VALIDATE posterior.
Reutiliza `uq_token_refresco_hash` e índices por familia/usuario existentes.

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

## Prueba y fuente
- Base previa mínima (`sesion/esquema-previo-v102.sql`: enum `estado_cuenta`,
  `usuario`, `token_refresco` observados) + coherentes/incoherentes de prueba.
- `SesionPersistenciaPostgresTest`: backfill (revocada/activa), roundtrip del
  adaptador, FK compuesta, CHECKs, jti único, objetos del diseño y MANDATORY.
- `MigracionV102AbortadaPostgresTest`: incoherentes abortan sin raíz parcial.
- En este equipo (sin Docker) quedan omitidas; corren en CI/Docker.

## Qué recibe Christian
Revisar el diff y esta revisión junto a V102 y la evidencia `EP-02-T002`.
Repetir el contraste contra el historial real antes de la ventana (V101 puede
mover el orden; no reemplazar versiones aplicadas). Aplicar como parte del
paquete coordinado, con backup confirmado: NO ejecutar V1–V4 sobre la base
manual ni activar Flyway sin ese procedimiento. La migración permanece fuera
de `db/migration` hasta entonces. V102 aún NO aplicada en nube.
