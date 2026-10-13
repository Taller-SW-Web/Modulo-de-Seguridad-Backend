-- Fixture mínima PostgreSQL de las tablas relevantes de V2/V3. No es el esquema remoto
-- ni reemplaza el traslado/verificación de V1-V4 por Christian. Solo se usa en tests.
CREATE TABLE usuario (id uuid PRIMARY KEY);
CREATE TABLE credencial (id uuid PRIMARY KEY, usuario_id uuid NOT NULL REFERENCES usuario(id));
CREATE TABLE desafio_mfa (
    id uuid PRIMARY KEY, usuario_id uuid NOT NULL REFERENCES usuario(id),
    hash_token varchar(255) NOT NULL UNIQUE, expiracion timestamptz NOT NULL,
    consumido boolean NOT NULL DEFAULT false, creado_en timestamptz NOT NULL,
    CONSTRAINT ck_desafio_mfa_expiracion CHECK (expiracion > creado_en)
);
CREATE UNIQUE INDEX uq_desafio_id_usuario ON desafio_mfa(id, usuario_id);
CREATE TABLE otp (
    id uuid PRIMARY KEY, usuario_id uuid NOT NULL REFERENCES usuario(id),
    hash_codigo varchar(255) NOT NULL, destino varchar(255) NOT NULL,
    canal varchar(50) NOT NULL, motivo varchar(50) NOT NULL,
    expira_en timestamptz NOT NULL, usado boolean NOT NULL DEFAULT false,
    intentos integer NOT NULL DEFAULT 0, creado_en timestamptz NOT NULL,
    CONSTRAINT ck_otp_intentos CHECK (intentos >= 0),
    CONSTRAINT ck_otp_expira_en CHECK (expira_en > creado_en)
);
INSERT INTO usuario VALUES ('00000000-0000-0000-0000-000000000091');
INSERT INTO desafio_mfa VALUES (
    '00000000-0000-0000-0000-000000000092','00000000-0000-0000-0000-000000000091',
    repeat('f',64),'2020-01-01 00:05:00+00',false,'2020-01-01 00:00:00+00'
);
INSERT INTO otp VALUES (
    '00000000-0000-0000-0000-000000000093','00000000-0000-0000-0000-000000000091',
    repeat('f',64),'historico@ejemplo.test','EMAIL','LOGIN_MFA','2020-01-01 00:05:00+00',
    false,0,'2020-01-01 00:00:00+00'
);
