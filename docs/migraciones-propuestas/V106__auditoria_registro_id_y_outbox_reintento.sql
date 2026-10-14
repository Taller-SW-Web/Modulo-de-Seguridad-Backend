-- V106 — Christian: registro_id en auditoría + reclamo durable en outbox
-- BORRADOR pendiente de revisión del equipo y backup confirmado. NO APLICAR.
-- Fuentes: DISENO-PERSISTENCIA §7; EP-06-T001; TEC-INFRA-T003.
-- Conjunta EP-06-T001/TEC-INFRA-T003: una sola V106 del dueño.

-- auditoria_seguridad: identificador interno único para deduplicar reintentos.
-- El DEFAULT cubre el histórico sin UPDATE de filas ni desactivar el trigger
-- append-only. El id numérico y el DTO público se mantienen.
ALTER TABLE "auditoria_seguridad"
    ADD COLUMN "registro_id" uuid NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX "uq_auditoria_registro_id"
    ON "auditoria_seguridad" ("registro_id");

-- outbox: campos de reclamo del worker (lote 20 SKIP LOCKED, lease 60 s, poll 2 s).
-- Estados existentes PENDIENTE/ENVIADO/FALLIDO se conservan.
ALTER TABLE "outbox"
    ADD COLUMN "proximo_intento_en" timestamptz,
    ADD COLUMN "reservado_hasta" timestamptz,
    ADD COLUMN "reserva_id" uuid,
    ADD COLUMN "ultimo_error" varchar(500);

CREATE INDEX "idx_outbox_reclamo"
    ON "outbox" ("estado", "proximo_intento_en");
