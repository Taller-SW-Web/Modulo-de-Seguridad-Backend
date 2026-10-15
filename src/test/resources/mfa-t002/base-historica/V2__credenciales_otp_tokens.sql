-- V2__credenciales_otp_tokens.sql
-- Migración V2: Credenciales, Historial de Contraseñas, OTP y Tokens de un solo uso
-- Responsable: Juan José Cano Vásquez

-- ============================================================
-- TABLA: credencial
-- ============================================================

CREATE TABLE credencial (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    usuario_id uuid NOT NULL,
    password_hash varchar(255) NOT NULL,
    salt varchar(255),
    algoritmo varchar(50) NOT NULL,
    requiere_cambio boolean NOT NULL DEFAULT FALSE,
    intentos_fallidos integer NOT NULL DEFAULT 0,
    bloqueada_hasta timestamptz,
    creado_en timestamptz NOT NULL DEFAULT now(),
    actualizado_en timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_credencial
        PRIMARY KEY (id),

    CONSTRAINT uq_credencial_usuario
        UNIQUE (usuario_id),

    CONSTRAINT ck_credencial_intentos_fallidos
        CHECK (intentos_fallidos >= 0)
);

COMMENT ON TABLE credencial IS
    'Credenciales de acceso del usuario. Almacena el hash y estado de la contraseña.';

COMMENT ON COLUMN credencial.password_hash IS
    'Hash de la contraseña. Nunca se almacena la contraseña en texto plano.';

COMMENT ON COLUMN credencial.salt IS
    'Salt utilizado para el cálculo del hash, cuando aplique.';

COMMENT ON COLUMN credencial.algoritmo IS
    'Algoritmo utilizado para generar el hash de la contraseña.';

COMMENT ON COLUMN credencial.requiere_cambio IS
    'Indica si el usuario debe cambiar su contraseña.';

COMMENT ON COLUMN credencial.intentos_fallidos IS
    'Cantidad de intentos fallidos consecutivos.';

COMMENT ON COLUMN credencial.bloqueada_hasta IS
    'Fecha y hora hasta la que la credencial permanece bloqueada.';


-- ============================================================
-- TABLA: password_historial
-- ============================================================

CREATE TABLE password_historial (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    credencial_id uuid NOT NULL,
    password_hash varchar(255) NOT NULL,
    creado_en timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_password_historial
        PRIMARY KEY (id)
);

COMMENT ON TABLE password_historial IS
    'Historial de contraseñas utilizadas por la credencial para evitar su reutilización.';

COMMENT ON COLUMN password_historial.credencial_id IS
    'Referencia a la credencial a la que pertenece este registro de historial.';

COMMENT ON COLUMN password_historial.password_hash IS
    'Hash de una contraseña utilizada anteriormente.';

COMMENT ON COLUMN password_historial.creado_en IS
    'Fecha y hora en la que se registró la contraseña en el historial.';

CREATE INDEX idx_password_historial_credencial_creado_en
    ON password_historial (credencial_id, creado_en DESC);


-- ============================================================
-- TABLA: otp
-- ============================================================

CREATE TABLE otp (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    usuario_id uuid NOT NULL,
    hash_codigo varchar(255) NOT NULL,
    destino varchar(255) NOT NULL,
    canal varchar(50) NOT NULL,
    motivo varchar(50) NOT NULL,
    expira_en timestamptz NOT NULL,
    usado boolean NOT NULL DEFAULT FALSE,
    intentos integer NOT NULL DEFAULT 0,
    creado_en timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_otp
        PRIMARY KEY (id),

    CONSTRAINT ck_otp_intentos
        CHECK (intentos >= 0),

    CONSTRAINT ck_otp_expira_en
        CHECK (expira_en > creado_en)
);

COMMENT ON TABLE otp IS
    'Códigos OTP utilizados para verificación, recuperación de contraseña o autenticación.';

COMMENT ON COLUMN otp.usuario_id IS
    'Usuario al que pertenece el OTP.';

COMMENT ON COLUMN otp.hash_codigo IS
    'Hash del código OTP. No se almacena el código en texto plano.';

COMMENT ON COLUMN otp.destino IS
    'Destino al que se envió el código OTP.';

COMMENT ON COLUMN otp.canal IS
    'Canal utilizado para enviar el OTP, por ejemplo EMAIL o SMS.';

COMMENT ON COLUMN otp.motivo IS
    'Motivo de generación del OTP.';

COMMENT ON COLUMN otp.expira_en IS
    'Fecha y hora de expiración del OTP.';

COMMENT ON COLUMN otp.usado IS
    'Indica si el OTP ya fue utilizado.';

COMMENT ON COLUMN otp.intentos IS
    'Número de intentos realizados para validar el OTP.';

CREATE INDEX idx_otp_usuario_id
    ON otp (usuario_id);

CREATE INDEX idx_otp_expira_en
    ON otp (expira_en);


-- ============================================================
-- TABLA: token_un_uso
-- ============================================================

CREATE TABLE token_un_uso (
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    usuario_id uuid NOT NULL,
    hash_token varchar(255) NOT NULL,
    motivo varchar(50) NOT NULL,
    expira_en timestamptz NOT NULL,
    usado boolean NOT NULL DEFAULT FALSE,
    creado_en timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT pk_token_un_uso
        PRIMARY KEY (id),

    CONSTRAINT uq_token_un_uso_hash
        UNIQUE (hash_token),

    CONSTRAINT ck_token_un_uso_expira_en
        CHECK (expira_en > creado_en)
);

COMMENT ON TABLE token_un_uso IS
    'Tokens de un solo uso para operaciones sensibles como recuperación de contraseña o verificación.';

COMMENT ON COLUMN token_un_uso.usuario_id IS
    'Usuario al que pertenece el token.';

COMMENT ON COLUMN token_un_uso.hash_token IS
    'Hash del token de un solo uso. No se almacena el token en texto plano.';

COMMENT ON COLUMN token_un_uso.motivo IS
    'Motivo de generación del token.';

COMMENT ON COLUMN token_un_uso.expira_en IS
    'Fecha y hora de expiración del token.';

COMMENT ON COLUMN token_un_uso.usado IS
    'Indica si el token ya fue utilizado.';

CREATE INDEX idx_token_un_uso_usuario_id
    ON token_un_uso (usuario_id);

CREATE INDEX idx_token_un_uso_expira_en
    ON token_un_uso (expira_en);


-- ============================================================
-- FOREIGN KEYS
-- ============================================================

ALTER TABLE credencial
    ADD CONSTRAINT fk_credencial_usuario
    FOREIGN KEY (usuario_id)
    REFERENCES usuario (id)
    ON UPDATE NO ACTION
    ON DELETE CASCADE;

ALTER TABLE password_historial
    ADD CONSTRAINT fk_password_historial_credencial
    FOREIGN KEY (credencial_id)
    REFERENCES credencial (id)
    ON UPDATE NO ACTION
    ON DELETE CASCADE;

ALTER TABLE otp
    ADD CONSTRAINT fk_otp_usuario
    FOREIGN KEY (usuario_id)
    REFERENCES usuario (id)
    ON UPDATE NO ACTION
    ON DELETE CASCADE;

ALTER TABLE token_un_uso
    ADD CONSTRAINT fk_token_un_uso_usuario
    FOREIGN KEY (usuario_id)
    REFERENCES usuario (id)
    ON UPDATE NO ACTION
    ON DELETE CASCADE;