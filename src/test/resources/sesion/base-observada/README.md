# Base desechable para EP-02-T002

`schema.sql` es copia byte a byte de `docs/arquitectura/schema.sql` del repositorio
de documentación, ya contrastado para MFA. SHA-256:
`5f771377b423b3e201044706902b1e2313adeb3e3f4d1b8ace35cddead93b62c`.

Se carga únicamente en PostgreSQL 17.6 de Testcontainers. No contiene filas de
producción ni se aplica en Supabase. Evita sustituir el esquema observado por
tablas mínimas inventadas. Las otras capacidades no se escanean como entidades
en la prueba JPA de sesiones.

Los históricos coherentes/incoherentes son sintéticos. V102 se ejecuta completa
en una sola transacción; si falla, se revierte también su DDL. Docker es obligatorio
para estas pruebas: no hay condición que las convierta en omitidas.
