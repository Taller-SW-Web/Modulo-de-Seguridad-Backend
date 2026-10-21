# Runbook — clientes de servicio y rotación de clave (FEAT-05)

**Dueño del catálogo:** Sergio (PO). **Ejecuta:** Christian (operaciones), en su
ventana y con aviso al grupo. Fuentes: SPEC-17, `docs/integracion/matriz-scopes.md`
del repo de documentación.

Nada de este runbook va a git con valores reales: ni secretos, ni hashes, ni PEM.

## 1. Catálogo de scopes

`src/main/resources/db/identidad/R__catalogo_scopes.sql` es repetible e
idempotente. Se aplica con el migrador (perfil `migration`) junto con
`R__catalogo_autorizacion`. Solo siembra la tabla `scope`; no crea clientes.

## 2. Crear un cliente

Por cada `client_id` de la matriz (ocho previstos: `modulo-marketplace`,
`modulo-chatbot`, `modulo-retail`, `modulo-ventas`, `modulo-postventa`,
`modulo-despacho`, `modulo-productos`, `simulador-pasarela`):

1. Generar el secreto **en tu máquina**, con al menos 256 bits:

   ```bash
   SECRETO=$(openssl rand -base64 48 | tr -d '\n')
   ```

2. Calcular su hash SHA-256 en hex (64 caracteres, minúsculas). Es lo único que
   llega a la base:

   ```bash
   printf %s "$SECRETO" | sha256sum | cut -d' ' -f1
   ```

3. Insertar el cliente con el **hash**, nunca con el secreto:

   ```sql
   INSERT INTO cliente_servicio (client_id, client_secret_hash, nombre, activo)
   VALUES ('modulo-despacho', '<hash-64-hex>', 'G2 Despacho y entrega', true);
   ```

4. Entregar el secreto al equipo dueño por **canal privado** (no WhatsApp grupal,
   no issue, no PR). Después, borrar la variable: `unset SECRETO`.

## 3. Conceder scopes

Con los clientes creados, ejecutar `docs/migraciones-propuestas/concesiones-servicio.sql`.
Es idempotente y solo concede a clientes existentes. Un scope nuevo exige antes
una fila «Aprobada» en la matriz y en `R__catalogo_scopes.sql`.

## 4. Comprobar

```bash
curl -s -X POST "$BASE/api/v1/auth/token" \
  -d grant_type=client_credentials -d client_id=modulo-despacho \
  --data-urlencode "client_secret=$SECRETO"
```

Debe responder 200 con `scopes`. El `accessToken` **no** se pega en chats ni en
evidencia: basta con anotar el `client_id`, los scopes y la hora.

## 5. Revocar o rotar un secreto

- **Revocar:** `UPDATE cliente_servicio SET activo = false WHERE client_id = '…';`
  Los tokens ya emitidos mueren en su `exp`, como mucho una hora después.
- **Rotar:** repetir §2 con un secreto nuevo y `UPDATE … SET client_secret_hash = '<nuevo>'`.

## 6. Rotar la clave de firma (RF-17.3, ESC-17.2)

1. Generar la clave nueva (RSA ≥ 3072, PKCS8) y extraer la **pública** de la actual:
   `openssl pkey -in actual.pem -pubout -out anterior-publica.pem`.
2. En Render: `AUTH_CLAVE_PRIVADA` = nueva y `AUTH_CLAVE_PUBLICA_ANTERIOR` =
   contenido de `anterior-publica.pem`. Redesplegar.
3. Comprobar que el JWKS publica **dos** `kid` y que un acceso emitido antes sigue
   validando.
4. Esperar **al menos 24 h** y recién entonces quitar `AUTH_CLAVE_PUBLICA_ANTERIOR`.
