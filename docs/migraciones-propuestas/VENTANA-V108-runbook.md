# Ventana V108 — `cambio_mfa` (FEAT-07 / PR #57)

**Ejecuta:** Christian Arancivia, con identidad migradora.
**Origen:** `feat/EP-04-mfa-v2` (PR #57), archivo `V108__cambio_mfa.sql`.
**Dependencias:** V102 (`sesion_familia`) y V103 (`otp`) ya aplicadas.
**Objetivo:** que #57 arranque en Render y que el cambio MFA v2 funcione con `g7_runtime`.
**Orden acordado:** V108 + grants → PR #57 → front #11.

No se usa `baseline-on-migrate`, `repair`, `reset` ni DDL manual fuera de Flyway.
No se copian contraseñas, cadenas de conexión ni tokens a este documento.

## 1. Prechequeos (solo lectura)

Comprobado el 22-oct-2026:

- `flyway_schema_history`: 100–107 + repetible `R__catalogo_autorizacion`; V108 ausente.
- `public.cambio_mfa`: no existe.
- `otp.cambio_mfa_id`: no existe.
- `g7_runtime`: existe.
- Políticas para `g7_runtime`: 28.
- `otp`: 27 filas.

Antes de ejecutar, confirmar que sigue igual y que no hay otra ventana en curso.
El plan free de Supabase no tiene backups automáticos: el riesgo se acepta porque
V108 es aditivo (tabla nueva + columna nullable) y no modifica filas existentes.

## 2. Aplicar V108 con Flyway

Usar el mismo Flyway CLI de la ventana T002 y una identidad migradora, desde un
worktree limpio de la rama del PR #57 (para que `db/migration` contenga V108):

```text
flyway -url="$DB_MIGRATION_JDBC_URL" -user="$DB_MIGRATION_USERNAME" ^
  -password="$DB_MIGRATION_PASSWORD" ^
  -locations="filesystem:src/main/resources/db/migration,filesystem:src/main/resources/db/autorizacion,filesystem:src/main/resources/db/identidad" ^
  info migrate info
```

Resultado esperado: V108 `SUCCESS`, 0 pendientes, sin cambios en 100–107 ni en R__.

## 3. Grants para `cambio_mfa`

V108 habilita RLS en `cambio_mfa`, pero no crea política para `g7_runtime`.
El script canónico `grants-g7-runtime.sql` ya se actualizó para incluir:

- `GRANT SELECT, INSERT, UPDATE, DELETE ON cambio_mfa TO g7_runtime`.
- `ALTER TABLE cambio_mfa ENABLE ROW LEVEL SECURITY`.
- Política `g7_runtime_total` para `cambio_mfa`.

Ejecutar el script completo como identidad administradora (es repetible):

```text
psql "$DB_ADMIN_PSQL_URL" -v migrador=<dueño de las tablas> ^
  -f docs/migraciones-propuestas/grants-g7-runtime.sql
```

Sin este paso, #57 puede arrancar pero el endpoint de cambio MFA falla por permisos.

## 4. Verificación

Como administrador:

```sql
SELECT version FROM flyway_schema_history ORDER BY installed_rank; -- incluye 108
SELECT to_regclass('public.cambio_mfa');                            -- no null
SELECT column_name FROM information_schema.columns
 WHERE table_schema='public' AND table_name='otp' AND column_name='cambio_mfa_id';
SELECT count(*) FROM pg_policies WHERE 'g7_runtime' = ANY(roles);    -- 29
SELECT privilege_type FROM information_schema.role_table_grants
 WHERE table_schema='public' AND table_name='cambio_mfa' AND grantee='g7_runtime';
```

Con `g7_runtime`, dentro de `BEGIN; ... ROLLBACK;`:

1. `SELECT count(*) FROM cambio_mfa;` → OK.
2. `INSERT`/`UPDATE`/`DELETE` de una fila de prueba con UUID y `familia_id` existentes → OK.
3. Confirmar que no puede escribir en `auditoria_seguridad`.

No dejar filas de prueba: usar `ROLLBACK` o borrar solo la fila creada por la prueba.

## 5. Ejecución real 22-oct-2026 (Christian)

- Prechequeo confirmado: historial 100–107 + R__, V108 pendiente, `cambio_mfa` y
  `otp.cambio_mfa_id` ausentes, 28 políticas, `otp` con 27 filas.
- Flyway 11.7.2: `Successfully applied 1 migration`, esquema en **v108**.
- Grants aplicados con el script canónico actualizado:
  - `cambio_mfa`: `SELECT, INSERT, UPDATE, DELETE` para `g7_runtime`.
  - Política `g7_runtime_total` (`ALL`) en `cambio_mfa`.
  - Políticas para `g7_runtime`: **29**.
  - `auditoria_seguridad`: `UPDATE` sigue denegado para `g7_runtime`.
- `ALTER ROLE g7_runtime ...` se omitió: el pooler de Supabase lo rechaza
  (`permission denied to alter role`), igual que en T002. Verificado después:
  `rolsuper=false`, `rolbypassrls=false`, `rolcanlogin=true`,
  `rolcreatedb=false`, `rolcreaterole=false`.
- `SET ROLE g7_runtime` no se pudo usar desde la identidad administradora del
  pooler para una prueba de escritura; la verificación se hizo con
  `has_table_privilege` y las políticas. La prueba real con la contraseña de
  `g7_runtime` queda para el arranque de #57 en Render.
- `cambio_mfa` quedó con 0 filas.

## 6. Cierre

- Anotar hora, versión de Flyway, resultado y conteo de políticas.
- Avisar al grupo y a David: V108 + grants aplicados; #57 puede fusionarse.
- Después de fusionar #57, comprobar arranque en Render y el recorrido de cambio MFA.
- Si algo falla, detenerse: no usar `repair` ni borrar tablas. Registrar el error y
  restaurar según el procedimiento acordado con el equipo.
