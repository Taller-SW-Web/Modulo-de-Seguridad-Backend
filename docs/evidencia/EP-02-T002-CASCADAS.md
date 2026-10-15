# EP-02-T002 — Ajuste independiente de cascadas V102

15-oct-2026. Rama codex/ep-02-t002-cascadas; base develop
87fda630385a2e96ab4bd16b80f19f53c67cc33f. Refiere al mismo ID EP-02-T002,
no crea otra tarea ni amplía #29. Pendiente autorizado en §5 del plan local
PLAN-BLOQUES-H3-LUIS.md y CONTINUIDAD §26.

## Cambio

Se corrige el BORRADOR fuera de db/migration:
- usuario → sesion_familia: ON DELETE CASCADE.
- sesion_familia → acceso_sesion: FK compuesta con ON DELETE CASCADE.
- sesion_familia → token_refresco: FK compuesta con ON DELETE CASCADE.

Se conservan backfill, pertenencia usuario/familia, índices y RLS. El borrado
físico lo decide Eva; no se añade un endpoint ni borrado por rotación/logout.
Se conserva la procedencia de la propuesta original de Jose Luis ya incorporada
en #24; el cambio correctivo queda separado y revisable.

## Verificación real

```powershell
.\gradlew.bat test --tests '*MigracionV102PostgresTest' --tests '*SesionPersistenciaPostgresTest' --console=plain --no-daemon
```

Java 21.0.10 / Gradle 8.14.3 / Docker 29.8.2 / PostgreSQL 17.6 desechable.
BUILD SUCCESSFUL en 58 s: **24 pruebas, 0 fallos/errores/omitidas**.
10 migración (dos casos nuevos) +14 adaptador. Borrado de familia conserva
usuario y otra familia; borrado de usuario recorre accesos/refrescos sin huérfanos;
rollback restaura las filas; otra cuenta y sus datos permanecen.
Las regresiones conservan FK de pertenencia, backfill, validación y RLS.

SHA-256 nuevo borrador: 289DF4D6B0CCCCF37EF7B980DA20AAF23F32E8C4A5AF35697CFC1D31A9BBAED9.
Informes: build/reports/tests/test/index.html y XML.

## Puerta de aplicación: Christian

No se consultó ni ejecutó SQL en Supabase en esta sesión.
Esta modificación NO confirma que el historial remoto siga vacío para V102.
Antes de utilizarla, Christian comprueba historial/checksum y backup/ventana:
si V102 YA se aplicó, NO sustituir checksum ni hacer repair; convertir el
ajuste en una migración NUEVA revisada. El borrador nunca se ejecuta desde aquí.

Estado: corrección local preparada para revisión, no despliegue ni cierre nuevo.

## Publicación manual en Git Bash

```bash
cd /d/desarrollo-uni/ciclo8/Taller/Modulo-de-Seguridad/Backend-auth-service/.worktrees/ep-02-t002-cascadas
git add docs/migraciones-propuestas/V102__sesion_familia_acceso.sql \
  docs/migraciones-propuestas/REVISION-V102.md \
  src/test/java/com/marketplace/auth_service/sesion/adapter/out/persistence/MigracionV102PostgresTest.java \
  docs/evidencia/EP-02-T002-CASCADAS.md
git diff --cached --stat
git commit -m "fix: completar cascadas del borrador V102 (EP-02-T002)"
git push -u origin codex/ep-02-t002-cascadas
```

## PR a develop

Título: EP-02-T002: completar cadena de cascadas del borrador V102

Corrige usuario→familia y familia→acceso/refresco con FK compuestas en cascada.
Conserva pertenencia, backfill y RLS. 24 pruebas PostgreSQL, sin fallos/omisiones,
incluido rollback y conservación de otra cuenta.

Solo modifica una propuesta local. Christian debe comprobar historial/checksum:
si V102 ya está aplicada, trasladar el ajuste a migración nueva.
Sin SQL remoto ni ampliación de #29.
