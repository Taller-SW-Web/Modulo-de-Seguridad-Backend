# FEAT-04-T3 — Confirmación del cambio de correo (backend)

## Qué corrige
Hoy el enlace del correo de cambio apunta a `/verificar-correo?token=…`, que llama a
`POST /auth/verificar-correo`. Ese servicio solo entendía tokens `VERIFICAR_CORREO`, así que un token
`CAMBIO_CORREO` terminaba en 410 `ENLACE_YA_USADO` y **nada cambiaba `usuario.correo`**.

Ahora el mismo endpoint (como pide RF-16.6 / ESC-16.3 y el openapi) reconoce los dos propósitos. Si es un
`CAMBIO_CORREO`, en una sola transacción: bloquea la cuenta → comprueba que la dirección siga libre →
reemplaza `usuario.correo` (minúsculas, `correo_verificado = true`) → consume el token → registra
`CORREO_CAMBIADO` (crítico) → publica `usuario.atributos_actualizados` con `"campos": ["correo"]`.
Cualquier rechazo revierte y el login sigue con el correo anterior. **No se tocó el frontend**: la pantalla
`VerificacionCorreoPage` ya funciona con ese 204.

## Archivos (11)
Producción: `TokenVerificacion` (+ `destinoCorreo`), `ResultadoAplicacionCorreo` (nuevo),
`TokensVerificacionPort`, `CuentasVerificacionPort`, `EfectosVerificacionPort`, `VerificacionCorreoService`,
`TokensVerificacionAdapter`, `CuentasVerificacionJpaAdapter`, `EfectosVerificacionAdapter`.
Pruebas: `VerificacionCorreoServiceTest` (ajustada + 6 casos nuevos) y
`CambioCorreoConfirmacionPostgresSmtpTest` (nueva, PostgreSQL + SMTP, 7 casos).

## Cómo aplicarlo en una rama nueva (PowerShell, desde la raíz del repo del backend)
```powershell
git status                                   # debe estar limpio
git fetch origin
git switch -c feat/FEAT-04-T3-confirmacion-correo origin/develop
git apply --check --ignore-whitespace ..\FEAT-04-T3-confirmacion\t3-confirmacion-cambio-correo.patch
git apply --ignore-whitespace ..\FEAT-04-T3-confirmacion\t3-confirmacion-cambio-correo.patch
git status                                   # deben salir 11 archivos (9 M + 2 nuevos)
```
(Ajusta la ruta del `.patch` a donde descomprimas el zip.) Si `--check` falla porque `develop` cambió esos
archivos después de mi copia, copia la carpeta `archivos\` encima del repo **solo si** `git diff origin/develop`
de esos 11 archivos estaba vacío; si no, avísame con el error.

## Cómo probarlo
```powershell
.\gradlew.bat test --tests "*VerificacionCorreoServiceTest"
.\gradlew.bat test --tests "*CambioCorreoConfirmacionPostgresSmtpTest"   # necesita Docker o PRUEBAS_POSTGRES_URL
.\gradlew.bat build
```
Luego:
```powershell
git add -A
git commit -m "FEAT-04-T3: confirmación del cambio de correo por POST /auth/verificar-correo"
git push -u origin feat/FEAT-04-T3-confirmacion-correo
```

## Qué verifiqué y qué no
- El parche aplica limpio sobre el zip de `develop` que me pasaste.
- Compilé y ejecuté `VerificacionCorreoServiceTest` (15 pruebas, incluidas las 4 originales) con un runner
  mínimo; todas pasan. No tengo Gradle/Spring/Docker en mi entorno.
- **No ejecuté** `CambioCorreoConfirmacionPostgresSmtpTest` ni el `gradlew build`. Si la prueba integrada falla
  por wiring (es la parte con más riesgo), es independiente del arreglo: puedes quitarla y avisarme.

## Decisiones a confirmar con el equipo
1. Dirección tomada por otra cuenta entre la solicitud y la confirmación → responde 410 `ENLACE_YA_USADO`
   (el contrato solo publica los dos 410 de enlace). Si prefieren un 409 propio, hay que cambiar openapi.
2. Un enlace vencido/usado de cambio de correo se audita como `USUARIO_VERIFICADO` con resultado FALLO
   (no existe un código de fallo propio del cambio de correo).
3. No se revocan sesiones al cambiar el correo (la spec no lo pide). Se puede agregar si Luis/Jose Luis lo piden.
