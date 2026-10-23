# FEAT-04-T1 — Direcciones del titular

## Implementación

- `GET /api/v1/usuarios/{id}/direcciones`: lista las direcciones propias, con la predeterminada primero.
- `POST /api/v1/usuarios/{id}/direcciones`: registra una dirección y responde `201`.
- Validación de campos obligatorios (no vacíos ni solo espacios) y máximos de longitud del contrato OpenAPI; `400 VALIDACION` sin repetir los valores enviados.
- Autorización por titularidad: si el UUID del path no corresponde al usuario autenticado, responde `403 SCOPE_INSUFICIENTE` **antes** de consultar nada, sin revelar si la cuenta existe. Una cuenta propia que ya no existe responde `404 NO_ENCONTRADO`.
- La primera dirección es predeterminada automáticamente. Al marcar una nueva como predeterminada, las anteriores se desmarcan y se fuerza el `flush` antes del `INSERT` para respetar el índice único parcial `uq_direccion_predeterminada`.
- La alta bloquea la fila de la cuenta propietaria, incluso cuando aún no existen direcciones, para serializar altas concurrentes.
- `etiqueta` y `referencia` vacías o solo con espacios se guardan como `null`; los demás textos se recortan.
- El núcleo (`DireccionesService`) no depende de Spring. La transacción la abre el decorador `DireccionesPortTransaccional` (`adapter/in/transaction`), igual que `PerfilPortTransaccional`: lecturas de solo lectura y alta en `READ_COMMITTED`. Así se cumple la regla ArchUnit de aplicación/dominio.
- El controlador depende solo del puerto de entrada `DireccionesPort`; persistencia y consumos de otras capacidades quedan detrás de puertos/adaptadores.
- El evento `usuario.atributos_actualizados` se guarda en el outbox dentro de la misma transacción del alta, con `campos` = `direcciones` (nombre del campo, nunca valores). `MensajeriaService.sobre` lo publica como lista JSON (`"campos": ["direcciones"]`).
- No se agrega migración: la tabla `direccion` forma parte del esquema existente y `g7_runtime` ya tiene `SELECT/INSERT/UPDATE/DELETE` sobre ella.

## Pruebas

| Prueba | Qué cubre |
|---|---|
| `DireccionesPersistidasPostgresTest` (PostgreSQL, Testcontainers) | primera predeterminada; nueva predeterminada reemplaza a la anterior; una segunda sin pedirlo conserva la predeterminada y la lista la pone primero; una sola predeterminada en base; aislamiento entre usuarios; opcionales vacíos a `null` y recorte |
| `DireccionesHttpTest` (HTTP, filtro de acceso real) | `200` lista vacía; `201` con la dirección; evento publicado y bloqueo previo a la escritura; una sola predeterminada; `403` de otro usuario sin consulta ni efecto; ajeno existente e inexistente responden igual; `404` propia inexistente; `400` por campo faltante, solo espacios, máximo excedido, cuerpo no JSON e id no UUID; `401` sin token; `403 TOKEN_NO_APLICABLE` con token de servicio |
| `DireccionesServiceTest` | titularidad, orden bloquear→crear, un solo evento, sin evento ni consultas ante `403`/`404` |
| `OutboxDireccionesAdapterTest` | el mensaje del outbox lleva solo `eventoId`, `usuarioId`, `campos` y `fecha` |
| `SobreAtributosActualizadosTest` | `campos` sale como lista JSON (uno, varios, vacío) |
| `ArquitecturaHexagonalTest` / `ContratosExportados` | la capacidad `direccion` está registrada en ambas |

## Validación pendiente

Las pruebas se escribieron y revisaron estáticamente, pero **no se han ejecutado todavía**. Antes de abrir el PR:

```bash
./gradlew test        # requiere Docker para Testcontainers
```

Anotar aquí el resultado, la fecha y el SHA del commit.

## Decisiones y puntos abiertos

- La SPEC-16 no define un máximo de direcciones por usuario; no se impone un límite hasta recibir confirmación de producto (Sergio).
- El acceso administrativo que menciona OpenAPI (`usuario.ver`, `ADMIN_SISTEMA`) queda fuera de esta tarea de direcciones propias; se retoma en T6.
- Por confirmar con el PO: si el backend debe rechazar el alta en cuentas sin rol `CLIENTE` (FRONT-06 solo muestra la sección a clientes).
- Auditoría `ATRIBUTOS_ACTUALIZADOS` (RF-16.10) no se registra aquí; queda coordinada con Christian junto con la de perfil.
- Se tocó `MensajeriaService` (módulo de auditoría) solo para publicar `campos` como lista; avisar al dueño en el PR.
