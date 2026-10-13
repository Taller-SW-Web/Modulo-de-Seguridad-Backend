-- PROPUESTA de EP-04-T001. No está en db/migration y no se aplica automáticamente.
-- Base esperada: V2/V3 conservadas; revisar historial/esquema con Christian antes de promover.
-- En V2 otp usa expira_en y canal varchar(50), distintos del DDL descriptivo.
-- No cambia credencial/usuario, no inventa revisión ni convierte históricos.

ALTER TABLE desafio_mfa
    ADD COLUMN credencial_id uuid,
    ADD COLUMN credencial_version bigint;

-- NOT VALID preserva históricos sin revisión, pero exige revisión en nuevas escrituras.
-- La aplicación rechaza esos históricos; no se validará la constraint sobre ellos a ciegas.
ALTER TABLE desafio_mfa
    ADD CONSTRAINT ck_desafio_mfa_revision
        CHECK (credencial_id IS NOT NULL AND credencial_version IS NOT NULL AND credencial_version >= 0) NOT VALID,
    ADD CONSTRAINT fk_desafio_mfa_credencial FOREIGN KEY (credencial_id) REFERENCES credencial(id) NOT VALID;

ALTER TABLE otp
    ADD COLUMN desafio_id uuid,
    ADD COLUMN invalidado_en timestamptz,
    ADD COLUMN hash_key_id varchar(50);

ALTER TABLE otp
    ADD CONSTRAINT fk_otp_desafio_usuario FOREIGN KEY (desafio_id, usuario_id)
        REFERENCES desafio_mfa(id, usuario_id) NOT VALID,
    ADD CONSTRAINT ck_otp_login_mfa_contexto
        CHECK (motivo <> 'LOGIN_MFA' OR
            (desafio_id IS NOT NULL AND hash_key_id IS NOT NULL AND length(btrim(hash_key_id)) > 0)) NOT VALID,
    ADD CONSTRAINT ck_otp_invalidado_en
        CHECK (invalidado_en IS NULL OR invalidado_en >= creado_en) NOT VALID;

-- Los campos recién agregados son nulos en históricos: estas FK/check son validables.
-- La revisión previa debe contrastar ese supuesto si la base fue creada con DDL manual.
ALTER TABLE desafio_mfa VALIDATE CONSTRAINT fk_desafio_mfa_credencial;
ALTER TABLE otp VALIDATE CONSTRAINT fk_otp_desafio_usuario;
ALTER TABLE otp VALIDATE CONSTRAINT ck_otp_invalidado_en;

CREATE INDEX idx_otp_usuario_creado_en ON otp(usuario_id, creado_en);
CREATE INDEX idx_otp_desafio_creado_en ON otp(desafio_id, creado_en DESC, id DESC);

COMMENT ON COLUMN desafio_mfa.credencial_version IS 'Revisión obtenida del dueño de credenciales al emitir el desafío';
COMMENT ON COLUMN otp.hash_key_id IS 'Identificador de clave HMAC externa; nunca el secreto';
COMMENT ON COLUMN otp.invalidado_en IS 'Reenvío/agotamiento invalidan sin confundirlo con consumo exitoso';
