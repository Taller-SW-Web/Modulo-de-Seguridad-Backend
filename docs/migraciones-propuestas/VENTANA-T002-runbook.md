# Ventana T002 — runbook BORRADOR (pendiente: backup, día/hora, SQL finales)

Ejecuta Christian con identidad migradora. Nada aquí se ha aplicado.
Precondición: los 6 SQL de `docs/migraciones-propuestas/` confirmados como
finales por sus dueños (V101 Juan José, V102 Jose Luis, V103 Luis,
V104 Luis, V105 Juan José, V106 Christian) y el catálogo
`db/autorizacion/R__catalogo_autorizacion.sql` (Sergio).

## Variables (solo en el entorno local de la ventana, nunca en git ni en el chat)

| Variable | Formato | Uso |
|---|---|---|
| `DB_MIGRATION_JDBC_URL` | `jdbc:postgresql://<host>:5432/postgres?sslmode=verify-full` | Flyway (pasos 1–2) |
| `DB_MIGRATION_USERNAME` / `DB_MIGRATION_PASSWORD` | identidad migradora | Flyway |
| `DB_ADMIN_PSQL_URL` | `postgresql://<admin>@<host>:5432/postgres?sslmode=verify-full` (URI libpq, **no** `jdbc:`) | psql (pasos 0, 3 y 4) |
| `DB_RUNTIME_PSQL_URL` | URI libpq con `g7_runtime` (por el pooler: `g7_runtime.<project-ref>`), la **misma host/puerto que usará el despliegue** | verificación (paso 4) |

psql no acepta URLs `jdbc:`; la contraseña la pide psql (o `PGPASSWORD` solo en esa terminal).

## 0. Prechequeos (solo lectura, verificados 15-oct-2026)

V101–V106 existen en `docs/migraciones-propuestas/` (V102 con cascadas del
PR #31); sus 8 columnas y 4 tablas objetivo están ausentes en remoto, sin
conflictos ni versiones aplicadas.

1. Backup/PITR disponible en el dashboard Supabase. Anotar hora **y cómo se restaura**.
2. `SELECT version();` → PostgreSQL 17.x esperado.
3. `SELECT * FROM flyway_schema_history;` debe FALLAR (sin historial = base
   manual, como en DAT-01). Si existe historial, detener y renumerar.
4. Repetir el chequeo de familias incoherentes:
   `SELECT familia_id FROM token_refresco GROUP BY 1 HAVING count(DISTINCT usuario_id) > 1;`
   debe dar 0 filas.
5. Anotar el estado actual del catálogo (lo corregirá R__ sin borrar filas):
   `SELECT codigo, nombre FROM rol ORDER BY 1; SELECT count(*) FROM permiso; SELECT count(*) FROM rol_permiso;`
6. Anotar qué rol es dueño de las tablas (será `migrador` en el paso 3):
   `SELECT DISTINCT tableowner FROM pg_tables WHERE schemaname = 'public';`

## 1. Baseline explícito (una vez, identidad migradora)

```
flyway -url="$DB_MIGRATION_JDBC_URL" -user="$DB_MIGRATION_USERNAME" \
  -password="$DB_MIGRATION_PASSWORD" -locations="filesystem:db/baseline-vacio" \
  baseline -baselineVersion=100 -baselineDescription="esquema manual verificado contra V1-V4"
```

Solo explícito; nunca `baseline-on-migrate=true`, repair ni DROP.

## 2. Migrate V101 → V106 + catálogo R__

Copiar los 6 archivos revisados a `src/main/resources/db/migration/` en el PR
de la ventana y ejecutar con el mismo Flyway CLI del paso 1 (no deja ningún
proceso levantado):

```
flyway -url="$DB_MIGRATION_JDBC_URL" -user="$DB_MIGRATION_USERNAME" \
  -password="$DB_MIGRATION_PASSWORD" \
  -locations="filesystem:src/main/resources/db/migration,filesystem:src/main/resources/db/autorizacion" \
  info migrate info
```

Orden Flyway: versionadas 101 → 106 y después la repetible
`R__catalogo_autorizacion` (idempotente: inserta lo que falta y corrige
etiquetas, no borra). Si una versión ya está ocupada en el historial,
renumerar antes (no reemplazar la aplicada).

Alternativa equivalente: el perfil `migration` (mismas ubicaciones, sin servidor web):
`java -jar app.jar --spring.profiles.active=migration`; comprobar que el proceso termina.

## 3. Grants (archivo `grants-g7-runtime.sql`)

Ejecutar con psql como identidad administradora dueña de las tablas, NUNCA como
`g7_runtime`, después del paso 2:

```
psql "$DB_ADMIN_PSQL_URL" -v migrador=<dueño anotado en 0.6> \
  -f docs/migraciones-propuestas/grants-g7-runtime.sql
```

Luego, en una sesión psql de esa misma identidad: `\password g7_runtime`
(la contraseña no se escribe en archivos, historial ni log de sentencias) y
guardarla solo en el gestor de secretos del despliegue (`DB_PASSWORD`).

El script es transaccional y repetible: crea el rol solo si falta, revoca a
PUBLIC/anon/authenticated lo existente **y los privilegios por defecto**
del migrador, deja auditoría en SELECT+INSERT, catálogo en SELECT y recrea
27 políticas RLS (22 operativas, 3 de catálogo en lectura, 2 de auditoría).

## 4. Verificación

Como administrador:

```
SELECT has_table_privilege('anon', 'usuario', 'SELECT');          -- f
SELECT count(*) FROM pg_tables WHERE schemaname='public' AND rowsecurity;  -- 26
SELECT count(*) FROM pg_policies WHERE 'g7_runtime' = ANY(roles); -- 27
```

Con el rol runtime real, **por la misma URL que usará el despliegue**
(`psql "$DB_RUNTIME_PSQL_URL"`), dentro de `BEGIN; … ROLLBACK;` para no dejar datos:

1. `INSERT INTO auditoria_seguridad (accion, resultado, actor_tipo) VALUES ('SESION_FALLIDA','FALLO','SISTEMA');` → OK.
2. `UPDATE auditoria_seguridad SET accion = accion;` → rechazado (sin privilegio; además el trigger).
3. `DELETE FROM auditoria_seguridad;` y `TRUNCATE auditoria_seguridad;` → rechazados.
4. `SELECT count(*) FROM rol;` → OK; `DELETE FROM rol_permiso;` → rechazado.
5. `SELECT count(*) FROM usuario;` → OK; `CREATE TABLE x(i int);` → rechazado.
6. `flyway info` muestra 100 (baseline) + 101–106 y R__ aplicadas, 0 pendientes.

## Ensayo local (15-oct-2026)

Ensayado en PostgreSQL 16 local desechable (nunca Supabase), emulando los
privilegios por defecto de Supabase a `anon`/`authenticated` y RLS activo:
base observada + V101–V106 (cada una en su transacción, como Flyway) + R__ +
grants aplicados dos veces. 22/22 comprobaciones OK como `g7_runtime`, `anon`
y una tabla creada después por el migrador. Mutaciones detectadas: sin
`ALTER DEFAULT PRIVILEGES` la tabla futura queda visible para `anon`; con
UPDATE/DELETE en auditoría el runtime deja de ser solo-anexado.
