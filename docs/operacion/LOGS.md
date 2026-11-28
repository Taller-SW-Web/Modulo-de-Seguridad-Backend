# Cómo leer los logs del módulo de Seguridad

Guía para saber **qué pasó** con una petición, **de quién es el problema** y **qué hacer**.
Sirve para depurar y para responder con datos cuando otro equipo dice «el problema es de
Seguridad».

> **Resumen en una línea:** busca el `id` de la petición, mira la marca del final
> (`[OK]`, `[CLIENTE]` o `[NOSOTROS]`) y lee el motivo.

---

## 1. Qué se registra

Por **cada petición** que llega al backend se escribe **una línea** en el logger
`g7.peticiones`. No hace falta que los otros módulos cambien nada: la línea se escribe de
nuestro lado, llame quien llame.

Además, cada petición tiene un **número de seguimiento** (`id`):

- Si quien llama manda la cabecera `X-Request-Id` con un valor seguro (8 a 64 caracteres:
  letras, números, `.`, `_`, `:` o `-`), usamos ese mismo número.
- Si no la manda, o manda algo raro, creamos uno de 16 caracteres, por ejemplo `9b21f0c4a1d2e3f4`.
- **Siempre lo devolvemos** en la cabecera `X-Request-Id` de la respuesta.
- En los errores también va dentro del cuerpo, en el campo `instance`:
  `"instance": "urn:g7:peticion:9b21f0c4a1d2e3f4"`.
- Todas las demás líneas de log de esa misma petición (avisos, errores internos) llevan el
  número entre corchetes, así que buscando el `id` se ve todo lo que ocurrió.

## 2. Dónde verlos

**En Render:** servicio `g7-auth-service` → pestaña **Logs**.

- Se ven en vivo y se pueden buscar con la caja de búsqueda (por `id`, por módulo, por código).
- Para guardarlos, selecciona las líneas y cópialas a un archivo. **Render los guarda solo
  unos días**: copia a tiempo lo que quieras conservar, por ejemplo antes de una demo o
  apenas otro equipo reporte un problema.
- Hace falta acceso al panel de Render (lo administra David).

**En local:** salen por la consola al ejecutar `./gradlew bootRun`.

**En JSON (opcional):** si se define la variable de entorno
`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` en Render, cada línea sale como JSON con los campos
por separado (`llamante`, `ruta`, `estado`, `codigo`, `motivo`, `ms`, `responsable`,
`idPeticion`). Sirve para enviarlos a un servicio de logs (Grafana Cloud, Better Stack, Axiom)
o para filtrarlos con herramientas. Sin esa variable salen en texto, que es más fácil de leer
a simple vista.

## 3. Cómo se lee una línea

```
2026-11-20T14:03:12.301-05:00  WARN 1 --- [auth-service] [nio-8080-exec-3] [9b21f0c4a1d2e3f4] g7.peticiones : modulo-despacho | GET /api/v1/usuarios/{id} | 403 SCOPE_INSUFICIENTE | el token es válido pero le falta el scope o permiso que exige esta ruta | 11 ms | id=9b21f0c4a1d2e3f4 | [CLIENTE]
```

La parte anterior a `g7.peticiones :` la pone Spring: fecha y hora, **nivel**, hilo y el `id`
entre corchetes. Lo importante viene después, separado por `|`, siempre en el mismo orden:

| # | Parte | Ejemplo | Qué dice |
|---|---|---|---|
| 1 | **Quién llamó** | `modulo-despacho` | Ver la tabla de abajo |
| 2 | **Qué pidió** | `GET /api/v1/usuarios/{id}` | Método y ruta **declarada en el backend**: los identificadores salen como `{id}` y nunca se copia la URL recibida. Si la ruta no existe, se escribe `(ruta no reconocida)` |
| 3 | **Qué respondimos** | `403 SCOPE_INSUFICIENTE` | Código HTTP y, si es un error del contrato, su código (ver §6) |
| 4 | **Por qué** | `el token es válido pero le falta…` | El motivo en palabras. Si falta un campo, dice **cuál**; si falla la contraseña, dice **qué regla** |
| 5 | **Cuánto tardó** | `11 ms` | Desde que llegó hasta que respondimos. Si pasa de 2 segundos se añade **`LENTO`** |
| 6 | **Número de seguimiento** | `id=9b21f0c4a1d2e3f4` | El mismo que recibió quien llamó en `X-Request-Id` |
| 7 | **De quién es el problema** | `[CLIENTE]` | Ver §4 |

**Quién llamó** puede ser:

| Valor | Significa |
|---|---|
| `modulo-marketplace`, `modulo-despacho`… | Un módulo con un **token de servicio válido**. Es su `client_id` |
| `modulo-ventas (pide token)` | Un módulo que **obtuvo** su token en `POST /auth/token` (respuesta 2xx). **El secreto nunca se registra** |
| `cliente no autenticado (pide token)` | Pidió token y **no** lo obtuvo (secreto incorrecto, cliente desconocido o cuerpo inválido). El `client_id` recibido no se copia: podría ser un secreto pegado por error. Para saber quién fue, cruza la hora con el módulo que reclama o usa su `X-Request-Id` |
| `client_id ausente (pide token)` | Obtuvo token sin mandar `client_id` (no debería ocurrir) |
| `usuario 3f2a1b2c (CLIENTE)` | Una persona con sesión válida: los 8 primeros caracteres de su identificador y sus roles. **Nunca su correo** |
| `token no aceptado` | Mandó un token, pero no era válido (vencido, mal formado, firma incorrecta). Por eso no sabemos quién es |
| `anónimo` | No mandó token. Es lo normal en rutas públicas: login, registro, JWKS… |
| `(valor no válido)` | Obtuvo token, pero su `client_id` tiene caracteres raros; no se copia al log por seguridad |

**El nivel** de la línea ayuda a filtrar: `INFO` si salió bien, `WARN` si lo envió mal quien
llamó y `ERROR` si el problema es nuestro.

## 4. De quién es el problema

| Marca | Cuándo | Qué significa | Qué hacer |
|---|---|---|---|
| `[OK]` | 2xx y 3xx | Respondimos bien | Nada. Si el otro módulo dice que falló, el problema está después: en cómo lee nuestra respuesta |
| `[CLIENTE]` | 4xx | **Quien llamó envió algo mal**: token, scope, cuerpo, límite de intentos, recurso inexistente | Mostrarle la línea y el motivo; él debe corregir su petición |
| `[NOSOTROS]` | 5xx | **El problema es nuestro**: un error inesperado o algo de lo que dependemos (base de datos, correo, auditoría, clave de firma) | Buscar las demás líneas con el mismo `id`, corregir y avisar |

Ser transparentes funciona en los dos sentidos: si sale `[NOSOTROS]`, lo reconocemos y lo
arreglamos. Eso da credibilidad cuando decimos que un `[CLIENTE]` no es nuestro.

## 5. Cómo buscar

| Quiero… | Busco… |
|---|---|
| Todo lo de una petición concreta | su `id`, por ejemplo `9b21f0c4a1d2e3f4` |
| Todo lo que hizo un módulo | su `client_id`, por ejemplo `modulo-marketplace` |
| Solo nuestros fallos | `[NOSOTROS]` o el nivel `ERROR` |
| Solo errores de otros | `[CLIENTE]` |
| Lo que fue lento | `LENTO` |
| Un tipo de error | su código, por ejemplo `SCOPE_INSUFICIENTE` |
| Una ruta | su plantilla, por ejemplo `/api/v1/usuarios/{id}` |

Cuando otro equipo reporte un problema, pídele **el `X-Request-Id` de su respuesta**, o el
campo `instance` del error. Con eso se encuentra la línea exacta. Si no lo tiene, pide la
**hora exacta** y la **ruta**, y busca por su `client_id`.

## 6. Guía por caso: qué pasó y qué hacer

### Errores de quien llama (`[CLIENTE]`)

| Código | Qué pasó | Qué decirle al otro equipo |
|---|---|---|
| `400 VALIDACION` | El cuerpo no cumple el contrato. La línea dice qué **campos** fallaron | «Su petición llegó sin el campo `grant_type` (o con un formato inválido). Revisen el contrato» |
| `400 LOTE_DEMASIADO_GRANDE` | Pidió más de 100 usuarios en el lote | «Partan la consulta en grupos de hasta 100» |
| `401 TOKEN_INVALIDO` | Token ausente, mal formado, vencido o con firma inválida | «Su token no es válido: puede estar vencido (dura 15 min) o no mandaron `Authorization: Bearer`. Pidan uno nuevo». **Ojo:** si aparece en **varios módulos a la vez**, sospecha de nosotros (§7) |
| `401 CLIENTE_INVALIDO` | En `POST /auth/token`: `client_id` desconocido o inactivo, o secreto incorrecto | «El `client_id` X no tiene un secreto que coincida. ¿Usan el secreto vigente? Si lo rotamos, el anterior dejó de servir» |
| `401 CREDENCIALES_INVALIDAS` | Correo o contraseña incorrectos, o la cuenta no está activa. **No se distingue a propósito** (SPEC-05) | «Las credenciales no son válidas». Para saber más, revisar la auditoría de esa cuenta |
| `401 REFRESCO_INVALIDO` | El token de refresco ya se usó, fue revocado o no existe | «La sesión se cerró o se bloqueó la cuenta; hay que iniciar sesión de nuevo» |
| `401 CODIGO_INVALIDO`, `OTP_INTENTOS_AGOTADOS` | El código del segundo factor no coincide, o se agotaron los 3 intentos | Error del usuario, no del sistema |
| `403 SCOPE_INSUFICIENTE` | El token es válido, pero al módulo le falta el scope de esa ruta (ver §8) | «Su cliente no tiene el scope `usuarios:leer`. Pídanlo en el issue de integración» |
| `403 TOKEN_NO_APLICABLE` | Un módulo usó su token de servicio en una ruta que solo puede usar una persona | «Esa ruta es para usuarios; con token de servicio no se puede» |
| `403 PASSWORD_CADUCADA` | Cuenta de gestión con la contraseña de 90 días o más | El usuario debe restablecerla |
| `404 NO_ENCONTRADO` | El usuario o recurso pedido no existe | «Ese identificador no existe en Seguridad. Revisen de dónde lo sacaron» |
| `404` sin código | La **ruta** no existe | «Están llamando a una ruta que no existe; revisen la URL (¿falta `/api/v1`?)» |
| `405` sin código | Método equivocado, por ejemplo `GET` en lugar de `POST` | «Esa ruta es `POST`» |
| `415` sin código | `Content-Type` equivocado | «`/auth/token` exige `application/x-www-form-urlencoded`» |
| `409 CORREO_NO_DISPONIBLE` | El correo ya está registrado | Error esperado |
| `410 ENLACE_EXPIRADO`, `ENLACE_YA_USADO` | El enlace del correo venció o ya se usó | Pedir uno nuevo |
| `422 POLITICA_INCUMPLIDA` | La contraseña no cumple la política. La línea dice **qué reglas** | Error del usuario |
| `422 ADMINISTRADOR_PROTEGIDO` | Intentó algo sobre su propia cuenta o sobre el último administrador | Error esperado |
| `429 DEMASIADAS_SOLICITUDES` | Superó el límite de intentos | «Están reintentando demasiado. Esperen lo que dice `Retry-After`» |

### Fallos nuestros (`[NOSOTROS]`)

| Código | Qué pasó | Qué hacer |
|---|---|---|
| `503 NO_DISPONIBLE` | Algo de lo que dependemos no respondió: base de datos (Supabase), cola de correo, auditoría crítica o la clave de firma | Buscar el mismo `id`: la línea `WARN` o `ERROR` vecina dice qué falló. Revisar Supabase y las variables de Render |
| `500` sin código | Error inesperado: la línea dice qué excepción, por ejemplo `(IllegalStateException)` | Buscar el `id` para ver el detalle, reproducirlo y corregirlo |

## 7. Situaciones típicas

**«El login de ustedes no funciona» (G1, la SPA).** Busca `POST /api/v1/auth/login` a esa
hora.
- `401 CREDENCIALES_INVALIDAS [CLIENTE]` → credenciales incorrectas o cuenta no activa.
- `503 [NOSOTROS]` → es nuestro.
- **No hay ninguna línea** → la petición no nos llegó: URL equivocada, problema de red del
  otro lado o del navegador (CORS). Que revisen la consola del navegador o sus logs.

**«Su token no sirve» (un módulo).** Pídele su `X-Request-Id` o la hora exacta.
- `cliente no autenticado (pide token) | POST /api/v1/auth/token | 401 CLIENTE_INVALIDO` → su secreto no coincide o el cliente no existe.
- `200` en `/auth/token` y luego `401 TOKEN_INVALIDO` en otra ruta → usan un token vencido o
  lo mandan mal. Si validan el token por su cuenta con el JWKS, puede que tengan en caché
  una clave vieja.
- `403 SCOPE_INSUFICIENTE` → les falta el scope. Se revisa en la matriz de scopes.

**Muchos `401 TOKEN_INVALIDO` de varios módulos a la vez.** Ya no es de un cliente: probablemente
algo nuestro. Por ejemplo, una rotación de la clave de firma mal hecha o un cambio de
`AUTH_ISSUER`. Revisa el JWKS y las variables de Render.

**Primera petición `LENTO` (de 30 a 60 segundos) tras un rato sin uso.** Es el **arranque en
frío** del plan gratuito de Render: el servicio estaba dormido. La línea es `[OK]`, pero si el
otro módulo corta antes por su propio tiempo de espera, para él es un error. **Esto sí es de
nuestro lado** (de la plataforma), y conviene decirlo. Solución: aumentar su tiempo de espera o
«despertar» el servicio antes de una demo.

**Un `[OK]` pero el otro equipo dice que falló.** Respondimos bien a esa hora. El problema está
en cómo interpretan la respuesta. Muéstrales la línea y la respuesta del contrato.

**No aparece ninguna línea a esa hora.** La petición no llegó a nuestro servicio. Que revisen
la URL (`https://g7-auth-service.onrender.com/api/v1/...`), su red y sus propios logs. Si usan la
pasarela de Azure, revisen también sus métricas: allí queda registrado lo que se rechazó antes
de llegar a nosotros (`401` sin clave de suscripción, `429` por límite).

## 8. Rutas de integración y el scope que piden

Para responder a un `403 SCOPE_INSUFICIENTE`. La respuesta al módulo no dice qué scope falta
(el contrato lo prohíbe), pero nosotros sí lo sabemos por la ruta:

| Ruta | Quién puede llamarla | Scope que necesita un módulo |
|---|---|---|
| `POST /api/v1/auth/token` | Cualquiera (es para pedir el token) | — |
| `POST /api/v1/auth/introspeccion` | Solo módulos | `tokens:introspeccion` |
| `GET /api/v1/roles`, `GET /api/v1/permisos` | Solo módulos | `roles:leer` |
| `GET /api/v1/usuarios/{id}` | Módulo o persona | `usuarios:leer` |
| `GET /api/v1/usuarios/{id}/direcciones` | Módulo o persona | `direcciones:leer` |
| `POST /api/v1/usuarios/lote` | Solo módulos | `usuarios:leer` |

Qué scopes tiene concedidos cada módulo está en la matriz de scopes del repositorio de
documentación (`docs/integracion/matriz-scopes.md`).

## 9. Lo que nunca aparece en los logs

Tokens, contraseñas, códigos OTP, `client_secret`, correos, celulares, identificadores
completos de usuario y cuerpos de petición. Del cuerpo solo se registran **nombres** de
campos o de reglas, nunca valores ni mensajes. Lo comprueba la prueba
`RegistroPeticionesHttpTest`, que falla si alguno de esos datos llega al log.

Si alguna vez ves uno de esos datos en un log, es un **incidente de seguridad**: avisa al
equipo de inmediato.

## 10. Plantilla para responder a otro equipo

> Revisamos su petición `X-Request-Id: 9b21f0c4a1d2e3f4` del 20-nov a las 14:03:12. En nuestro
> registro:
>
> `modulo-despacho | GET /api/v1/usuarios/{id} | 403 SCOPE_INSUFICIENTE | el token es válido pero le falta el scope o permiso que exige esta ruta | 11 ms`
>
> Su token es válido, pero su cliente no tiene el scope `usuarios:leer`, que exige esa ruta.
> Pídanlo en el issue de integración y lo concedemos.

## 11. Límites

- Si la petición **no llega** a nuestro servicio, no hay línea. Eso también es información.
- Render guarda los logs **pocos días**: copia lo que quieras conservar.
- El registro dice qué respondimos y por qué, pero no qué hizo el otro módulo con la
  respuesta.
- Las acciones de las personas (quién bloqueó a quién, quién cambió un rol) no están aquí:
  están en la **auditoría** de la base (SPEC-12), que guarda más tiempo y más detalle.

## 12. Para desarrolladores

- Código: `shared/adapter/web/FiltroRegistroPeticiones` (la línea), `LineaPeticion` (el
  formato, el catálogo de motivos y el responsable), `RegistroPeticiones` (el número de
  seguimiento) y `CapturaLlamante` (quién llama).
- Un error nuevo del contrato solo necesita su motivo en `LineaPeticion.MOTIVOS`. Si no lo
  tiene, la línea usa el `title` del error.
- Todo error que se construya con `Problemas` queda registrado solo: no hay que hacer nada
  en el controlador.
- La comprobación de salud (`/actuator/health`) con `200` no se registra: Render la llama
  cada pocos segundos y llenaría el log.
