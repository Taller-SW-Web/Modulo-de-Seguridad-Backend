# Reconciliación antes de EP-03-T008 — 15-oct-2026, America/Lima

## Remoto verificado

Backend origin/develop: d40b28af7aec070235f5e91e53f49d4d76a6b9f4. Origin/main: d51ac2eb1fb469c0a19eb26c5d0bb53dde199d3e. Árboles iguales tras #47. GitHub:47PR cerrados,44fusionados; #1/#2/#27 cerrados sin merge. #29/#30 y #34–#46 están fusionados. No hay PR backend abierto.

Frontend Taller-SW-Web/G7-Modulo-de-Seguridad-Frontend:10PR cerrados/fusionados; #10 develop→main. SHA develop82c94149c8e2326d3d6013a167026a6b38d75b4a,main a7e9947afbaaf277a0f18dee2f3b3bccc637d8e9 por ls-remote. Esto acredita publicaciones Git, no despliegue/aceptación funcional.

B04/B05, login, cambio de contraseña, perfil/actividad, auditoría/outbox/SMTP worker y publicador RabbitMQ ya están incorporados por Sergio/equipo. No se reimplementan. En develop existen TokensProteccionAdapter/EfectosProteccionAdapter/OtpLoginService; la preparación local OperacionesOtp no reemplaza esa implementación.

## Estados locales conservados sin alteración

| Worktree antiguo | HEAD | MERGE_HEAD | Archivos cambiados contra su HEAD | Comparación de esos archivos con develop |
|---|---|---|---:|---|
| h3-b04-proteccion |065cd958|2412501|98|84idénticos,12distintos,2sin ruta remota |
| h3-b05-otp |2412501|ninguno|5|1distinto,4sin ruta remota |
| h3-integracion-refresh-proteccion |74485c1|065cd958|35|30idénticos,4distintos,1sin ruta remota |

Se compararon blobs Git calculados desde los archivos locales; las diferencias pueden ser alternativas funcionales/tests/evidencia y NO se descartan por semejanza. Todos los bytes, índices y merges permanecen en sus worktrees originales. Se preservan también audit-mfa-909a5ee MERGING dc338a9(193cambios), otros checkouts de auditoría y archivos .agents/skills-lock del principal. El repositorio documental conserva el tablero de Jose Luis modificado y output/ ajenos.

**Los comandos antiguos para publicar estos tres estados quedan suspendidos.** No se concluyó ningún merge antiguo, no se hizo pull en MERGING, ni reset/clean/force/borrado. No archivar ni eliminar nada en este encargo. El trabajo local no incorporado queda disponible para revisión posterior, sin competir con lo integrado por Sergio.

## Coordinación y nueva base

Se revisaron PR y ramas remotas no incorporadas: ninguna nueva corrección A visible. El otro chat local Analiza la documentación del módulo está idle y proporcionó las indicaciones de revisión, sin tomar A. No se enviaron mensajes en nombre de Luis. No se asegura ausencia de trabajo humano fuera de estas superficies.

Nueva rama codex/ep-03-t008-correo-atomico, worktree .worktrees/ep-03-t008-correo-atomico, creado limpio desde origin/develop d40b28a. No reutiliza los merges viejos. A/EP-03-T008 es el único bloque de implementación de este cambio.

## Hallazgos vigentes y siguiente orden

A confirmado: EfectosCambioContrasenaAdapter solo auditaba; faltaba guardarCorreo. Se corrige en este bloque con MensajeriaPort/outbox/AES existentes, sin SMTP bajo locks. Además se rechaza sin mutaciones un acceso cuya familia no se resuelve, conforme PLAN Juan§8; no cambiar contraseña revocando todas como sustituto.

B confirmado: EntregaOtpSmtp programa JavaMailSender tras commit y documenta la desviación de outbox. Siguiente bloque propio: conectar la entrega al outbox existente sin rehacer OtpLoginService/cuota/vencimiento/reenvío.

C confirmado: TareasTrasCommitSpring usa ArrayBlockingQueue(10000), descarta en saturación y registra/descarta al fallar. Se deja para coordinación de Christian: diseño aprobado PLAN§4 exige cola privada durable seguridad.auditoria.pendiente, registro_id deduplicable y DLQ privada.

D confirmado: MensajeriaService aplica1/2/4/8min y5intentos a correo/eventos. El texto10/30/60s corresponde explícitamente al circuito de auditoría no crítica en PLAN Christian§4. Christian debe contrastar alcance de reintentos del worker y las fuentes vigentes antes de corregirlo; no aplicar unilateralmente tiempos de auditoría a otro circuito. C/D NO se modifican aquí.

Pendientes separados de merge: EP-03-T008 revisión de esta composición/atomicidad; EP-07-T006 pruebas de concurrencia/composición; EP-02-T010/T011,EP-03-T009,EP-04-T006,EP-07-T007 aceptaciones desplegadas, correo real y G5. Christian conserva ventanaSupabase/grants/SMTP/broker/despliegue. No SQL remoto ni cierre de IDs por merge.

## Operaciones ya ejecutadas

Fetch origin backend; lectura de los remotos frontend; inventario de worktrees/status/MERGE_HEAD; comparación de blobs locales; comprobación de PR/chat/ramas; creación del worktree limpio y rama nueva. Ninguna requirió autenticación humana en este turno. No hay comandos pendientes de autenticación. Los comandos de publicación nuevos se entregan en EP-03-T008.md y no se ejecutan automáticamente.