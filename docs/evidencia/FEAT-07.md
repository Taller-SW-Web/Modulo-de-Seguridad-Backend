# FEAT-07 Configuración del segundo factor

Corte: 20-oct-2026, America/Lima. Responsable: Luis David Morales Brenis.
Estado: implementación local preparada para revisión, **sin commit, PR, despliegue ni aceptación en nube**.

## Fuentes y alcance

Documentación consumida: `Taller-SW-Web/Modulo-de-Seguridad` main
`029171b61335ec0412af777c2d8a0559d8dadb16` (#64). SPEC-10, SPEC-09, FRONT-09,
OpenAPI v2 2.0.0, catálogos y acuerdos hexagonales/de persistencia.
Base backend: develop `4bd7b61165ff2e4940c68a8b0f050ba395b7b7ad`.
Rama: `feat/EP-04-mfa-v2`; frontend de la misma FEAT en su repositorio independiente.

FEAT-08 no se inició: backend #55 sigue abierto en `f3df36940cae413c811454e502a1a3f291a4de56`,
con tres checks success; el tablero requiere su fusión. No se incorporó ni desplegó ese PR.
FEAT-11-T3 conserva su ajuste documental separado y estado En curso.

Discrepancias identificadas: el resumen H4 menciona reautenticación/cambio de canal,
pero SPEC-10 y el cuerpo publicado de v2 definen acceso vigente más confirmación EMAIL
y acciones HABILITAR/DESHABILITAR. Se sigue ese cuerpo, sin añadir contraseña o canal.
El índice contractual marca v2 publicada mientras algunos metadatos conservan la etiqueta
histórica «local». Se consume el contenido versionado citado; no se modifica OpenAPI
ni se deduce disponibilidad de estas etiquetas.

## Implementación local

- T1/T2: solicitud, reenvío y confirmación v2; actor del filtro y familia resuelta por
  SesionesPort real. No nuevas sesiones, claims, scopes ni rutas históricas MFA v1.
- Cambio independiente de login, token aleatorio de 32 bytes por hash, usuario/familia/acción
  persistidos. Caduca a 600 s; cada OTP a 300 s. Nueva solicitud invalida la anterior.
- Tres intentos por código; cuota de tres envíos por usuario en 900 s compartida con login.
  Reenvío sustituye el código, conserva el vencimiento del cambio y devuelve Retry-After al limitar.
- HMAC distingue LOGIN_MFA y CAMBIO_MFA sin cambiar el formato de los OTP existentes de login.
  La cuota reutiliza la consulta existente sobre todos los OTP del usuario.
- Cuenta → familia → cambio → código bajo una transacción. Consumo, bandera voluntaria
  mediante CuentaEscrituraInterna, auditoría crítica y aviso cifrado comparten commit.
  Un código incorrecto confirma su intento antes del 401; sesión/cambio inválido no consume OTP.
- T3: receptor SMS simulado solo con perfil explícito `pruebas-sms`. Acotado a 50 entregas,
  sin logs ni endpoint público; el perfil normal sigue sin proveedor SMS. EMAIL conserva outbox real.

## Verificación ejecutada

Java 21.0.10, Gradle 8.14.3, PostgreSQL 17.6 en Testcontainers/Docker.

```bash
./gradlew test --tests '*mfa.*' --tests '*UsuarioJpaPostgresTest' \
  --tests '*ArquitecturaHexagonalTest' --tests '*ProteccionComposicionPostgresTest' bootJar
./gradlew test \
  --tests '*CambioMfaPostgresTest.avisoFallidoRevierteEstadoConsumoYAuditoriaCritica' \
  --tests '*CambioMfaPostgresTest.reenviarPersisteCodigoNuevoSinExtenderCambioYSuVencimientoPrecedeAlOtp'
```

Primera selección final: **155/155**, sin fallos, errores ni omisiones. Segunda selección:
**2/2 casos adicionales**, sin fallos ni omisiones. **157 casos distintos; 62 sobre PostgreSQL real**.
JAR generado: 73.756.715 bytes. `git diff --check` correcto.

Cubiertos habilitar/deshabilitar, roles múltiples/obligatorios, estado ya vigente, cuota
compartida, límites temporales, sustitución y agotamiento de OTP, reúso, familia distinta/revocada,
acceso nuevo en la misma familia, confirmaciones concurrentes, rollback de auditoría/outbox/aviso,
aislamiento de propósitos, contrato HTTP y regresión de login/protección.
HTTP usa filtro real con firma/caso de uso simulados; la integración PostgreSQL usa los
proveedores reales de cuentas, sesiones, auditoría, criptografía y outbox, con filas de prueba.
No acredita entrega SMTP real, navegador contra esta API ni despliegue.

Incidencias iniciales resueltas: Docker estaba apagado; dos fixtures antiguas necesitaban
aplicar V102/V108 como scripts completos dentro de transacción (LOCK TABLE/DO), y una
aserción nueva confundía el orden de la fixture con su contador inicial. Se corrigieron
las fixtures/aserción y se repitieron los casos afectados, sin omitir pruebas ni alterar migraciones históricas.

## Migración propuesta y aceptación pendiente

V108__cambio_mfa.sql está en db/migration y docs/migraciones-propuestas con contenido idéntico.
SHA-256: `d1cb23c5538c988c247c181dd674b64e865d53bc08b7a4ff15834293a7f1eda8`.
Añade cambio_mfa y el vínculo OTP/cambio; requiere V102/V103. Conserva OTP históricos y login.
Número libre en el código consultado; Christian debe confirmar reserva/historial real antes de aplicarlo.

Christian conserva revisión del esquema e historial, backup, ventana y permisos/políticas
del rol JDBC real sobre la nueva tabla (SELECT/INSERT/UPDATE). La propuesta habilita RLS
y revoca acceso público/anon/authenticated cuando esos roles existen; no inventa políticas
auth.uid() ni concede acceso desde navegador. No se ejecutó SQL remoto, baseline ni repair.

Antes de desplegar: revisión y CI, V108 aplicada/controlada y permisos del runtime comprobados;
después desplegar los SHAs identificados de backend/frontend. Verificar desde la SPA
correo real, ambos cambios, roles de gestión, cuota, refresh dentro de familia, logout/revocación,
ausencia de secretos en trazas y rechazo de token técnico. Registrar URL, SHA y evidencia
sanitizada; medir los criterios de rendimiento pendientes. Solo entonces evaluar Verificada.

Uso de IA: implementación contrastada con las fuentes y pruebas indicadas. Sin cambios de Marketplace,
mensajes externos, edición contractual, publicación Git ni migraciones remotas.
