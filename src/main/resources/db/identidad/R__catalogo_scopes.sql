-- ============================================================================
-- Catálogo de scopes de servicio (SPEC-17 RF-17.12/17.15, acuerdo A4) — dueño: Sergio
--
-- Siembra la tabla scope con los scopes APROBADOS en docs/integracion/matriz-scopes.md
-- (corte 10-nov-2026) y la API dueña de cada uno, que es la audiencia del token.
-- Cada API dueña define y valida su scope; Seguridad solo lo registra y lo emite.
--
-- Migración REPETIBLE e idempotente, como R__catalogo_autorizacion: inserta lo que
-- falte y corrige audiencia/descripción; no borra filas. No contiene clientes,
-- secretos ni concesiones: los aprovisiona operaciones con su runbook
-- (docs/runbooks/clientes-servicio.md), nunca esta migración.
--
-- Requiere la tabla scope de V4. Vive en db/identidad (dueño: identidad), aparte del
-- catálogo de autorización, y el perfil migration la incluye en spring.flyway.locations. Un scope nuevo entra aquí solo cuando la matriz
-- lo marca como aprobado por su dueño.
--
-- 10-nov-2026 (PO-T2, H5): se añaden ventas:pedidos:reemplazar, productos:fisicos:leer
-- e inventario:operaciones:leer. Ya estaban en la base desplegada (27 scopes, consulta
-- Q7 de docs/integracion/hito-5-clientes-tecnicos.md) y los define su API dueña:
-- api-ventas.md §1.11 de G5 y el OpenAPI 0.6.0-rc.3 de G6. Con ellos el archivo tiene
-- 31 scopes. Render no ejecuta Flyway (spring.flyway.enabled=false), así que la base
-- desplegada no los recibe al desplegar: David aplica este archivo a mano después del
-- merge, previa consulta de solo lectura de los códigos que faltan.
-- ============================================================================

INSERT INTO scope (codigo, audiencia, descripcion) VALUES
    -- api-seguridad (SPEC-17/18)
    ('tokens:introspeccion',            'api-seguridad',  'Introspección de tokens de usuario'),
    ('usuarios:leer',                   'api-seguridad',  'Datos básicos de usuario, individuales y por lote'),
    ('usuarios:leer:documento',         'api-seguridad',  'Número de documento en claro en vez de enmascarado'),
    ('direcciones:leer',                'api-seguridad',  'Direcciones de entrega del cliente'),
    ('roles:leer',                      'api-seguridad',  'Catálogo de roles y de permisos'),
    -- api-despacho (A4, A8, A10)
    ('cotizaciones:calcular',           'api-despacho',   'Calcular cobertura, costo y plazo de envío'),
    ('seguimientos:leer',               'api-despacho',   'Consultar el seguimiento por idPedido'),
    ('despachos:crear',                 'api-despacho',   'Crear un despacho de un pedido'),
    ('despachos:cancelar',              'api-despacho',   'Cancelar un despacho'),
    -- api-ventas (A10, #51, G5 api-ventas §1.11)
    ('ventas:pedidos:crear',            'api-ventas',     'Crear un pedido'),
    ('ventas:pedidos:anular',           'api-ventas',     'Anular un pedido'),
    ('ventas:pedidos:estado',           'api-ventas',     'Actualizar el estado de un pedido'),
    ('ventas:pedidos:consultar',        'api-ventas',     'Consultar un pedido conocido por su id'),
    ('ventas:pedidos:reemplazar',       'api-ventas',     'Crear la orden de reemplazo de un cambio'),
    ('ventas:pagos:webhook',            'api-ventas',     'Notificar el resultado de un pago'),
    -- api-postventa (A10)
    ('postventa:devoluciones:escribir', 'api-postventa',  'Registrar devoluciones'),
    ('postventa:reclamos:escribir',     'api-postventa',  'Registrar reclamos'),
    ('postventa:reembolsos:procesar',   'api-postventa',  'Procesar reembolsos'),
    -- api-productos (A9, G6 #87 A2, #51, G6 OpenAPI 0.6.0-rc.3)
    ('catalogo:leer',                   'api-productos',  'Consultar el catálogo'),
    ('precios:leer',                    'api-productos',  'Consultar precios'),
    ('promociones:leer',                'api-productos',  'Consultar promociones'),
    ('promociones:evaluar',             'api-productos',  'Evaluar promociones aplicables'),
    ('cupones:validar',                 'api-productos',  'Validar cupones'),
    ('recomendaciones:leer',            'api-productos',  'Consultar recomendaciones'),
    ('inventario:disponibilidad:leer',  'api-productos',  'Consultar disponibilidad comercial'),
    ('inventario:reservar',             'api-productos',  'Reservar inventario de un pedido'),
    ('inventario:consumir',             'api-productos',  'Consumir inventario reservado'),
    ('inventario:liberar',              'api-productos',  'Liberar inventario reservado'),
    ('inventario:reintegrar',           'api-productos',  'Reintegrar inventario por devolución'),
    ('inventario:operaciones:leer',     'api-productos',  'Consultar el resultado de una operación de inventario'),
    ('productos:fisicos:leer',          'api-productos',  'Consultar peso y dimensiones físicas por SKU')
ON CONFLICT (codigo) DO UPDATE
    SET audiencia = EXCLUDED.audiencia,
        descripcion = EXCLUDED.descripcion;
