# ADR 0006 · Evento `perfil.profesional-actualizado`

## Estado

Aceptada. Decisión de Paula Andrea Muñoz Delgado, 13 de octubre de 2026.

## Contexto

Entrevista necesita una copia del Perfil Profesional activo de cada Usuario para armar las preguntas de una entrevista, y no debe llamar a Perfil en línea: si Perfil está caído, una entrevista en curso no puede fallar por eso. La necesidad está fijada en la historia técnica HT-03: Perfil publica el perfil cada vez que queda activo o cambia estando activo, y Entrevista descarta lo que tenga un instante anterior a su copia y consume de forma idempotente.

Tres restricciones condicionan el diseño:

- Una caída del broker no puede deshacer ni bloquear la escritura de la persona (fiabilidad, AC-0004).
- Una escritura que se revierte no puede dejar un evento publicado.
- La carga es un contrato con otro servicio: sus nombres, límites y valores no pueden depender de cómo un serializador trate los nulos.

## Decisión

- **Tabla de salida en la misma transacción.** Toda escritura confirmada que deja un perfil en `COMPLETED` guarda una fila en `evento_saliente` dentro de la transacción del cambio. Son once: nombre y resumen, agregar y quitar experiencia, formación y habilidad, agregar, cambiar y quitar un rol objetivo, y la finalización. La expectativa salarial no forma parte de la carga y no publica. Un perfil en Borrador no publica.
- **Un intento después del commit y un Job de relevo.** Tras el commit se intenta publicar una vez, en un ejecutor acotado de 2 hilos y cola de 100 (no en el hilo de la petición: al terminar el commit su conexión sigue tomada y anotar el resultado desde ahí pediría otra, con el pool lleno cada escritura esperaría el plazo completo; medido en `ProfileEventsSmallPoolIT`); con la cola llena el intento se descarta y la fila queda pendiente; lo que el broker no confirme queda pendiente y lo recoge el Job `profile-events-relay`. El mensaje lleva siempre el mismo `message_id`, así que el consumidor puede deduplicar.
- **Carga completa, versión 1, sin `embedding`.** El cuerpo es el estado completo del perfil (nunca un cambio parcial), con los nombres en español que fija HT-03 y los opcionales como `null`. `embedding` no viaja: lo calcula Entrevista. El esquema formal está en `docs/eventos/perfil-profesional-actualizado-v1.schema.json` y una prueba lo compara con los límites del dominio.
- **Perfil declara la cola de Entrevista.** `entrevista.perfil-profesional-actualizado` y su enlace a `perfil.events` se declaran también en Perfil, con los argumentos de Entrevista (`x-dead-letter-exchange` `entrevista.dlx`, `x-dead-letter-routing-key` igual al nombre de la cola). Así los mensajes esperan en la cola mientras Entrevista no esté desplegada.
- **Correlación con el `X-Request-Id`.** `correlation_id` y `x-causation-id` son el `X-Request-Id` de la petición si cumple `^[A-Za-z0-9._-]{1,64}$`; si falta o no cumple, el identificador del evento. Un encabezado hostil nunca llega a la tabla ni al registro.
- **`actualizadoEn` del reloj inyectado**, en UTC y truncado a milisegundos. Como las escrituras de un perfil se serializan con el bloqueo de su fila (ADR 0005), dos eventos del mismo perfil nunca tienen un instante decreciente.

## Alternativas descartadas

- **Publicar dentro de la transacción.** Una caída del broker revertiría la escritura de la persona.
- **Que cada caso de uso llame al publicador.** Once puntos de falla; un único punto de guardado (`saveAndAnnounce`) cubre todas las escrituras.
- **Pasar el `X-Request-Id` en cada comando.** Obliga a cambiar once firmas y tres pasarían de tres parámetros; un puerto de dominio (`RequestCorrelation`) lo lee de la petición en curso.
- **Serializar la carga en el servicio de aplicación.** `application` no debe conocer Jackson ni el contrato de mensajería; el adaptador de la tabla de salida arma el JSON.
- **Que solo Entrevista declare la cola (opción B de la decisión D-16).** Un evento publicado antes de que Entrevista declare su cola se devuelve sin destino y queda pendiente; declararla en ambos lados evita esa ventana.

## Consecuencias

- Entrega **al menos una vez** y **sin orden** garantizado: Entrevista debe ser idempotente por `message_id` y descartar lo anterior a su copia.
- Entrevista debe declarar su cola con **exactamente los mismos argumentos**; si difieren, el broker rechaza la declaración de quien llegue segundo.
- Cambiar un campo de la carga exige una **versión 2** en paralelo; un campo opcional nuevo actualiza el esquema de la versión 1.
- Si el Job de relevo no está programado en un ambiente, lo que falle tras el commit solo se reintenta cuando alguien lo ejecute.
- Un perfil antiguo sin nombre o sin resumen viaja con esos campos en `null` en lugar de romper la publicación; el esquema los declara obligatorios porque solo se publica un perfil activo, que siempre los tiene.
- **Relojes entre instancias.** `actualizadoEn` sale del reloj de la instancia que escribe. Si dos instancias tuvieran relojes desfasados y dos escrituras del mismo perfil cayeran en ese lapso, Entrevista descartaría el evento del segundo cambio y su copia quedaría desactualizada hasta la siguiente edición (cada evento lleva el perfil completo). El desfase en Cloud Run es de milisegundos y la alternativa, tomar el mayor entre el reloj y el último evento del perfil, cuesta una consulta por escritura y cambia el diseño. Se acepta el riesgo (decisión de Paula Andrea Muñoz Delgado, 15 de octubre de 2026) y se revisa si Entrevista reporta cambios perdidos. La restricción `fecha_publicacion >= fecha_creacion` de la tabla de salida comparte este origen.
- **Fallo al armar el evento.** No es previsible: el nivel y el estado de empleo son `NOT NULL` con `CHECK`, el nombre y el resumen de un perfil activo no se pueden vaciar y la serialización solo maneja textos. Si ocurriera, la escritura se revierte por completo y responde el error genérico (decisión de Paula, 15 de octubre de 2026). La caída de la base la cubre su propio código en otra tarea.
