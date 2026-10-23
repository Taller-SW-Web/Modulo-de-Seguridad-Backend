-- EP-03-T004: vincula el enlace de desbloqueo con una revisión concreta del bloqueo.
-- La columna es nullable para conservar tokens históricos y propósitos que no usan contexto.
ALTER TABLE token_un_uso
    ADD COLUMN contexto_id uuid;

COMMENT ON COLUMN token_un_uso.contexto_id IS
    'Revisión UUID del bloqueo automático al que pertenece un enlace DESBLOQUEO_AUTOMATICO; null para otros propósitos/históricos.';
