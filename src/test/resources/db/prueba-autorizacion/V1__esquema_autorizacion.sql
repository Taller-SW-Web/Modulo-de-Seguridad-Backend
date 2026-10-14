-- ============================================================================
-- SOLO PRUEBAS. Copia literal de las tablas de autorización de V1
-- (backend/src/main/resources/db/migration/V1__usuario_y_roles.sql del repo de
-- documentación): tipo codigo_rol y tablas rol, permiso, rol_permiso y
-- usuario_rol, con sus PK, CHECK, índices y FK internas.
--
-- Omite la FK usuario_rol → usuario porque la tabla usuario es de Eva y no
-- hace falta para probar autorización. Cuando V1–V4 estén en db/migration
-- (TEC-INFRA-T002), las pruebas deben pasar a usar esas migraciones reales y
-- este archivo se borra.
-- ============================================================================

CREATE TYPE codigo_rol AS ENUM (
    'CLIENTE',
    'VENDEDOR',
    'ADMIN_VENTAS',
    'GESTOR_DESPACHO',
    'GESTOR_COMERCIAL',
    'ADMIN_SISTEMA'
);

CREATE TABLE IF NOT EXISTS rol (
    codigo codigo_rol NOT NULL,
    nombre varchar(50) NOT NULL,
    descripcion varchar(255),
    PRIMARY KEY (codigo)
);

CREATE TABLE IF NOT EXISTS permiso (
    codigo varchar(50) NOT NULL,
    modulo varchar(50) NOT NULL,
    descripcion varchar(255),
    PRIMARY KEY (codigo),
    CONSTRAINT ck_permiso_codigo CHECK (codigo ~ '^[a-z_]+\.[a-z_]+$')
);

CREATE TABLE IF NOT EXISTS rol_permiso (
    rol_codigo codigo_rol NOT NULL,
    permiso_codigo varchar(50) NOT NULL,
    PRIMARY KEY (rol_codigo, permiso_codigo)
);

CREATE INDEX idx_rol_permiso_permiso ON rol_permiso (permiso_codigo);

CREATE TABLE IF NOT EXISTS usuario_rol (
    usuario_id uuid NOT NULL,
    rol_codigo codigo_rol NOT NULL,
    PRIMARY KEY (usuario_id, rol_codigo)
);

CREATE INDEX idx_usuario_rol_rol ON usuario_rol (rol_codigo);

ALTER TABLE rol_permiso
    ADD FOREIGN KEY (rol_codigo) REFERENCES rol (codigo)
    ON UPDATE NO ACTION ON DELETE RESTRICT;

ALTER TABLE rol_permiso
    ADD FOREIGN KEY (permiso_codigo) REFERENCES permiso (codigo)
    ON UPDATE NO ACTION ON DELETE RESTRICT;

ALTER TABLE usuario_rol
    ADD FOREIGN KEY (rol_codigo) REFERENCES rol (codigo)
    ON UPDATE NO ACTION ON DELETE RESTRICT;
