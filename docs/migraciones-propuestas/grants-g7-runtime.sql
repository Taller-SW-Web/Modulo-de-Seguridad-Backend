-- Grants de g7_runtime — BORRADOR pendiente de ventana (TEC-INFRA-T002).
-- Ejecutar con psql como identidad administradora, nunca como g7_runtime, y
-- DESPUÉS de migrar V101–V108 + R__catalogo (las tablas deben existir):
--
--   psql "$DB_ADMIN_PSQL_URL" -v migrador=<rol que ejecuta Flyway> \
--        -f docs/migraciones-propuestas/grants-g7-runtime.sql
--
-- La contraseña NO va en este archivo: al terminar, en la misma sesión psql,
--   \password g7_runtime
-- (psql la envía ya cifrada; no queda en el historial ni en el log de sentencias).
-- Repetible: crea el rol solo si falta y recrea las políticas.
-- V108 (FEAT-07): incluye cambio_mfa y su política RLS para g7_runtime.

\set ON_ERROR_STOP on
BEGIN;

-- 1. Rol runtime mínimo, sin contraseña hasta \password.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'g7_runtime') THEN
        CREATE ROLE g7_runtime WITH LOGIN;
    END IF;
END
$$;
-- No se incluye ALTER ROLE g7_runtime: el pooler de Supabase lo rechaza con
-- "permission denied to alter role" y, con ON_ERROR_STOP, aborta la transacción.
-- CREATE ROLE ... WITH LOGIN ya crea el rol con los atributos mínimos por defecto
-- (NOSUPERUSER, NOCREATEDB, NOCREATEROLE, NOREPLICATION, NOBYPASSRLS). Si se
-- ejecuta contra un PostgreSQL directo y el rol ya existía con atributos
-- incorrectos, corregirlo aparte con una identidad superusuario, no desde el pooler.

GRANT CONNECT ON DATABASE postgres TO g7_runtime;
GRANT USAGE ON SCHEMA public TO g7_runtime;

-- 2. Cerrar acceso público y de Supabase Auth (anon/authenticated) sobre lo
--    existente y sobre lo que cree después el migrador (privilegios por defecto
--    de Supabase en public).
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC, anon, authenticated;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC, anon, authenticated;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC, anon, authenticated;
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrador" IN SCHEMA public REVOKE ALL ON TABLES FROM PUBLIC, anon, authenticated;
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrador" IN SCHEMA public REVOKE ALL ON SEQUENCES FROM PUBLIC, anon, authenticated;
ALTER DEFAULT PRIVILEGES FOR ROLE :"migrador" IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM PUBLIC, anon, authenticated;

-- 3. Auditoría: solo leer y anexar (RF-12.4). Sin UPDATE/DELETE/TRUNCATE.
GRANT SELECT, INSERT ON auditoria_seguridad TO g7_runtime;

-- 4. Catálogo de autorización: solo lectura. Lo escribe la migración
--    R__catalogo_autorizacion con el migrador, nunca el runtime.
GRANT SELECT ON rol, permiso, rol_permiso TO g7_runtime;
--    Guard ADMIN (SELECT … FOR UPDATE sobre la fila ADMIN_SISTEMA, orden de locks de
--    roles, login fallido y desbloqueo): PostgreSQL exige UPDATE para bloquear filas.
--    Se concede solo sobre una columna inocua y la política de abajo rechaza toda
--    escritura (WITH CHECK false): el runtime puede bloquear, nunca modificar.
GRANT UPDATE (descripcion) ON rol TO g7_runtime;

-- 5. Operativa del dominio: lectura/escritura, sin TRUNCATE.
GRANT SELECT, INSERT, UPDATE, DELETE ON
    usuario, perfil_cliente, perfil_vendedor, direccion,
    credencial, password_historial, otp, token_un_uso, solicitud_limitada,
    token_refresco, desafio_mfa, intento_login, bloqueo,
    sesion_familia, acceso_sesion, racha_fallo, huella_fallo,
    usuario_rol, cliente_servicio, scope, cliente_servicio_scope, outbox,
    cambio_mfa
    TO g7_runtime;

-- 5b. Vista de estado efectivo (V3): se evalúa con los privilegios de quien consulta
--     (security_invoker, ENTORNO-Y-REACT §1) y el runtime solo la lee.
ALTER VIEW usuario_estado_efectivo SET (security_invoker = true);
GRANT SELECT ON usuario_estado_efectivo TO g7_runtime;

-- 6. Secuencias de identity/serial (incluye auditoria_seguridad_id_seq).
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO g7_runtime;

-- 7. Políticas RLS solo para el runtime; anon/authenticated sin política = sin
--    acceso. RLS ya está ENABLED en las 22 tablas (DAT-01); se asegura también
--    en las nuevas de V102/V104/V108. Cada política coincide con su grant.
ALTER TABLE sesion_familia ENABLE ROW LEVEL SECURITY;
ALTER TABLE acceso_sesion ENABLE ROW LEVEL SECURITY;
ALTER TABLE racha_fallo ENABLE ROW LEVEL SECURITY;
ALTER TABLE huella_fallo ENABLE ROW LEVEL SECURITY;
ALTER TABLE cambio_mfa ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    tabla text;
BEGIN
    -- Operativa: todas las operaciones.
    FOREACH tabla IN ARRAY ARRAY[
        'usuario', 'perfil_cliente', 'perfil_vendedor', 'direccion',
        'credencial', 'password_historial', 'otp', 'token_un_uso', 'solicitud_limitada',
        'token_refresco', 'desafio_mfa', 'intento_login', 'bloqueo',
        'sesion_familia', 'acceso_sesion', 'racha_fallo', 'huella_fallo',
        'usuario_rol', 'cliente_servicio', 'scope', 'cliente_servicio_scope', 'outbox',
        'cambio_mfa']
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS g7_runtime_total ON %I', tabla);
        EXECUTE format('CREATE POLICY g7_runtime_total ON %I FOR ALL TO g7_runtime '
                       'USING (true) WITH CHECK (true)', tabla);
    END LOOP;

    -- Catálogo: solo lectura.
    FOREACH tabla IN ARRAY ARRAY['rol', 'permiso', 'rol_permiso']
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS g7_runtime_total ON %I', tabla);
        EXECUTE format('DROP POLICY IF EXISTS g7_runtime_lectura ON %I', tabla);
        EXECUTE format('CREATE POLICY g7_runtime_lectura ON %I FOR SELECT TO g7_runtime USING (true)', tabla);
    END LOOP;

    -- Solo bloqueo de fila en rol: USING permite el FOR UPDATE; WITH CHECK impide escribir.
    DROP POLICY IF EXISTS g7_runtime_guard ON rol;
    CREATE POLICY g7_runtime_guard ON rol FOR UPDATE TO g7_runtime USING (true) WITH CHECK (false);
END
$$;

-- Auditoría: leer y anexar; ninguna política de UPDATE/DELETE.
DROP POLICY IF EXISTS g7_runtime_total ON auditoria_seguridad;
DROP POLICY IF EXISTS g7_runtime_lectura ON auditoria_seguridad;
DROP POLICY IF EXISTS g7_runtime_anexar ON auditoria_seguridad;
CREATE POLICY g7_runtime_lectura ON auditoria_seguridad FOR SELECT TO g7_runtime USING (true);
CREATE POLICY g7_runtime_anexar ON auditoria_seguridad FOR INSERT TO g7_runtime WITH CHECK (true);

COMMIT;
