# Despliegue del backend G7 (TEC-INFRA-T005) — guía

Imagen: `Dockerfile` de la raíz (JDK 21 para construir, JRE 21 para ejecutar,
usuario `g7` sin privilegios, sin secretos). El CI construye la imagen en cada PR
(job «Backend - imagen Docker»). Sirve en cualquier plataforma que ejecute un
Dockerfile (Render, Railway, Fly.io, Koyeb, Cloud Run); abajo, los pasos para
Render como propuesta por su nivel gratuito con Docker y archivos secretos.

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
| `G7_CORS_ORIGENES` | origen exacto de la SPA, p. ej. `https://<spa>.vercel.app` (varios: separados por coma; nunca `*`) | no |
| `PORT` | lo inyecta la plataforma (por defecto 8080) | no |
| `AUTH_FALLOS_HMAC_KEY_ID` / `AUTH_FALLOS_HMAC_KEY` | id corto y clave Base64 ≥ 32 bytes para huellas de fallos (protección) | sí (la clave) |
| `AUTH_OTP_HMAC_KEY_ID` / `AUTH_OTP_HMAC_KEY` | id corto y clave Base64 ≥ 32 bytes para desafíos y códigos OTP | sí (la clave) |
| `SPRING_MAIL_HOST` / `SPRING_MAIL_PORT` | SMTP real, p. ej. `smtp.gmail.com` / `587` | no |
| `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | cuenta y contraseña de aplicación del SMTP | sí |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` / `…_STARTTLS_ENABLE` | `true` / `true` | no |
| `G7_CORREO_REMITENTE` | remitente visible (por defecto el usuario SMTP) | no |

Opcionales: `G7_FIXTURES_CUENTAS_HABILITADAS=true` + `G7_FIXTURES_CUENTAS_CLAVE`
(secreta) para cargar las cuentas de prueba A/B/V/ADM/M/L/C (EP-01-T007) en el
primer arranque; desactivarlo después. `G7_FIXTURES_CUENTAS_CORREO_ADM` cambia solo el
correo de la cuenta ADM (ADMIN_SISTEMA, MFA obligatorio) para que el OTP llegue a un buzón
real en la demo. Sin SMTP configurado, `POST /auth/otp/solicitar` responde 503 sin consumir cuota. `G7_SALUD_BROKER`/`G7_SALUD_CORREO=true`
cuando existan broker y SMTP (TEC-INFRA-T004).

## Archivos secretos

| Archivo | Contenido | Origen |
|---|---|---|
| `firma-g7.pem` | clave privada PKCS8 de firma | dueño del entorno (Christian) |
| `supabase-ca.crt` | CA del proyecto Supabase (Dashboard → Database → SSL) | Christian |
| `password-comunes.txt` | lista operativa de contraseñas comunes, UTF-8, una por línea (EP-03-T002) | Juan José / Christian |

Sin la lista común el servicio arranca, pero no permite fijar contraseñas
(`NO_DISPONIBLE`), y la carga de cuentas de prueba falla.

## Render (propuesta)

1. New → Web Service → repositorio `Modulo-de-Seguridad-Backend`, rama `main`
   (solo versiones probadas), runtime **Docker**, región la más cercana a Supabase.
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

El nivel gratuito duerme el servicio tras inactividad: despertarlo unos minutos
antes de la demo con `/actuator/health`.

## Qué protege la configuración (probado en `DespliegueHttpTest`)

- `/actuator/health` es público, solo `{"status":"UP"}`, aunque llegue un token
  inválido; el resto de actuator no se publica (401).
- CORS solo para los orígenes de `G7_CORS_ORIGENES`, sin credenciales de
  navegador, con `Retry-After` y `Location` expuestas; un origen no acordado
  recibe 403 sin cabeceras CORS; `*`, comodines o rutas en el origen detienen el arranque.
