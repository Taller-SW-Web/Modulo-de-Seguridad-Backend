# Despliegue del backend G7 (TEC-INFRA-T005) — guía

Imagen: `Dockerfile` de la raíz (JDK 21 para construir, JRE 21 para ejecutar,
usuario `g7` sin privilegios, sin secretos). El CI construye la imagen en cada PR
(job «Backend - imagen Docker»). Sirve en cualquier plataforma que ejecute un
Dockerfile (Render, Railway, Fly.io, Koyeb, Cloud Run); abajo, los pasos para
Render, la plataforma usada en H3. La [ficha del entorno real](#entorno-real-h3)
recoge los valores públicos aplicados; las tablas de variables conservan
ejemplos genéricos.

## Entorno real H3

Valores públicos informados por el equipo para la ronda del 16-oct-2026 y
comprobados de forma independiente el 17-oct-2026 04:45–04:51 UTC (noche del
16-oct en Lima). Detalle de cada comprobación en
[TEC-INFRA-T005](../evidencia/TEC-INFRA-T005.md).

| Componente | Alojamiento | URL / valor | Comprobado |
|---|---|---|---|
| SPA | Render Static Site | https://g7-seguridad-spa.onrender.com | 200, `/login` 200 |
| Backend | Render Web Service (Docker) | https://g7-auth-service.onrender.com | 200 |
| Salud | — | https://g7-auth-service.onrender.com/actuator/health | 200 `UP` (tras arranque en frío) |
| Descubrimiento | — | https://g7-auth-service.onrender.com/api/v1/auth/.well-known/openid-configuration | 200, issuer exacto |
| JWKS | — | https://g7-auth-service.onrender.com/api/v1/auth/.well-known/jwks.json | 200, una clave RS256 sin `d` |
| Login | — | `POST https://g7-auth-service.onrender.com/api/v1/auth/login` | No ejecutado en la comprobación; usado en la ronda del 16-oct |
| Base de datos | PostgreSQL en Supabase | privada | Indirecto: login y OTP de la ronda |
| Correo | Brevo SMTP, puerto 2525 en el entorno probado | privado | Indirecto: avisos recibidos en la ronda |

| Variable pública | Valor | Comprobado |
|---|---|---|
| `AUTH_ISSUER` | `https://g7-auth-service.onrender.com/api/v1/auth` | Igual al `issuer` publicado |
| `G7_CORS_ORIGENES` | `https://g7-seguridad-spa.onrender.com` | Preflight: ese origen 200; otro 403 |
| `G7_SPA_URL` | `https://g7-seguridad-spa.onrender.com` | Indirecto: el enlace de desbloqueo abrió la SPA (ronda) |
| `VITE_API_BASE_URL` (build de la SPA) | `https://g7-auth-service.onrender.com/api/v1` | Bundle servido idéntico al build con ese valor. Se había informado sin `/api/v1`; el valor efectivo lo incluye |

Revisiones: backend **reportado** `4bd7b61` en `develop` (sin registro de
despliegue en GitHub); frontend sin SHA del panel, contenido igual al árbol de
`82c9414`. La rama de Render es `develop`, no `main` como proponía el paso 1 de abajo.

El plan gratuito duerme el servicio: la primera petición tardó más de 60 s.
Secretos (contraseñas, claves de firma/HMAC/AES, cadena JDBC, credenciales SMTP)
no se publican aquí.

## Precondiciones (bloquean el arranque)

1. **Ventana T002 aplicada en Supabase** (runbook `docs/migraciones-propuestas/VENTANA-T002-runbook.md`):
   el runtime valida el esquema al arrancar (`ddl-auto=validate`); sin V101–V106
   falla con `Schema-validation: missing table [acceso_sesion]` (comprobado el 15-oct).
2. **Usuario `g7_runtime`** creado por los grants de la ventana, con su contraseña.
3. **Clave de firma RS256 persistente** (PKCS8, RSA ≥ 3072) generada una sola vez por
   el dueño del entorno y guardada solo en el gestor de secretos. Nunca en git, logs
   ni frontend; nunca una clave efímera por arranque.

## Variables de entorno

| Variable | Valor | Secreta |
|---|---|---|
| `DB_JDBC_URL` | `jdbc:postgresql://<pooler>:5432/postgres?sslmode=verify-full&sslrootcert=/etc/secrets/supabase-ca.crt` | sí |
| `DB_USERNAME` | `g7_runtime` (por el pooler: `g7_runtime.<project-ref>`) | sí |
| `DB_PASSWORD` | la fijada con `\password` en la ventana | sí |
| `AUTH_ISSUER` | `https://<servicio>/api/v1/auth` (URL pública exacta, sin `/` final) | no |
| `AUTH_CLAVE_PRIVADA_ARCHIVO` | `/etc/secrets/firma-g7.pem` | (ruta) |
| `G7_PASSWORD_COMUNES_ARCHIVO` | `/etc/secrets/password-comunes.txt` | (ruta) |
| `G7_CORS_ORIGENES` | origen exacto de la SPA, p. ej. `https://<spa>.onrender.com` (varios: separados por coma; nunca `*`); valor real en la ficha | no |
| `PORT` | lo inyecta la plataforma (por defecto 8080) | no |
| `AUTH_FALLOS_HMAC_KEY_ID` / `AUTH_FALLOS_HMAC_KEY` | id corto y clave Base64 ≥ 32 bytes para huellas de fallos (protección) | sí (la clave) |
| `AUTH_OTP_HMAC_KEY_ID` / `AUTH_OTP_HMAC_KEY` | id corto y clave Base64 ≥ 32 bytes para desafíos y códigos OTP | sí (la clave) |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` | SMTP real, p. ej. `smtp.gmail.com` / `587` (H3: Brevo, puerto `2525`) | no |
| `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | cuenta y contraseña de aplicación del SMTP | sí |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` / `…_STARTTLS_ENABLE` | `true` / `true` | no |
| `G7_CORREO_REMITENTE` | remitente visible (por defecto el usuario SMTP) | no |
| `AUTH_OUTBOX_AES_KEY_ID` / `AUTH_OUTBOX_AES_KEY` | id sin `:` y clave Base64 de 32 bytes: cifra destino y texto de los correos del outbox | sí (la clave) |
| `G7_SPA_URL` | origen público de la SPA para el enlace de desbloqueo del correo | no |
| `G7_EVENTOS_PUBLICAR` | `true` para publicar los eventos del outbox en RabbitMQ (por defecto `false`: quedan PENDIENTE) | no |
| `SPRING_RABBITMQ_ADDRESSES` | URI AMQPS del broker, p. ej. `amqps://usuario:clave@host/vhost` (CloudAMQP) | sí |

Opcionales: `G7_FIXTURES_CUENTAS_HABILITADAS=true` + `G7_FIXTURES_CUENTAS_CLAVE`
(secreta) para cargar las cuentas de prueba A/B/V/ADM/M/L/C (EP-01-T007) en el
primer arranque; desactivarlo después. `G7_FIXTURES_CUENTAS_CORREO_ADM` cambia solo el
correo de la cuenta ADM (ADMIN_SISTEMA, MFA obligatorio) para que el OTP llegue a un buzón
real en la demo. Solicitar OTP por EMAIL guarda el correo cifrado en el outbox
en el mismo commit del código y la cuota: requiere las claves `AUTH_OUTBOX_AES_*`.
Si falla ese encolado, revierte la solicitud; si SMTP falla después, el mensaje
queda para el worker, sin devolver cuota ni extender la vigencia del código/desafío.
Una solicitud aceptada no acredita que el correo haya llegado al buzón.
`G7_SALUD_BROKER`/`G7_SALUD_CORREO=true`
cuando existan broker y SMTP (TEC-INFRA-T004).

## Archivos secretos

| Archivo | Contenido | Origen |
|---|---|---|
| `firma-g7.pem` | clave privada PKCS8 de firma | dueño del entorno (Christian) |
| `supabase-ca.crt` | CA del proyecto Supabase (Dashboard → Database → SSL) | Christian |
| `password-comunes.txt` | lista operativa de contraseñas comunes, UTF-8, una por línea (EP-03-T002) | Juan José / Christian |

Sin la lista común el servicio arranca, pero no permite fijar contraseñas
(`NO_DISPONIBLE`), y la carga de cuentas de prueba falla.

## Render

1. New → Web Service → repositorio `Modulo-de-Seguridad-Backend`, rama `main`
   (solo versiones probadas), runtime **Docker**, región la más cercana a Supabase.
   En H3 el servicio se informó sobre `develop`; antes de fusionar en la rama
   que sigue Render, revisar si tiene despliegue automático activo.
2. Health check path: `/actuator/health`.
3. Environment: las variables de la tabla; Secret Files: los tres archivos (Render
   los monta en `/etc/secrets/`).
4. Desplegar y comprobar:
   ```
   curl -s https://<servicio>/actuator/health                         # {"status":"UP"}
   curl -s https://<servicio>/api/v1/auth/.well-known/openid-configuration  # issuer = AUTH_ISSUER
   curl -s https://<servicio>/api/v1/auth/.well-known/jwks.json      # una clave RS256, sin "d"
   curl -s -o /dev/null -w '%{http_code}' -X POST https://<servicio>/api/v1/usuarios/x/roles  # 401
   ```
5. Pasar a G5 solo la URL pública del JWKS y el `issuer` (nunca la clave).

El despliegue de integración desde GitHub Actions usa `RENDER_DEPLOY_HOOK_URL`
(URL completa y secreta, incluida su autenticación) y `RENDER_API_KEY`. El job
fija `ref=<GITHUB_SHA>` y verifica id, estado y commit del deploy antes de
comprobar health. Detalle y límites en
[CI.md](../../.github/CI.md#despliegue-a-render-feat-09-t1).

El nivel gratuito duerme el servicio tras inactividad: despertarlo unos minutos
antes de la demo con `/actuator/health`.

## Qué protege la configuración (probado en `DespliegueHttpTest`)

- `/actuator/health` es público, solo `{"status":"UP"}`, aunque llegue un token
  inválido; el resto de actuator no se publica (401).
- CORS solo para los orígenes de `G7_CORS_ORIGENES`, sin credenciales de
  navegador, con `Retry-After` y `Location` expuestas; un origen no acordado
  recibe 403 sin cabeceras CORS; `*`, comodines o rutas en el origen detienen el arranque.
