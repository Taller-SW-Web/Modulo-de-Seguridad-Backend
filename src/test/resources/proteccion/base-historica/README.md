# Copias históricas para pruebas de protección

Origen: repositorio de documentación, SHA f9afa1b7299b18ebdda94f349343b5ab12ef6def,
backend/src/main/resources/db/migration. Son copias exactas, sin modificación:

| Fuente | Blob Git |
|---|---|
| V1__usuario_y_roles.sql | 2483bb71a710d45a2d069b9121f07416bca3da3a |
| V2__credenciales_otp_tokens.sql | 8c2dbd6a7eccb4e46e6dcafc79418c96444408ed |
| V3__sesion_y_bloqueo.sql | b166dd9b462e0eecd10348a9de4e2b151b9d4f12 |
| V4__auditoria_y_integracion.sql | 3734c7879fc0c9e56ef6ab65b7ba9e85acb754e8 |

ProteccionJpaPostgresTest ejecuta esas fuentes solo en un contenedor PostgreSQL,
inserta un bloqueo histórico de prueba y aplica V104 propuesta. JDBC ejecuta
cada fuente completa para respetar funciones/triggers con bloques $$ de V4.
No se trasladan a migraciones runtime (Christian coordina ese proceso), ni se
usa V100, historial supuesto, Supabase, secretos o la aplicación Maven antigua.
El esquema resultante no acredita equivalencia con el DDL manual de Supabase.
