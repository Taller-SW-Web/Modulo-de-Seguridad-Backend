-- PROPUESTA EP-02-T002 (V102 reservada a Jose Luis, después de V100).
-- Fuera de db/migration: Christian aplica tras revisión, backup y ventana.
-- Base: token_refresco observado con familia_id/usuario_id/hash_token únicos;
-- sesion_familia y acceso_sesion son tablas nuevas, sin renumerar familias.
-- No modifica usuario/credenciales, no ejecuta V1–V4 sobre la base compartida,
-- no añade grants, baseline, repair ni semillas.
--
-- SPEC-06 RF-06.1/06.3 y DAT-01: raíz independiente por familia, asociación
-- jti → usuario/familia/vigencia, y FK compuesta que impide atribuir un token
-- o acceso a otra familia. La familia nunca se infiere por sub.

CREATE TABLE sesion_familia (
    id uuid NOT NULL,
    usuario_id uuid NOT NULL REFERENCES usuario(id),
    revocada boolean NOT NULL DEFAULT false,
    creado_en timestamptz NOT NULL DEFAULT now(),
    revocada_en timestamptz,
    PRIMARY KEY (id),
    CONSTRAINT uq_sesion_familia_id_usuario UNIQUE (id, usuario_id),
    CONSTRAINT ck_sesion_familia_revocada
        CHECK ((NOT revocada AND revocada_en IS NULL)
            OR (revocada AND revocada_en IS NOT NULL AND revocada_en >= creado_en))
);
COMMENT ON TABLE sesion_familia IS 'Raíz estable de sesión (SPEC-06). No caduca ni se elimina al rotar; revocar marca raíz y tokens, sin reactivación.';
COMMENT ON COLUMN sesion_familia.usuario_id IS 'Dueña física Eva; FK escalar, sin relación JPA navegable ni escritura ajena.';

CREATE TABLE acceso_sesion (
    jti uuid NOT NULL DEFAULT gen_random_uuid(),
    usuario_id uuid NOT NULL,
    familia_id uuid NOT NULL,
    expira_en timestamptz NOT NULL,
    creado_en timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (jti),
    CONSTRAINT fk_acceso_sesion_familia
        FOREIGN KEY (familia_id, usuario_id) REFERENCES sesion_familia(id, usuario_id),
    CONSTRAINT ck_acceso_sesion_expiracion CHECK (expira_en > creado_en)
);
COMMENT ON TABLE acceso_sesion IS 'Asociación jti → usuario/familia/vigencia (DAT-01). Un jti nuevo por acceso emitido, incluso tras refresh; se conserva mientras el acceso sea vigente.';
CREATE INDEX idx_acceso_sesion_familia ON acceso_sesion (familia_id);
CREATE INDEX idx_acceso_sesion_expira ON acceso_sesion (expira_en);

-- Backfill: una raíz por familia_id existente, del único usuario de esa familia.
-- Cualquier revocación histórica de la familia mantiene la raíz revocada.
-- Familia con usuarios incoherentes detiene la migración; no se elige un
-- usuario arbitrario ni se infiere familia por sub.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM token_refresco
               GROUP BY familia_id HAVING count(DISTINCT usuario_id) > 1) THEN
        RAISE EXCEPTION 'V102 abortada: familia_id con más de un usuario_id en token_refresco';
    END IF;
END $$;

INSERT INTO sesion_familia (id, usuario_id, revocada, creado_en, revocada_en)
SELECT familia_id,
       min(usuario_id),
       bool_or(revocado),
       min(creado_en),
       CASE WHEN bool_or(revocado) THEN now() ELSE NULL END
  FROM token_refresco
 GROUP BY familia_id;

-- FK compuesta sobre tokens existentes, después de validar datos: el backfill
-- ya exige un solo usuario por familia, y VALIDATE lo comprueba fila por fila.
ALTER TABLE token_refresco
    ADD CONSTRAINT fk_token_refresco_familia
        FOREIGN KEY (familia_id, usuario_id) REFERENCES sesion_familia(id, usuario_id) NOT VALID;
ALTER TABLE token_refresco VALIDATE CONSTRAINT fk_token_refresco_familia;
COMMENT ON CONSTRAINT fk_token_refresco_familia ON token_refresco IS 'Cada refresco pertenece a su familia y usuario; impedir atribución cruzada (RF-06.3). Se reutilizan uq_token_refresco_hash e índices por familia/usuario.';
