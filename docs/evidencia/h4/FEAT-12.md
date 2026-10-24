# FEAT-12 — Suite de seguridad E2E (H4)

**Dueño:** Jose Luis Limachi Sarmiento · **Épica:** EP-02 · **FEAT:** FEAT-12 `feat/EP-02-seguridad-e2e`
**Fecha de esta evidencia:** 22-oct-2026 · **Última actualización:** 24-oct-2026
**Estado:** En curso — T1 y T2 implementadas en la rama `feat/EP-02-seguridad-e2e` y
[PR #60](https://github.com/Taller-SW-Web/Modulo-de-Seguridad-Backend/pull/60) en borrador
(faltan T3–T5, H4 §6.4). Ninguna tarea se marca **Verificada** sin su comprobación en el
despliegue: T1 ver §T1.4/§T1.5 y T2 ver §T2.4.

## T1 — Configuración por entorno del emisor

### T1.1 Fuentes consumidas

- `plan/hito-4/README.md` v1.2 §1, §3 (FEAT-12-T1), §6 y §7; `tareas/hito-4/README.md` y `tareas/hito-4/jose-limachi/TAREAS.md`.
- Documentación SDD del equipo: repo `Modulo-de-Seguridad`, rama `main`,
  `d44dafb7a054368b6790c87a0b455b95374bd046`. [SPEC-17](https://github.com/Taller-SW-Web/Modulo-de-Seguridad/blob/main/specs/funcionales/SPEC-17-tokens-servicio.md) RF-17.1, RF-17.2 y RF-17.14.
- Base H3: `IdentidadConfig` / `FirmarAccesoService` / `DescubrimientoController` (TEC-IDENTIDAD-T001 y T003, fusionadas) y `docs/despliegue/DESPLIEGUE-T005.md`.
- `AGENTS.md` y `CONTEXT.md` de documentación y `AGENTS.md` de este repositorio.

### T1.2 Qué comprueba la prueba nueva

`src/test/java/com/marketplace/auth_service/configuration/ConfiguracionEntornoHttpTest.java`
(2 casos, servidor HTTP real, sin base de datos; misma estructura que `DespliegueHttpTest`):

| Caso | Comprobación |
|---|---|
| `elDescubrimientoDeclaraLasTresUrlsAbsolutasDeLaUrlPublicaDelEntorno` | El servidor se configura con la **URL pública del entorno** (`https://g7-auth-service.onrender.com/api/v1/auth`, la de `AUTH_ISSUER` en Render) y no con `localhost`. El descubrimiento publica `issuer`, `jwks_uri`, `token_endpoint` e `introspection_endpoint` absolutos en `https://`, sin `localhost`/`127.0.0.1` y sin el nombre interno `auth-service`. Demuestra que las URLs salen de la variable, no de la petición, del `Host` interno ni del puerto del proceso. |
| `elIssDelTokenEmitidoCoincideConElDiscoveryYElJwksPublicadoVerificaSuFirma` | Un acceso firmado por el `FirmaPort` real tiene `iss` idéntico al `issuer` del descubrimiento (RF-17.14), lleva `kid`, y la firma se verifica **solo con las claves que publica `GET /.well-known/jwks.json`** por HTTP (RF-17.1): así lo haría un tercero. |

Nota metodológica: `jwks_uri` es absoluto contra la URL pública, por lo que la prueba
lee esa misma ruta **relativa** contra el servidor local (la URL pública no la sirve
este proceso de pruebas) y compara la absoluta contra la constante del entorno.

### T1.3 Resultado de las pruebas (JDK 21.0.12, Gradle 8.14.3)

- `ConfiguracionEntornoHttpTest`: **2/2 en verde**.
- `configuration.*` + `identidad.*`: **155 pruebas, 0 fallos, 12 omitidas**.
- Las pruebas PostgreSQL no se ejecutan aquí: esta máquina no tiene Docker
  (`docker: command not found`); corresponden al CI, como en H3.

### T1.4 Comprobación en el despliegue (22-oct-2026, solo lectura)

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
y su firma se verifica con el JWKS publicado (prueba §T1.2); **la misma comprobación
contra el entorno desplegado con un token real queda pendiente** (§T1.5).

**Observación para FEAT-09 (Christian):** el discovery desplegado **no incluye**
`token_endpoint`, `introspection_endpoint`, `grant_types_supported` ni
`scopes_supported`, que sí publica el código de `develop`. El SHA desplegado es
anterior a FEAT-05; hay que verificar el SHA real del servicio (FEAT-09-T1/T2).

### T1.5 Pendientes de esta tarea

- [ ] Emitir un token **en el despliegue** y confirmar que su `iss` coincide con el
  discovery de ese entorno. Requiere credenciales de prueba (fixtures) o token de
  servicio entregados por canal seguro; no se piden ni registran en git. Si no hay
  credenciales antes de S3, se ejecuta junto con la prueba conjunta G5 (FEAT-12-T5).
- [ ] Estado final **Verificada** solo después de esa comprobación desplegada
  (plan §1 y §6: fusionar no acredita).

No se modificó `problemaIssuer()` (decisión del dueño, 22-oct): la validación actual
de RF-17.14 se conserva y las pruebas locales siguen usando `localhost` como issuer.

### T1.6 Historial de revisión

- **22-oct-2026 · commit `12070f4`:** PR #60 en borrador; CI en verde (destino,
  build y pruebas, imagen Docker).
- **22-oct-2026 · revisión de Luis David (comentario en PR #60):** conforme como
  avance de FEAT-12-T1; mantener el PR en borrador (faltan T2 y T3, H4 §6.4), no
  marcar **Verificada** y corregir esta evidencia.
- **24-oct-2026 · correcciones aplicadas:** estado con PR en borrador, referencia
  al repo de documentación con su SHA en lugar de un enlace relativo inexistente
  en el backend, y el pendiente de validación desplegada explícito en §T1.4 y §T1.5.
  La rama trae `develop` (merge `fddbdd9`).

## T2 — Auditoría `SESION_CERRADA` en el logout

### T2.1 Fuentes consumidas

- `plan/hito-4/README.md` v1.2 §3 (FEAT-12-T2), §6 y §7; tablero `tareas/hito-4/jose-limachi/TAREAS.md`.
- Documentación SDD del equipo, repo `Modulo-de-Seguridad`: [SPEC-06](https://github.com/Taller-SW-Web/Modulo-de-Seguridad/blob/main/specs/funcionales/SPEC-06-renovacion-cierre-sesion.md) RF-06.2, RF-06.5 y RF-06.6 (ESC-06.3/06.5), [SPEC-12](https://github.com/Taller-SW-Web/Modulo-de-Seguridad/blob/main/specs/funcionales/SPEC-12-registro-auditoria.md) RF-12.2 (catálogo cerrado), RF-12.5 (detalle sin secretos) y el registro no crítico tras commit.
- Base H3: `CierreController` y `SesionService.cerrar` (EP-02-T005), `AuditoriaPort.registrarNoCriticaTrasCommit` / `RegistroAuditoria` (EP-06, Christian) — el código `SESION_CERRADA` ya existía en `AccionAuditable` — y el patrón de puente `EfectosRestablecimientoPort` / `EfectosRestablecimientoAdapter` (credencial).

### T2.2 Qué implementa el cambio

| Archivo | Cambio |
|---|---|
| `sesion/application/port/out/EfectosCierrePort.java` (nuevo) | Puerto de salida del cierre: el hecho no crítico `SESION_CERRADA` sale hacia la auditoría de Christian con usuario, familia, contexto y hora; jamás con tokens ni refrescos. |
| `sesion/adapter/out/capacidad/EfectosCierreAdapter.java` (nuevo) | Puente local cierre → `AuditoriaPort` (capacidades solo por su puerto exportado): `RegistroSeguro(…, "SESION_CERRADA", "EXITO", "USUARIO", …)` con `objetivoUsuarioId` y detalle `familiaId` (clave admitida por `RegistroAuditoria`; sin fragmentos prohibidos, RF-12.5). |
| `sesion/application/port/in/SesionesPort.java` | `cerrar(SecretoRefresco, ContextoPeticion)`: el contexto de la petición viaja para auditar ip y agente, como en `abrir`. |
| `sesion/application/service/SesionService.java` | El hecho nace **solo** desde `CierreResultado.Cerrada` (una familia real recién revocada); `YaCerrada` — logout repetido, refresco desconocido o familia ya revocada — no audita: sin duplicados ni identidades inventadas. |
| `sesion/adapter/in/transaction/SesionTransaccional.java` | Conserva la firma y el orden de bloqueos; el hecho se programa tras el commit (no crítico). |
| `sesion/adapter/in/web/CierreController.java` | Construye el `ContextoPeticion` con `ContextoPeticiones.desde(peticion)` (ip y `User-Agent`; nunca el cuerpo). |
| `configuration/SesionConfig.java` | Cablea `EfectosCierreAdapter(AuditoriaPort)`; los contextos de prueba que importan `SesionConfig` aportan su bean de auditoría. |

Catálogo SPEC-12: **sin cambios**, `SESION_CERRADA(false)` ya existía y no es
crítica (RF-12.6 no aplica): su fallo deja el logout en 204.

### T2.3 Pruebas (JDK 21.0.12, Gradle 8.14.3)

| Prueba | Qué acredita |
|---|---|
| `SesionServiceTest` (núcleo, doble de `EfectosCierrePort`) | `cerrarRevocaLaFamiliaYAuditaElHechoConElContexto`: un solo hecho con usuario, familia, hora fija e ip del contexto. `cerrarRepetidoYDesconocidoSonYaCerradaSinAuditar`: el logout repetido y el refresco desconocido no auditan. `cerrarConRefrescoVencidoCierraIgual`: el vencimiento no cambia el hecho. |
| `CerrarSesionPostgresTest` (PostgreSQL real) | Sobre la familia revocada en base: un solo llamado de auditoría con usuario/familia/ip; repetido y desconocido no duplican. |
| `SesionesIntegracionPostgresTest` (HTTP + PostgreSQL reales; con T2 se integra **auditoría**, antes solo sesiones) | `logoutRegistraSesionCerradaEnActividadSinDuplicar`: logout → **204** y, tras el registro asíncrono, `GET /api/v1/auth/me/actividad` con el access token de la persona devuelve **1** registro `SESION_CERRADA` / `EXITO` con `objetivoUsuarioId`, `ip` y `detalle.familiaId` (sin tokens); un segundo logout y un logout con refresco desconocido responden 204 y el total **sigue en 1** (comprobado también contra `auditoria_seguridad`). La fixture amplía la base de sesión con V106 (`registro_id`). |

Resultado local: **compilación de código y pruebas en verde**; suites de sesión
`SesionServiceTest` 16/16, `SesionHttpTest` 7/7 y `LoginServiceTest` 12/12, y
`ArquitecturaHexagonalTest` en verde (puertos y puentes sin tipos ajenos). Suite
completa: **790 pruebas, 21 fallos, 82 omitidas**; los 21 fallos son solo la falta
de Docker/Testcontainers en esta máquina — comprobado ejecutando la misma suite
con los cambios guardados (`git stash`): idénticos 21 fallos sin ellos. Las
pruebas PostgreSQL/HTTP de T2 corren en el CI (imagen Docker), como en H3.

### T2.4 Comprobación desplegada (pendiente)

- [ ] Captura de la actividad en el **despliegue**: logout real contra
  `g7-auth-service` y `GET /auth/me/actividad` con el access token del usuario de
  prueba mostrando `SESION_CERRADA` una sola vez. Requiere credenciales/fixtures
  por canal seguro (mismo pendiente que §T1.5); si no hay antes de S3, se ejecuta
  junto con FEAT-12-T5. Sin esa comprobación, T2 no se marca **Verificada**.

Sanitización: las capturas y el registro no llevan tokens, refrescos ni secretos;
solo ip, agente, identificadores y la familia cerrada por su id.
