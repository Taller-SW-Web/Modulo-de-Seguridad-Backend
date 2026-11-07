# FEAT-04-T1/T2 — Direcciones del titular y puerto `ConsultaDireccionesPort`

Corte backend temprano de FEAT-04 (rama `feat/EP-08-direcciones-base`). No cierra FEAT-04 ni acredita todos sus RF.

## Implementación

### T1 — `GET` y `POST /usuarios/{id}/direcciones`

- `GET /api/v1/usuarios/{id}/direcciones`: lista las direcciones propias, con la predeterminada primero.
- `POST /api/v1/usuarios/{id}/direcciones`: registra una dirección y responde `201`.
- Validación de campos obligatorios (no vacíos ni solo espacios) y máximos de longitud del contrato OpenAPI; `400 VALIDACION` sin repetir los valores enviados.
- Autorización por titularidad: si el UUID del path no corresponde al usuario autenticado, responde `403 SCOPE_INSUFICIENTE` **antes** de consultar nada, sin revelar si la cuenta existe. Una cuenta propia que ya no existe responde `404 NO_ENCONTRADO`.
- La primera dirección es predeterminada automáticamente. Al marcar una nueva como predeterminada, las anteriores se desmarcan y se fuerza el `flush` antes del `INSERT` para respetar el índice único parcial `uq_direccion_predeterminada`.
- La alta bloquea la fila de la cuenta propietaria, incluso cuando aún no existen direcciones, para serializar altas concurrentes.
- `etiqueta` y `referencia` vacías o solo con espacios se guardan como `null`; los demás textos se recortan.
- El núcleo (`DireccionesService`) no depende de Spring. La transacción la abre el decorador `DireccionesPortTransaccional` (`adapter/in/transaction`), igual que `PerfilPortTransaccional`: lecturas de solo lectura y alta en `READ_COMMITTED`. Así se cumple la regla ArchUnit de aplicación/dominio.
- El controlador depende solo del puerto de entrada `DireccionesPort`; persistencia y consumos de otras capacidades quedan detrás de puertos/adaptadores.
- El evento `usuario.atributos_actualizados` se guarda por el puerto exportado `MensajeriaPort.guardarEvento` (no por `OutboxPort`) dentro de la misma transacción del alta, con `campos` = `direcciones` (nombre del campo, nunca valores). `MensajeriaService.sobre` lo publica como lista JSON (`"campos": ["direcciones"]`).
- **Auditoría (RF-16.10).** Cada alta registra `ATRIBUTOS_ACTUALIZADOS` con resultado `EXITO` por `AuditoriaPort.registrarNoCriticaTrasCommit` (acción no crítica: se intenta tras el commit y con reintento privado). El detalle lleva solo `campos=direcciones`; nunca los valores de la dirección. Si la auditoría falla, el adaptador lo registra en el log (solo la clase de la excepción) y no revierte ni oculta la dirección creada. Un alta rechazada (`400`, `403`, `404`) no deja registro de éxito.
- **Acceso según el contrato, con permisos vigentes.** `GET` lo puede hacer el titular o quien tenga hoy `usuario.ver`; `POST`, el titular o quien tenga hoy el rol `ADMIN_SISTEMA`. La decisión pasa por `AutorizacionPort.resolver` (base de datos), no por los claims del token. El orden se mantiene: primero autorización (`403` neutro, sin revelar si la cuenta existe) y luego existencia (`404`). Con un alta administrativa, el evento y la auditoría apuntan a la cuenta objetivo, y el actor de la auditoría es el administrador.
- No se agrega migración: la tabla `direccion` forma parte del esquema existente y `g7_runtime` ya tiene `SELECT/INSERT/UPDATE/DELETE` sobre ella.

### T2 — `ConsultaDireccionesPort`

- `ConsultaDireccionesPort.consultar(UUID)` en `direccion/application/port/in`; DTO `DireccionDatos`, sin entidades JPA.
- `ConsultaDireccionesService` (sin Spring) delega en `DireccionesPersistidasPort`, devuelve copia inmutable y rechaza `usuarioId` nulo.
- Bean `ConsultaDireccionesPort` en `DireccionesConfig`, conectado al adaptador real de PostgreSQL.
- Contrato y ejemplo de adaptador puente para FEAT-06: [`docs/capacidades/direcciones.md`](../../capacidades/direcciones.md).
- No se añade endpoint ni migración: el consumo HTTP de FEAT-06 (`direcciones:leer`) es de Sergio; el `GET` del titular es el de T1.

## Pruebas

| Prueba | Qué cubre |
|---|---|
| `DireccionesPersistidasPostgresTest` (PostgreSQL, Testcontainers) | primera predeterminada; nueva predeterminada reemplaza a la anterior; una segunda sin pedirlo conserva la predeterminada y la lista la pone primero; una sola predeterminada en base; aislamiento entre usuarios; opcionales vacíos a `null` y recorte |
| `DireccionesHttpTest` (HTTP, filtro de acceso real) | `200` lista vacía; `201` con la dirección; evento publicado y bloqueo previo a la escritura; una sola predeterminada; `403` de otro usuario sin consulta ni efecto; lectura ajena con `usuario.ver` vigente (`200`) y con el permiso solo en el token (`403`); alta ajena con `ADMIN_SISTEMA` vigente (`201`) y con solo `usuario.ver` (`403`); auditoría del alta; ajeno existente e inexistente responden igual; `404` propia inexistente; `400` por campo faltante, solo espacios, máximo excedido, cuerpo no JSON e id no UUID; `401` sin token; `403 TOKEN_NO_APLICABLE` con token de servicio |
| `DireccionesServiceTest` | titularidad, orden bloquear→crear, un solo evento, sin evento ni consultas ante `403`/`404`; una auditoría por alta y ninguna si se rechaza; `usuario.ver` lee ajeno pero no añade; `ADMIN_SISTEMA` añade con evento y auditoría sobre el objetivo; el permiso solo en el token se deniega |
| `MensajeriaDireccionesAdapterTest` | el evento entregado a `MensajeriaPort` lleva solo `usuarioId`, `campos` y `fecha` |
| `SobreAtributosActualizadosTest` | `campos` sale como lista JSON (uno, varios, vacío) |
| `AuditoriaDireccionesAdapterTest` | éxito no crítico con actor, objetivo, ip y agente, detalle solo `campos`; un fallo de `AuditoriaPort` no se propaga |
| `AutorizacionDireccionesAdapterTest` | titular sin consulta; `usuario.ver` lee pero no añade; `ADMIN_SISTEMA` añade; claims del token sin respaldo en la base no autorizan |
| `ArquitecturaHexagonalTest` / `ContratosExportados` | la capacidad `direccion` está registrada en ambas |

### Pruebas de T2

| Prueba | Qué cubre |
|---|---|
| `ConsultaDireccionesServiceTest` | delegación, lista vacía, orden respetado, lista inmutable y aislada de la fuente, `null` rechazado sin tocar persistencia |
| `ConsultaDireccionesPostgresTest` (PostgreSQL, esquema real) | el puerto real lee de la base: solo las del usuario, predeterminada primero, vacío si no hay o la cuenta no existe, lista inmutable, el DTO no trae documento ni fecha de nacimiento |
| `ArquitecturaHexagonalTest` / `ContratosExportados` | el puerto y el DTO no exponen tipos internos; `application` sin Spring |

## Validación

Rutas: `src/main/java/.../direccion/**` y `src/test/java/.../direccion/**` en la raíz del repo (sin copia anidada del backend).

| Comprobación | Resultado | SHA |
|---|---|---|
| CI del PR #68 (3 checks) | aprobado, según la revisión de @LuisBrenis3011 | `bec1b8d` |
| `./gradlew build` local (compila y corre todas las pruebas) | PENDIENTE: pegar `BUILD SUCCESSFUL` y el total de pruebas | PENDIENTE |
| Pruebas `direccion` (HTTP, PostgreSQL) y ArchUnit | PENDIENTE: pegar el resultado del run del último commit | PENDIENTE |
| CI tras los cambios de la revisión | PENDIENTE: enlace al run | PENDIENTE |

Antes de pedir la nueva revisión, sustituir cada PENDIENTE por el resultado real y el SHA del último commit.

## Aviso al grupo (borrador)

> **FEAT-04-T2 listo para consumir** (rama `feat/EP-08-direcciones-base`, SHA `<pegar>`).
> `com.marketplace.auth_service.direccion.application.port.in.ConsultaDireccionesPort`
> `List<DireccionDatos> consultar(UUID usuarioId)` — solo lectura, predeterminada primero, `[]` si no hay, sin documento ni fecha de nacimiento.
> No autoriza ni comprueba que la cuenta exista: validen `direcciones:leer` y existencia (`CuentasPort.leer`) en su adaptador puente. Detalle: `docs/capacidades/direcciones.md`.
> @Sergio — FEAT-06-T3.

## Decisiones y puntos abiertos

- La SPEC-16 no define un máximo de direcciones por usuario; no se impone un límite hasta recibir confirmación de producto (Sergio).
- El acceso administrativo de OpenAPI (`usuario.ver` en `GET`, `ADMIN_SISTEMA` en `POST`) **sí** queda implementado en este corte, con permisos vigentes. T6 (`PATCH /usuarios/{id}/atributos`) no cubre estas rutas.
- Por confirmar con el PO: si el backend debe rechazar el alta en cuentas sin rol `CLIENTE` (FRONT-06 solo muestra la sección a clientes).
- La auditoría del alta (RF-16.10) se registra aquí, no crítica; la del cambio de correo (`CORREO_CAMBIADO`) llega con T3.
- Se tocó `MensajeriaService.sobre` (módulo de auditoría) solo para publicar `campos` como lista; avisar al dueño en el PR.
