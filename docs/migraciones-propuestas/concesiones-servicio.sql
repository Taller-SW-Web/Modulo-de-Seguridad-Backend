-- ============================================================================
-- Concesiones de scopes a clientes de servicio (SPEC-17 RF-17.5/17.15) — PROPUESTA
--
-- NO es una migración Flyway y nunca se ejecuta sola. La aplica operaciones
-- (Christian) en su ventana, DESPUÉS de crear los clientes con su hash según
-- docs/runbooks/clientes-servicio.md y DESPUÉS de R__catalogo_scopes.
--
-- Fuente: docs/integracion/matriz-scopes.md del repo de documentación (corte
-- 16-oct-2026), solo filas «Aprobada». Sin secretos ni hashes: solo client_id ↔ scope.
-- Idempotente: solo concede a clientes que ya existen y no duplica filas. Un
-- cliente que aún no se creó simplemente no recibe nada.
-- ============================================================================

INSERT INTO cliente_servicio_scope (client_id, scope)
SELECT v.client_id, v.scope
  FROM (VALUES
    -- api-seguridad
    ('modulo-marketplace', 'usuarios:leer'),
    ('modulo-chatbot',     'usuarios:leer'),
    ('modulo-chatbot',     'tokens:introspeccion'),
    ('modulo-retail',      'usuarios:leer'),
    ('modulo-retail',      'roles:leer'),
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
    ('modulo-marketplace', 'ventas:pedidos:anular'),
    ('modulo-chatbot',     'ventas:pedidos:crear'),
    ('modulo-chatbot',     'ventas:pedidos:anular'),
    ('modulo-chatbot',     'ventas:pagos:webhook'),
    ('modulo-retail',      'ventas:pedidos:crear'),
    ('modulo-retail',      'ventas:pedidos:anular'),
    ('modulo-despacho',    'ventas:pedidos:estado'),
    ('simulador-pasarela', 'ventas:pagos:webhook'),
    ('modulo-postventa',   'ventas:pedidos:consultar'),
    -- api-postventa
    ('modulo-marketplace', 'postventa:devoluciones:escribir'),
    ('modulo-marketplace', 'postventa:reclamos:escribir'),
    ('modulo-chatbot',     'postventa:devoluciones:escribir'),
    ('modulo-chatbot',     'postventa:reclamos:escribir'),
    ('modulo-retail',      'postventa:devoluciones:escribir'),
    ('modulo-retail',      'postventa:reclamos:escribir'),
    ('modulo-ventas',      'postventa:reembolsos:procesar'),
    -- api-productos
    ('modulo-marketplace', 'catalogo:leer'),
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
    ('modulo-postventa',   'inventario:reintegrar')
  ) AS v(client_id, scope)
  JOIN cliente_servicio c ON c.client_id = v.client_id
  JOIN scope s ON s.codigo = v.scope
ON CONFLICT (client_id, scope) DO NOTHING;

-- Pendientes, NO concedidos (matriz §Catálogo de Productos): productos:fisicos:leer
-- para modulo-despacho sigue esperando la confirmación de G6 por cliente.
-- catalogo:leer para modulo-marketplace quedó confirmado por G6 el 26-oct (ver
-- matriz-scopes.md) y ya está en la lista de arriba.
