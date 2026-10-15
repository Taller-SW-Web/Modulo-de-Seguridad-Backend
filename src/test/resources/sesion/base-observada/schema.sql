-- Modelo de datos — Módulo de Seguridad y Autenticación (G7)
-- PostgreSQL 13+ (usa gen_random_uuid()). Diseño inicial en drawdb (anatoly-lab/drawdb-mcp);
-- desde la auditoría del 29-sep este .sql es la FUENTE DE VERDAD de las migraciones Flyway
-- y schema.dbml es su diagrama.
-- 22 tablas, 18 relaciones, 1 vista, 11 enums, restricciones CHECK, índices y notas de diseño.
-- Actualizado el 2-oct con los acuerdos A6 (activación por enlace) y A7 (búsqueda por documento).
--
-- NOTAS DE DISEÑO (seguridad / integridad, ver specs):
--  * BLOQUEADO es estado EFECTIVO, calculado desde "bloqueo" (RF-14.7). "usuario.estado"
--    persiste solo el ciclo de vida; el estado expuesto lo da la vista
--    "usuario_estado_efectivo": una cuenta ACTIVO está BLOQUEADO si tiene fila en "bloqueo"
--    con bloqueado_hasta NULL o futuro. El login lee usuario + bloqueo en una misma consulta.
--  * FK COMPUESTA otp(desafio_id, usuario_id) -> desafio_mfa(id, usuario_id): obliga a
--    que un OTP y su desafío sean del mismo usuario (ESC-09.9). Con MATCH SIMPLE, si
--    desafio_id es NULL no se evalúa (OTP de verificación de contacto).
--  * otp.hash_codigo: HMAC-SHA256 con clave de servidor (6 dígitos = baja entropía),
--    comparación en tiempo constante. Nunca hash simple.
--  * documento: cifrado AES con clave fuera de la BD; documento_key_id permite rotar la
--    clave sin re-cifrar. (Blind index HMAC para búsqueda/unicidad: opcional, no requerido.)
--  * Retención: auditoria_seguridad e intento_login 90 días (configurable, SPEC-12);
--    otp/desafio_mfa/token_un_uso/token_refresco/solicitud_limitada se purgan por job.
--  * Tokens (refresco, desafío, un solo uso): se guarda solo su hash y se buscan por él;
--    por eso cada hash_token es UNIQUE.
--  * ENUMS NATIVOS Y JPA: Hibernate envía los enums como varchar y PostgreSQL rechaza el
--    INSERT ("column is of type ... but expression is of type character varying"). En cada
--    atributo enum de una entidad usar @Enumerated(EnumType.STRING) junto con
--    @JdbcTypeCode(SqlTypes.NAMED_ENUM) (Hibernate 6.2+, Spring Boot 3.1+).
--  * auditoria_seguridad es de solo anexado (RF-12.4): el trigger impide UPDATE, y el
--    usuario de BD de la aplicación no debe tener UPDATE ni DELETE (el DELETE de la purga
--    lo hace otro usuario). Los GRANT van en el despliegue, no aquí.
--
-- REPARTO EN MIGRACIONES (issues #21 a #24): V1 Eva (usuario, perfiles, direccion, rol,
-- permiso, rol_permiso, usuario_rol) · V2 Juan José (credencial, password_historial, otp,
-- token_un_uso, solicitud_limitada) · V3 Jose Luis (token_refresco, desafio_mfa,
-- intento_login, bloqueo, vista usuario_estado_efectivo) · V4 Christian (auditoria_seguridad
-- y su trigger, cliente_servicio, scope, cliente_servicio_scope, outbox). Cada tipo enum va
-- en la migración de la primera tabla que lo usa. La FK compuesta otp -> desafio_mfa
-- necesita las dos tablas (V2 y V3) y una migración aplicada no se edita: va en V5.
-- Las migraciones viven en backend/src/main/resources/db/migration/.

CREATE TYPE "estado_cuenta" AS ENUM (
	'ACTIVO',
	'INACTIVO',
	'PENDIENTE_VERIFICACION'
);

CREATE TYPE "codigo_rol" AS ENUM (
	'CLIENTE',
	'VENDEDOR',
	'ADMIN_VENTAS',
	'GESTOR_DESPACHO',
	'GESTOR_COMERCIAL',
	'ADMIN_SISTEMA'
);

CREATE TYPE "tipo_documento" AS ENUM (
	'DNI',
	'CE',
	'PASAPORTE'
);

CREATE TYPE "canal_origen" AS ENUM (
	'WEB',
	'CHATBOT',
	'RETAIL',
	'MARKETPLACE'
);

CREATE TYPE "canal_otp" AS ENUM (
	'EMAIL',
	'SMS'
);

CREATE TYPE "tipo_bloqueo" AS ENUM (
	'AUTOMATICO',
	'MANUAL'
);

CREATE TYPE "resultado" AS ENUM (
	'EXITO',
	'FALLO'
);

CREATE TYPE "actor_tipo" AS ENUM (
	'USUARIO',
	'MODULO',
	'SISTEMA'
);

CREATE TYPE "proposito_token" AS ENUM (
	'VERIFICAR_CORREO',
	'CAMBIO_CORREO',
	'RECUPERACION',
	'DESBLOQUEO',
	'ACTIVACION'
);

CREATE TYPE "estado_cuenta_efectivo" AS ENUM (
	'ACTIVO',
	'INACTIVO',
	'BLOQUEADO',
	'PENDIENTE_VERIFICACION'
);

CREATE TYPE "tipo_limite" AS ENUM (
	'REENVIO_VERIFICACION',
	'RECUPERACION',
	'BUSQUEDA_DOCUMENTO'
);

CREATE TABLE IF NOT EXISTS "usuario" (
	"id" uuid NOT NULL DEFAULT gen_random_uuid(),
	"correo" varchar(254) NOT NULL,
	"nombres" varchar(100) NOT NULL,
	"apellidos" varchar(100) NOT NULL,
	"celular" varchar(12),
	"estado" estado_cuenta NOT NULL,
	"correo_verificado" boolean NOT NULL DEFAULT false,
	"celular_verificado" boolean NOT NULL DEFAULT false,
	"mfa_habilitado" boolean NOT NULL DEFAULT false,
	"acepta_terminos" boolean NOT NULL DEFAULT false,
	"fecha_aceptacion" timestamptz,
	"version_terminos" varchar(20),
	"canal_origen" canal_origen NOT NULL DEFAULT 'WEB',
	"intentos_fallidos_consecutivos" integer NOT NULL DEFAULT 0,
	"bloqueos_seguidos" integer NOT NULL DEFAULT 0,
	"creado_en" timestamptz NOT NULL DEFAULT now(),
	"actualizado_en" timestamptz,
	PRIMARY KEY("id"),
	CONSTRAINT "ck_usuario_correo_minusculas" CHECK ("correo" = LOWER("correo")),
	CONSTRAINT "ck_usuario_celular_formato" CHECK ("celular" IS NULL OR "celular" ~ '^\+51[0-9]{9}$'),
	CONSTRAINT "ck_usuario_terminos" CHECK (NOT "acepta_terminos" OR ("fecha_aceptacion" IS NOT NULL AND "version_terminos" IS NOT NULL)),
	CONSTRAINT "ck_usuario_contadores" CHECK ("intentos_fallidos_consecutivos" >= 0 AND "bloqueos_seguidos" >= 0)
);
COMMENT ON TABLE "usuario" IS 'Entidad central (dueña SPEC-01). Un solo dueño de identidad en el marketplace.';
COMMENT ON COLUMN "usuario"."correo" IS 'Se guarda normalizado a minúsculas (ck_usuario_correo_minusculas).';
COMMENT ON COLUMN "usuario"."celular" IS '+51 + 9 dígitos, igual que el pattern del contrato';
COMMENT ON COLUMN "usuario"."estado" IS 'Ciclo de vida persistido (sin BLOQUEADO). BLOQUEADO es estado efectivo: ver la vista usuario_estado_efectivo.';
COMMENT ON COLUMN "usuario"."acepta_terminos" IS 'true en el registro público (SPEC-01) y al activar una cuenta creada por otro (SPEC-03). Una cuenta sin activar en 30 días se elimina (RF-03.9).';
COMMENT ON COLUMN "usuario"."intentos_fallidos_consecutivos" IS 'Fuente de verdad del contador (SPEC-14); se actualiza en la misma transacción que intento_login.';
COMMENT ON COLUMN "usuario"."bloqueos_seguidos" IS 'Escalera de bloqueos 1/2/4/null; solo vuelve a cero con login correcto (RF-14.4).';

-- Unicidad insensible a mayúsculas (correo normalizado a minúsculas).
CREATE UNIQUE INDEX "uq_usuario_correo_lower" ON "usuario" (LOWER("correo"));

CREATE TABLE IF NOT EXISTS "credencial" (
	"usuario_id" uuid NOT NULL,
	"hash_contrasena" varchar(255) NOT NULL,
	"ultimo_cambio" timestamptz NOT NULL DEFAULT now(),
	"requiere_cambio" boolean NOT NULL DEFAULT false,
	PRIMARY KEY("usuario_id")
);
COMMENT ON TABLE "credencial" IS 'Hash de contraseña, separado para no exponerlo en lecturas de perfil (SPEC-07).';
COMMENT ON COLUMN "credencial"."hash_contrasena" IS 'bcrypt/argon2id, nunca texto plano';

CREATE TABLE IF NOT EXISTS "password_historial" (
	"id" uuid NOT NULL DEFAULT gen_random_uuid(),
	"usuario_id" uuid NOT NULL,
	"hash_contrasena" varchar(255) NOT NULL,
	"creado_en" timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY("id")
);
COMMENT ON TABLE "password_historial" IS 'Últimas 5 contraseñas para impedir reutilización (SPEC-07).';
-- «Las últimas 5 de este usuario»: ORDER BY creado_en DESC LIMIT 5.
CREATE INDEX "idx_password_historial_usuario_fecha" ON "password_historial" ("usuario_id", "creado_en");

CREATE TABLE IF NOT EXISTS "perfil_cliente" (
	"usuario_id" uuid NOT NULL,
	"tipo_documento" tipo_documento,
	"documento_cifrado" text,
	"fecha_nacimiento" date,
	"documento_key_id" varchar(50),
	"documento_hmac" varchar(64),
	PRIMARY KEY("usuario_id"),
	-- Un documento sin tipo, cifrado sin la clave con que se cifró o sin su HMAC no se puede leer ni buscar.
	CONSTRAINT "ck_perfil_cliente_documento" CHECK (
		("tipo_documento" IS NULL AND "documento_cifrado" IS NULL AND "documento_key_id" IS NULL AND "documento_hmac" IS NULL)
		OR ("tipo_documento" IS NOT NULL AND "documento_cifrado" IS NOT NULL AND "documento_key_id" IS NOT NULL AND "documento_hmac" IS NOT NULL)
	)
);
COMMENT ON TABLE "perfil_cliente" IS 'Atributos de CLIENTE (SPEC-16). Documento cifrado AES con clave fuera de BD.';
COMMENT ON COLUMN "perfil_cliente"."documento_cifrado" IS 'AES; se expone enmascarado *****234';
COMMENT ON COLUMN "perfil_cliente"."documento_key_id" IS 'key_id de la clave AES, para rotación sin re-cifrar.';
COMMENT ON COLUMN "perfil_cliente"."documento_hmac" IS 'HMAC-SHA256 en hex de tipo y número normalizados, con clave de servidor: permite buscar sin descifrar (ADR-007, RF-03.8).';
-- Un documento pertenece a una sola cuenta, y es lo que usa la búsqueda en tienda (RF-03.7).
CREATE UNIQUE INDEX "uq_perfil_cliente_documento_hmac" ON "perfil_cliente" ("documento_hmac");

CREATE TABLE IF NOT EXISTS "perfil_vendedor" (
	"usuario_id" uuid NOT NULL,
	"codigo_vendedor" varchar(50),
	"tienda" varchar(100),
	"fecha_ingreso" date,
	PRIMARY KEY("usuario_id")
);
COMMENT ON TABLE "perfil_vendedor" IS 'Atributos de VENDEDOR (SPEC-16). "tienda" es un nombre, no una FK (la entidad tienda vive en otro módulo).';

CREATE TABLE IF NOT EXISTS "direccion" (
	"id" uuid NOT NULL DEFAULT gen_random_uuid(),
	"usuario_id" uuid NOT NULL,
	"etiqueta" varchar(50),
	"departamento" varchar(100) NOT NULL,
	"provincia" varchar(100) NOT NULL,
	"distrito" varchar(100) NOT NULL,
	"direccion" varchar(255) NOT NULL,
	"referencia" varchar(255),
	"es_predeterminada" boolean NOT NULL DEFAULT false,
	PRIMARY KEY("id")
);
COMMENT ON TABLE "direccion" IS 'Direcciones de entrega del cliente (SPEC-16, expuesta por SPEC-18).';
COMMENT ON COLUMN "direccion"."es_predeterminada" IS 'Solo una por usuario (índice único parcial).';
CREATE INDEX "idx_direccion_usuario" ON "direccion" ("usuario_id");

-- Una sola predeterminada por usuario (ESC-16.9). Al cambiarla: en la MISMA
-- transacción, primero quitar la anterior y luego marcar la nueva.
CREATE UNIQUE INDEX "uq_direccion_predeterminada" ON "direccion" ("usuario_id") WHERE "es_predeterminada" = true;

CREATE TABLE IF NOT EXISTS "bloqueo" (
	"usuario_id" uuid NOT NULL,
	"tipo" tipo_bloqueo NOT NULL,
	"bloqueado_hasta" timestamptz,
	"motivo" varchar(500),
	"creado_en" timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY("usuario_id"),
	-- Manual: motivo obligatorio y sin vencimiento (RF-15.1, RF-15.2). Automático: sin motivo.
	CONSTRAINT "ck_bloqueo_tipo" CHECK (
		("tipo" = 'MANUAL' AND "motivo" IS NOT NULL AND "bloqueado_hasta" IS NULL)
		OR ("tipo" = 'AUTOMATICO' AND "motivo" IS NULL)
	)
);
COMMENT ON TABLE "bloqueo" IS 'Bloqueo vigente (1:1). Fuente de verdad del estado BLOQUEADO (efectivo). Manual no vence; automático según escalera 1/2/4/null (SPEC-14/15).';
COMMENT ON COLUMN "bloqueo"."bloqueado_hasta" IS 'null = no vence (manual, o automático desde el 4º)';
COMMENT ON COLUMN "bloqueo"."motivo" IS 'Solo manual, hasta 500 caracteres como en el contrato; nunca a token de servicio';

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
-- Revocar todas las sesiones de una cuenta: baja, bloqueo manual, cambio de roles, restablecimiento.
CREATE INDEX "idx_token_refresco_usuario" ON "token_refresco" ("usuario_id");

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
-- UNIQUE(id, usuario_id) habilita la FK compuesta desde otp.
CREATE UNIQUE INDEX "uq_desafio_id_usuario" ON "desafio_mfa" ("id", "usuario_id");
CREATE UNIQUE INDEX "uq_desafio_mfa_hash" ON "desafio_mfa" ("hash_token");
CREATE INDEX "idx_desafio_mfa_usuario" ON "desafio_mfa" ("usuario_id");

CREATE TABLE IF NOT EXISTS "otp" (
	"id" uuid NOT NULL DEFAULT gen_random_uuid(),
	"usuario_id" uuid NOT NULL,
	"desafio_id" uuid,
	"hash_codigo" varchar(255) NOT NULL,
	"canal" canal_otp NOT NULL,
	"intentos" integer NOT NULL DEFAULT 0,
	"expiracion" timestamptz NOT NULL,
	"usado" boolean NOT NULL DEFAULT false,
	"creado_en" timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY("id"),
	CONSTRAINT "ck_otp_intentos" CHECK ("intentos" BETWEEN 0 AND 3),
	CONSTRAINT "ck_otp_expiracion" CHECK ("expiracion" > "creado_en")
);
COMMENT ON TABLE "otp" IS 'Código OTP de 6 dígitos, solo hash, 5 min, máx 3 intentos (SPEC-09).';
COMMENT ON COLUMN "otp"."desafio_id" IS 'null para validación de contacto sin login';
COMMENT ON COLUMN "otp"."hash_codigo" IS 'HMAC-SHA256 con clave de servidor (6 dígitos = baja entropía). Nunca hash simple.';
-- Límite de 3 solicitudes por usuario en 15 minutos (RF-09.6).
CREATE INDEX "idx_otp_usuario_fecha" ON "otp" ("usuario_id", "creado_en");
CREATE INDEX "idx_otp_desafio" ON "otp" ("desafio_id");

CREATE TABLE IF NOT EXISTS "token_un_uso" (
	"id" uuid NOT NULL DEFAULT gen_random_uuid(),
	"proposito" proposito_token NOT NULL,
	"usuario_id" uuid NOT NULL,
	"hash_token" varchar(255) NOT NULL,
	"destino" varchar(254),
	"expiracion" timestamptz NOT NULL,
	"usado" boolean NOT NULL DEFAULT false,
	"creado_en" timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY("id"),
	-- Solo el cambio de correo lleva el correo nuevo (SPEC-16).
	CONSTRAINT "ck_token_un_uso_destino" CHECK (("proposito" = 'CAMBIO_CORREO') = ("destino" IS NOT NULL)),
	CONSTRAINT "ck_token_un_uso_expiracion" CHECK ("expiracion" > "creado_en")
);
COMMENT ON TABLE "token_un_uso" IS 'Tokens de un solo uso: verificación (24h), recuperación (30m), desbloqueo (30m), activación de una cuenta creada por otro (72h) (SPEC-02/03/08/14).';
COMMENT ON COLUMN "token_un_uso"."destino" IS 'nuevo correo en CAMBIO_CORREO';
CREATE UNIQUE INDEX "uq_token_un_uso_hash" ON "token_un_uso" ("hash_token");
-- Invalidar los anteriores del mismo propósito al emitir uno nuevo (RF-02.4, RF-08.3, RF-14.11).
CREATE INDEX "idx_token_un_uso_usuario_proposito" ON "token_un_uso" ("usuario_id", "proposito");

-- Límites por dirección de correo que deben contar aunque la cuenta NO exista (RF-02.4 y
-- RF-08.8): no pueden vivir en tablas con usuario_id. Se guarda un HMAC-SHA256 del correo
-- normalizado, no el correo: no se retienen direcciones de personas que no son usuarias
-- (Ley 29733). Se purga por job pasada la ventana de una hora.
CREATE TABLE IF NOT EXISTS "solicitud_limitada" (
	"id" bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY,
	"tipo" tipo_limite NOT NULL,
	"clave_hash" varchar(64) NOT NULL,
	"creado_en" timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY("id")
);
COMMENT ON TABLE "solicitud_limitada" IS 'Solicitudes limitadas: por correo, exista o no la cuenta (RF-02.4, RF-08.8), y búsquedas por documento por vendedor (RF-03.7). Solo guarda HMAC, nunca el dato.';
COMMENT ON COLUMN "solicitud_limitada"."clave_hash" IS 'HMAC-SHA256 en hex, con clave de servidor, del correo en minúsculas o del id del vendedor';
CREATE INDEX "idx_solicitud_limitada" ON "solicitud_limitada" ("tipo", "clave_hash", "creado_en");

CREATE TABLE IF NOT EXISTS "rol" (
	"codigo" codigo_rol NOT NULL,
	"nombre" varchar(50) NOT NULL,
	"descripcion" varchar(255),
	PRIMARY KEY("codigo")
);
COMMENT ON TABLE "rol" IS 'Catálogo cerrado de 6 roles (SPEC-11). Se siembra, no se inserta en runtime.';

CREATE TABLE IF NOT EXISTS "permiso" (
	"codigo" varchar(50) NOT NULL,
	"modulo" varchar(50) NOT NULL,
	"descripcion" varchar(255),
	PRIMARY KEY("codigo"),
	CONSTRAINT "ck_permiso_codigo" CHECK ("codigo" ~ '^[a-z_]+\.[a-z_]+$')
);
COMMENT ON TABLE "permiso" IS 'Permisos recurso.accion (SPEC-11). Solo los de este módulo: los demás autorizan por roles y con sus propios datos.';
COMMENT ON COLUMN "permiso"."codigo" IS 'recurso.accion, con punto: los dos puntos son de los scopes';

CREATE TABLE IF NOT EXISTS "rol_permiso" (
	"rol_codigo" codigo_rol NOT NULL,
	"permiso_codigo" varchar(50) NOT NULL,
	PRIMARY KEY("rol_codigo", "permiso_codigo")
);
COMMENT ON TABLE "rol_permiso" IS 'N:N rol-permiso (fuente de permisos efectivos).';
CREATE INDEX "idx_rol_permiso_permiso" ON "rol_permiso" ("permiso_codigo");

CREATE TABLE IF NOT EXISTS "usuario_rol" (
	"usuario_id" uuid NOT NULL,
	"rol_codigo" codigo_rol NOT NULL,
	PRIMARY KEY("usuario_id", "rol_codigo")
);
COMMENT ON TABLE "usuario_rol" IS 'N:N usuario-rol. Asignación de a uno en uno (SPEC-11).';
-- «¿Queda otro ADMIN_SISTEMA activo?» antes de quitar el rol, dar de baja o bloquear (RF-11.7).
CREATE INDEX "idx_usuario_rol_rol" ON "usuario_rol" ("rol_codigo");

CREATE TABLE IF NOT EXISTS "auditoria_seguridad" (
	"id" bigint NOT NULL GENERATED BY DEFAULT AS IDENTITY,
	"fecha" timestamptz NOT NULL DEFAULT now(),
	"accion" varchar(50) NOT NULL,
	"resultado" resultado NOT NULL,
	"actor_tipo" actor_tipo NOT NULL,
	"actor_id" varchar(64),
	"objetivo_usuario_id" uuid,
	"ip" varchar(45),
	"agente" varchar(255),
	"detalle" jsonb,
	PRIMARY KEY("id")
);
COMMENT ON TABLE "auditoria_seguridad" IS 'Solo-anexado, sin UPDATE/DELETE para la app, retención 90 días configurable (SPEC-12). Sin FK a usuario a propósito: el registro sobrevive a la cuenta.';
COMMENT ON COLUMN "auditoria_seguridad"."actor_id" IS 'uuid usuario o client_id';
-- Las consultas de SPEC-13 filtran por un campo y ordenan por fecha descendente.
CREATE INDEX "idx_auditoria_objetivo_fecha" ON "auditoria_seguridad" ("objetivo_usuario_id", "fecha");
CREATE INDEX "idx_auditoria_actor_fecha" ON "auditoria_seguridad" ("actor_id", "fecha");
CREATE INDEX "idx_auditoria_accion_fecha" ON "auditoria_seguridad" ("accion", "fecha");
CREATE INDEX "idx_auditoria_fecha" ON "auditoria_seguridad" ("fecha");

-- Solo anexado (RF-12.4, ESC-12.6): ningún UPDATE, venga de quien venga.
CREATE OR REPLACE FUNCTION "fn_auditoria_solo_anexado"() RETURNS trigger AS $$
BEGIN
	RAISE EXCEPTION 'auditoria_seguridad es de solo anexado: no admite UPDATE';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER "tg_auditoria_solo_anexado"
BEFORE UPDATE ON "auditoria_seguridad"
FOR EACH ROW EXECUTE FUNCTION "fn_auditoria_solo_anexado"();

CREATE TABLE IF NOT EXISTS "cliente_servicio" (
	"client_id" varchar(50) NOT NULL,
	"client_secret_hash" varchar(255) NOT NULL,
	"nombre" varchar(100),
	"activo" boolean NOT NULL DEFAULT true,
	PRIMARY KEY("client_id")
);
COMMENT ON TABLE "cliente_servicio" IS 'Los seis módulos consumidores (SPEC-17). client_secret siempre hasheado.';

CREATE TABLE IF NOT EXISTS "cliente_servicio_scope" (
	"client_id" varchar(50) NOT NULL,
	"scope" varchar(50) NOT NULL,
	PRIMARY KEY("client_id", "scope")
);
COMMENT ON TABLE "cliente_servicio_scope" IS 'Scopes concedidos por módulo (SPEC-17).';
CREATE INDEX "idx_cliente_servicio_scope_scope" ON "cliente_servicio_scope" ("scope");

CREATE TABLE IF NOT EXISTS "scope" (
	"codigo" varchar(50) NOT NULL,
	"audiencia" varchar(50) NOT NULL,
	"descripcion" varchar(255),
	PRIMARY KEY("codigo"),
	CONSTRAINT "ck_scope_codigo" CHECK ("codigo" ~ '^[a-z_]+(:[a-z_]+)+$'),
	CONSTRAINT "ck_scope_audiencia" CHECK ("audiencia" ~ '^api-[a-z]+$')
);
COMMENT ON TABLE "scope" IS 'Catálogo de scopes que emitimos (SPEC-17, acuerdo A4). El dueño de la API lo define y lo valida; nosotros solo lo registramos y lo copiamos al token.';
COMMENT ON COLUMN "scope"."codigo" IS 'recurso:accion, p. ej. usuarios:leer, cotizaciones:calcular';
COMMENT ON COLUMN "scope"."audiencia" IS 'API dueña del scope, va al claim aud: api-seguridad, api-despacho…';

CREATE TABLE IF NOT EXISTS "outbox" (
	"id" uuid NOT NULL DEFAULT gen_random_uuid(),
	"topico" varchar(100) NOT NULL,
	"payload" jsonb NOT NULL,
	"estado" varchar(20) NOT NULL DEFAULT 'PENDIENTE',
	"creado_en" timestamptz NOT NULL DEFAULT now(),
	"intentos" integer NOT NULL DEFAULT 0,
	"procesado_en" timestamptz,
	PRIMARY KEY("id"),
	CONSTRAINT "ck_outbox_estado" CHECK ("estado" IN ('PENDIENTE', 'ENVIADO', 'FALLIDO')),
	CONSTRAINT "ck_outbox_intentos" CHECK ("intentos" >= 0)
);
COMMENT ON TABLE "outbox" IS 'Outbox transaccional: correos asíncronos y eventos after-commit (SPEC-02/12/14).';
COMMENT ON COLUMN "outbox"."estado" IS 'PENDIENTE → ENVIADO, o FALLIDO al agotar los reintentos';
COMMENT ON COLUMN "outbox"."intentos" IS 'reintentos de envío del mensaje';
COMMENT ON COLUMN "outbox"."procesado_en" IS 'cuándo se despachó el mensaje';
CREATE INDEX "idx_outbox_estado_creado" ON "outbox" ("estado", "creado_en");

-- Estado que ven la API y los módulos (RF-14.7, RF-15.5): una cuenta ACTIVO con un bloqueo
-- sin vencer es BLOQUEADO. Un bloqueo vencido no se borra: deja de contar solo.
-- Las cuentas INACTIVO o PENDIENTE_VERIFICACION nunca se muestran BLOQUEADO.
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

ALTER TABLE "credencial"
ADD FOREIGN KEY("usuario_id") REFERENCES "usuario"("id")
ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "password_historial"
ADD FOREIGN KEY("usuario_id") REFERENCES "usuario"("id")
ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "perfil_cliente"
ADD FOREIGN KEY("usuario_id") REFERENCES "usuario"("id")
ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "perfil_vendedor"
ADD FOREIGN KEY("usuario_id") REFERENCES "usuario"("id")
ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "direccion"
ADD FOREIGN KEY("usuario_id") REFERENCES "usuario"("id")
ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "bloqueo"
ADD FOREIGN KEY("usuario_id") REFERENCES "usuario"("id")
ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "intento_login"
ADD FOREIGN KEY("usuario_id") REFERENCES "usuario"("id")
ON UPDATE NO ACTION ON DELETE SET NULL;
ALTER TABLE "token_refresco"
ADD FOREIGN KEY("usuario_id") REFERENCES "usuario"("id")
ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "desafio_mfa"
ADD FOREIGN KEY("usuario_id") REFERENCES "usuario"("id")
ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "otp"
ADD FOREIGN KEY("usuario_id") REFERENCES "usuario"("id")
ON UPDATE NO ACTION ON DELETE CASCADE;
-- FK compuesta: el OTP y su desafío deben ser del mismo usuario (ESC-09.9).
-- MATCH SIMPLE (default): si desafio_id es NULL, no se evalúa.
ALTER TABLE "otp"
ADD FOREIGN KEY("desafio_id", "usuario_id") REFERENCES "desafio_mfa"("id", "usuario_id")
ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "token_un_uso"
ADD FOREIGN KEY("usuario_id") REFERENCES "usuario"("id")
ON UPDATE NO ACTION ON DELETE CASCADE;
-- Catálogos (rol, permiso, scope): RESTRICT. Borrar un rol o un permiso no puede quitar
-- en silencio asignaciones vigentes; primero se retiran a mano.
ALTER TABLE "rol_permiso"
ADD FOREIGN KEY("rol_codigo") REFERENCES "rol"("codigo")
ON UPDATE NO ACTION ON DELETE RESTRICT;
ALTER TABLE "rol_permiso"
ADD FOREIGN KEY("permiso_codigo") REFERENCES "permiso"("codigo")
ON UPDATE NO ACTION ON DELETE RESTRICT;
ALTER TABLE "usuario_rol"
ADD FOREIGN KEY("usuario_id") REFERENCES "usuario"("id")
ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "usuario_rol"
ADD FOREIGN KEY("rol_codigo") REFERENCES "rol"("codigo")
ON UPDATE NO ACTION ON DELETE RESTRICT;
ALTER TABLE "cliente_servicio_scope"
ADD FOREIGN KEY("client_id") REFERENCES "cliente_servicio"("client_id")
ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "cliente_servicio_scope"
ADD FOREIGN KEY("scope") REFERENCES "scope"("codigo")
ON UPDATE NO ACTION ON DELETE RESTRICT;
