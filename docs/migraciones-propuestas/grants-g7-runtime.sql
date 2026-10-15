-- Grants de g7_runtime — BORRADOR pendiente de ventana (TEC-INFRA-T002).
-- Ejecutar como identidad administradora, nunca como g7_runtime.
-- La contraseña se entrega por canal privado en la ventana; aquí va marcador.
-- Sin RLS para anon/authenticated: solo políticas del rol servidor.

-- 1. Rol runtime mínimo (ajustar '<>' en la ventana).
CREATE ROLE g7_runtime WITH LOGIN PASSWORD '<definir-en-ventana>';
ALTER ROLE g7_runtime WITH NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;

GRANT CONNECT ON DATABASE postgres TO g7_runtime;
GRANT USAGE ON SCHEMA public TO g7_runtime;

-- 2. Auditoría: solo anexar (RF-12.4). Sin UPDATE/DELETE/TRUNCATE.
GRANT SELECT, INSERT ON auditoria_seguridad TO g7_runtime;
GRANT USAGE, SELECT ON SEQUENCE auditoria_seguridad_id_seq TO g7_runtime;

-- 3. Operativa del dominio: lectura/escritura, sin TRUNCATE.
GRANT SELECT, INSERT, UPDATE, DELETE ON
    usuario, perfil_cliente, perfil_vendedor, direccion,
    credencial, password_historial, otp, token_un_uso, solicitud_limitada,
    token_refresco, desafio_mfa, intento_login, bloqueo,
    sesion_familia, acceso_sesion, racha_fallo, huella_fallo,
    rol, permiso, rol_permiso, usuario_rol,
    cliente_servicio, scope, cliente_servicio_scope, outbox
    TO g7_runtime;

-- 4. Secuencias de identity/serial del dominio.
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO g7_runtime;

-- 5. Cerrar acceso público y de Supabase Auth sobre tablas G7.
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC, anon, authenticated;

-- 6. Políticas RLS: el runtime opera; anon/authenticated quedan sin política = sin acceso.
-- (RLS ya está ENABLED en las 22 tablas + racha/huella nuevas.)
CREATE POLICY g7_runtime_total ON usuario FOR ALL TO g7_runtime USING (true) WITH CHECK (true);
-- Repetir por tabla antes de aplicar; generar el bloque completo en la ventana
-- a partir del inventario real (22 + nuevas).
