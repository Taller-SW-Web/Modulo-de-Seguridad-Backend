# Capacidad `autorizacion` — roles y permisos

**Dueño:** Sergio Osorio · **Épica:** EP-05 (SPEC-11) · **Tareas:** EP-05-T001
(catálogo, mappings y permisos efectivos), EP-05-T002 (decisiones de permiso,
titularidad y guard de administrador).
**Fuentes:** SPEC-11 RF-11.1/11.2/11.4/11.7/11.9/11.10, ESC-11.2/11.3/11.4/11.7;
`plan/hito-3/DISENO-PERSISTENCIA.md` §§1–2 y `ACUERDOS-INTERNOS.md` §2 en el
repo de documentación.

## Qué hace, en una frase

Sabe qué roles tiene cada persona y calcula sus permisos efectivos, que el
login pone en los claims `roles` y `permisos` del acceso.

## Conceptos

- **Rol**: uno de los seis del catálogo cerrado — `CLIENTE`, `VENDEDOR`,
  `ADMIN_VENTAS`, `GESTOR_DESPACHO`, `GESTOR_COMERCIAL`, `ADMIN_SISTEMA`. Una
  persona puede tener varios. Los cuatro últimos son **roles de gestión**
  (`Rol.esDeGestion()`): basta uno para que el segundo factor sea obligatorio.
- **Permiso**: capacidad `recurso.accion` de **este** módulo. Hay diez:

| Rol | Permisos |
|---|---|
| `ADMIN_SISTEMA` | `usuario.ver`, `usuario.crear`, `usuario.desactivar`, `usuario.reactivar`, `rol.asignar`, `auditoria.ver`, `cuenta.bloquear`, `atributos.editar` |
| `VENDEDOR` | `cliente.registrar`, `cliente.buscar` |
| los demás | ninguno: los otros módulos autorizan por rol y con sus datos (A5) |

- **Permisos efectivos**: la unión sin duplicados de los permisos de todos los
  roles de la persona. `ADMIN_SISTEMA` + `VENDEDOR` = los diez.

## Cómo se usa desde otra capacidad

Solo por el puerto exportado `autorizacion.application.port.in.AutorizacionPort`,
desde un **puente** en el `adapter.out.capacidad` de quien lo consume (regla de
ArchUnit). El puerto expone únicamente tipos exportables: el DTO
`autorizacion.application.dto.PermisosResueltos` y el vocabulario común
`shared.domain.Rol` / `shared.domain.Permiso`. El modelo interno
`PermisosEfectivos` no sale de autorización.

```java
PermisosResueltos efectivos = autorizacionPort.resolver(usuarioId);
efectivos.codigosRoles();     // ["ADMIN_SISTEMA", "VENDEDOR"]  → claim roles
efectivos.codigosPermisos();  // ["atributos.editar", ...]      → claim permisos
efectivos.tiene(Rol.ADMIN_SISTEMA);
efectivos.tiene(Permiso.ROL_ASIGNAR);
```

- Se lee de la base **en cada llamada**: un cambio de roles se refleja en el
  siguiente acceso emitido.
- Persona sin roles (o inexistente): listas vacías, nunca `null`.
- Listas en orden alfabético: los mismos roles producen siempre el mismo token.
- Si `rol_permiso` contuviera un permiso que no es de Seguridad, `resolver`
  lanza `IllegalStateException` en vez de meterlo en un token.
- `Rol` y `Permiso` están en `shared.domain` porque varios puertos los usan
  (`exigirPermiso`, `usuariosConRol`, `MfaPort.esEfectivo`…). `Rol.esDeGestion()`
  es la fuente única de «rol de gestión» para el MFA obligatorio.
- Para convertir un código externo: `Rol.buscar("VENDEDOR")` devuelve `Optional`.
  Autorización valida con `CatalogoAutorizacion.rol(codigo)`, que lanza
  `RolInvalidoException` (el adaptador HTTP lo traduce a `400 VALIDACION`).
- Ejemplo de puente probado: `src/test/.../sesion/adapter/out/capacidad/fixture/PuenteAutorizacionDeEjemplo.java`
  (`ConsumoAutorizacionTest`).

### Decidir si alguien puede hacer algo

**Los permisos del token no autorizan.** Toda decisión se toma con los permisos
actuales de la base: un permiso retirado deja de servir aunque el acceso
emitido antes siga vigente, y uno nuevo sirve sin esperar a otro login.
Ocultar un botón o tener un scope de servicio tampoco concede nada.

```java
// Operación sobre cualquier cuenta: hace falta el permiso.
autorizacionPort.exigirPermiso(actor, Permiso.ROL_ASIGNAR);

// Operación que el titular puede hacer sobre su propia cuenta (RF-11.10):
autorizacionPort.exigirTitularOPrivilegio(actor, usuarioId, Permiso.USUARIO_VER);
```

- Si no se cumple lanzan `AccesoDenegadoException`. El adaptador HTTP responde
  `403 SCOPE_INSUFICIENTE` **sin decir qué permiso faltaba**; la excepción
  guarda `actor()` y `permiso()` para auditar `ACCESO_DENEGADO`.
- La titularidad solo cubre lo que la spec permite al titular: ver su perfil,
  cambiar su contraseña y editar nombres, apellidos y celular. **Documento y
  atributos de rol exigen `atributos.editar` incluso sobre la cuenta propia**:
  para eso se usa `exigirPermiso`, no `exigirTitularOPrivilegio`.
- `actor` es `shared.domain.ActorUsuario(sub, jti, roles, permisos)`. Solo lo
  crea el filtro de acceso a partir de un JWT verificado (TEC-IDENTIDAD-T002).

### Guard de administrador (RF-11.7)

```java
@Transactional  // la transacción del caso de uso
void revocarRol(...) {
    autorizacionPort.bloquearCatalogoAdministrador(); // 1º: antes que cuentas y familias
    // ... locks de cuentas (Eva), familias (Jose Luis), filas propias
}
```

- Bloquea la fila fija `rol.ADMIN_SISTEMA` (`SELECT ... FOR UPDATE`) hasta el
  fin de la transacción. Es el **nivel 1** del orden de locks
  (ACUERDOS-INTERNOS §3).
- Lo llaman todas las operaciones que pueden dejar al sistema sin
  administrador activo: asignar/revocar roles, registrar un fallo de login y
  desbloquear (Luis), y las futuras baja/reactivación/bloqueo manual. Dos
  operaciones así se ejecutan una detrás de otra.
- Exige una transacción abierta (`MANDATORY`): sin ella lanza
  `IllegalTransactionStateException` en vez de bloquear y soltar al instante.
- Solo serializa; no añade reglas. La regla «no quitar el último
  administrador» la aplica cada caso de uso (EP-05-T004 para roles).

### Quién tiene un rol

`usuariosConRol(Rol)` devuelve los UUID. Eva lo usa para filtrar el listado
por rol y para contar administradores activos con su estado efectivo, sin
consultar `usuario_rol` desde su repositorio.

## Asignar un rol — `POST /api/v1/usuarios/{id}/roles` (EP-05-T003)

`RolesController` recibe `{"rol": "..."}`. El actor sale del acceso validado,
nunca del cuerpo, y la operación la hace `GestionRolesPort.asignar` en **una
sola transacción** (`REQUIRED`, `READ_COMMITTED`), en este orden
(ACUERDOS-INTERNOS §§3–4):

1. `rol.asignar` con los permisos **actuales** de la base. Sin él: 403, antes de
   mirar el código o la cuenta, y `ACCESO_DENEGADO` se audita después del rollback.
2. Código de rol: si no es uno de los seis, 400 `VALIDACION` con `errores[{campo: "rol"}]`.
3. Nivel 1: lock de `rol.ADMIN_SISTEMA`.
4. Nivel 2: lock de la cuenta objetivo (Eva). Si no existe, 404 `NO_ENCONTRADO`.
5. Reautorización bajo lock: si otra transacción le quitó el permiso al actor
   mientras esperaba, 403 y nada cambia.
6. Fila en `usuario_rol`. Si ya tenía el rol, responde 200 sin más efectos.
7. Nivel 3: revoca **todas** las familias de refresco del objetivo (Jose Luis).
8. Auditoría crítica `ROL_ASIGNADO` y evento `usuario.roles_cambiados` en el
   outbox, con la lista completa resultante (Christian).

Cualquier fallo en 7–8 revierte también la fila de `usuario_rol`. Un fallo
técnico responde 503 `NO_DISPONIBLE`. La respuesta 200 es el esquema `Usuario`
con sus campos obligatorios (`id`, `nombreCompleto`, `correo`, `estado`,
`roles`); la pantalla recarga el detalle completo después.

Los accesos ya emitidos siguen válidos hasta su `exp` (máx. 15 min). Revocar
los refrescos obliga a iniciar sesión de nuevo, y el nuevo acceso trae los roles
actuales. Las operaciones privilegiadas de G7 ya revalidan los permisos en cada llamada.

### Lo que falta conectar: un puente por proveedor

El cambio de rol depende de cuatro puertos de **salida** propios, que cada
dueño cumple con un puente en `autorizacion/adapter/out/capacidad/` hacia su
puerto exportado. Mientras falte alguno, `RolesConfig` instala
`GestionRolesNoDisponible`: comprueba el permiso (403 a quien no lo tiene) y
responde 503. El arranque deja este aviso:
`Gestión de roles no disponible (responde 503): faltan [...]`.

| Puerto de salida (Sergio) | Método | Puerto del dueño (ACUERDOS §2) | Requisito |
|---|---|---|---|
| `CuentasRolesPort` | `bloquear(UUID)` → `Optional<CuentaRoles>` | `CuentasPort.bloquear` (Eva) | Lock de fila en la transacción; `estado` = estado **efectivo** |
| `SesionesRolesPort` | `revocarTodas(UUID)` | `SesionesPort.revocarTodas` (Jose Luis) | `MANDATORY`; bloquea raíces en orden |
| `AuditoriaRolesPort` | `registrarCritica(RegistroRol)` / `registrarNoCritica(RegistroRol)` | `AuditoriaPort.registrarCritica` / `registrarNoCriticaTrasCommit` (Christian) | Crítica en el mismo commit; si falla, lanzar `GestionRolesNoDisponibleException` |
| `EventosRolesPort` | `guardar(RolesCambiadosEvento)` | `MensajeriaPort.guardarEvento` (Christian) | Outbox en el mismo commit; `datos.roles` = lista completa |

El puente lo escribe quien entrega el puerto del dueño, o Sergio en cuanto ese
puerto exista. No hace falta tocar el caso de uso.

## Tablas (todas de Sergio)

| Tabla | Entidad JPA | Notas |
|---|---|---|
| `rol` | `RolJpaEntity` (solo lectura) | enum PostgreSQL `codigo_rol` |
| `permiso` | `PermisoJpaEntity` (solo lectura) | `CHECK` `recurso.accion` |
| `rol_permiso` | `RolPermisoJpaEntity` (solo lectura) | fuente de los permisos efectivos |
| `usuario_rol` | `UsuarioRolJpaEntity` | PK `(usuario_id, rol_codigo)`: no hay duplicados. `usuario_id` es un UUID, sin relación JPA con la entidad de Eva |

Las consultas viven en `UsuarioRolJpaRepository`; los permisos efectivos son
**una** consulta (`DISTINCT` sobre `usuario_rol ⋈ rol_permiso`). Medido:
~2,5 ms por llamada sobre PostgreSQL 16.4 local (objetivo < 15 ms).

## Catálogo sembrado

`src/main/resources/db/autorizacion/R__catalogo_autorizacion.sql` siembra
`rol`, `permiso` y `rol_permiso`, separado de V100 (que mezclaba catálogo y
cuentas de prueba). Es **repetible e idempotente**: no ocupa número de versión,
se puede aplicar donde V100 ya corrió y corrige las etiquetas antiguas
(«Cliente Final» → «Cliente»). Debe coincidir con `CatalogoAutorizacion.java`;
una prueba compara la base con la clase.

> **Pendiente de Christian (TEC-INFRA-T002):** el archivo está en
> `db/autorizacion/` y no en `db/migration/` porque necesita las tablas de V1,
> que aún no están en este repo. Al incorporar V1–V4, decidir su ubicación en
> el historial de Flyway.

## Dónde está cada pieza

```text
autorizacion/
  domain/model/            CatalogoAutorizacion, PermisosEfectivos (internos)
  domain/exception/        RolInvalidoException, AccesoDenegadoException,
                           UsuarioNoEncontradoException, GestionRolesNoDisponibleException
  application/port/in/     AutorizacionPort          ← lo único que consumen los demás
                           GestionRolesPort          ← solo lo usa RolesController
  application/port/out/    AsociacionesRolPort, CuentasRolesPort, SesionesRolesPort,
                           AuditoriaRolesPort, EventosRolesPort
  application/dto/         PermisosResueltos         ← lo que devuelve resolver
                           CambioRolCommand, CuentaRoles, RolesCambiados, RegistroRol,
                           RolesCambiadosEvento
  application/service/     AutorizacionService, GestionRolesService (Java puro),
                           GestionRolesNoDisponible
  adapter/in/transaction/  AutorizacionTransaccional, GestionRolesTransaccional
  adapter/in/web/          RolesController, RolesErroresAdvice, RolesWebMapper, dto/
  adapter/out/persistence/ AutorizacionJpaAdapter, entity/, repository/, mapper/
  adapter/out/capacidad/   (pendiente) puentes hacia Eva, Jose Luis y Christian
configuration/             AutorizacionConfig, RolesConfig
shared/domain/             Rol, Permiso (vocabulario común), ActorUsuario, ContextoPeticion
shared/adapter/web/        Problemas, ContextoPeticiones
```

## Pruebas

Unitarias (sin base): `CatalogoYRolesTest`, `PermisosEfectivosTest`,
`AutorizacionServiceTest` y `GestionRolesServiceTest`, que comprueba el orden
exacto de locks y efectos. De arquitectura: `ConsumoAutorizacionTest` evalúa la
regla de consumo entre capacidades sobre un puente de ejemplo de `sesion`
(pasa) y sobre un puente con el modelo interno y un consumo sin puente (fallan).

HTTP (`RolesHttpTest`): servidor real con el filtro de acceso, para el formato
de 200/400/401/403/404/503.

`GestionRolesPostgresTest` usa la transacción real del decorador. Los
proveedores son dobles que escriben con JDBC en tablas `prueba_*` dentro de la
misma transacción, y la cuenta se bloquea con `FOR UPDATE`. Comprueba que un
fallo de auditoría u outbox no deja ni asociación ni revocación, que
`ACCESO_DENEGADO` queda registrado tras el rollback, que dos asignaciones
concurrentes conservan ambos roles y que perder el permiso mientras se espera
el lock rechaza el cambio. **No** acredita los proveedores reales: eso es EP-05-T006.

De persistencia (`AutorizacionPersistenciaTest`, `GuardAdministradorTest`):
PostgreSQL real, nunca H2. `GuardAdministradorTest` abre dos transacciones
reales y comprueba que la segunda espera hasta que la primera confirma; si se
quita el `FOR UPDATE`, falla.
Usan un esquema aleatorio nuevo por clase, crean las tablas de V1 desde
`src/test/resources/db/prueba-autorizacion/` y aplican la semilla real.

```bash
# Con Docker: Testcontainers arranca PostgreSQL 16.6 solo.
./gradlew test --tests 'com.marketplace.auth_service.autorizacion.*'

# Sin Docker: cualquier PostgreSQL local (nunca Supabase).
PRUEBAS_POSTGRES_URL=jdbc:postgresql://localhost:5432/postgres \
PRUEBAS_POSTGRES_USUARIO=postgres PRUEBAS_POSTGRES_CONTRASENA=... \
./gradlew test --tests 'com.marketplace.auth_service.autorizacion.*'
```

Sin ninguna de las dos opciones se **omiten** (JUnit las muestra como
omitidas, no como pasadas).
