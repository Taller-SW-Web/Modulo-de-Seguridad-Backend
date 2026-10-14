-- PROPUESTA EP-07-T001. No se ejecuta automáticamente ni acredita esquema remoto.
-- Requiere usuario/bloqueo de la base previamente contrastada con Christian.
-- No edita usuario, credencial, sesión ni token_un_uso.
ALTER TABLE bloqueo
    ADD COLUMN revision uuid NOT NULL DEFAULT gen_random_uuid();
COMMENT ON COLUMN bloqueo.revision IS 'Identificador de cada bloqueo; un bloqueo posterior genera otro UUID';

CREATE TABLE racha_fallo (
    usuario_id uuid PRIMARY KEY REFERENCES usuario(id) ON DELETE CASCADE,
    racha_id uuid NOT NULL,
    hash_key_id varchar(50) NOT NULL,
    creado_en timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_racha_fallo_usuario_racha UNIQUE(usuario_id, racha_id),
    CONSTRAINT ck_racha_fallo_clave CHECK (length(btrim(hash_key_id)) > 0 AND hash_key_id = btrim(hash_key_id))
);

CREATE TABLE huella_fallo (
    usuario_id uuid NOT NULL,
    racha_id uuid NOT NULL,
    huella_hmac char(64) NOT NULL,
    creado_en timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, racha_id, huella_hmac),
    CONSTRAINT fk_huella_fallo_racha FOREIGN KEY(usuario_id, racha_id)
        REFERENCES racha_fallo(usuario_id, racha_id) ON DELETE CASCADE,
    CONSTRAINT ck_huella_fallo_hmac CHECK (huella_hmac ~ '^[0-9a-f]{64}$')
);
-- PK/UNIQUE cubren búsquedas y FK por usuario/racha; no añadir índices duplicados.
-- Límite de cinco huellas se aplica por el coordinador T002 bajo lock de cuenta/racha.
-- No borrar rachas/huellas por antigüedad: no existe ventana temporal de fallos.
ALTER TABLE racha_fallo ENABLE ROW LEVEL SECURITY;
ALTER TABLE huella_fallo ENABLE ROW LEVEL SECURITY;
-- Grants/políticas de g7_runtime se coordinan por TEC-INFRA-T002, no acceso anon/authenticated.
