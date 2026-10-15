# Observed schema fixture for EP-03-T001

`schema.sql` is the unedited copy used by the test at `src/test/resources/mfa/base-observada/schema.sql`. Its source is `Modulo-de-Seguridad/docs/arquitectura/schema.sql`, the documented PostgreSQL schema for the G7 security module. The fixture's source and copy were checked for identical contents.

The `credencial` and `password_historial` table shapes were also compared with `INVENTARIO-V103-20261014.json`, the metadata-only Supabase catalog inventory attached to backend PR #9. The inventory contains schema metadata, not business rows.

The test runs on disposable PostgreSQL Testcontainers, applies proposed V101, and starts Hibernate with `ddl-auto=validate`. It does not connect to Supabase, contain copied database rows, or prove the remote migration history or permissions.
