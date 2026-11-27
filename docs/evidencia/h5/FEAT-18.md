# FEAT-18 — Publicación de G7 y procesamiento por el consumidor de G1

Evidencia del Hito 5. Dueño: Christian Gabriel Arancivia Salas.

La prueba se hizo en dos fases: una primera de recepción (G1 leyó el mensaje de la
cola) y una segunda con el consumidor de G1 activo, que procesó los eventos.

## Alcance y nivel de evidencia

- G7 publica eventos de usuario en RabbitMQ; G1 los recibe en su cola y, en la segunda
  fase, **los procesó con su consumidor**.
- **Nivel: entorno de prueba.** La instancia de G7 que publicó fue una ejecución local
  (PostgreSQL 16 desechable + receptor de correo), no el despliegue de Render. El
  backend de Render no se tocó y sigue con su broker de integración.
- El broker fue una instancia de pruebas de G1 (CloudAMQP, plan gratuito, vhost
  `gdppuvle`). Por el límite del plan, G7 y G1 compartieron la credencial; G1 la rotó
  al terminar cada sesión y G7 dejó de usarla.

## Topología

| Elemento | Valor |
|---|---|
| Exchange | `seguridad.usuarios` (topic, durable), declarado por G1 |
| Cola del consumidor | `marketplace.usuarios` (durable), propiedad de G1 |
| Binding | `marketplace.usuarios` → `seguridad.usuarios`, routing key `usuario.*` |
| Publicador | G7, con confirmaciones del broker |
| Identificador | `messageId` = `eventoId` del sobre |

G7 solo publica: no declara ni conoce la cola del consumidor. El exchange lo declaró
G1 antes de la prueba, con las mismas propiedades que usa G7 (topic, durable), por lo
que la redeclaración de la aplicación fue idempotente.

## Fase 1 — Recepción verificada

1. Registro de una cuenta ficticia en la instancia local de G7.
2. Verificación del correo con el enlace enviado al receptor de pruebas.
3. `usuario.creado` guardado en el outbox y publicado en `seguridad.usuarios`.
4. G1 verificó la recepción leyendo el mensaje de `marketplace.usuarios` con su
   herramienta, sin consumirlo.

| Dato | Valor |
|---|---|
| Evento | `usuario.creado` |
| `eventoId` en G7 (outbox) | `5206c121-61e7-4de9-8fe9-0b11306c0daa` |
| Fecha (Lima) | 2026-11-25 23:07:26 |
| Fecha (UTC) | 2026-11-26 04:07:26 |
| `eventoId` recibido por G1 | `5206c121-61e7-4de9-8fe9-0b11306c0daa` |
| Coincidencia | **Sí, exacta** |

Nota de contrato: `usuario.creado` se emite al **verificar** la cuenta, no al
registrarla.

## Fase 2 — Consumidor activo

G1 conectó su consumidor a las 18:41 (Lima). Sobre la misma cuenta de prueba se
publicaron tres eventos; como el consumidor aún no estaba conectado cuando se
generaron, quedaron en la cola de descarte y G1 los recuperó manualmente, una fila
cada uno.

| Evento | `eventoId` | Hora Lima | Recuperado por G1 |
|---|---|---|---|
| `usuario.creado` | `1f691d84-03d5-4330-8299-c174b9362a25` | 18:34:04 | Sí, una fila |
| `usuario.roles_cambiados` | `f00d6cad-003c-4c8e-af64-206bd4d2ee14` | 18:37:37 | Sí, una fila |
| `usuario.bloqueado` | `d1c7dc90-9ef5-48f9-ad2c-e95f375b3eb0` | 18:37:40 | Sí, una fila |

Después se publicó un `usuario.creado` nuevo, de otra cuenta de prueba, con el
consumidor ya conectado. La entrega fue automática y el consumidor lo procesó:

| Dato | Valor |
|---|---|
| Evento | `usuario.creado` |
| `eventoId` en G7 (outbox) | `00e928f9-4a84-4cb8-84e2-831b7cbb9673` |
| Routing key | `usuario.creado` |
| Publicado (Lima) | 2026-11-26 18:50:05 (23:50:05 UTC) |
| Procesado por G1 (Lima) | 18:50:08 |
| Resultado en G1 | una fila única en el consumidor |

Registro del publicador:

```text
PublicacionEventosWorker : outbox: 1 evento(s) publicado(s) en RabbitMQ
```

## Intentos previos descartados

Durante la preparación se publicaron dos `usuario.creado` que no forman parte de la
evidencia: `669319ae-a183-42f1-8b72-236d89c74598` y
`a30962ea-5288-4c5b-a85c-036f2a67c499`. G1 los identificó en la cola y se acordó
ignorarlos.

## Pendiente (no verificado en esta evidencia)

- **Deduplicación.** El sobre incluye `eventoId` y el publicador lo envía también como
  `messageId`, que es la base para descartar repetidos, pero no se probó un reenvío
  deliberado del mismo mensaje.
- **Reintentos y DLQ controlados.** Los tres eventos de la fase 2 pasaron por la cola
  de descarte y G1 los recuperó, lo que confirma que el descarte funciona en la
  práctica; no se hizo una prueba controlada de reintentos ni del límite de entregas.

## Versiones

| Componente | Versión |
|---|---|
| Backend G7 | `develop` `27e5d19` (ejecución local) |
| Broker | CloudAMQP de pruebas de G1, vhost `gdppuvle` |
| Consumidor | Módulo de G1 (cola `marketplace.usuarios`) |

## Sanitización

Este documento no incluye la URI AMQP, usuarios, contraseñas, direcciones de correo
completas ni el contenido de los mensajes. Las credenciales compartidas se rotaron al
finalizar cada prueba y G7 borró la URI de su configuración local.
