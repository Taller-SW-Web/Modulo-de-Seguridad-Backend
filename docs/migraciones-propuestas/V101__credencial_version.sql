-- Propuesta EP-03-T001 sobre la estructura observada de credencial.
-- Solo agrega la revisión optimista a la tabla credencial observada.
-- No aplicar directamente en Supabase.
ALTER TABLE credencial
    ADD COLUMN version bigint NOT NULL DEFAULT 0;
