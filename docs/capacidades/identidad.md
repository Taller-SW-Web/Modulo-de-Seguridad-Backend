# Capacidad `identidad` — firma, JWKS y descubrimiento

**Dueño:** Sergio Osorio · **Tareas:** TEC-IDENTIDAD-T001 (firma), T003 (JWKS y
descubrimiento).
**Fuentes:** SPEC-17 RF-17.1/17.2/17.14, SPEC-05 RF-05.6, contrato
`tokenUsuario`, `plan/hito-3/ENTORNO-Y-REACT.md` §2 y `plan/transversales/TEC-IDENTIDAD.md`
en el repo de documentación.

## Qué hace, en una frase

Firma los tokens de acceso de las personas con una clave RSA persistente y
publica la parte pública para que cualquiera los verifique sin llamarnos.

## Cómo se usa desde otra capacidad

Solo a través del puerto exportado
`identidad.application.port.in.FirmaPort` (nunca de sus adaptadores):

| Operación | Quién la usa | Qué devuelve |
|---|---|---|
| `firmar(ClaimsAcceso)` | Login (Jose Luis, EP-02) y OTP (Luis, EP-04) | `AccesoFirmado`: token, `jti`, `emitidoEn`, `expiraEn`, `expiresIn()` = 900 |
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
  domain/exception/FirmaNoDisponibleException
  application/dto/        ClaimsAcceso, AccesoFirmado, ContenidoAcceso, ClavePublica, Descubrimiento
  application/port/in/    FirmaPort            ← lo único que consumen los demás
  application/port/out/   FirmadorJws
  application/service/    FirmarAccesoService (Java puro), FirmaDenegada
  adapter/out/crypto/     ClaveFirmaRsa (carga PKCS8, kid), NimbusFirmadorJws
  adapter/in/web/         DescubrimientoController, IdentidadWebMapper, dto/
configuration/            IdentidadConfig (wiring), IdentidadProperties, SeguridadConfig
shared/adapter/web/       Problemas (errores RFC 7807 del contrato)
```

## Pruebas

```bash
./gradlew test --tests 'com.marketplace.auth_service.identidad.*' --tests 'com.marketplace.auth_service.configuration.*'
```

No necesitan Docker ni base de datos.

## Fuera de alcance en H3

Rotación de claves (clave nueva y anterior a la vez), tokens de servicio,
introspección y consultas SPEC-18.
