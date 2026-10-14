-- V1__usuario_y_roles.sql
-- Migración V1: Identidades, Perfiles, Direcciones y RBAC Base
-- Dueña: Eva Moreno
-- Enums creados aquí porque son los primeros en usarse

-- ============================================================
-- ENUMS (los que usa V1)
-- ============================================================
CREATE TYPE estado_cuenta AS ENUM (
    'ACTIVO',
    'INACTIVO',
    'PENDIENTE_VERIFICACION'
);

CREATE TYPE codigo_rol AS ENUM (
    'CLIENTE',
    'VENDEDOR',
    'ADMIN_VENTAS',
    'GESTOR_DESPACHO',
    'GESTOR_COMERCIAL',
    'ADMIN_SISTEMA'
);

CREATE TYPE tipo_documento AS ENUM (
    'DNI',
    'CE',
    'PASAPORTE'
);

CREATE TYPE canal_origen AS ENUM (
    'WEB',
    'CHATBOT',
    'RETAIL',
    'MARKETPLACE'
);

-- tipo_bloqueo y estado_cuenta_efectivo NO van aquí: los crea V3, junto con la
-- tabla bloqueo y la vista que los usan (regla de schema.sql). Si se crearan en
-- los dos sitios, V3 fallaría con «type already exists».

-- ============================================================
-- TABLA: usuario
-- ============================================================
CREATE TABLE IF NOT EXISTS usuario (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    correo varchar(254) NOT NULL,
    nombres varchar(100) NOT NULL,
    apellidos varchar(100) NOT NULL,
    celular varchar(12),
    estado estado_cuenta NOT NULL,
    correo_verificado boolean NOT NULL DEFAULT false,
    celular_verificado boolean NOT NULL DEFAULT false,
    mfa_habilitado boolean NOT NULL DEFAULT false,
    acepta_terminos boolean NOT NULL DEFAULT false,
    fecha_aceptacion timestamptz,
    version_terminos varchar(20),
    canal_origen canal_origen NOT NULL DEFAULT 'WEB',
    intentos_fallidos_consecutivos integer NOT NULL DEFAULT 0,
    bloqueos_seguidos integer NOT NULL DEFAULT 0,
    creado_en timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz,
    PRIMARY KEY (id),
    CONSTRAINT ck_usuario_correo_minusculas CHECK (correo = LOWER(correo)),
    CONSTRAINT ck_usuario_celular_formato CHECK (celular IS NULL OR celular ~ '^\+51[0-9]{9}$'),
    CONSTRAINT ck_usuario_terminos CHECK (NOT acepta_terminos OR (fecha_aceptacion IS NOT NULL AND version_terminos IS NOT NULL)),
    CONSTRAINT ck_usuario_contadores CHECK (intentos_fallidos_consecutivos >= 0 AND bloqueos_seguidos >= 0)
);

COMMENT ON TABLE usuario IS 'Entidad central (dueña SPEC-01). Un solo dueño de identidad en el marketplace.';
COMMENT ON COLUMN usuario.correo IS 'Se guarda normalizado a minúsculas (ck_usuario_correo_minusculas).';
COMMENT ON COLUMN usuario.celular IS '+51 + 9 dígitos, igual que el pattern del contrato';
COMMENT ON COLUMN usuario.estado IS 'Ciclo de vida persistido (sin BLOQUEADO). BLOQUEADO es estado efectivo: ver la vista usuario_estado_efectivo.';
COMMENT ON COLUMN usuario.acepta_terminos IS 'true en el registro público (SPEC-01). Las cuentas que da de alta un administrador (SPEC-03) no pasan por el consentimiento.';
COMMENT ON COLUMN usuario.intentos_fallidos_consecutivos IS 'Fuente de verdad del contador (SPEC-14); se actualiza en la misma transacción que intento_login.';
COMMENT ON COLUMN usuario.bloqueos_seguidos IS 'Escalera de bloqueos 1/2/4/null; solo vuelve a cero con login correcto (RF-14.4).';

-- Unicidad insensible a mayúsculas (correo normalizado a minúsculas).
CREATE UNIQUE INDEX uq_usuario_correo_lower ON usuario (LOWER(correo));

-- ============================================================
-- TABLA: perfil_cliente
-- ============================================================
CREATE TABLE IF NOT EXISTS perfil_cliente (
    usuario_id uuid NOT NULL,
    tipo_documento tipo_documento,
    documento_cifrado text,
    fecha_nacimiento date,
    documento_key_id varchar(50),
    documento_hmac varchar(64),
    PRIMARY KEY (usuario_id),
    -- Un documento sin tipo, cifrado sin la clave con que se cifró o sin su HMAC no se puede leer ni buscar.
    CONSTRAINT ck_perfil_cliente_documento CHECK (
        (tipo_documento IS NULL AND documento_cifrado IS NULL AND documento_key_id IS NULL AND documento_hmac IS NULL)
        OR (tipo_documento IS NOT NULL AND documento_cifrado IS NOT NULL AND documento_key_id IS NOT NULL AND documento_hmac IS NOT NULL)
    )
);

COMMENT ON TABLE perfil_cliente IS 'Atributos de CLIENTE (SPEC-16). Documento cifrado AES con clave fuera de BD.';
COMMENT ON COLUMN perfil_cliente.documento_cifrado IS 'AES; se expone enmascarado *****234';
COMMENT ON COLUMN perfil_cliente.documento_key_id IS 'key_id de la clave AES, para rotación sin re-cifrar.';
COMMENT ON COLUMN perfil_cliente.documento_hmac IS 'HMAC-SHA256 en hex de tipo y número normalizados: permite buscar sin descifrar (ADR-007).';
CREATE UNIQUE INDEX uq_perfil_cliente_documento_hmac ON perfil_cliente (documento_hmac);

-- ============================================================
-- TABLA: perfil_vendedor
-- ============================================================
CREATE TABLE IF NOT EXISTS perfil_vendedor (
    usuario_id uuid NOT NULL,
    codigo_vendedor varchar(50),
    tienda varchar(100),
    fecha_ingreso date,
    PRIMARY KEY (usuario_id)
);

COMMENT ON TABLE perfil_vendedor IS 'Atributos de VENDEDOR (SPEC-16). "tienda" es un nombre, no una FK (la entidad tienda vive en otro módulo).';

-- ============================================================
-- TABLA: direccion
-- ============================================================
CREATE TABLE IF NOT EXISTS direccion (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    usuario_id uuid NOT NULL,
    etiqueta varchar(50),
    departamento varchar(100) NOT NULL,
    provincia varchar(100) NOT NULL,
    distrito varchar(100) NOT NULL,
    direccion varchar(255) NOT NULL,
    referencia varchar(255),
    es_predeterminada boolean NOT NULL DEFAULT false,
    PRIMARY KEY (id)
);

COMMENT ON TABLE direccion IS 'Direcciones de entrega del cliente (SPEC-16, expuesta por SPEC-18).';
COMMENT ON COLUMN direccion.es_predeterminada IS 'Solo una por usuario (índice único parcial).';

CREATE INDEX idx_direccion_usuario ON direccion (usuario_id);

-- Una sola predeterminada por usuario (ESC-16.9). Al cambiarla: en la MISMA
-- transacción, primero quitar la anterior y luego marcar la nueva.
CREATE UNIQUE INDEX uq_direccion_predeterminada ON direccion (usuario_id) WHERE es_predeterminada = true;

-- ============================================================
-- TABLA: rol
-- ============================================================
CREATE TABLE IF NOT EXISTS rol (
    codigo codigo_rol NOT NULL,
    nombre varchar(50) NOT NULL,
    descripcion varchar(255),
    PRIMARY KEY (codigo)
);

COMMENT ON TABLE rol IS 'Catálogo cerrado de 6 roles (SPEC-11). Se siembra, no se inserta en runtime.';

-- ============================================================
-- TABLA: permiso
-- ============================================================
CREATE TABLE IF NOT EXISTS permiso (
    codigo varchar(50) NOT NULL,
    modulo varchar(50) NOT NULL,
    descripcion varchar(255),
    PRIMARY KEY (codigo),
    CONSTRAINT ck_permiso_codigo CHECK (codigo ~ '^[a-z_]+\.[a-z_]+$')
);

COMMENT ON TABLE permiso IS 'Permisos recurso.accion (SPEC-11). Solo los de este módulo: los demás autorizan por roles y con sus propios datos.';
COMMENT ON COLUMN permiso.codigo IS 'recurso.accion, con punto: los dos puntos son de los scopes';

-- ============================================================
-- TABLA: rol_permiso
-- ============================================================
CREATE TABLE IF NOT EXISTS rol_permiso (
    rol_codigo codigo_rol NOT NULL,
    permiso_codigo varchar(50) NOT NULL,
    PRIMARY KEY (rol_codigo, permiso_codigo)
);

COMMENT ON TABLE rol_permiso IS 'N:N rol-permiso (fuente de permisos efectivos).';

CREATE INDEX idx_rol_permiso_permiso ON rol_permiso (permiso_codigo);

-- ============================================================
-- TABLA: usuario_rol
-- ============================================================
CREATE TABLE IF NOT EXISTS usuario_rol (
    usuario_id uuid NOT NULL,
    rol_codigo codigo_rol NOT NULL,
    PRIMARY KEY (usuario_id, rol_codigo)
);

COMMENT ON TABLE usuario_rol IS 'N:N usuario-rol. Asignación de a uno en uno (SPEC-11).';

-- «¿Queda otro ADMIN_SISTEMA activo?» antes de quitar el rol, dar de baja o bloquear (RF-11.7).
CREATE INDEX idx_usuario_rol_rol ON usuario_rol (rol_codigo);

-- ============================================================
-- FOREIGN KEYS
-- ============================================================

-- Perfiles y dirección: CASCADE (si se borra usuario, se borran sus datos)
ALTER TABLE perfil_cliente
    ADD FOREIGN KEY (usuario_id) REFERENCES usuario (id)
    ON UPDATE NO ACTION ON DELETE CASCADE;

ALTER TABLE perfil_vendedor
    ADD FOREIGN KEY (usuario_id) REFERENCES usuario (id)
    ON UPDATE NO ACTION ON DELETE CASCADE;

ALTER TABLE direccion
    ADD FOREIGN KEY (usuario_id) REFERENCES usuario (id)
    ON UPDATE NO ACTION ON DELETE CASCADE;

-- RBAC: RESTRICT en catálogos (no se puede borrar rol/permiso si tiene asignaciones)
ALTER TABLE rol_permiso
    ADD FOREIGN KEY (rol_codigo) REFERENCES rol (codigo)
    ON UPDATE NO ACTION ON DELETE RESTRICT;

ALTER TABLE rol_permiso
    ADD FOREIGN KEY (permiso_codigo) REFERENCES permiso (codigo)
    ON UPDATE NO ACTION ON DELETE RESTRICT;

ALTER TABLE usuario_rol
    ADD FOREIGN KEY (usuario_id) REFERENCES usuario (id)
    ON UPDATE NO ACTION ON DELETE CASCADE;

ALTER TABLE usuario_rol
    ADD FOREIGN KEY (rol_codigo) REFERENCES rol (codigo)
    ON UPDATE NO ACTION ON DELETE RESTRICT;