-- Solo esquema previo mínimo para probar V102; no es semilla de runtime ni de Supabase.
-- Extrae usuario/token_refresco de la base observada (misma copia que EP-04-T001).
CREATE TYPE estado_cuenta AS ENUM ('ACTIVO', 'INACTIVO', 'BLOQUEADO', 'PENDIENTE_VERIFICACION');

CREATE TABLE IF NOT EXISTS "usuario" (
    "id" uuid NOT NULL DEFAULT gen_random_uuid(),
    "correo" varchar(254) NOT NULL,
    "nombres" varchar(100) NOT NULL,
    "apellidos" varchar(100) NOT NULL,
    "estado" estado_cuenta NOT NULL,
    PRIMARY KEY("id")
);

CREATE TABLE IF NOT EXISTS "token_refresco" (
    "jti" uuid NOT NULL DEFAULT gen_random_uuid(),
    "usuario_id" uuid NOT NULL,
    "familia_id" uuid NOT NULL,
    "hash_token" varchar(255) NOT NULL,
    "revocado" boolean NOT NULL DEFAULT false,
    "usado" boolean NOT NULL DEFAULT false,
    "expiracion" timestamptz NOT NULL,
    "creado_en" timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY("jti"),
    CONSTRAINT "ck_token_refresco_expiracion" CHECK ("expiracion" > "creado_en")
);
CREATE UNIQUE INDEX "uq_token_refresco_hash" ON "token_refresco" ("hash_token");
CREATE INDEX "idx_token_refresco_familia" ON "token_refresco" ("familia_id");
CREATE INDEX "idx_token_refresco_usuario" ON "token_refresco" ("usuario_id");
