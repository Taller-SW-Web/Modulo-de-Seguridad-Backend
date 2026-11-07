# Capacidad `direccion` — contrato exportado (FEAT-04-T1/T2)

**Puerto:** `com.marketplace.auth_service.direccion.application.port.in.ConsultaDireccionesPort`
**DTO:** `com.marketplace.auth_service.direccion.application.dto.DireccionDatos`

```java
public interface ConsultaDireccionesPort {
    List<DireccionDatos> consultar(UUID usuarioId);
}

public record DireccionDatos(UUID id, String etiqueta, String departamento, String provincia,
        String distrito, String direccion, String referencia, boolean esPredeterminada) {}
```

## Garantías

- Solo lectura. La implementación es `ConsultaDireccionesService`, registrada como bean en `DireccionesConfig`, y lee de la base real a través de `DireccionesPersistidasAdapter`.
- Orden: la predeterminada primero y luego por identificador.
- Usuario sin direcciones: lista vacía. `usuarioId` nulo: `NullPointerException`.
- La lista devuelta **no es modificable** y no cambia aunque la fuente cambie después.
- `DireccionDatos` es un DTO de aplicación: no expone entidades ni repositorios JPA, y **no incluye documento ni fecha de nacimiento** (ESC-18.4).

## Lo que NO hace (responsabilidad del consumidor)

- **No autoriza.** Verificar antes el scope `direcciones:leer` (token de servicio) o la titularidad / permiso `usuario.ver` (token de persona).
- **No distingue cuenta inexistente de cuenta sin direcciones**: ambas devuelven `[]`. Si el endpoint debe responder `404` para un id desconocido (OpenAPI), comprobar la existencia con el puerto de cuentas (`CuentasPort.leer`) antes de llamar a este puerto.
- No audita el acceso. Si FEAT-06 lo exige, se registra en su adaptador.

## Cómo consumirlo (ArchUnit)

La regla `otraCapacidadSoloSeConsumeDesdeUnPuenteYPorExports` solo permite usar este puerto desde `<tu-capacidad>.adapter.out.capacidad`, por ejemplo:

```java
package com.marketplace.auth_service.<tu_capacidad>.adapter.out.capacidad;

public final class DireccionesClienteAdapter implements <TuPuertoDeSalida> {
    private final ConsultaDireccionesPort direcciones;   // port.in exportado de direccion
    public DireccionesClienteAdapter(ConsultaDireccionesPort direcciones) { this.direcciones = direcciones; }

    @Override public List<DireccionDatos> de(UUID usuarioId) { return direcciones.consultar(usuarioId); }
}
```

Y se inyecta desde `configuration/`. No importar `DireccionJpaEntity`, `DireccionJpaRepository` ni nada de `direccion.adapter.out.persistence`.

## Transacciones

El puerto no abre transacción propia: la lectura corre en la transacción de solo lectura del adaptador de persistencia, así que es segura tanto dentro como fuera de una transacción del consumidor.

## Acceso HTTP del titular y administrativo

| Ruta | Quién | Cómo se decide |
|---|---|---|
| `GET /api/v1/usuarios/{id}/direcciones` | titular, o quien tenga `usuario.ver` | permisos vigentes (`AutorizacionPort.resolver`), no los claims del token |
| `POST /api/v1/usuarios/{id}/direcciones` | titular, o quien tenga el rol `ADMIN_SISTEMA` | ídem |

Sin privilegio, `403 SCOPE_INSUFICIENTE` antes de consultar la cuenta. Cada alta registra `ATRIBUTOS_ACTUALIZADOS` (no crítica, solo `campos=direcciones`) y publica `usuario.atributos_actualizados`.
