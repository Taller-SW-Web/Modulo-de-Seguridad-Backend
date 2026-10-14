# Capacidad `identidad` — firma, validación, JWKS y descubrimiento

**Dueño:** Sergio Osorio · **Tareas:** TEC-IDENTIDAD-T001 (firma), T003 (JWKS y
descubrimiento), T002 (validación y filtro de acceso).
**Fuentes:** SPEC-17 RF-17.1/17.2/17.14, SPEC-05 RF-05.6, contrato
`tokenUsuario`, `plan/hito-3/ENTORNO-Y-REACT.md` §2 y `plan/transversales/TEC-IDENTIDAD.md`
en el repo de documentación.

## Qué hace, en una frase

Firma los tokens de acceso de las personas con una clave RSA persistente,
publica la parte pública para que cualquiera los verifique sin llamarnos, y
valida los accesos que llegan a nuestro propio backend.

## Cómo se usa desde otra capacidad

Solo a través del puerto exportado
`identidad.application.port.in.FirmaPort` (nunca de sus adaptadores):

| Operación | Quién la usa | Qué devuelve |
|---|---|---|
| `firmar(ClaimsAcceso)` | Login (Jose Luis, EP-02) y OTP (Luis, EP-04) | `AccesoFirmado`: token, `jti`, `emitidoEn`, `expiraEn`, `expiresIn()` = 900 |
| `validar(String token)` | El filtro de acceso (nadie más lo necesita) | `AccesoValidado`: `sub`, `jti`, roles, permisos, `expiraEn` |
| `jwks()` | El controlador de descubrimiento | Claves públicas vigentes |
| `descubrimiento()` | El controlador de descubrimiento | `issuer`, `jwks_uri`, algoritmos |

`ClaimsAcceso(usuarioId, email, roles, permisos)` llega ya calculado: identidad
de Eva y roles/permisos de `AutorizacionPort`. La firma no autentica
contraseñas, no calcula roles y no abre familias de sesión.

Si la configuración no permite firmar, **todas** las operaciones lanzan
`FirmaNoDisponibleException`. El login debe traducirla a `503 NO_DISPONIBLE`.

## El token que se emite

Cabecera `alg=RS256`, `typ=JWT`, `kid`. Claims, exactamente los del contrato:

| Claim | Valor |
|---|---|
| `iss` | `AUTH_ISSUER`, carácter a carácter |
| `sub` | UUID del usuario |
| `email` | correo |
| `roles` / `permisos` | arrays, también vacíos |
| `tipo` | `acceso` |
| `iat` / `exp` | segundos; `exp = iat + 900` |
| `jti` | UUID nuevo por emisión |

No lleva `aud`: el perfil de persona no la exige (contrato, A10).

## Rutas públicas

| Ruta | Respuesta |
|---|---|
| `GET /api/v1/auth/.well-known/jwks.json` | `{"keys":[{"kty","use":"sig","alg":"RS256","kid","n","e"}]}` |
| `GET /api/v1/auth/.well-known/openid-configuration` | `issuer`, `jwks_uri`, `id_token_signing_alg_values_supported` |

- `Cache-Control: max-age=3600, public`. Es seguro: en una rotación la clave
  anterior sigue publicada al menos 24 h (RF-17.3).
- `jwks_uri` se deriva del issuer configurado, no de la petición: detrás de un
  proxy sigue apuntando a la URL externa.
- `token_endpoint`, `introspection_endpoint` y `scopes_supported` **no** se
  publican todavía: esos servicios son de un incremento posterior.
- Sin firma configurada: `503` con el problema `NO_DISPONIBLE` y
  `detail: "Usa tu copia en caché del JWKS."`.

## Cómo se protege una ruta de nuestro backend (TEC-IDENTIDAD-T002)

El filtro (`configuration/SeguridadConfig`) ya está puesto: **toda ruta exige
un acceso de persona válido**, salvo las públicas listadas una a una. Un
controlador recibe a la persona así:

```java
@PostMapping("/api/v1/usuarios/{id}/roles")
ResponseEntity<?> asignar(@AuthenticationPrincipal ActorUsuario actor, ...) {
    autorizacionPort.exigirPermiso(actor, Permiso.ROL_ASIGNAR); // permisos ACTUALES, no los del token
    ...
}
```

Qué comprueba `validar` (y por tanto el filtro):

| Comprobación | Si falla |
|---|---|
| Cabecera `Authorization: Bearer …` (nunca `?access_token=` ni cookie) | 401 `TOKEN_INVALIDO` |
| Firma RS256 con la clave **local** (sin HTTP a nuestro JWKS); otros algoritmos, `none` y HS256 se rechazan | 401 |
| `kid` = el de nuestra clave | 401 |
| `iss` = `AUTH_ISSUER` exacto | 401 |
| `exp` posterior a ahora, **sin tolerancia** (`exp <= ahora` es vencido) | 401 |
| `iat` presente y no futuro; `nbf`, si viene, no futuro (mismo `Clock`, tolerancia cero). Se leen del token tal cual: Spring completaría un `iat` ausente | 401 |
| `tipo = acceso`, `exp - iat` = 900 s exactos, `email` con formato de correo, `sub` y `jti` UUID, `roles`/`permisos` arrays | 401 |
| `tipo = servicio` con firma válida (token de un módulo) | **403 `TOKEN_NO_APLICABLE`** |
| Sin clave configurada: no se puede validar | 503 `NO_DISPONIBLE` |

- Todos los errores salen en RFC 7807 con el `code` del contrato, nunca HTML;
  el 401 lleva `WWW-Authenticate: Bearer`. Solo el 401 `TOKEN_INVALIDO`
  autoriza a la SPA a intentar renovar la sesión.
- Sin sesión HTTP ni cookies (stateless), y por eso sin CSRF.
- En las rutas públicas no se lee el `Authorization`: un token caducado no
  impide descargar el JWKS.
- `AccesoDenegadoException` de cualquier controlador → 403 `SCOPE_INSUFICIENTE`
  sin decir qué permiso faltaba (`AutorizacionErroresAdvice`).
- Un scope de servicio **nunca** se convierte en rol ni permiso.
- **Para añadir una ruta pública** (login, refresh, OTP…), su dueño la agrega
  a `SeguridadConfig` de forma explícita, sin comodines.

## Cómo lo valida un consumidor (Ventas y los demás módulos)

Con Spring Security basta una línea; Spring descubre el JWKS y comprueba el issuer:

```yaml
spring.security.oauth2.resourceserver.jwt.issuer-uri: https://<host G7>/api/v1/auth
```

Además el consumidor debe exigir `tipo = acceso` y leer `sub` como UUID.
`DescubrimientoHttpTest` reproduce exactamente este flujo contra un servidor real.

## Configuración

| Variable | Contenido |
|---|---|
| `AUTH_ISSUER` | URL absoluta `http(s)`, sin `/` final, sin query, nunca el host `auth-service` |
| `AUTH_CLAVE_PRIVADA_ARCHIVO` | ruta a un PEM PKCS8 (`BEGIN PRIVATE KEY`) RSA ≥ 3072 bits |
| `AUTH_CLAVE_PRIVADA` | alternativa: el texto PEM. Configurar solo una de las dos |

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out ../firma-local.pem
```

El `kid` es la huella SHA-256 de la clave pública (RFC 7638): misma clave,
mismo `kid`, también tras reiniciar. **En nube la clave no se cambia entre
despliegues** o todos los consumidores dejarán de verificar los accesos vigentes.

Sin issuer o sin clave válida la aplicación **arranca igual**, registra el motivo
(nunca la clave) y deniega toda emisión. Nunca se genera una clave efímera.

## Dónde está cada pieza

```text
identidad/
  domain/exception/FirmaNoDisponibleException, TokenInvalidoException, TokenNoAplicableException
  application/dto/        ClaimsAcceso, AccesoFirmado, ContenidoAcceso, ClavePublica, Descubrimiento,
                          ClaimsVerificados, AccesoValidado
  application/port/in/    FirmaPort            ← lo único que consumen los demás
  application/port/out/   FirmadorJws
  application/service/    FirmarAccesoService (Java puro), FirmaDenegada
  adapter/out/crypto/     ClaveFirmaRsa (carga PKCS8, kid), NimbusFirmadorJws (firma y verifica)
  adapter/in/security/    AccesoAuthenticationManager, ActorAutenticado, RespuestasAcceso
  adapter/in/web/         DescubrimientoController, IdentidadWebMapper, dto/
configuration/            IdentidadConfig (wiring), IdentidadProperties, SeguridadConfig
shared/adapter/web/       Problemas (errores RFC 7807 del contrato)
```

## Pruebas

```bash
./gradlew test --tests 'com.marketplace.auth_service.identidad.*' --tests 'com.marketplace.auth_service.configuration.*'
```

No necesitan Docker ni base de datos. `FiltroAccesoHttpTest` levanta un
servidor real y recorre todos los casos de la tabla anterior;
`VerificacionJwsTest` firma tokens falsos (otra clave, otro kid, RS512, HS256,
`none`) y comprueba que se rechazan.

### Matriz criptográfica en runtime (TEC-IDENTIDAD-T004)

`ValidacionCriptograficaRuntimeTest` arranca el backend como en un entorno:
clave PKCS8 de 3072 bits leída de **archivo** e issuer externo fijo, igual
que detrás de un proxy. No usa JWT de ejemplo: los accesos válidos los emite
`FirmaPort` y los inválidos se firman en la prueba con claves generadas en la
ejecución.

| Comprueba | Resultado esperado |
|---|---|
| Perfil del acceso emitido | RS256, `typ` JWT, `kid` = huella SHA-256; exactamente `iss sub email tipo roles permisos iat exp jti`; `exp = iat + 900`; sin `aud`; arrays aunque estén vacíos |
| Verificación de un tercero | Con solo el JWKS publicado por HTTP (`n`/`e`/`kid`, 3072 bits, sin parte privada), la firma verifica |
| Acceso válido, roles y permisos vacíos | 200 y el controlador recibe el `sub` |
| Payload alterado para escalar a ADMIN_SISTEMA, otra clave con nuestro kid, otra clave con su kid, RSA 2048, sin kid, kid desconocido, RS512, PS256, HS256 con la clave pública como secreto, `alg: none` | 401 `TOKEN_INVALIDO` |
| Vencido en el segundo actual (una tolerancia de 60 s lo aceptaría), sin `exp`, issuer de otro entorno, issuer con barra final | 401 `TOKEN_INVALIDO` |
| `sub` que no es UUID, sin `jti`, `roles` como texto, sin `permisos`, sin `tipo`, `tipo` refresco | 401 `TOKEN_INVALIDO` |
| Token de servicio firmado con nuestra clave (fixture aislada, sin clientes A10) | 403 `TOKEN_NO_APLICABLE` |
| Reinicio con el mismo archivo de clave | Mismo `kid` publicado; un acceso emitido antes del reinicio sigue entrando |
| Otro entorno con otra clave | `kid` distinto; nuestros accesos dan 401 allí |
| Logs de todo lo anterior | Sin `PRIVATE KEY`, sin el cuerpo del PEM, sin tokens ni firmas |

El borde exacto `exp == ahora` se prueba con reloj fijo en
`FirmarAccesoServiceTest.venceExactamenteEnExpSinTolerancia` y
`VerificacionJwsTest.elRelojDelCasoDeUsoRechazaElAccesoAlVencer`. Con el reloj
real, «ahora» siempre queda unos milisegundos por delante.

Estas pruebas son locales: la verificación con el validador de Ventas (G5)
contra el entorno desplegado es TEC-IDENTIDAD-T005.

## Fuera de alcance en H3

Rotación de claves (clave nueva y anterior a la vez), emisión de tokens de
servicio, introspección y consultas SPEC-18. La auditoría `ACCESO_DENEGADO`
se conectará cuando exista `AuditoriaPort` (Christian).
