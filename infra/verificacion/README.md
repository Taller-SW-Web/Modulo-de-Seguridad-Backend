# Comprobaciones en el despliegue (FEAT-05-T4 y FEAT-06-T4)

**Dueño:** Sergio (PO). **Opera Render y Supabase:** David.

Dos guiones de solo lectura que escriben la tabla de evidencia. No imprimen
secretos, tokens ni datos personales. La salida se pega en
`docs/evidencia/h5/FEAT-05-06.md` del repo de documentación.

## FEAT-05-T4 — rotación de la clave de firma

Aceptación: el JWKS publica dos claves y un token anterior sigue validando.
Los pasos de la rotación están en `docs/runbooks/clientes-servicio.md` §6.

**Estado al 10-nov-2026:** el JWKS desplegado publica una sola clave. La
rotación no se ha ejecutado.

1. **Antes de rotar (Sergio o David):** anota el `kid` vigente.

   ```bash
   python infra/verificacion/comprobar_rotacion.py --esperadas 1
   ```

2. **Antes de rotar:** inicia sesión con una cuenta de prueba **sin**
   `formatoAcceso`, para recibir el JWS de 3 partes. El token de la SPA no
   sirve: desde FEAT-16 es un JWE cuya cabecera no lleva `kid`, y el guion lo
   rechaza. Vive 15 minutos: los pasos 3 y 4 deben terminar dentro de ese tiempo.

   ```bash
   read -rs -p 'Correo: ' G7_CORREO && echo
   read -rs -p 'Contraseña: ' G7_CONTRASENA && echo
   export G7_TOKEN_ANTERIOR=$(python - <<'EOF'
   import json, os, urllib.request
   cuerpo = json.dumps({'correo': os.environ['G7_CORREO'], 'contrasena': os.environ['G7_CONTRASENA']}).encode()
   peticion = urllib.request.Request('https://g7-auth-service.onrender.com/api/v1/auth/login', data=cuerpo,
                                     headers={'Content-Type': 'application/json'})
   print(json.load(urllib.request.urlopen(peticion))['accessToken'])
   EOF
   )
   unset G7_CORREO G7_CONTRASENA
   ```

   `read -rs` evita que la contraseña quede en el historial de la terminal. La
   cuenta no debe tener MFA, porque el login respondería con un desafío.

3. **David, en Render:** ejecuta el §6 del runbook. `AUTH_CLAVE_PRIVADA` pasa a
   la clave nueva y `AUTH_CLAVE_PUBLICA_ANTERIOR` recibe la pública de la
   actual. Vuelve a desplegar.

4. **Después de rotar:**

   ```bash
   python infra/verificacion/comprobar_rotacion.py
   unset G7_TOKEN_ANTERIOR
   ```

   Debe terminar con código 0: dos claves, el `kid` del token anterior
   publicado y `GET /auth/me` con `200`.

5. **Avisa a los seis módulos** antes del paso 3. Quien guarde el JWKS en caché
   debe refrescarlo al ver un `kid` desconocido; el kit de integración ya lo pide.

6. **A las 24 horas o más:** David quita `AUTH_CLAVE_PUBLICA_ANTERIOR` y se
   comprueba con `--esperadas 1`.

Si el redespliegue tarda más de 15 minutos, el token del paso 2 vence y el
paso 4 da `401` por vencimiento, no por la rotación. En ese caso la evidencia
solo acredita las dos claves, y la parte del token anterior queda como «no
probada». Para evitarlo, despierta el servicio antes del paso 2 y ten lista la
clave nueva antes de iniciar sesión.

## FEAT-06-T4 — consulta de identidad con token de servicio

Aceptación: con token de servicio, `200` con el scope y `403` sin él.

Necesita el cliente `modulo-prueba-h5`, aprobado por el PO el 10-nov con los
scopes `usuarios:leer` y `direcciones:leer`. Ya existe y está desactivado: David
lo reactiva desde el módulo de configuración (`/admin/clientes-tecnicos`), le
concede `direcciones:leer` y rota su secreto.

```bash
read -rs -p 'Secreto: ' G7_CLIENT_SECRET && echo && export G7_CLIENT_SECRET
export G7_USUARIO_ID=<UUID de una cuenta de prueba>
python infra/verificacion/comprobar_consulta_identidad.py
unset G7_CLIENT_SECRET
```

`read -rs` evita que el secreto quede en el historial de la terminal. Lo más
simple es que lo ejecute David, que crea el cliente: así el secreto no sale de
su máquina. Al terminar, rota el secreto desde el módulo de configuración.

Debe terminar con código 0:

| Petición | Esperado |
|---|---|
| `POST /auth/token` | 200, con los dos scopes |
| `GET /usuarios/{id}` | 200 |
| `GET /usuarios/{id}/direcciones` | 200 |
| `GET /roles` | 403 `SCOPE_INSUFICIENTE`, porque el cliente no tiene `roles:leer` |
| `GET /usuarios/{id}` sin token | 401 `TOKEN_INVALIDO` |

El 10-nov se ejecutó con un secreto inválido para probar el guion: respondió
`401 CLIENTE_INVALIDO`, como se esperaba. El camino positivo sigue sin probar.
