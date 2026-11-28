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
-- El INSERT refleja la base desplegada al 10-nov (consulta Q2), más las concesiones
-- del 12, 23 y 25-nov hechas desde el módulo de configuración. Las concesiones
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
    ('modulo-chatbot',     'ventas:pagos:webhook'),    -- 23-nov, #108
    ('modulo-chatbot',     'ventas:pedidos:anular'),   -- 23-nov, #108
    ('modulo-retail',      'ventas:pedidos:crear'),
    -- Conservadas por decisión del 21-nov (docs #109): se suspende su revocación mientras G5
    -- confirma el uso. El contrato de G5 las reserva a modulo-postventa y modulo-despacho.
    ('modulo-retail',      'ventas:pedidos:consultar'),
    ('modulo-retail',      'ventas:pedidos:estado'),
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
    -- Aplicadas en la base desplegada (23 y 25-nov, confirmadas por David en #107 y #108):
    ('modulo-ventas',      'cupones:consumir'),
    ('modulo-ventas',      'cupones:restaurar'),
    ('modulo-ventas',      'cupones:operaciones:leer'),
    ('modulo-ventas',      'inventario:reservas:estado:leer'),
    ('modulo-ventas',      'inventario:disponibilidad:leer'),  -- #107
    ('promotions-svc',     'inventario:reservas:estado:leer'), -- #107, cliente nuevo de G6
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
--   ('modulo-retail',      'ventas:pedidos:anular'),

-- En espera de G5: ventas:pedidos:evidencia-cupon:leer para promotions-svc (#107).
--
-- No se conceden a ningún canal: postventa:devoluciones:escribir y postventa:reclamos:escribir.
-- G5 eligió el token del cliente para devoluciones, reclamos y evidencias (G5 #67, opción A,
-- 22-nov), así que los canales no necesitan token de servicio para esas operaciones.

-- ventas:pedidos:consultar y ventas:pedidos:estado para modulo-retail están en la base sin
-- respaldo del dueño (Q2 del 10-nov). Desde el 21-nov (docs #109) se conservan y figuran en el
-- INSERT mientras G5 confirma su uso; si G5 no lo confirma, se revocan desde el módulo de
-- configuración y se quitan de aquí.
