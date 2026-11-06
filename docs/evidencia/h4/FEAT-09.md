# FEAT-09 — CI/CD del backend y mensajería real

Evidencia del Hito 4 para FEAT-09 (TEC-INFRA). Dueño: Christian Gabriel Arancivia Salas.
Rama de trabajo: `feat/FEAT-09-T1`, `feat/FEAT-09-T2-smoke` (fusionadas en `develop`).

Este documento se actualiza durante S3. Las secciones T1, T2 y T5 están verificadas;
T3 y el cierre de T6 quedan pendientes y se marcan como tal, sin declarar lo que no existe.

## Estado por tarea

| Tarea | Estado | Evidencia |
|---|---|---|
| T1 — Deploy desde el pipeline | Verificada | runs `41163685985` y `41167699740` (job `deploy` en verde, SHA comprobado en Render) |
| T2 — Smoke test | Verificada | run `41167699740`: health, JWKS y login real en verde |
| T3 — Frontend desde el pipeline | Pendiente | bloqueada por permisos en el repo frontend (sin job de deploy) |
| T4 — Ventana de migraciones V108+ | En curso | esquema en V108; sin migraciones nuevas pendientes |
| T5 — CloudAMQP y SMTP real | Verificada | evento publicado, correo entregado y OTP recibido (ver abajo) |
| T6 — Evidencia del recorrido | En curso | este documento; falta el E2E (FEAT-12-T4) para el run completo |

## T1 — Job de despliegue

- Job `Backend - deploy a Render` en `.github/workflows/ci.yml`, con `needs` de build, pruebas e imagen.
- Dispara el deploy hook de Render y espera a que el servicio quede `live` con el `GITHUB_SHA` esperado antes de dar el job por bueno (`render_deploy.py`).
- Auto-Deploy de Render desactivado: el único disparador es el pipeline.
- Evidencia (run `41167699740`, `develop`):

```text
Deploy dep-dbimnloae00c73c4his0 live para 4b7e21e12bd081504a9417365964d02f6c1663f4. Comprobando salud...
Deploy dep-dbimnloae00c73c4his0 verificado: 4b7e21e... live y salud UP.
```

## T2 — Smoke test

- Paso `Smoke test del entorno desplegado (health, JWKS y login)` tras la verificación del SHA.
- Comprueba `/actuator/health` = `UP`, el JWKS público (solo claves RSA/RS256 de firma, sin material privado) y un login real con la fixture A.
- Sin reintento del login: un rechazo deja el run en rojo y no consume intentos de protección.
- Evidencia (run `41167699740`):

```text
Smoke: salud UP.
Smoke: JWKS válido (1 clave(s) RSA RS256 pública(s)).
Smoke: login OK (tokenType=Bearer, expiresIn=900s). No se registran tokens.
```

- Último run de `develop` con deploy y smoke en verde: `41358047159`, SHA `255092e`.

## T5 — CloudAMQP y SMTP real

Verificado el 6-nov-2026 (UTC) sobre el servicio desplegado en Render, con
`G7_EVENTOS_PUBLICAR=true`, `G7_SALUD_BROKER=true` y `G7_SALUD_CORREO=true`.

### Broker (CloudAMQP)

- Conexión establecida al broker (`shrimp.rmq.cloudamqp.com:5671`) al arrancar:

```text
CachingConnectionFactory : Created new connection: ... [delegate=amqp://<usuario>@<host>:5671/<vhost>]
```

- Exchange declarado por la aplicación (topic, duradero): `seguridad.usuarios`.
- Colas internas presentes: `seguridad.auditoria.pendiente` y `seguridad.auditoria.dlq`.
- Evento publicado al registrar una cuenta de prueba:

```text
PublicacionEventosWorker : outbox: 1 evento(s) publicado(s) en RabbitMQ
```

El evento es `usuario.creado` (routing key del catálogo). No hay colas suscritas a
esa routing key, así que el mensaje se publica y se descarta: la evidencia es la
publicación, no la entrega a una cola.

### Correo (Brevo, puerto 2525)

- El correo de verificación de la cuenta de prueba salió del outbox y se entregó al SMTP:

```text
EnvioCorreosWorker : outbox: 1 correo(s) entregado(s) al SMTP
```

- En Brevo (*Transactional → Logs*) el envío figura como **delivered**.
- El correo llegó al buzón de prueba (en la carpeta de spam).
- Prueba de OTP: se solicitó la activación de MFA sobre la cuenta de prueba y llegó
  un correo con un código de 6 dígitos (`Canal: EMAIL`, expiración 300 s). El código
  no se conserva ni se documenta.

### Salud

- `GET /actuator/health` responde `200` con `status: UP` teniendo activos los dos
  indicadores (`G7_SALUD_BROKER` y `G7_SALUD_CORREO`). Con `show-details=never` el
  estado agregado es la comprobación disponible: si el broker o el SMTP fallaran,
  el servicio pasaría a `DOWN`.

### Observaciones

- El correo de verificación llegó a la carpeta de spam del buzón de Gmail. Es un
  tema de entregabilidad (dominio remitente sin SPF/DKIM/DMARC validados en Brevo),
  no de la aplicación. Para la demo basta con revisar spam; se puede mejorar después
  verificando el dominio en Brevo.
- La cuenta usada para la prueba es desechable y quedó con MFA voluntario activo.

## T3 — Frontend desde el pipeline

Pendiente. Estado a la fecha:

- El workflow del repo frontend ejecuta lint, typecheck, pruebas y build, pero **no
  tiene job de deploy** ni compila con `VITE_API_BASE_URL` por entorno.
- El servicio de Render del SPA existe (`https://g7-seguridad-spa.onrender.com`) y
  ese origen ya está contemplado en `G7_CORS_ORIGENES`.
- Bloqueo: el usuario de trabajo no tiene permiso de escritura en el repositorio
  frontend, así que el cambio debe hacerse con acceso delegado o por el dueño del repo.

Criterio de cierre: push → build → deploy del SPA → el frontend llama al backend real
del entorno, con el preflight CORS en `200` para su origen.

## T4 — Ventana de migraciones

- Esquema en `V108` (`cambio_mfa`). No hay migraciones nuevas pendientes al cierre de
  esta evidencia.
- Cada migración futura se aplica en ventana, con su gemelo en
  `docs/migraciones-propuestas` y aviso al grupo. Sin baseline, repair ni reset.
- Pendiente menor: prueba funcional con el rol `g7_runtime` (el pooler de administración
  no permite `SET ROLE`).

## T6 — Checklist de secretos y recorrido

Secretos del repositorio (solo nombres; los valores no se documentan):

| Secreto | Uso | Estado |
|---|---|---|
| `RENDER_API_KEY` | consultar el estado del deploy en Render | presente |
| `RENDER_DEPLOY_HOOK_URL` | disparar el deploy desde el pipeline | presente |
| `SMOKE_LOGIN_EMAIL` | cuenta del smoke | presente |
| `SMOKE_LOGIN_PASSWORD` | contraseña vigente de esa cuenta | presente |

Recorrido exigido: build → compile → test → deploy → smoke → E2E.

- build, compile, test, deploy y smoke: cubiertos y en verde (runs citados).
- E2E: pendiente; depende de la suite de FEAT-12 (PR frontend en borrador).

## Sanitización

Este documento no incluye contraseñas, tokens, códigos OTP, credenciales SMTP o AMQP,
ni direcciones de correo completas. Los logs citados se recortaron para omitir el
usuario del broker y los buzones.
