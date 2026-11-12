-- ============================================================================
-- Concesiones de scopes a clientes de servicio (SPEC-17 RF-17.5/17.15) — PROPUESTA
--
-- NO es una migración Flyway y nunca se ejecuta sola. Sirve para reconstruir las
-- concesiones en una base nueva, DESPUÉS de crear los clientes con su hash según
-- docs/runbooks/clientes-servicio.md y DESPUÉS de R__catalogo_scopes.
--
-- Fuente: docs/integracion/matriz-scopes.md del repo de documentación (corte
-- 10-nov-2026), solo filas «Aprobada». Sin secretos ni hashes: solo client_id ↔ scope.
-- Idempotente: solo concede a clientes que ya existen y no duplica filas. Un
-- cliente que aún no se creó simplemente no recibe nada.
--
-- Desde H5 una concesión nueva en la base desplegada se hace desde el módulo de
-- configuración (SPEC-19, FEAT-14), que la audita con autor y fecha. Este archivo
-- se actualiza después, para que el repositorio refleje la base.
--
-- El INSERT refleja la base desplegada al 10-nov (consulta Q2), más la concesión
-- del 12-nov a modulo-productos hecha desde el módulo de configuración. Las concesiones
-- aprobadas en la matriz que la base todavía no tiene están al final, comentadas:
-- ejecutar este archivo no las concede.
-- ============================================================================

INSERT INTO cliente_servicio_scope (client_id, scope)
SELECT v.client_id, v.scope
  FROM (VALUES
    -- api-seguridad
    ('modulo-chatbot',     'tokens:introspeccion'),
    ('modulo-ventas',      'usuarios:leer'),
    ('modulo-postventa',   'usuarios:leer'),
    ('modulo-despacho',    'usuarios:leer'),
    ('modulo-despacho',    'direcciones:leer'),
    ('modulo-productos',   'tokens:introspeccion'),
    ('modulo-productos',   'roles:leer'),
    -- api-despacho
    ('modulo-marketplace', 'cotizaciones:calcular'),
    ('modulo-marketplace', 'seguimientos:leer'),
    ('modulo-chatbot',     'cotizaciones:calcular'),
    ('modulo-chatbot',     'seguimientos:leer'),
    ('modulo-ventas',      'despachos:crear'),
    ('modulo-ventas',      'despachos:cancelar'),
    -- api-ventas
    ('modulo-marketplace', 'ventas:pedidos:crear'),
    ('modulo-chatbot',     'ventas:pedidos:crear'),
    ('modulo-retail',      'ventas:pedidos:crear'),
    ('modulo-despacho',    'ventas:pedidos:estado'),
    ('simulador-pasarela', 'ventas:pagos:webhook'),
    ('modulo-postventa',   'ventas:pedidos:consultar'),
    ('modulo-postventa',   'ventas:pedidos:reemplazar'),
    -- api-postventa
    ('modulo-ventas',      'postventa:reembolsos:procesar'),
    -- api-productos
    ('modulo-marketplace', 'catalogo:leer'),
    ('modulo-marketplace', 'precios:leer'),
    ('modulo-marketplace', 'inventario:disponibilidad:leer'),
    ('modulo-chatbot',     'catalogo:leer'),
    ('modulo-chatbot',     'precios:leer'),
    ('modulo-chatbot',     'promociones:leer'),
    ('modulo-chatbot',     'promociones:evaluar'),
    ('modulo-chatbot',     'cupones:validar'),
    ('modulo-chatbot',     'recomendaciones:leer'),
    ('modulo-chatbot',     'inventario:disponibilidad:leer'),
    ('modulo-ventas',      'inventario:reservar'),
    ('modulo-ventas',      'inventario:consumir'),
    ('modulo-ventas',      'inventario:liberar'),
    ('modulo-ventas',      'inventario:operaciones:leer'),
    ('modulo-postventa',   'inventario:reintegrar'),
    ('modulo-postventa',   'inventario:disponibilidad:leer'),
    ('modulo-productos',   'catalogo:leer'),
    ('modulo-productos',   'precios:leer'),
    ('modulo-productos',   'inventario:disponibilidad:leer'),  -- 12-nov, desde Clientes técnicos (#78)
    ('modulo-despacho',    'productos:fisicos:leer')
  ) AS v(client_id, scope)
  JOIN cliente_servicio c ON c.client_id = v.client_id
  JOIN scope s ON s.codigo = v.scope
ON CONFLICT (client_id, scope) DO NOTHING;

-- Aprobadas en la matriz y NO concedidas al 10-nov: ningún módulo las ha pedido.
-- Se conceden desde el módulo de configuración cuando el módulo las pida, y
-- entonces se mueven al INSERT de arriba.
--   ('modulo-marketplace', 'usuarios:leer'),
--   ('modulo-chatbot',     'usuarios:leer'),
--   ('modulo-retail',      'usuarios:leer'),
--   ('modulo-retail',      'roles:leer'),
--   ('modulo-marketplace', 'ventas:pedidos:anular'),
--   ('modulo-chatbot',     'ventas:pedidos:anular'),
--   ('modulo-chatbot',     'ventas:pagos:webhook'),
--   ('modulo-retail',      'ventas:pedidos:anular'),
--   ('modulo-marketplace', 'postventa:devoluciones:escribir'),
--   ('modulo-marketplace', 'postventa:reclamos:escribir'),
--   ('modulo-chatbot',     'postventa:devoluciones:escribir'),
--   ('modulo-chatbot',     'postventa:reclamos:escribir'),
--   ('modulo-retail',      'postventa:devoluciones:escribir'),
--   ('modulo-retail',      'postventa:reclamos:escribir'),

-- Concedidas en la base sin respaldo en la matriz (Q2 del 10-nov), NO incluidas aquí:
-- ventas:pedidos:consultar y ventas:pedidos:estado para modulo-retail. El contrato de
-- G5 reserva la primera a modulo-postventa y la segunda a modulo-despacho. Quedan en
-- la base hasta que G5 las confirme o se revoquen desde el módulo de configuración.
