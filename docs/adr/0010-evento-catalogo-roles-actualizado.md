# ADR 0010 · Evento `perfil.catalogo-roles-actualizado`

## Estado

Aceptada. Decisión de Backend y del equipo, 22 de octubre de 2026; aprobada por Paula Andrea Muñoz Delgado el 22 de octubre de 2026.

## Contexto

Entrevista valida el rol objetivo de una sesión contra los roles activos del catálogo, y ese catálogo solo vive en Perfil. Entrevista no puede llamar a Perfil en línea (HT-03): si Perfil está caído, una entrevista no puede fallar por eso. Perfil ya publica `perfil.profesional-actualizado` con una tabla de salida y un relevo (ADR 0006), así que el catálogo viaja por el mismo camino.

Hechos del catálogo que condicionan el diseño:

- Son 46 roles (menos de 10 KB) y solo cambian con una migración y un despliegue: ninguna ruta crea, edita ni desactiva roles.
- Perfil nunca borra un rol: lo desactiva (`ON DELETE RESTRICT` desde `rol_objetivo`).
- Un servicio nuevo, o uno que perdió su copia, necesita el catálogo completo, no solo los cambios.

## Decisión

- **Una instantánea completa en un solo mensaje.** El evento lleva todos los roles, activos e inactivos, con `codigo`, `nombreEs`, `nombreEn` y `estado`. Un servicio con la tabla vacía se llena con un mensaje y una transacción.
- **Se anota al arrancar.** Al terminar de arrancar la API (`ApplicationReadyEvent`), `RoleCatalogEventsAppService` guarda la instantánea en `evento_saliente` y el relevo la publica, salvo que ya haya una instantánea del catálogo sin publicar. Es idempotente y cubre «cada vez que un rol cambia», porque el catálogo cambia con un despliegue. La propiedad `perfil.events.announce-role-catalog-on-startup` (por omisión `true`) la apaga.
- **La tarea de relevo no la anota.** El Job `profile-events-relay` arranca la aplicación en cada corrida (cada hora en staging); con el anunciador encendido publicaría el catálogo en cada corrida. El perfil `profile-events-relay` la trae en `false`: el Job solo publica lo pendiente.
- **Un fallo al anotar no tumba el arranque.** El anunciador captura la excepción, escribe un `ERROR` con la clase de la causa (sin nombres de rol ni el mensaje de la excepción) y la instantánea se anota en el siguiente arranque.
- **`agregado_id` constante.** El catálogo no pertenece a un perfil: la fila usa el UUID nulo (`00000000-0000-0000-0000-000000000000`).
- **Migración `V10`.** Reemplaza `ck_evento_saliente_tipo` para admitir el tipo nuevo; no toca datos.
- **Correlación derivada del evento.** No hay petición HTTP que origine el evento: `correlation_id` es `perfil-catalogo-` más los 8 primeros caracteres del `message_id`.
- **Candado de la base al anotar.** `RoleCatalogEventsAppService` toma un candado de transacción de PostgreSQL (`pg_try_advisory_xact_lock`, sin esperar) antes de comprobar si hay una pendiente: la instancia que llega mientras otra anota se salta. Medido con tres instancias reales a la vez: una se saltó y dos anotaron, porque el candado dura lo que la transacción (milisegundos) y la que llega después encuentra la fila ya publicada. Reduce el mensaje de más pero no lo elimina; el consumidor aplica el instante más reciente.
- **Perfil declara la cola de Entrevista**, `entrevista.catalogo-roles-actualizado`, con los argumentos de Entrevista y el enlace con la clave `perfil.catalogo-roles-actualizado`. Sin ella el broker devolvería el mensaje (el publicador usa `mandatory`) y el evento quedaría pendiente.
- **`nombreEn` nunca vacío.** Si el valor está en blanco se envía el nombre en español, para que la instantánea no viole su propio esquema. Un nombre en español vacío, o un catálogo sin roles o con más de 200, hace fallar la anotación antes de escribir: una instantánea que el consumidor rechazaría no se publica.

## Alternativas descartadas

- **Un mensaje por rol cambiado.** Exige saber qué cambió y no llena una tabla vacía con un solo mensaje.
- **Publicar solo si el catálogo difiere del último publicado (guardar una huella).** Agrega estado y una tabla para ahorrar un mensaje de menos de 10 KB por cada arranque en frío. Se reevalúa si DevOps sube los arranques en frío.
- **Una tarea programada.** Más maquinaria para un catálogo que cambia con un despliegue.
- **Que Entrevista consulte el catálogo a Perfil.** Rompe HT-03.

## Consecuencias

- Cada arranque en frío de la API agrega un mensaje de menos de 10 KB a la cola de Entrevista, que lo procesa de forma idempotente.
- El Job de relevo corre cada hora en staging y publica la instantánea que el broker no confirmó. En un ambiente donde no esté programado, esa instantánea solo se reintenta cuando alguien lo ejecute. Mientras Entrevista tenga la tabla vacía rechaza todo rol; por eso el reintento importa.
- La migración `V10` y la de alineación del estándar son consecutivas: Flyway falla si una versión menor llega después de una mayor ya aplicada.
- Cambiar un campo de la carga exige una versión 2 en paralelo; el contrato está en `docs/eventos/perfil-catalogo-roles-actualizado-v1.md`.
- La instantánea no contiene datos personales. Una instantánea falsa publicada por quien comparta la credencial del broker podría habilitar un rol inexistente en Entrevista; el riesgo es el de la credencial compartida y se reevalúa con la separación de credenciales.
