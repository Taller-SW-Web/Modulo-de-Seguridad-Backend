-- SOLO PRUEBAS: satisface ddl-auto=validate al escanear el contexto compartido.
-- Columnas observadas en docs/arquitectura/schema.sql; el mapping y V101 se
-- prueban de forma completa e independiente en CredencialJpaPostgresTest.
CREATE TABLE credencial (
    usuario_id uuid PRIMARY KEY,
    hash_contrasena varchar(255) NOT NULL,
    ultimo_cambio timestamptz NOT NULL DEFAULT now(),
    requiere_cambio boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE password_historial (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id uuid NOT NULL,
    hash_contrasena varchar(255) NOT NULL,
    creado_en timestamptz NOT NULL DEFAULT now()
);
