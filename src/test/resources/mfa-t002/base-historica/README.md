# Base completa de prueba de componentes MFA T002

Copias exactas de V1–V4 conservadas en el repo de documentación, revisión
f9afa1b7299b18ebdda94f349343b5ab12ef6def, backend/src/main/resources/db/migration.

| Fuente | Blob Git |
|---|---|
| V1__usuario_y_roles.sql | 2483bb71a710d45a2d069b9121f07416bca3da3a |
| V2__credenciales_otp_tokens.sql | 8c2dbd6a7eccb4e46e6dcafc79418c96444408ed |
| V3__sesion_y_bloqueo.sql | b166dd9b462e0eecd10348a9de4e2b151b9d4f12 |
| V4__auditoria_y_integracion.sql | 3734c7879fc0c9e56ef6ab65b7ba9e85acb754e8 |

La prueba aplica las cuatro fuentes y V103 propuesta exclusivamente en PostgreSQL
16.6 desechable. JPA valida sus mappings; no V100 ni baseline. Las cuentas y
credenciales insertadas son referencias de fixture; los proveedores de cuentas,
roles y revisión son dobles definidos únicamente en @TestConfiguration.
No prueba las capacidades de otros dueños ni equivalencia con Supabase/PG 17.6.
No trasladar estas copias al historial runtime: Christian coordina ese proceso.
