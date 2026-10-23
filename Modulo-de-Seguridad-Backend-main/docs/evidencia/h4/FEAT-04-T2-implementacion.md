# FEAT-04-T2 — Puerto `ConsultaDireccionesPort` exportado

## Implementación

- `ConsultaDireccionesPort.consultar(UUID)` en `direccion/application/port/in`; DTO `DireccionDatos`, sin entidades JPA.
- `ConsultaDireccionesService` (sin Spring) delega en `DireccionesPersistidasPort`, devuelve copia inmutable y rechaza `usuarioId` nulo.
- Bean `ConsultaDireccionesPort` en `DireccionesConfig`, conectado al adaptador real de PostgreSQL.
- Contrato y ejemplo de adaptador puente para FEAT-06: [`docs/integracion/FEAT-04-T2-ConsultaDireccionesPort.md`](../../integracion/FEAT-04-T2-ConsultaDireccionesPort.md).
- No se añade endpoint ni migración: el consumo HTTP de FEAT-06 (`direcciones:leer`) es de Sergio; el `GET` del titular es el de T1.

## Pruebas

| Prueba | Qué cubre |
|---|---|
| `ConsultaDireccionesServiceTest` | delegación, lista vacía, orden respetado, lista inmutable y aislada de la fuente, `null` rechazado sin tocar persistencia |
| `ConsultaDireccionesPostgresTest` (PostgreSQL, esquema real) | el puerto real lee de la base: solo las del usuario, predeterminada primero, vacío si no hay o la cuenta no existe, lista inmutable, el DTO no trae documento ni fecha de nacimiento |
| `ArquitecturaHexagonalTest` / `ContratosExportados` | el puerto y el DTO no exponen tipos internos; `application` sin Spring |

## Validación pendiente

```bash
./gradlew test        # requiere Docker para Testcontainers
```

Anotar resultado, fecha y SHA. T2 va en su propio PR, apilado sobre T1 (necesita `DireccionesPersistidasPort`, `DireccionDatos` y `DireccionesConfig` de T1). Después: fusionar, comprobar el despliegue con T1 y avisar al grupo.

## Aviso al grupo (borrador)

> **FEAT-04-T2 listo para consumir** (rama `<pegar rama>`, SHA `<pegar>`).
> `com.marketplace.auth_service.direccion.application.port.in.ConsultaDireccionesPort`
> `List<DireccionDatos> consultar(UUID usuarioId)` — solo lectura, predeterminada primero, `[]` si no hay, sin documento ni fecha de nacimiento.
> No autoriza ni comprueba que la cuenta exista: validen `direcciones:leer` y existencia (`CuentasPort.leer`) en su adaptador puente (`<capacidad>.adapter.out.capacidad`). Detalle y ejemplo: `docs/integracion/FEAT-04-T2-ConsultaDireccionesPort.md`.
> @Sergio — FEAT-06-T3.
