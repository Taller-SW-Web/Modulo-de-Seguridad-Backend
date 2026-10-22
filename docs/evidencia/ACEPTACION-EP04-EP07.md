# Aceptación desplegada pendiente — EP-04-T006 / EP-07-T007

Preparación del16-oct-2026, Lima. Backend remoto develop827814f0dc1f007a6892e675aa3831efa52d79b6 con #48/#49/#50 incorporados. OTP-outbox permanece como propuesta local en codex/ep-04-otp-outbox, actualizada a esa base; publicar/revisar ese cambio antes de acreditar entrega OTP durable desplegada. Frontend consultado en develop82c94149c8e2326d3d6013a167026a6b38d75b4a, sin asumir que ese SHA sea el desplegado.

Fuentes: documentación450e95aa38563d40b21c89255e4c2078cbed75e0, tareas EP-04-T006/EP-07-T007, PLAN Luis§7, SPEC-09/14 y FRONT-04/12. Los resultados locales están separados en EP-04-T003-OUTBOX.md y EP-07-T006.md; no reemplazan estas aceptaciones. No se han ejecutado los recorridos de esta matriz.

## Entrada necesaria y comprobación previa

En los README y documentos de despliegue consultados solo se encontraron ejemplos/propuestas y URLs locales; ninguna pareja de URLs públicas con versión desplegada verificable. Esto no demuestra ausencia de un despliegue externo: su estado queda SIN ACREDITAR.

| Entrada | Evidencia necesaria | Responsable |
| --- | --- | --- |
| Backend y frontend HTTPS | URLs reales, SHA/imagen de cada servicio y hora; confirmar que contienen las entregas que se aceptarán | Christian / dueños |
| Correo real | SMTP configurado con TLS, remitente y buzones controlados M/ADM/L; recepción comprobada; secretos solo en gestor del entorno | Christian |
| Outbox / broker | Claves AES persistentes; estado de worker, RabbitMQ accesible y confirmación real de publicación; no deducirlos de health genérico | Christian |
| Identidad y navegador | issuer/JWKS concordantes, clave persistente tras reinicio, CORS o proxy acorde con origenSPA, enlaces de correo apuntan a ella | Christian / Sergio |
| Datos de prueba | Cuentas M/ADM para EP-04 y cuenta L para EP-07, identificadas por el equipo; no modificar/bloquear A/B ni activar semillas ciegas | Luis / dueños |
| Ventana | #49 registra ejecución reportada; aceptación de riesgo sin backup y controles remotos restantes siguen por acreditar | Christian / equipo |

Comprobaciones iniciales de solo lectura: health200, descubrimiento/JWKS públicos sin material privado, versión desplegada y pantalla de login. No iniciar fallos/reenvíos hasta contar con cuentas y buzones autorizados. No ejecutar migraciones remotas desde esta aceptación.

## EP-04-T006 — segundo factor (resultado: PENDIENTE)

| Caso | Resultado requerido / evidencia sanitizada |
| --- | --- |
| Recorrido correcto M/ADM | Contraseña correcta produce solo desafío, llega correo real, OTP correcto abre una sesión. Gestión no recibe sesión antes del segundo factor |
| Error y tercer intento | Respuestas del contrato; el tercero invalida el código. Fallos OTP no aumentan la racha de contraseñas |
| Reenvío y cuota | Anterior inválido, desafío no extendido, cuarto envío en15min limitado con Retry-After positivo. Evitar reenvíos automáticos o recargas que consuman cuota accidentalmente |
| Caducidad y reúso | Código/desafío vencido, usado u otro desafío rechazados; doble envío correcto no abre dos familias |
| Cambio concurrente / fallo técnico | Cuenta bloqueada o revisión cambiada no abre sesión; fallo al crear familia no deja consumo parcial ni tokens entregados |
| Interfaz | Reloj menor entre código/desafío, recarga sin desafío vuelve a login, móvil390px y teclado; OTP/desafío ausentes deURL, consola y almacenamiento persistente |

Vigencias productivas intactas: esperar el vencimiento real al aceptar el entorno. Conservar correos, hora y resultado sin copiar códigos ni challengeToken en evidencia. Los fallos técnicos destructivos se prueban en entorno controlado con su dueño; mientras no se prueben, indicar pendiente, sin apagar servicios compartidos ni crear fallos SQL remotos.

## EP-07-T007 — protección (resultado: PENDIENTE)

| Caso | Resultado requerido / evidencia sanitizada |
| --- | --- |
| Bloqueo de L | Cinco contraseñas erróneas distintas producen un bloqueo y un correo real con enlace; nueva autenticación responde genéricamente aun con contraseña correcta |
| Página de enlace | Abrir/previsualizar no consume. Token pasa a memoria y se retira deURL; solo botón explícito hace POST, doble clic una petición |
| Liberación | Submit válido responde204, lectura autorizada refleja estado efectivo y login posterior funciona; enlace usado rechazado |
| Sesión previa | Familia abierta antes del automático continúa renovando; no prometer revocación inmediata del JWT en módulos consumidores |
| Vencimiento / reemplazo | Usar revisión vigente; enlace sustituido/vencido rechazado. No acelerar tiempos productivos ni implementar operaciones manuales para la demo |
| Transporte y hechos | Auditoría crítica, evento y correo persistidos una sola vez; recepciónSMTP y confirmaciónRabbitMQ se documentan como resultados independientes |

Los negativos que puedan bloquear L durante más tiempo se programan después del recorrido principal, con el equipo; no se bloquean cuentas A/B. Bloqueo manual histórico solo cuenta como aceptación si existe una fixture autorizada para ello: no se añade endpoint manual ni se amplía SPEC-08/H-14.

## Registro y cierre por ID

Para cada ejecución registrar ID, caso, fecha/hora, URL y SHA frontend/backend, resultado esperado/obtenido, códigoHTTP, identificadores no secretos de correlación/mensaje y captura sanitizada. No publicar Authorization, contraseñas, códigos, enlaces de desbloqueo, tokens, payload descifrado ni credenciales de infraestructura.

EP-04-T006 y EP-07-T007 requieren resultados propios y revisión de otro integrante. Mientras falten URLs/versión, buzones, SMTP o broker reales, su aceptación permanece pendiente. EP-02-T010/T011, EP-03-T009 y la integraciónG5 conservan sus criterios separados: no se cierran con estos recorridos ni por merge. C/D de infraestructura siguen con Christian; esta matriz no los implementa ni acredita.
