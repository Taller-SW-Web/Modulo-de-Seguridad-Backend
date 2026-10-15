# Cuentas — EP-01-T001/T002

Dueña: Eva Moreno. Corrección autorizada por Eva según comunicación de Luis.
Fuentes: SDD `450e95aa38563d40b21c89255e4c2078cbed75e0`, tablero de Eva,
ACUERDOS-INTERNOS §§2–3 y DISENO-PERSISTENCIA §§1–2.
Base de corrección: PR #17, `36b7768ab01c784e6b0a1c269889bafa66747683`.

## Qué entrega esta rama

Mapping de `usuario` y lectura inmutable de `usuario_estado_efectivo`, consultas
internas, lock de cuenta y escritura acotada de básicos/contadores. No añade
migraciones ni escribe tablas de otros dueños.

Se retiraron de este PR los controladores/DTO HTTP incompletos. Listado y detalle
administrativo públicos siguen en EP-01-T003/T004: allí se deben exigir permisos
antes de consultar, componer perfiles/roles/bloqueo mediante sus dueños y devolver
los Problems del contrato. Esta entrega no cierra esas tareas ni la demo H3.

## Puertos exportados

`usuario.application.port.in.CuentasPort` conserva sus cinco firmas:

| Método | Semántica |
|---|---|
| buscarPorCorreoNormalizado(String) | El caller entrega correo ya normalizado; ausencia no distingue cuentas en ningún endpoint público. Incluye estado efectivo real. |
| leer(UUID) | Identidad propia + vista por el mismo UUID; Optional vacío solo si no existe. |
| bloquear(UUID) | PESSIMISTIC_WRITE; exige transacción existente y retiene el lock hasta el commit/rollback del coordinador. |
| contarActivos(Set<UUID>) | Cuenta ACTIVO efectivo, excluyendo bloqueos vigentes y conservando los vencidos como activos. Set vacío devuelve cero. |
| consultar(FiltroCuenta, Paginacion) | Aplica estado efectivo/correo/UUID por rol antes de count y paginación; orden creado_en DESC, UUID ASC para desempate. |

Hay un único bean CuentasPort: el decorador transaccional. El núcleo no se publica
como bean alternativo. Las consultas son readOnly y el lock es MANDATORY.

`CuentaEscrituraInterna` también conserva sus tres firmas. Todas exigen
transacción existente y se usan después de bloquear la cuenta desde el coordinador.
No abren un commit propio. guardarMfaVoluntario continúa reservado para SPEC-10
y lanza UnsupportedOperationException dentro de una transacción.

## DTO de aplicación

- CuentaSnapshot contiene UUID, nombres/apellidos, correo/celular, estado base y
  efectivo, verificaciones de correo/celular, bandera MFA voluntaria,
  bloqueosSeguidos e intentosFallidosConsecutivos. No contiene roles, documento,
  credenciales ni atributos de perfil. Los aporta el dueño correspondiente.
- mfaHabilitado refleja la bandera persistida. El MFA efectivo se calcula con
  MfaPort y los roles actuales; no inferirlo solo de esta bandera.
- CambioBasicos contiene CampoBasico<String> para nombres, apellidos y celular.
  omitido() conserva el dato; presente(valor) lo cambia; presente(null) limpia
  solo celular. Nombres/apellidos no admiten null. Cambiar el celular quita su
  verificación; enviar el mismo valor la conserva.
- ContadoresProteccion mantiene el orden (bloqueosSeguidos, intentosFallidos)
  y rechaza negativos. Guardarlos no cambia básicos, estado ni MFA.
- PaginaResultado copia defensivamente su lista. Paginacion exige pagina >= 1,
  tamaño 1..100 y calcula offset sin desbordar int. Una página remota vacía conserva
  el total real. Estado/rol inválidos se rechazan, no se convierten en filtro ausente.

Las formas anteriores de CuentaSnapshot/CambioBasicos estaban solo en el PR
sin fusionar. Sus consumidores de ramas de trabajo deben usar estas formas
acordes al diseño; no hubo cambios del OpenAPI ni de una API ya integrada a develop.

## Composición y ejemplo

Los consumidores importan únicamente estos puertos/DTO desde su
adapter/out/capacidad. No importan entidades, repositorios ni ConsultaCuentasService.

```java
// Dentro de la transacción del coordinador, después del guard de Sergio
// cuando corresponda y respetando el orden global de UUID.
var antes = cuentas.bloquear(usuarioId);
escritura.guardarContadores(usuarioId,
        new ContadoresProteccion(antes.bloqueosSeguidos(), antes.intentosFallidosConsecutivos() + 1));
```

Para actualizar perfil, Valery entrega un CambioBasicos ya validado y mantiene
sus propios atributos fuera de este puerto. Para MFA/login, Luis/Jose Luis toman
identidad/verificación/estado y consultan roles y credenciales en sus capacidades.

El filtro por rol usa RolesConsultaAdapter hacia AutorizacionPort.usuariosConRol,
sin SQL ni entidades de autorización. Un conjunto vacío no elimina el filtro.
La proyección de estado se obtiene en lote para cada página.

## Comprobación

UsuarioJpaPostgresTest usa PostgreSQL 17.6 desechable con el esquema observado,
ddl-auto=validate, commits reales, rollback y dos coordinadores concurrentes.
La dependencia de autorización se simula solo en esa prueba: no acredita el
recorrido conjunto con roles/login/MFA de los otros dueños.

UsuarioConfigurationTest y el smoke test completo comprueban unicidad de puertos
y ausencia del núcleo sin decorar. El smoke conserva PostgreSQL/RabbitMQ de
Testcontainers; no se usa H2 para sustituirlo.
