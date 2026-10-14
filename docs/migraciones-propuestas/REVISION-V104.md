# Revisión antes de promover V104

El PR #13 de Christian confirma DDL manual sin Flyway, 22 tablas y PostgreSQL
17.6. Las V1–V4 conservadas son un antecedente de 21 tablas; se ejecutan solo
en tests locales. No se supone que la nueva propuesta pueda aplicarse tal cual
a Supabase por haber pasado esas pruebas.

Christian debe adjuntar el inventario de las tablas de protección, el historial
o ausencia de historial, los índices/constraints de bloqueo y si ya existen
racha_fallo, huella_fallo o revision. Consultas de lectura para TEC-INFRA-T002:

```sql
SELECT table_name, column_name, data_type, udt_name, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN ('bloqueo', 'intento_login', 'racha_fallo', 'huella_fallo')
ORDER BY table_name, ordinal_position;

SELECT c.relname AS tabla, k.conname, k.convalidated,
       pg_get_constraintdef(k.oid) AS definicion
FROM pg_constraint k
JOIN pg_class c ON c.oid = k.conrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relname IN ('bloqueo', 'intento_login', 'racha_fallo', 'huella_fallo')
ORDER BY c.relname, k.conname;

SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename IN ('bloqueo', 'intento_login', 'racha_fallo', 'huella_fallo')
ORDER BY tablename, indexname;
```

Estas consultas no se ejecutaron desde EP-07-T001; no contienen filas de negocio.
Con ese resultado Luis ajusta el camino de migración y lo prueba sobre una copia
local del esquema observado, incluida su versión PostgreSQL. Christian revisa
el costo del ALTER DEFAULT gen_random_uuid() en bloqueo, respaldo, grants/RLS y
ventana de aplicación. No baseline automático, repair, DROP ni IF NOT EXISTS para
ocultar incompatibilidades; si V104 ya está ocupada se acuerda nueva versión.

El rol g7_runtime requiere acceso acotado a tablas nuevas y políticas para sus
operaciones de servidor, sin grants a anon/authenticated. Los índices PK/UNIQUE
cubren consultas/FK de huellas por usuario/racha; no añadir índices duplicados.
La migración no escribe contadores en usuario ni intenta purgar rachas por fecha.
