-- ============================================================================
-- Catálogo de autorización (SPEC-11 RF-11.1, RF-11.9) — dueño: Sergio (EP-05)
--
-- Siembra las tablas rol, permiso y rol_permiso, que son de Sergio
-- (DISENO-PERSISTENCIA §1). Separa el catálogo de V100, que mezclaba catálogo
-- y cuentas de prueba y no debe ejecutarse automáticamente.
--
-- Migración REPETIBLE e idempotente: no ocupa número de versión (V101–V106 están
-- reservadas) y se puede aplicar sobre una base donde V100 ya corrió.
--   * rol y permiso: inserta lo que falte y lleva nombre/descripción a los
--     canónicos (FRONT-00), corrigiendo las etiquetas antiguas de V100.
--   * rol_permiso: inserta la matriz cerrada; no borra filas.
-- Debe coincidir con CatalogoAutorizacion.java; una prueba lo comprueba.
--
-- Requiere las tablas de V1. Vive en db/autorizacion y no en db/migration hasta
-- que Christian incorpore V1–V4 al backend nuevo (TEC-INFRA-T002) y decida su
-- ubicación en el historial.
-- ============================================================================

INSERT INTO rol (codigo, nombre, descripcion) VALUES
    ('CLIENTE',          'Cliente',                   'Persona que compra en la plataforma'),
    ('VENDEDOR',         'Vendedor',                  'Vendedor de tienda: registra y busca clientes'),
    ('ADMIN_VENTAS',     'Administrador de ventas',   'Rol de gestión de Ventas'),
    ('GESTOR_DESPACHO',  'Gestor de despacho',        'Rol de gestión de Despacho'),
    ('GESTOR_COMERCIAL', 'Gestor comercial',          'Rol de gestión de Productos y ofertas'),
    ('ADMIN_SISTEMA',    'Administrador del sistema', 'Administra cuentas, roles y seguridad')
ON CONFLICT (codigo) DO UPDATE
    SET nombre = EXCLUDED.nombre,
        descripcion = EXCLUDED.descripcion;

INSERT INTO permiso (codigo, modulo, descripcion) VALUES
    ('usuario.ver',        'seguridad', 'Listar y consultar cualquier cuenta'),
    ('usuario.crear',      'seguridad', 'Dar de alta vendedores y roles de gestión'),
    ('usuario.desactivar', 'seguridad', 'Dar de baja una cuenta'),
    ('usuario.reactivar',  'seguridad', 'Reactivar una cuenta dada de baja'),
    ('rol.asignar',        'seguridad', 'Asignar y revocar roles'),
    ('auditoria.ver',      'seguridad', 'Consultar y exportar la auditoría'),
    ('cuenta.bloquear',    'seguridad', 'Bloquear y desbloquear cuentas'),
    ('atributos.editar',   'seguridad', 'Editar los atributos de cualquier cuenta'),
    ('cliente.registrar',  'seguridad', 'Registrar a un cliente en tienda (A6)'),
    ('cliente.buscar',     'seguridad', 'Encontrar a un cliente por su documento (A7)')
ON CONFLICT (codigo) DO UPDATE
    SET modulo = EXCLUDED.modulo,
        descripcion = EXCLUDED.descripcion;

INSERT INTO rol_permiso (rol_codigo, permiso_codigo) VALUES
    ('ADMIN_SISTEMA', 'usuario.ver'),
    ('ADMIN_SISTEMA', 'usuario.crear'),
    ('ADMIN_SISTEMA', 'usuario.desactivar'),
    ('ADMIN_SISTEMA', 'usuario.reactivar'),
    ('ADMIN_SISTEMA', 'rol.asignar'),
    ('ADMIN_SISTEMA', 'auditoria.ver'),
    ('ADMIN_SISTEMA', 'cuenta.bloquear'),
    ('ADMIN_SISTEMA', 'atributos.editar'),
    ('VENDEDOR',      'cliente.registrar'),
    ('VENDEDOR',      'cliente.buscar')
ON CONFLICT (rol_codigo, permiso_codigo) DO NOTHING;
