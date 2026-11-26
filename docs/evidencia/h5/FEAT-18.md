# FEAT-18 — Mecanismo asincrónico con consumidor real

Evidencia del Hito 5. Dueño: Christian Gabriel Arancivia Salas.
Fecha de la prueba: 26-nov-2026.

## Alcance y nivel de evidencia

- G7 publica eventos de usuario en RabbitMQ y un módulo distinto los consume desde una cola propia.
- **Nivel: entorno de prueba.** La instancia de G7 que publicó fue una ejecución local (PostgreSQL 16 desechable + receptor de correo), no el despliegue de Render. El backend de Render no se tocó y sigue con su broker de integración.
- El broker usado fue una instancia de pruebas de G1 (CloudAMQP, plan gratuito, vhost `gdppuvle`). Por el límite del plan, G7 y G1 compartieron la credencial de esa instancia, algo aceptable por tratarse de un entorno desechable. G1 rotó la credencial al terminar la prueba y G7 dejó de usarla.

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

## Flujo verificado

1. Registro de una cuenta ficticia en la instancia local de G7.
2. Verificación del correo con el enlace enviado al receptor de pruebas.
3. `usuario.creado` guardado en el outbox y publicado en `seguridad.usuarios`.
4. G1 recibió el mensaje en `marketplace.usuarios`.

Nota de contrato: `usuario.creado` se emite al **verificar** la cuenta, no al registrarla.

## Resultado

| Dato | Valor |
|---|---|
| Evento | `usuario.creado` |
| `eventoId` en G7 (outbox) | `5206c121-61e7-4de9-8fe9-0b11306c0daa` |
| Hora (UTC) | 2026-11-26 04:07:26 |
| `eventoId` recibido por G1 | `5206c121-61e7-4de9-8fe9-0b11306c0daa` |
| Coincidencia | **Sí, exacta** |
| Confirmado por | Responsable de G1 (Jim) |

Registro en el publicador:

```text
PublicacionEventosWorker : outbox: 1 evento(s) publicado(s) en RabbitMQ
```

## Intentos previos descartados

Durante la preparación se publicaron dos `usuario.creado` que **no forman parte de
esta evidencia**: `669319ae-a183-42f1-8b72-236d89c74598` y
`a30962ea-5288-4c5b-a85c-036f2a67c499`. G1 los identificó en la cola y se acordó
ignorarlos; el evento válido es el de la tabla anterior.

## Deduplicación

El sobre incluye `eventoId` y el publicador lo envía también como `messageId`, de modo
que el consumidor puede descartar repetidos. La prueba de reenvío deliberado de un
mismo mensaje queda pendiente y no se declara como verificada.

## Versiones

| Componente | Versión |
|---|---|
| Backend G7 | `develop` `27e5d19` (ejecución local) |
| Broker | CloudAMQP de pruebas de G1, vhost `gdppuvle` |
| Consumidor | Módulo de G1 (cola `marketplace.usuarios`) |

## Sanitización

Este documento no incluye la URI AMQP, usuarios, contraseñas, direcciones de correo
completas ni el contenido de los mensajes. La credencial compartida se rotó al
finalizar la prueba.
