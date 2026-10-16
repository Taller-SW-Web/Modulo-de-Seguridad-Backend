-- V107 — HMAC de documento en perfil_cliente (desbloquea validate del arranque).
-- Rellena documento_hmac varchar(64) + índice único, según schema.sql y ADR-007.
-- Capacidad perfil (Valery/Sergio); la aplica Christian en solitario porque
-- bloquea el despliegue y no hay otro disponible. Solo agrega, no toca datos.
ALTER TABLE perfil_cliente
    ADD COLUMN documento_hmac varchar(64);

CREATE UNIQUE INDEX uq_perfil_cliente_documento_hmac ON perfil_cliente (documento_hmac);

COMMENT ON COLUMN perfil_cliente.documento_hmac IS 'HMAC-SHA256 en hex de tipo y número normalizados: permite buscar sin descifrar (ADR-007, RF-03.8).';
