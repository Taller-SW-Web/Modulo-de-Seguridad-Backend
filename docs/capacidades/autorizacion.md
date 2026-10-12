# Capacidad `autorizacion` — roles y permisos

**Dueño:** Sergio Osorio · **Épica:** EP-05 (SPEC-11) · **Tareas:** EP-05-T001
(catálogo, mappings y permisos efectivos).
**Fuentes:** SPEC-11 RF-11.1/11.2/11.4/11.9, ESC-11.2/11.4/11.7;
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

Solo por el puerto exportado `autorizacion.application.port.in.AutorizacionPort`:

```java
PermisosEfectivos efectivos = autorizacionPort.resolver(usuarioId);
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
- Para convertir un código externo: `Rol.desdeCodigo("VENDEDOR")`; un código
  fuera del catálogo (`"BUYER"`, `"admin_sistema"`) lanza `RolInvalidoException`
  (el adaptador HTTP lo traducirá a `400 VALIDACION`).

Siguientes tareas: EP-05-T002 añade `exigirPermiso`, `exigirTitularOPrivilegio`,
`bloquearCatalogoAdministrador` y `usuariosConRol`; T003/T004 asignan y revocan.

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
  domain/model/            Rol, Permiso, CatalogoAutorizacion, PermisosEfectivos
  domain/exception/        RolInvalidoException
  application/port/in/     AutorizacionPort          ← lo único que consumen los demás
  application/port/out/    AsociacionesRolPort
  application/service/     AutorizacionService (Java puro)
  adapter/in/transaction/  AutorizacionTransaccional (frontera @Transactional)
  adapter/out/persistence/ AutorizacionJpaAdapter, entity/, repository/, mapper/
configuration/             AutorizacionConfig (wiring: un único bean AutorizacionPort)
```

## Pruebas

Unitarias (sin base): `CatalogoYRolesTest`, `PermisosEfectivosTest`,
`AutorizacionServiceTest`.

De persistencia (`AutorizacionPersistenciaTest`): PostgreSQL real, nunca H2.
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
