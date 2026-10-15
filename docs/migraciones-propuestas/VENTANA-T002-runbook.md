# Ventana T002 — runbook BORRADOR (pendiente: backup, día/hora, SQL finales)

Ejecuta Christian con identidad migradora. Nada aquí se ha aplicado.
Precondición: los 6 SQL de `docs/migraciones-propuestas/` confirmados como
finales por sus dueños (V101 Juan José, V102 Jose Luis, V103 Luis,
V104 Luis, V105 Juan José, V106 Christian).

## 0. Prechequeos (solo lectura, verificados 15-oct-2026)

V101–V106 existen en `docs/migraciones-propuestas/` (V102 con cascadas del
PR #31); sus 8 columnas y 4 tablas objetivo están ausentes en remoto, sin
conflictos ni versiones aplicadas.

1. Backup/PITR disponible en el dashboard Supabase. Anotar hora.
2. `SELECT version();` → PostgreSQL 17.x esperado.
3. `SELECT * FROM flyway_schema_history;` debe FALLAR (sin historial = base
   manual, como en DAT-01). Si existe historial, detener y renumerar.
4. Repetir el chequeo de familias incoherentes:
   `SELECT familia_id FROM token_refresco GROUP BY 1 HAVING count(DISTINCT usuario_id) > 1;`
   debe dar 0 filas.

## 1. Baseline explícito (una vez, identidad migradora)

```
flyway -url="$DB_MIGRATION_JDBC_URL" -user="$DB_MIGRATION_USERNAME" \
  -password="$DB_MIGRATION_PASSWORD" -locations="filesystem:db/baseline-vacio" \
  baseline -baselineVersion=100 -baselineDescription="esquema manual verificado contra V1-V4"
```

Solo explícito; nunca `baseline-on-migrate=true`, repair ni DROP.

## 2. Migrate V101 → V106 en orden

Copiar los 6 archivos revisados a `src/main/resources/db/migration/` en el PR
de la ventana y ejecutar con el perfil `migration`:

```
SPRING_PROFILES_ACTIVE=migration ./gradlew bootRun
```

Orden Flyway por versión: 101, 102, 103, 104, 105, 106. Si una versión ya está
ocupada en el historial, renumerar antes (no reemplazar la aplicada).

## 3. Grants (archivo grants-g7-runtime.sql de propuestas, con rol real)

Ejecutar como identidad administradora, NUNCA como `g7_runtime`:

```
psql "$DB_MIGRATION_JDBC_URL" -f docs/migraciones-propuestas/grants-g7-runtime.sql
```

## 4. Verificación con el rol runtime real

1. `SET ROLE g7_runtime; INSERT INTO auditoria_seguridad (...)` → OK.
2. `UPDATE auditoria_seguridad SET ...` → rechazado (trigger + sin privilegio).
3. `DELETE/TRUNCATE` sobre auditoría → rechazados.
4. Lectura/escritura operativa en tablas de dominio → OK.
5. `flyway info` muestra 100 (baseline) + 101–106 aplicadas, 0 pendientes.
