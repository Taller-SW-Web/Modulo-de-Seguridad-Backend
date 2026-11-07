-- PROPUESTA FEAT-07. Requiere V102 y V103; Christian aplica tras revisión y ventana.
-- No modifica historias aplicadas ni concede acceso al navegador/Data API.
CREATE TABLE cambio_mfa (
    id uuid PRIMARY KEY,
    usuario_id uuid NOT NULL,
    familia_id uuid NOT NULL,
    habilitar boolean NOT NULL,
    hash_token varchar(64) NOT NULL UNIQUE CHECK (hash_token ~ '^[a-f0-9]{64}$'),
    creado_en timestamptz NOT NULL,
    expira_en timestamptz NOT NULL CHECK (expira_en = creado_en + interval '600 seconds'),
    consumido boolean NOT NULL DEFAULT false,
    invalidado_en timestamptz CHECK (invalidado_en IS NULL OR invalidado_en >= creado_en),
    otp_actual_id uuid,
    UNIQUE (id, usuario_id),
    FOREIGN KEY (familia_id,usuario_id) REFERENCES sesion_familia(id,usuario_id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uq_cambio_mfa_pendiente ON cambio_mfa(usuario_id)
    WHERE NOT consumido AND invalidado_en IS NULL;
CREATE INDEX idx_cambio_mfa_familia ON cambio_mfa(familia_id,usuario_id);
ALTER TABLE otp ADD COLUMN cambio_mfa_id uuid;
ALTER TABLE otp ADD CONSTRAINT fk_otp_cambio_mfa
    FOREIGN KEY (cambio_mfa_id,usuario_id) REFERENCES cambio_mfa(id,usuario_id) ON DELETE CASCADE;
ALTER TABLE otp ADD CONSTRAINT ck_otp_cambio_mfa CHECK (
    (motivo = 'CAMBIO_MFA' AND cambio_mfa_id IS NOT NULL AND desafio_id IS NULL
        AND hash_key_id IS NOT NULL AND destino IS NOT NULL AND canal = 'EMAIL')
    OR (motivo IS DISTINCT FROM 'CAMBIO_MFA' AND cambio_mfa_id IS NULL)) NOT VALID;
ALTER TABLE otp VALIDATE CONSTRAINT ck_otp_cambio_mfa;
CREATE INDEX idx_otp_cambio_mfa ON otp(cambio_mfa_id) WHERE cambio_mfa_id IS NOT NULL;
ALTER TABLE cambio_mfa ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE cambio_mfa FROM PUBLIC;
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='anon') THEN REVOKE ALL ON cambio_mfa FROM anon; END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN REVOKE ALL ON cambio_mfa FROM authenticated; END IF;
END $$;
-- El rol/políticas del runtime los confirma Christian en el entorno real; no usar auth.uid().
COMMENT ON TABLE cambio_mfa IS 'SPEC-10: operación MFA de 600 s ligada a familia; token solo por hash. Dueño Luis David.';
