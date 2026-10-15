# Base de prueba del esquema manual

Copia sin editar de `docs/arquitectura/schema.sql` del repo documental.
Sus tablas de esta capacidad se contrastaron con metadatos reales el
14-oct-2026 (`INVENTARIO-V103-20261014.json` en `docs/migraciones-propuestas`).
No es un dump de datos, no acredita identidad completa de las 22 tablas remotas,
no es una migración V1 y se ejecuta únicamente en PostgreSQL desechable de pruebas.
Los datos históricos que se insertan son ficticios. No conectar tests a Supabase.

La misma copia se reutiliza en `CredencialJpaPostgresTest` (EP-03-T001).
Las tablas `credencial` y `password_historial` también se contrastaron con el
inventario de metadatos de V103 adjunto al PR #9. El inventario no contiene filas
de negocio.

Cada prueba aplica su migración propuesta en su propia base desechable: V103
para MFA y V101 para credenciales. Hibernate valida los mappings con
`ddl-auto=validate`. Estas comprobaciones no acreditan el historial de
migraciones ni los permisos de la base remota.
