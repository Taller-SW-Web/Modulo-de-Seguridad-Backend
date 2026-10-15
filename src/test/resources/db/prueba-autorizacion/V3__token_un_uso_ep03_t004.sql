-- SOLO PRUEBAS: reproduce token_un_uso observado y la columna de contexto propuesta por T004.
CREATE TYPE proposito_token AS ENUM (
    'VERIFICAR_CORREO', 'CAMBIO_CORREO', 'RECUPERACION', 'DESBLOQUEO', 'ACTIVACION'
);

CREATE TABLE token_un_uso (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    proposito proposito_token NOT NULL,
    usuario_id uuid NOT NULL,
    hash_token varchar(255) NOT NULL,
    destino varchar(254),
    expiracion timestamptz NOT NULL,
    usado boolean NOT NULL DEFAULT false,
    creado_en timestamptz NOT NULL DEFAULT now(),
    contexto_id uuid,
    CONSTRAINT ck_token_un_uso_destino CHECK ((proposito = 'CAMBIO_CORREO') = (destino IS NOT NULL)),
    CONSTRAINT ck_token_un_uso_expiracion CHECK (expiracion > creado_en)
);
CREATE UNIQUE INDEX uq_token_un_uso_hash ON token_un_uso (hash_token);
CREATE INDEX idx_token_un_uso_usuario_proposito ON token_un_uso (usuario_id, proposito);
