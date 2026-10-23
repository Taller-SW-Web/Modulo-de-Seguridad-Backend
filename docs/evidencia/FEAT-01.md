# FEAT-01 Registro y verificación de correo (G1 F-001)

Corte: 23-oct-2026, America/Lima. Responsable: Eva Lucía Moreno Zevallos.
Estado: **T1–T3 implementadas y probadas en local y en el CI del PR**; T4 (retorno por canal),
T5 (pantallas FRONT-02/03) y T6 (recorrido desplegado) pendientes. **Sin fusión, despliegue
ni aceptación en nube**: esta evidencia no acredita RF en el entorno de integración.

## Fuentes y alcance

Documentación consumida: `Taller-SW-Web/Modulo-de-Seguridad` main
`1b034d2b1db0a19bb5c12c1c5426c797f47864b5`. SPEC-01, SPEC-02, SPEC-07 (política), SPEC-12
(auditoría), OpenAPI v1 (`/auth/registro`, `/auth/verificar-correo`, `/reenviar`), catálogos de
errores y eventos, `BACKEND-HEXAGONAL.md` y el tablero `tareas/hito-4/eva-moreno/TAREAS.md`.
Base backend: develop `813b8099a59974c172d95a0b45b23d125e58caa6`, con develop
`ceee1aae971c44f665125a7d7ee4d0c33780a55c` fusionado después. Rama `feat/EP-01-registro-verificacion`.

| Commit | Tarea |
|---|---|
| `ac41bc8` | T1: `POST /auth/registro` con `canalOrigen`, política y 409 neutro |
| `1b17f80` | T1: contraseña vacía por política (422) y auditoría de los 400 (revisión) |
| `c080d59` | T2: token `VERIFICAR_CORREO` y correo de verificación por el outbox |
| `dd4fafc` | T2: prueba PostgreSQL con V106 (corrección del CI) |
| `0c3b80c` | T3: `verificar-correo` y `reenviar` con cuota por correo |

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
  `{G7_SPA_URL}/verificar-correo?token=…` para todos los canales. Si la cola de correo no está
  disponible, el registro se revierte y responde 503 `NO_DISPONIBLE`.
- **T3 verificación.** Cuenta bloqueada y aún pendiente → token consumido → `ACTIVO` con correo
  verificado → `usuario.creado` por el outbox. 204; 410 `ENLACE_EXPIRADO`; 410 `ENLACE_YA_USADO`
  para usado o desconocido. Todo rechazo revierte. Audita `USUARIO_VERIFICADO`.
- **T3 reenvío.** 202 exista o no la cuenta; 3 por hora por correo en `solicitud_limitada`
  (`REENVIO_VERIFICACION`, HMAC con `AUTH_SOLICITUD_LIMITADA_HMAC_KEY`, mismo formato que la
  recuperación) y 429 con `Retry-After`. Sin clave HMAC, 503 para cualquier correo. Si la cola de
  correo falla con una cuenta pendiente, responde 202 (un 503 delataría la cuenta) y se registra.
- **Puertos nuevos de otros dueños.** `CredencialInicialInterna` (credencial) y
  `RolClienteInicialInterna` (autorización, solo `CLIENTE`). Asunto «Confirma tu correo» en
  `MensajeriaService.asunto()`.
- **Sin migración.** Columnas, enums, `token_un_uso` y `solicitud_limitada` ya existen.

## Índice único del correo

`uq_usuario_correo_lower` (`LOWER(correo)`) **no viene de V101–V108**: lo crea el esquema base
manual anterior a H3, `docs/arquitectura/schema.sql` línea 144 del repo documental (antiguo
`V1__usuario_y_roles.sql`, copiado en `src/test/resources/mfa-t002/base-historica`). Las bases de
prueba `*/base-observada/schema.sql` son copia sin editar de ese archivo, y las pruebas PostgreSQL
lo ejercitan. **No está acreditado contra Supabase**: los inventarios del 14-oct
(INVENTARIO-V103/V104) no cubren `usuario`. Confirmación pendiente con Christian, solo lectura:

```sql
select indexname, indexdef from pg_indexes where schemaname = 'public' and tablename = 'usuario';
```

Sin ese índice seguiría bloqueado el duplicado secuencial (consulta previa y
`ck_usuario_correo_minusculas`), pero no el concurrente.

## Verificación ejecutada

Gradle 8.14.3 con toolchain Java 21; PostgreSQL 17.6 en Testcontainers sobre Docker Engine 29.9.2.

```bash
./gradlew test
```

Suite completa: **855/855**, 0 fallos, 0 errores, 0 omitidas. Selección de FEAT-01 (70 casos):

| Clase | Casos | Cubre |
|---|---:|---|
| `RegistroClienteServiceTest` | 6 | orden de efectos, rechazos y reversión, cola caída |
| `RegistroHttpTest` | 9 | contrato 201/400/409/422/503, ruta pública con Bearer inválido |
| `EfectosRegistroAdapterTest` | 2 | detalle de auditoría aceptado por el validador de SPEC-12 |
| `CorreoVerificacionAdapterTest` | 3 | propósito y TTL, enlace codificado, fallo del outbox |
| `VerificacionCorreoServiceTest` | 9 | cuatro casos del token, cuota y reenvío neutro |
| `VerificacionCorreoHttpTest` | 7 | 204/410/400/202/429/503 |
| `HuellaCorreoHmacTest` | 2 | huella estable sin correo; sin clave falla cerrado |
| `RegistroClientePostgresTest` | 10 | punta a punta sobre PostgreSQL real (abajo) |
| `UsuarioConfigurationTest`, `ArquitecturaHexagonalTest`, `MensajeriaYAuditoriaServiceTest` | 22 | wiring, fronteras hexagonales y asunto |

`RegistroClientePostgresTest` usa los proveedores reales de cuentas, credenciales (hasher y
política), autorización, tokens de un uso, cuota y outbox cifrado; la auditoría va en memoria.
Comprueba: cuenta con credencial, rol y términos; duplicado con otras mayúsculas sin fila nueva;
política que revierte; token guardado solo como hash y enlace del correo descifrado cuyo SHA-256
coincide; outbox caído que revierte la cuenta; verificación única que publica un solo
`usuario.creado`; enlace vencido que deja la cuenta pendiente; reenvío que invalida el enlace
anterior y limita a 3; correo sin cuenta que responde igual y también se limita.

El CI del PR («CI Backend - build y pruebas») quedó en verde tras `dd4fafc`.

No se acredita: entrega SMTP real, despliegue, navegador contra la API ni datos de Supabase.
No se conectó a Supabase ni se aplicó SQL remoto.

## Pendientes

- Christian: `AUTH_SOLICITUD_LIMITADA_HMAC_KEY` en Render; confirmar el índice en Supabase;
  `usuario.creado` lleva `roles` como texto porque `MensajeriaService.sobre()` solo arma la lista
  para `usuario.roles_cambiados`.
- Sergio (PO): versión real de los términos; token desconocido como `ENLACE_YA_USADO` en el
  contrato; pantalla propia de CHATBOT (RF-02.8) pendiente, hoy todos los canales usan la SPA.
- Juan José: unificar el limitador de reenvíos con el de recuperación al fusionar FEAT-03.
- T4–T6 de FEAT-01; evidencia del recorrido desplegado en T6.
