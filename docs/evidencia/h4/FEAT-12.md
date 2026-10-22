# FEAT-12-T1 — Configuración por entorno del emisor (H4)

**Dueño:** Jose Luis Limachi Sarmiento · **Épica:** EP-02 · **FEAT:** FEAT-12 `feat/EP-02-seguridad-e2e`
**Fecha de esta evidencia:** 22-oct-2026 · **Estado:** En curso (commit local, sin PR; criterio de despliegue parcial, ver §4)

## 1. Fuentes consumidas

- `plan/hito-4/README.md` v1.2 §1, §3 (FEAT-12-T1), §6 y §7; `tareas/hito-4/README.md` y `tareas/hito-4/jose-limachi/TAREAS.md`.
- [SPEC-17](../../../Modulo-de-Seguridad/specs/funcionales/SPEC-17-tokens-servicio.md) RF-17.1, RF-17.2 y RF-17.14.
- Base H3: `IdentidadConfig` / `FirmarAccesoService` / `DescubrimientoController` (TEC-IDENTIDAD-T001 y T003, fusionadas) y `docs/despliegue/DESPLIEGUE-T005.md`.
- `AGENTS.md` y `CONTEXT.md` de documentación y `AGENTS.md` de este repositorio.

## 2. Qué comprueba la prueba nueva

`src/test/java/com/marketplace/auth_service/configuration/ConfiguracionEntornoHttpTest.java`
(2 casos, servidor HTTP real, sin base de datos; misma estructura que `DespliegueHttpTest`):

| Caso | Comprobación |
|---|---|
| `elDescubrimientoDeclaraLasTresUrlsAbsolutasDeLaUrlPublicaDelEntorno` | El servidor se configura con la **URL pública del entorno** (`https://g7-auth-service.onrender.com/api/v1/auth`, la de `AUTH_ISSUER` en Render) y no con `localhost`. El descubrimiento publica `issuer`, `jwks_uri`, `token_endpoint` e `introspection_endpoint` absolutos en `https://`, sin `localhost`/`127.0.0.1` y sin el nombre interno `auth-service`. Demuestra que las URLs salen de la variable, no de la petición, del `Host` interno ni del puerto del proceso. |
| `elIssDelTokenEmitidoCoincideConElDiscoveryYElJwksPublicadoVerificaSuFirma` | Un acceso firmado por el `FirmaPort` real tiene `iss` idéntico al `issuer` del descubrimiento (RF-17.14), lleva `kid`, y la firma se verifica **solo con las claves que publica `GET /.well-known/jwks.json`** por HTTP (RF-17.1): así lo haría un tercero. |

Nota metodológica: `jwks_uri` es absoluto contra la URL pública, por lo que la prueba
lee esa misma ruta **relativa** contra el servidor local (la URL pública no la sirve
este proceso de pruebas) y compara la absoluta contra la constante del entorno.

## 3. Resultado de las pruebas (JDK 21.0.12, Gradle 8.14.3)

- `ConfiguracionEntornoHttpTest`: **2/2 en verde**.
- `configuration.*` + `identidad.*`: **155 pruebas, 0 fallos, 12 omitidas**.
- Las pruebas PostgreSQL no se ejecutan aquí: esta máquina no tiene Docker
  (`docker: command not found`); corresponden al CI, como en H3.

## 4. Comprobación en el despliegue (22-oct-2026, solo lectura)

Sanitizado: sin tokens, claves, secretos ni cuentas; `kid` y URLs son datos públicos.

```text
GET https://g7-auth-service.onrender.com/actuator/health
200 {"status":"UP"}                    (primer intento: sin respuesta en 25 s, servicio dormido; 2.ª petición lo despertó en 56.8 s)

GET https://g7-auth-service.onrender.com/api/v1/auth/.well-known/openid-configuration
200 {"issuer":"https://g7-auth-service.onrender.com/api/v1/auth",
     "jwks_uri":"https://g7-auth-service.onrender.com/api/v1/auth/.well-known/jwks.json",
     "id_token_signing_alg_values_supported":["RS256"]}

GET https://g7-auth-service.onrender.com/api/v1/auth/.well-known/jwks.json
200 {"keys":[{"kty":"RSA","use":"sig","alg":"RS256","kid":"12VnSlgesm0okVRMAdltXyR-wJBxsRI44QiOcnm1XeQ","n":"<clave pública, omitida>","e":"AQAB"}]}
```

**Criterio de aceptación de T1:** ✅ `issuer` y `jwks_uri` absolutos según la URL
pública del entorno (no `localhost`) y `jwks_uri` responde 200 con una clave RS256
sin material privado. El `iss` del token emitido en local coincide con el discovery
y su firma se verifica con el JWKS publicado (prueba §2); **la misma comprobación
contra el entorno despleguedo con un token real queda pendiente** (§5).

**Observación para FEAT-09 (Christian):** el discovery desplegado **no incluye**
`token_endpoint`, `introspection_endpoint`, `grant_types_supported` ni
`scopes_supported`, que sí publica el código de `develop`. El SHA desplegado es
anterior a FEAT-05; hay que verificar el SHA real del servicio (FEAT-09-T1/T2).

## 5. Pendientes de esta tarea

- [ ] Emitir un token **en el despliegue** y confirmar que su `iss` coincide con el
  discovery de ese entorno. Requiere credenciales de prueba (fixtures) o token de
  servicio entregados por canal seguro; no se piden ni registran en git. Si no hay
  credenciales antes de S3, se ejecuta junto con la prueba conjunta G5 (FEAT-12-T5).
- [ ] Estado final **Verificada** solo después de esa comprobación desplegada
  (plan §1 y §6: fusionar no acredita).

No se modificó `problemaIssuer()` (decisión del dueño, 22-oct): la validación actual
de RF-17.14 se conserva y las pruebas locales siguen usando `localhost` como issuer.
