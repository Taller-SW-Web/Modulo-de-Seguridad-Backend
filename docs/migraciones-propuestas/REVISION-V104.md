# Entrega V104 — EP-07-T001

## Contraste real (14-oct-2026)
Solo lectura de catálogos de Supabase `sghqhbakbnaapxdapmbc`; inventario en
`INVENTARIO-V104-20261014.json`, sin filas ni secretos.
Bloqueo tiene PK usuario_id, tipo_bloqueo, bloqueado_hasta, motivo y creado_en,
sin revision. Intento_login y sus tipos/índices coinciden con los mappings de
protección. La revisión añade revision al bloqueo y racha_fallo/huella_fallo;
no modifica usuario, credencial, sesiones ni tokens.

V104 conserva filas previas (incluidos bloqueos manuales/automáticos), asigna
una revisión UUID a cada bloqueo existente y deja activada RLS en tablas nuevas.
No elimina índices ni altera el estado del usuario. PK/FK cubren búsquedas de
racha/huella. Grants runtime pertenecen a Christian, no anon/authenticated.

## Prueba y límites
Copia de docs/arquitectura/schema.sql, SHA-256 `5f771377b423b3e201044706902b1e2313adeb3e3f4d1b8ace35cddead93b62c`, en
`proteccion/base-observada/schema.sql`. Solo las tablas relevantes se contrastaron:
no es un dump ni certifica toda la base. Contenedor PostgreSQL 17.6 desechable;
casos y resultado real en `docs/evidencia/EP-07-T001.md`.

## Entrega a Christian
Revisión de este PR/SHA, SQL V104, inventario y evidencia. Repetir lectura antes
de la ventana para confirmar ausencia de revision/tablas nuevas. Si ya existen,
parar y reconciliar historial; no ocultarlo con IF NOT EXISTS ni repair.
Con backup y paquete V101–V106 coordinados, aplicar con identidad migradora.
Después verificar revision NOT NULL, integridad/RLS, grants g7_runtime y validate
de mappings. Aplicación remota y grants siguen pendientes; no se hizo DDL remoto.
V104 permanece en docs/migraciones-propuestas hasta esa decisión.
