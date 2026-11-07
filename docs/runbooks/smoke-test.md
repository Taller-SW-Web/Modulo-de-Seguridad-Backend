# Runbook — smoke test del despliegue (FEAT-09-T2)

**Ejecuta:** Christian (operaciones), con aviso al grupo.
**Job:** `Backend - deploy a Render` en `.github/workflows/ci.yml`.
**Script:** `.github/scripts/smoke_test.py`.

El smoke corre solo después de que el deploy de `develop` quedó `live` con el
`GITHUB_SHA` esperado. Comprueba salud, JWKS y un login real con una cuenta
fixture. No imprime tokens, contraseñas ni correos.

## 1. Cuenta de prueba

Usar una cuenta `ACTIVO`, con correo verificado y **sin MFA**. La recomendada es
la fixture A:

- correo: `cliente.a@fixtures.g7.test`
- rol: `CLIENTE`
- UUID: `00000000-0000-4000-8000-00000000f00a`

Las cuentas fixture se cargan al arrancar solo si el entorno tiene:

```text
G7_FIXTURES_CUENTAS_HABILITADAS=true
G7_FIXTURES_CUENTAS_CLAVE=<clave privada que cumpla la política>
G7_PASSWORD_COMUNES_ARCHIVO=<ruta del archivo de contraseñas comunes>
```

La clave se comparte por canal privado y nunca se copia a Git, issues, PRs,
logs o evidencia. Debe cumplir la política real y no puede contener la parte
local de los correos de las fixtures (`cliente`, `prueba`, `admin` o
`vendedor`).

> Si las cuentas fixture ya existían, el cargador **no cambia** su contraseña.
> En ese caso hay que usar la clave con la que se crearon o una cuenta activa
> conocida. No borrar ni recrear cuentas para “arreglar” el smoke.

La cuenta A queda reservada para el smoke. Las pruebas de cambio de perfil o de
contraseña usan la fixture C (`cliente.cambios@fixtures.g7.test`). Si la
contraseña de A cambia, actualizar el secreto `SMOKE_LOGIN_PASSWORD` antes del
siguiente push a `develop`.

## 2. Secretos de GitHub Actions

En el repositorio backend: **Settings → Secrets and variables → Actions →
New repository secret**.

| Nombre | Valor |
|---|---|
| `SMOKE_LOGIN_EMAIL` | Correo de la cuenta fixture, por ejemplo `cliente.a@fixtures.g7.test`. |
| `SMOKE_LOGIN_PASSWORD` | Contraseña de esa cuenta. Debe ser la misma que se usó al crearla. |

No hace falta crear un secreto para la URL: `SMOKE_BASE_URL` tiene por defecto
`https://g7-auth-service.onrender.com`. Si se prueba otro entorno, se puede
sobrescribir en el job.

## 3. Ejecutar

La forma normal es un push a `develop` que supere build, pruebas e imagen. El
job de deploy dispara Render y, al terminar, ejecuta el smoke.

Para probar el script sin desplegar, en una máquina con las variables cargadas:

```bash
export SMOKE_BASE_URL=https://g7-auth-service.onrender.com
export SMOKE_LOGIN_EMAIL='...'
export SMOKE_LOGIN_PASSWORD='...'
python3 .github/scripts/smoke_test.py
```

Salida esperada, sin secretos:

```text
Smoke: salud UP.
Smoke: JWKS válido (1 clave(s) RSA RS256 pública(s)).
Smoke: login OK (tokenType=Bearer, expiresIn=900s). No se registran tokens.
```

## 4. Si falla

- **Falta `SMOKE_LOGIN_EMAIL` o `SMOKE_LOGIN_PASSWORD`:** crear el secreto en
  GitHub Actions y volver a ejecutar el job.
- **HTTP 401 `CREDENCIALES_INVALIDAS`:** revisar que la cuenta esté `ACTIVO`,
  verificada, sin bloqueo y que la contraseña sea la correcta; si A cambió de
  contraseña, actualizar `SMOKE_LOGIN_PASSWORD`. No repetir el login en bucle:
  cada intento fallido consume protección de la cuenta.
- **`mfaRequerido: true`:** usar una cuenta `CLIENTE` sin MFA; el smoke no
  completa OTP.
- **JWKS inválido o sin claves:** revisar `AUTH_CLAVE_PRIVADA` (o
  `AUTH_CLAVE_PRIVADA_ARCHIVO`), `AUTH_CLAVE_PUBLICA_ANTERIOR` si hay rotación y
  el issuer del entorno; el servicio no debe publicar material privado.
- **Health distinto de `UP`:** revisar el arranque en Render y los logs sin
  copiar secretos.

## 5. Evidencia

Para FEAT-09-T6 basta el enlace al run verde y una nota con: SHA desplegado,
resultado del health, número de claves del JWKS y resultado del login. No se
adjuntan tokens, contraseñas ni correos completos.
