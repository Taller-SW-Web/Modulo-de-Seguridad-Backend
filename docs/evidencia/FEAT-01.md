# FEAT-01 Registro y verificación de correo (G1 F-001)

Corte: 25-oct-2026, America/Lima (actualizado tras la revisión de Luis sobre `c5caf51`). Responsable: Eva Lucía Moreno Zevallos.
Estado: **T1–T3 y la parte backend de T4 implementadas y probadas en local y en el CI del PR**;
T5 (pantallas FRONT-02/03 y la parte SPA de T4) en el frontend, PR
Taller-SW-Web/G7-Modulo-de-Seguridad-Frontend#16. Pendientes: retorno conjunto de T4 con G1 y T6
(recorrido desplegado). **Sin fusión, despliegue ni aceptación en nube**: esta evidencia no acredita
RF en el entorno de integración.

## Fuentes y alcance

Documentación consumida: `Taller-SW-Web/Modulo-de-Seguridad` main
`e18687b36c36c77464e8ca920528ec6320643c19`, y FRONT-03 «Retorno por canal» en el PR
Taller-SW-Web/Modulo-de-Seguridad#71. SPEC-01, SPEC-02, SPEC-07 (política), SPEC-12 (auditoría),
OpenAPI v1 (`/auth/registro`, `/auth/verificar-correo`, `/reenviar`), catálogos de errores y eventos,
`BACKEND-HEXAGONAL.md`, la regla de entregas parciales del plan H4 §6 y el tablero
`tareas/hito-4/eva-moreno/TAREAS.md`. Base backend: develop `813b8099a59974c172d95a0b45b23d125e58caa6`;
develop fusionado después hasta `c90037b` (PR #55, #56, #64, #65, #68, #69 y #71).
Rama `feat/EP-01-registro-verificacion`.

| Commit | Tarea |
|---|---|
| `ac41bc8` | T1: `POST /auth/registro` con `canalOrigen`, política y 409 neutro |
| `1b17f80` | T1: contraseña vacía por política (422) y auditoría de los 400 (revisión) |
| `c080d59` | T2: token `VERIFICAR_CORREO` y correo de verificación por el outbox |
| `dd4fafc` | T2: prueba PostgreSQL con V106 (corrección del CI) |
| `0c3b80c` | T3: `verificar-correo` y `reenviar` con cuota por correo |
| `f8700fa` | Evidencia y causa registrada en los 503 (revisión de Sergio) |
| `55bf44a` | T3: cuota de reenvío confirmada antes del envío (revisión de Luis) |
| `c5caf51` | T4: canal de origen en el enlace de verificación |
| (este corte) | `usuario.creado` con `roles` como lista y prueba del mensaje publicado (revisión de Luis) |

## Implementación

- **T1 registro.** Una transacción: identidad `PENDIENTE_VERIFICACION` con términos (fecha y
  versión `G7_REGISTRO_VERSION_TERMINOS`) y `canalOrigen` → política SPEC-07 → credencial →
  rol `CLIENTE` → auditoría crítica `USUARIO_CREADO`. Sin tokens de sesión. 400 `VALIDACION` por
  campo (auditado como `USUARIO_CREADO`/FALLO solo con nombres de campo), 422
  `POLITICA_INCUMPLIDA`, 409 `CORREO_NO_DISPONIBLE` con el texto neutro del contrato.
- **Duplicados.** `AltaCuentaJpaAdapter` consulta el correo normalizado y, ante una inserción
  concurrente, captura la violación de `uq_usuario_correo_lower` y devuelve 409 con rollback:
  no queda fila huérfana. Ver «Índice único del correo».
- **T2 correo.** En la misma transacción, `TokensUnUsoPort.emitir(VERIFICAR_CORREO, 24 h)` (invalida
  los previos; solo se guarda el SHA-256) y correo cifrado por el outbox con el enlace
  `{G7_SPA_URL}/verificar-correo?token=…`. Si la cola de correo no está disponible, el registro se
  revierte y responde 503 `NO_DISPONIBLE`.
- **T3 verificación.** Cuenta bloqueada y aún pendiente → token consumido → `ACTIVO` con correo
  verificado → `usuario.creado` por el outbox, con `"datos": {"roles": ["CLIENTE"]}` como pide el
  catálogo. 204; 410 `ENLACE_EXPIRADO`; 410 `ENLACE_YA_USADO` para usado o desconocido. Todo rechazo
  revierte. Audita `USUARIO_VERIFICADO`.
- **T3 reenvío.** 202 exista o no la cuenta; 3 por hora por correo en `solicitud_limitada`
  (`REENVIO_VERIFICACION`, HMAC con `AUTH_SOLICITUD_LIMITADA_HMAC_KEY`, mismo formato que la
  recuperación) y 429 con `Retry-After`. La cuota se confirma en su propia transacción antes de
  enviar: si después falla la cola de correo, la solicitud ya contó y el límite es el mismo para
  correos registrados y no registrados; responde 202 (un 503 delataría la cuenta), se revierte solo
  el enlace nuevo y se registra el fallo. Sin clave HMAC, 503 para cualquier correo.
- **T4 retorno por canal (backend).** Fuera de `WEB`, el enlace añade `&canal=<CANAL>` (lista
  cerrada, nunca una URL), en el registro y en el reenvío. La SPA lo traduce a su login permitido por
  entorno (FRONT-03; frontend #16). Sin variables nuevas en el backend.
- **Puertos y cambios en módulos de otros dueños.** `CredencialInicialInterna` (credencial),
  `RolClienteInicialInterna` (autorización, solo `CLIENTE`) y, en `MensajeriaService` (auditoría),
  el asunto «Confirma tu correo» y `usuario.creado` en el mismo caso del sobre que
  `usuario.roles_cambiados`.
- **Sin migración.** Columnas, enums, `token_un_uso` y `solicitud_limitada` ya existen.

## Índice único del correo

`uq_usuario_correo_lower` (`LOWER(correo)`) **no viene de V101–V108**: lo crea el esquema base
manual anterior a H3, `docs/arquitectura/schema.sql` línea 144 del repo documental (antiguo
`V1__usuario_y_roles.sql`, copiado en `src/test/resources/mfa-t002/base-historica`). Las bases de
prueba `*/base-observada/schema.sql` son copia sin editar de ese archivo, y las pruebas PostgreSQL
lo ejercitan. Comprobación en Supabase, solo lectura:

```sql
select indexname, indexdef from pg_indexes where schemaname = 'public' and tablename = 'usuario';
```

Resultado en Supabase (25-oct-2026, consulta ejecutada por Eva en el SQL Editor; captura sin datos
de usuarios):

| indexname | indexdef |
|---|---|
| `usuario_pkey` | `CREATE UNIQUE INDEX usuario_pkey ON public.usuario USING btree (id)` |
| `uq_usuario_correo_lower` | `CREATE UNIQUE INDEX uq_usuario_correo_lower ON public.usuario USING btr…` (truncado en la captura) |

**Confirmado: `uq_usuario_correo_lower` existe en la base real y es único.** La captura corta la
definición, así que la expresión `lower(correo)` se da por la del esquema base, no por esta salida.

Sin ese índice seguiría bloqueado el duplicado secuencial (consulta previa y
`ck_usuario_correo_minusculas`), pero no el concurrente.

## Verificación ejecutada

Gradle 8.14.3 con toolchain Java 21; PostgreSQL 17.6 en Testcontainers sobre Docker Engine 29.9.2.

```bash
./gradlew test
```

Suite completa, con develop hasta `c90037b` fusionado: **1003/1003**, 0 fallos, 0 errores,
0 omitidas. Selección de FEAT-01 (73 casos):

| Clase | Casos | Cubre |
|---|---:|---|
| `RegistroClienteServiceTest` | 6 | orden de efectos, rechazos y reversión, cola caída, canal enviado |
| `RegistroHttpTest` | 9 | contrato 201/400/409/422/503, ruta pública con Bearer inválido |
| `EfectosRegistroAdapterTest` | 2 | detalle de auditoría aceptado por el validador de SPEC-12 |
| `CorreoVerificacionAdapterTest` | 4 | propósito y TTL, enlace codificado, canal en el enlace, fallo del outbox |
| `VerificacionCorreoServiceTest` | 9 | cuatro casos del token, cuota y reenvío neutro |
| `VerificacionCorreoHttpTest` | 7 | 204/410/400/202/429/503 |
| `HuellaCorreoHmacTest` | 2 | huella estable sin correo; sin clave falla cerrado |
| `RegistroClientePostgresTest` | 11 | punta a punta sobre PostgreSQL real (abajo) |
| `UsuarioConfigurationTest`, `ArquitecturaHexagonalTest`, `MensajeriaYAuditoriaServiceTest` | 23 | wiring, fronteras hexagonales, asunto y sobre de `usuario.creado` |

`RegistroClientePostgresTest` usa los proveedores reales de cuentas, credenciales (hasher y
política), autorización, tokens de un uso, cuota y outbox cifrado; la auditoría va en memoria.
Comprueba: cuenta con credencial, rol y términos; duplicado con otras mayúsculas sin fila nueva;
política que revierte; token guardado solo como hash y enlace del correo descifrado cuyo SHA-256
coincide y que lleva `&canal=MARKETPLACE`; outbox caído que revierte la cuenta; verificación única
que publica un solo `usuario.creado` y, al procesar el outbox, entrega al publicador el sobre del
catálogo con `"datos": {"roles": ["CLIENTE"]}`, sin token ni correo; enlace vencido que deja la cuenta
pendiente; reenvío que invalida el enlace anterior y limita a 3; correo sin cuenta que responde igual
y también se limita; con la cola de correo caída (fallo inducido con un `CHECK` sobre `outbox`), la
cuota se conserva y el límite es idéntico para un correo registrado y otro sin cuenta, y el enlace
anterior sigue valiendo.

El CI del PR («CI Backend - build y pruebas») quedó en verde en `dd4fafc`, `ff7f902` y `c5caf51`.

No se acredita: entrega SMTP real, despliegue, navegador contra la API ni el retorno con G1.

## Configuración requerida para desplegar

Sin migraciones nuevas. Variables de entorno del servicio en Render:

| Variable | Valor | Si falta |
|---|---|---|
| `AUTH_OUTBOX_AES_KEY_ID` / `AUTH_OUTBOX_AES_KEY` | ya existentes (id y clave Base64 de 32 bytes del outbox) | registro 503 `NO_DISPONIBLE`: no hay correo de verificación |
| `G7_SPA_URL` | ya existente; origen público de la SPA, p. ej. `https://g7-seguridad-spa.onrender.com` | el enlace apunta a `http://localhost:5173` |
| `AUTH_SOLICITUD_LIMITADA_HMAC_KEY` | Base64 de al menos 32 bytes, secreta; la misma que la recuperación. **Cargada en Render el 24-oct («Save only»): se aplica en el próximo despliegue** | reenvío 503 `NO_DISPONIBLE` para cualquier correo |
| `G7_REGISTRO_VERSION_TERMINOS` | versión vigente de los términos (máx. 20 caracteres), pendiente de confirmar con el PO | se guarda `1.0` |

En la SPA (frontend #16): `VITE_RETORNO_MARKETPLACE` y `VITE_RETORNO_RETAIL` (públicas) y la ruta
`/verificar-correo`. Nada de esto se configuró desde esta rama.

## Pendientes

- Sergio (PO): corte parcial (T1–T4 backend; T5 en frontend #16; retorno conjunto de T4 y T6
  después del despliegue), versión real de los términos, token desconocido como `ENLACE_YA_USADO` en
  el contrato, destino de RETAIL (SPEC-02 frente al OpenAPI) y pantalla propia de CHATBOT.
- David (FEAT-11-T1): URL real del login de G1 por entorno y captura del retorno con G1.
- Christian: revisar los dos cambios en `MensajeriaService` (asunto y sobre de `usuario.creado`).
- Juan José: unificar el limitador de reenvíos con el de recuperación, ya fusionado en develop.
- T6: recorrido desplegado con evidencia, después de fusionar y desplegar backend y frontend.
