# EP-04-T003 — entrega OTP por outbox cifrado (bloque B)

Preparación inicial15-oct-2026, Lima, desde **114986ed3859e4ece9969d05df0f02ab37dbb813** (#48). **Base vigente16-oct: origin/develop827814f0dc1f007a6892e675aa3831efa52d79b6**, con #49/#50 fusionados. La rama `codex/ep-04-otp-outbox`, worktree `.worktrees/ep-04-otp-outbox`, se actualizó por fast-forward sin conflictos y conservó sus seis cambios locales originales. Cero PR backend abiertos al comprobar coordinación. Los worktrees históricos B04/B05/integración y sus merges incompletos permanecen intactos; sus comandos antiguos siguen suspendidos.

Fuentes consumidas: documentación **450e95aa38563d40b21c89255e4c2078cbed75e0**, SPEC-09 RF-09.4/09.6/09.14–09.16, tarea EP-04-T003 y ACUERDOS-INTERNOS §3 (productor solicitar OTP) / contrato MensajeriaPort. La copia local de tableros tiene estados históricos: no se usa para negar los merges verificados. Esta entrega conserva la implementación y autoría de Sergio/equipo; añade únicamente el puente de entrega faltante.

## Diferencia confirmada y corrección

El wiring anterior enviaba EMAIL directamente mediante EntregaOtpSmtp después del commit. Ahora MfaConfig conecta EntregaOtpPort a EntregaOtpOutbox, un puente en `mfa.adapter.out.capacidad` que consume exclusivamente MensajeriaPort y EntregaCorreo exportados. El proveedor existente cifra destino/contenido mediante AES-GCM e inserta `correo.otp` en el outbox dentro de la transacción del productor. Un fallo de cifrado/persistencia se propaga y revierte el commit; no se ofrece un envío directo alternativo.

Se reutilizan MensajeriaService, OutboxJpaAdapter, cifrado y worker existentes. No se crean tablas, migraciones, otro worker ni políticas nuevas. Solicitar EMAIL no necesita un SMTP operativo en ese instante: confirma la entrega durable pendiente; el worker utiliza el SMTP real después del commit. El destino se obtiene de cuentas y la respuesta pública continúa enmascarada y sin código. No se modifican firmas de puertos ni HTTP/OpenAPI.

OtpLoginService solo cambia su comentario: generación/HMAC, cuota persistida 3/15 minutos, invalidación de código anterior, desafío de 300 segundos y consumo/verificación permanecen como estaban. Reintentar el mismo mensaje no crea OTP, no consume otra cuota ni extiende vencimiento. La rama SMS conserva el adaptador/configuración anteriores; no se afirma entrega SMS real. La clase SMTP histórica permanece, pero EMAIL ya no la usa en el wiring productivo.

## Evidencia por ID

| ID | Evidencia de este cambio | Límite real |
| --- | --- | --- |
| EP-04-T003 | Composición real OTP + correo cifrado en un commit; rollback; cuota/reenvío/concurrencia; fallo/reintento de transporte posterior | Preparado localmente para revisión y CI, sin recepción SMTP desplegada |
| EP-04-T004 | Regresión de las 9 pruebas existentes de reglas OTP, incluidos consumo único, intentos, vencimiento y reenvío; 7 pruebas PG de desafío/composición básica existentes | No se reimplementa el coordinador ni se presenta esta selección como aceptación completa de apertura de sesión |
| EP-04-T006 | Ningún cierre nuevo | Pendientes recorrido desplegado, correo recibido y negativos reales |

Seis casos nuevos ejecutan beans productivos de MFA, cuentas, roles, credenciales/Argon2, auditoría, AES-GCM y outbox contra PostgreSQL 17.6 desechable, sin worker programado ni transporte exitoso ficticio:

1. EMAIL sin SMTP configurado confirma OTP HMAC y un correo pendiente; se descifra solo en test para comprobar el mismo código, sin imprimirlo.
2. Constraint de fallo inducida exclusivamente en ese PostgreSQL revierte nuevo código, cuota y la invalidación del anterior: sigue existiendo solo el primer OTP/correo.
3. Reenvío invalida anteriores y respeta cuota; cuarta solicitud no añade OTP/correo ni extiende desafío.
4. Desafío vencido no crea OTP/correo ni consume cuota.
5. EnvioCorreoSmtp real sin configurar falla: el mensaje permanece cifrado; dos intentos usan el mismo UUID/payload y no añaden OTP/cuota. Adelantar elegibilidad solo en la fixture no cambia el backoff productivo.
6. Dos solicitudes concurrentes después de dos aceptadas: una aceptada y una limitada, tres OTP/correos en total.

La fixture carga esquema observado y V101–V106 existentes únicamente en el contenedor local. No se ejecutó SQL remoto ni se limpiaron históricos.

## Ejecuciones iniciales conservadas (15-oct)

JDK21, Gradle8.14.3. Primera invocación sin escalamiento no inició Gradle por acceso a la caché del usuario; se ejecutó con permiso de herramientas sobre esa caché y Docker local.

```powershell
.\gradlew.bat test --tests '*OtpOutboxPostgresTest' --tests '*OtpLoginServiceTest' --tests '*MfaTransaccionalPostgresTest' --tests '*architecture*' bootJar --console=plain --no-daemon
# 1min18s: 45 pruebas, 41 aprobadas y 4 fallidas; cero omitidas.
.\gradlew.bat test --tests '*OtpOutboxPostgresTest' --tests '*ArquitecturaHexagonalTest' bootJar --console=plain --no-daemon
# 1min7s: 12 pruebas aprobadas; cero fallos/errores/omisiones.
```

Fallos iniciales corregidos: el puente nuevo estaba en `adapter.out.entrega` y la regla exige `adapter.out.capacidad`; se trasladó sin relajar arquitectura. Tres casos nuevos dependían de un reloj congelado con nanosegundos: PostgreSQL redondeaba creado_en a microsegundos, que podía quedar posterior al reloj y rechazar el desafío inicial. La fixture ahora fija precisión de microsegundos; no se cambió la regla productiva. Se repitió esa clase por compartir fixture y la arquitectura afectada, sin repetir núcleo/MFA/contratos ya aprobados.

**45 casos distintos verificados entre selecciones:** 6 PG nuevos + 7 PG MFA existentes + 9 núcleo OTP + 23 arquitectura. Los XML finales por clase están reunidos en `build/evidencia-otp-outbox`; no representan una sola ejecución completa. El informe `build/reports/tests/test/index.html` corresponde al último filtro (12 casos). `bootJar` final: **73.710.041 bytes**. `git diff --check` correcto. Suite completa al CI.

## Pendientes y alcance

La persistencia/reintento durable está verificada localmente; no acredita recepción SMTP, configuración de secretos/grants, Supabase, despliegue ni G5. El worker existente no descarta mensajes por vencimiento o reenvío del OTP: podría entregar tarde un código que el verificador ya rechaza. No se afirma cancelación/purga de correos obsoletos ni se amplía infraestructura en este bloque. El texto evita prometer cinco minutos adicionales desde la recepción; la vigencia contractual sigue ligada al código/desafío.

C (auditoría no crítica privada durable) y D (contraste de reintentos) siguen con Christian; no se modificaron cola en memoria, backoff, DLQ ni broker. EP-07-T006 ya tiene su composición local incorporada por #50; se sustituye su antiguo estado de preparación. Las aceptaciones desplegadas EP-02-T010/T011, EP-03-T009, EP-04-T006, EP-07-T007 siguen pendientes según su evidencia propia. Ningún ID se cerró por merge o por estas pruebas.

## Verificación vigente tras #50 (16-oct)

Se verificó #50 fusionado (headf0c353af37cde1a39c21c22208cb536ad0fff63c) y sus tres checks success. Tras fetch se incorporaron #49/#50 por `git merge --ff-only origin/develop`, sin crear commit propio ni usar stash/reset/clean. No se repitieron ni copiaron las implementaciones fusionadas. Las referencias a45casos y JAR anteriores quedan como evidencia histórica de la base inicial.

```powershell
.\gradlew.bat test --tests '*OtpOutboxPostgresTest' --tests '*ProteccionComposicionPostgresTest' --tests '*architecture*' bootJar --console=plain --no-daemon
```

**BUILD SUCCESSFUL2min12s:39/39, cero fallos/errores/omisiones** sobre la base827814f más esta preparación:6PG OTP,10PG composición de protección/login/MFA/refresh de #50 y23arquitectura. bootJar vigente **73.710.644bytes**. XML separados en `build/evidencia-otp-outbox-post50`; diff-check correcto. Los45casos anteriores no se presentan como una ejecución nueva; suite completa alCI.

Se corrige la guía de despliegue, que aún afirmaba503 inmediato por ausencia deSMTP: con este cambio EMAIL se acepta si el encolado cifrado confirma, y un fallo de transporte posterior no devuelve cuota. Se añade `ACEPTACION-EP04-EP07.md` con criterios separados porID y entradas reales pendientes; no contiene resultados inventados ni amplía proveedores. En README/guías consultados no se encontró una pareja deURLs públicas con versión desplegada acreditada: solo ejemplos y propuesta. No se intentó adivinar endpoints ni ejecutar recorridos contra nube.

## Publicación manual y texto de PR hacia develop

No se hizo stage, commit, push, PR, aprobación ni merge remoto. Ejecutado: fetch/verificación de base, creación de worktree/rama nueva y pruebas afectadas. Luis puede revisar y publicar exclusivamente esta rama:

```bash
cd /d/desarrollo-uni/ciclo8/Taller/Modulo-de-Seguridad/Backend-auth-service/.worktrees/ep-04-otp-outbox
git add src/main/java/com/marketplace/auth_service/configuration/MfaConfig.java src/main/java/com/marketplace/auth_service/mfa/adapter/out/capacidad/EntregaOtpOutbox.java src/main/java/com/marketplace/auth_service/mfa/application/port/out/EntregaOtpPort.java src/main/java/com/marketplace/auth_service/mfa/application/service/OtpLoginService.java src/test/java/com/marketplace/auth_service/mfa/adapter/out/persistence/OtpOutboxPostgresTest.java docs/evidencia/EP-04-T003-OUTBOX.md docs/evidencia/ACEPTACION-EP04-EP07.md docs/despliegue/DESPLIEGUE-T005.md
git diff --cached --stat
git commit -m "fix: encolar OTP cifrado en el commit de solicitud (EP-04-T003)"
git push -u origin codex/ep-04-otp-outbox
```

**Título:** EP-04-T003: entrega OTP mediante outbox cifrado

**Cuerpo:**

Solicitar OTP enviaba EMAIL directamente después del commit. Ahora el puente EntregaOtpOutbox usa MensajeriaPort y el outbox AES-GCM existentes en el mismo commit de generación/cuota e invalidación anterior. El worker existente envía/reintenta después; un fallo de encolado revierte la solicitud completa. Se mantienen las reglas y firmas OTP, HTTP y SMS existentes, sin rehacer B04/B05 ni infraestructura.

Validación vigente tras integrar #50:39/39 (6PG OTP,10PG composición de protección/login/MFA/refresh y23arquitectura), bootJar y diff-check correctos. Cubre ciphertext/HMAC, rollback, reenvío, cuota concurrente, expiración y reintento real fallido sin duplicar OTP. Evidencia anterior conservada y selección exacta en docs/evidencia/EP-04-T003-OUTBOX.md; suite completa alCI. T004 solo recibe evidencia de regresión. Incluye corrección de la guía sobre aceptación durable y matriz de aceptación desplegada separada porID.

Base develop827814f con #48/#49/#50 incorporados, sin conflictos al actualizar. No SQL remoto. No acredita correo recibido/despliegue/G5; el worker no cancela correos de OTP ya obsoletos. Auditoría privada durable y contraste del backoff quedan fuera con Christian. No se cierran IDs por este PR.
