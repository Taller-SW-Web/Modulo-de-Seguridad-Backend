-- Fixture local mínima basada en la forma histórica V2; no es el esquema Supabase G7.
CREATE TABLE usuario (
    id uuid PRIMARY KEY
);

CREATE TABLE credencial (
    id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    usuario_id uuid NOT NULL UNIQUE REFERENCES usuario(id) ON DELETE CASCADE,
    password_hash varchar(255) NOT NULL,
    salt varchar(255),
    algoritmo varchar(50) NOT NULL,
    requiere_cambio boolean NOT NULL DEFAULT false,
    intentos_fallidos integer NOT NULL DEFAULT 0 CHECK (intentos_fallidos >= 0),
    bloqueada_hasta timestamptz,
    creado_en timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE password_historial (
    id uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    credencial_id uuid NOT NULL REFERENCES credencial(id) ON DELETE CASCADE,
    password_hash varchar(255) NOT NULL,
    creado_en timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_password_historial_credencial_creado_en
    ON password_historial (credencial_id, creado_en DESC);
