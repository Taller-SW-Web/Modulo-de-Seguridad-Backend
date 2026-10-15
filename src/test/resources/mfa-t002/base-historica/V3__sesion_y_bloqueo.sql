-- V3 — Jose Luis: sesión, MFA, intentos de login y bloqueo
-- Tablas: token_refresco, desafio_mfa, intento_login, bloqueo
-- Vista: usuario_estado_efectivo
-- Enums nuevos: resultado, tipo_bloqueo, estado_cuenta_efectivo

-- ═══════════════════════════════════════════════════════════════
-- ENUMS (primera tabla que los usa en este orden de migraciones)
-- ═══════════════════════════════════════════════════════════════

CREATE TYPE "resultado" AS ENUM (
	'EXITO',
	'FALLO'
);

CREATE TYPE "tipo_bloqueo" AS ENUM (
	'AUTOMATICO',
	'MANUAL'
);

CREATE TYPE "estado_cuenta_efectivo" AS ENUM (
	'ACTIVO',
	'INACTIVO',
	'BLOQUEADO',
	'PENDIENTE_VERIFICACION'
);

-- ═══════════════════════════════════════════════════════════════
-- TABLA: token_refresco
-- Refresh tokens con familia. Rotación en cada uso;
-- reutilización revoca la familia (SPEC-06).
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS "token_refresco" (
	"jti" uuid NOT NULL DEFAULT gen_random_uuid(),
	"usuario_id" uuid NOT NULL,
	"familia_id" uuid NOT NULL,
	"hash_token" varchar(255) NOT NULL,
	"revocado" boolean NOT NULL DEFAULT false,
	"usado" boolean NOT NULL DEFAULT false,
	"expiracion" timestamptz NOT NULL,
	"creado_en" timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY("jti"),
	CONSTRAINT "ck_token_refresco_expiracion" CHECK ("expiracion" > "creado_en")
);
COMMENT ON TABLE "token_refresco" IS 'Refresh tokens con familia. Rotación en cada uso; reutilización revoca la familia (SPEC-06).';
COMMENT ON COLUMN "token_refresco"."familia_id" IS 'una sesión = una familia';

CREATE UNIQUE INDEX "uq_token_refresco_hash" ON "token_refresco" ("hash_token");
CREATE INDEX "idx_token_refresco_familia" ON "token_refresco" ("familia_id");
CREATE INDEX "idx_token_refresco_usuario" ON "token_refresco" ("usuario_id");

-- ═══════════════════════════════════════════════════════════════
-- TABLA: desafio_mfa
-- challengeToken del login con segundo factor (SPEC-09).
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS "desafio_mfa" (
	"id" uuid NOT NULL DEFAULT gen_random_uuid(),
	"usuario_id" uuid NOT NULL,
	"hash_token" varchar(255) NOT NULL,
	"expiracion" timestamptz NOT NULL,
	"consumido" boolean NOT NULL DEFAULT false,
	"creado_en" timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY("id"),
	CONSTRAINT "ck_desafio_mfa_expiracion" CHECK ("expiracion" > "creado_en")
);
COMMENT ON TABLE "desafio_mfa" IS 'challengeToken del login con segundo factor (SPEC-09).';

-- UNIQUE(id, usuario_id) habilita la FK compuesta desde otp (V2).
CREATE UNIQUE INDEX "uq_desafio_id_usuario" ON "desafio_mfa" ("id", "usuario_id");
CREATE UNIQUE INDEX "uq_desafio_mfa_hash" ON "desafio_mfa" ("hash_token");
CREATE INDEX "idx_desafio_mfa_usuario" ON "desafio_mfa" ("usuario_id");

-- ═══════════════════════════════════════════════════════════════
-- TABLA: intento_login
-- Intentos de login (append-only, 90 días). Registro de hechos;
-- el contador vive en usuario (SPEC-14).
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS "intento_login" (
	"id" uuid NOT NULL DEFAULT gen_random_uuid(),
	"usuario_id" uuid,
	"correo_intentado" varchar(254),
	"resultado" resultado NOT NULL,
	"ip" varchar(45),
	"user_agent" varchar(255),
	"fecha" timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY("id")
);
COMMENT ON TABLE "intento_login" IS 'Intentos de login (append-only, 90 días). Registro de hechos; el contador vive en usuario (SPEC-14).';
COMMENT ON COLUMN "intento_login"."usuario_id" IS 'null si el correo no existe';

CREATE INDEX "idx_intento_login_usuario_fecha" ON "intento_login" ("usuario_id", "fecha");
CREATE INDEX "idx_intento_login_ip_fecha" ON "intento_login" ("ip", "fecha");
CREATE INDEX "idx_intento_login_fecha" ON "intento_login" ("fecha");

-- ═══════════════════════════════════════════════════════════════
-- TABLA: bloqueo
-- Bloqueo vigente (1:1). Fuente de verdad del estado BLOQUEADO (efectivo).
-- Manual no vence; automático según escalera 1/2/4/null (SPEC-14/15).
-- ═══════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS "bloqueo" (
	"usuario_id" uuid NOT NULL,
	"tipo" tipo_bloqueo NOT NULL,
	"bloqueado_hasta" timestamptz,
	"motivo" varchar(500),
	"creado_en" timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY("usuario_id"),
	CONSTRAINT "ck_bloqueo_tipo" CHECK (
		("tipo" = 'MANUAL' AND "motivo" IS NOT NULL AND "bloqueado_hasta" IS NULL)
		OR ("tipo" = 'AUTOMATICO' AND "motivo" IS NULL)
	)
);
COMMENT ON TABLE "bloqueo" IS 'Bloqueo vigente (1:1). Fuente de verdad del estado BLOQUEADO (efectivo). Manual no vence; automático según escalera 1/2/4/null (SPEC-14/15).';
COMMENT ON COLUMN "bloqueo"."bloqueado_hasta" IS 'null = no vence (manual, o automático desde el 4º)';
COMMENT ON COLUMN "bloqueo"."motivo" IS 'Solo manual, hasta 500 caracteres como en el contrato; nunca a token de servicio';

-- ═══════════════════════════════════════════════════════════════
-- VISTA: usuario_estado_efectivo
-- Estado que ven la API y los módulos (RF-14.7, RF-15.5).
-- Una cuenta ACTIVO con un bloqueo sin vencer es BLOQUEADO.
-- ═══════════════════════════════════════════════════════════════

CREATE OR REPLACE VIEW "usuario_estado_efectivo" AS
SELECT
	u."id" AS "usuario_id",
	CASE
		WHEN u."estado" = 'ACTIVO'
			AND b."usuario_id" IS NOT NULL
			AND (b."bloqueado_hasta" IS NULL OR b."bloqueado_hasta" > now())
		THEN 'BLOQUEADO'::estado_cuenta_efectivo
		ELSE u."estado"::text::estado_cuenta_efectivo
	END AS "estado"
FROM "usuario" u
LEFT JOIN "bloqueo" b ON b."usuario_id" = u."id";

-- ═══════════════════════════════════════════════════════════════
-- FKs a usuario (creada en V1)
-- ═══════════════════════════════════════════════════════════════

ALTER TABLE "token_refresco"
	ADD FOREIGN KEY("usuario_id") REFERENCES "usuario"("id")
	ON UPDATE NO ACTION ON DELETE CASCADE;

ALTER TABLE "desafio_mfa"
	ADD FOREIGN KEY("usuario_id") REFERENCES "usuario"("id")
	ON UPDATE NO ACTION ON DELETE CASCADE;

ALTER TABLE "intento_login"
	ADD FOREIGN KEY("usuario_id") REFERENCES "usuario"("id")
	ON UPDATE NO ACTION ON DELETE SET NULL;

ALTER TABLE "bloqueo"
	ADD FOREIGN KEY("usuario_id") REFERENCES "usuario"("id")
	ON UPDATE NO ACTION ON DELETE CASCADE;
