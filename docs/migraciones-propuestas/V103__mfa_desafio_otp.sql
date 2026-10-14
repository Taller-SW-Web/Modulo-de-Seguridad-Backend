-- PROPUESTA EP-04-T001, contrastada con metadatos reales el 14-oct-2026.
-- Fuera de db/migration: Christian aplica tras revisión, backup y ventana.
-- Base manual: otp.expiracion, canal_otp, desafio_id y FK compuesta ya existen.
-- No modifica cuentas/credenciales ni ejecuta V1–V4 sobre la base compartida.
ALTER TABLE desafio_mfa
    ADD COLUMN credencial_id uuid,
    ADD COLUMN credencial_version bigint;
-- Identidad de revisión OPACA validada por el port de Juan José; no asumir
-- credencial.id. La PK física observada es usuario_id. NOT VALID conserva
-- históricos sin revisión/credencial: la aplicación no los autoriza.
ALTER TABLE desafio_mfa
    ADD CONSTRAINT ck_desafio_mfa_revision
        CHECK (credencial_id IS NOT NULL AND credencial_version IS NOT NULL AND credencial_version >= 0) NOT VALID,
    ADD CONSTRAINT fk_desafio_mfa_credencial_usuario
        FOREIGN KEY (usuario_id) REFERENCES credencial(usuario_id) NOT VALID;
ALTER TABLE otp
    ADD COLUMN destino varchar(255),
    ADD COLUMN motivo varchar(50),
    ADD COLUMN invalidado_en timestamptz,
    ADD COLUMN hash_key_id varchar(50);
-- No inventar destino, propósito ni HMAC para históricos. NULL sigue inválido
-- para login; NOT VALID preserva filas previas y controla nuevas escrituras.
ALTER TABLE otp
    ADD CONSTRAINT ck_otp_proposito
        CHECK (motivo IS NOT NULL AND length(btrim(motivo)) > 0) NOT VALID,
    ADD CONSTRAINT ck_otp_login_mfa_contexto
        CHECK (motivo <> 'LOGIN_MFA' OR
            (desafio_id IS NOT NULL AND hash_key_id IS NOT NULL AND length(btrim(hash_key_id)) > 0
             AND destino IS NOT NULL AND length(btrim(destino)) > 0)) NOT VALID,
    ADD CONSTRAINT ck_otp_invalidado_en
        CHECK (invalidado_en IS NULL OR invalidado_en >= creado_en) NOT VALID;
ALTER TABLE otp VALIDATE CONSTRAINT ck_otp_invalidado_en;
-- Reutilizar idx_otp_usuario_fecha y otp_desafio_id_usuario_id_fkey.
CREATE INDEX idx_otp_desafio_creado_en ON otp(desafio_id, creado_en DESC, id DESC);
COMMENT ON COLUMN desafio_mfa.credencial_id IS 'Identidad opaca de revisión del dueño de credenciales, sin FK a una columna física id';
COMMENT ON COLUMN desafio_mfa.credencial_version IS 'Versión obtenida del port de credenciales al emitir el desafío';
COMMENT ON COLUMN otp.hash_key_id IS 'Identificador de clave HMAC externa; nunca el secreto';
COMMENT ON COLUMN otp.motivo IS 'Propósito explícito; NULL histórico no autoriza login';
COMMENT ON COLUMN otp.destino IS 'Destino del OTP nuevo; no rellenar desde datos históricos';
COMMENT ON COLUMN otp.invalidado_en IS 'Reenvío/agotamiento invalidan sin confundirlo con consumo';
