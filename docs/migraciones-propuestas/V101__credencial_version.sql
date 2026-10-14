-- PROPUESTA de EP-03-T001. NO APLICAR antes de TEC-INFRA-T002 y revisión de Christian.
-- El historial/esquema G7 real no está verificado. Esta propuesta parte de la forma
-- de credencial.version acordada en DISENO-PERSISTENCIA §4 y de la tabla credencial
-- con id de V2; confirmar columnas, versión libre y constraints antes de promover.

ALTER TABLE credencial
    ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE credencial
    ADD CONSTRAINT ck_credencial_version_no_negativa
        CHECK (version >= 0) NOT VALID;

ALTER TABLE credencial
    VALIDATE CONSTRAINT ck_credencial_version_no_negativa;
