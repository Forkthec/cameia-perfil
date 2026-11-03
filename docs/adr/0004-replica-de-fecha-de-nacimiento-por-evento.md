# ADR 0004 · Réplica de la fecha de nacimiento por evento

## Estado

Aceptada. Decisión de Paula Andrea Muñoz Delgado, 9 de octubre de 2026.

## Contexto

Perfil valida las fechas de experiencia y formación contra la fecha de nacimiento del Usuario, que es un dato de Cuentas. Perfil no puede consultarla a Cuentas en cada petición: el Product Owner descartó la consulta síncrona el 8 de octubre de 2026, porque acopla la disponibilidad de Perfil a la de Cuentas y la fecha nunca cambia después del registro.

Cuentas publica `cuenta.creada` (con `usuarioId`, `fechaNacimiento`, `email` y `creadaEn`) y `cuenta.eliminada` en el exchange `cuentas.events`. Los eventos llegan al menos una vez y en cualquier orden.

## Decisión

Perfil mantiene una réplica local de dos columnas, `fecha_nacimiento_usuario(firebase_uid, fecha_nacimiento)`, y la actualiza consumiendo esos eventos:

- **Réplica mínima.** Solo la identidad y la fecha: ni el correo ni el instante de creación se leen, se guardan ni se registran (REST-0003).
- **Inbox.** Cada mensaje se anota en `evento_procesado(message_id, tipo, procesado_en)` en la misma transacción que su efecto, sin plazo de borrado. Un mensaje ya anotado se confirma y no tiene efecto (FIA-02, IOP-04).
- **Confirmación después del commit.** El `ack` se envía cuando el consumidor termina (`acknowledge-mode: auto`), es decir, después del commit de la base de datos. Si el proceso cae entre el commit y el `ack`, la reentrega la reconoce el Inbox.
- **Colas.** `perfil.cuenta-creada` y `perfil.cuenta-eliminada`, enlazadas a `cuentas.events` con las claves `cuenta.creada` y `cuenta.eliminada`.
- **Una cola de fallidos por cola.** Cada cola envía sus rechazos a `perfil.dlx` con su propio nombre como clave y llegan a `perfil.cuenta-creada.dlq` o `perfil.cuenta-eliminada.dlq`. El mensaje que incumple el contrato va a fallidos al primer intento; un fallo técnico se intenta 3 veces (esperas de 1 s y 2 s) y luego va a fallidos. Un mensaje nunca vuelve a su propia cola (`default-requeue-rejected: false`).
- **Lectura estricta.** El tipo del consumidor decide qué clase se instancia (se ignora `__TypeId__`) y la fecha se lee en modo estricto: un texto con hora, con otro formato o numérico se rechaza.
- **Registro.** Un rechazo por contrato se registra una vez en `WARN` con la cola, el identificador del mensaje y la razón; un fallo técnico agotado, una vez en `ERROR` con traza. Nunca se escribe el cuerpo del mensaje, la fecha ni el correo, y un identificador de mensaje con forma inesperada se registra como `invalid`.

## Alternativas descartadas

- **Solo la clave natural, sin Inbox.** `INSERT … ON CONFLICT DO NOTHING` ya hace idempotente `cuenta.creada`, pero no cubre `cuenta.eliminada` seguido de una reentrega de la creación, que resucitaría la fila.
- **Inbox con borrado a 30 días o con un Job de limpieza.** Agrega un proceso y una ventana en la que una reentrega tardía tendría efecto; la tabla guarda un UUID y un instante por evento, así que su crecimiento no justifica el riesgo.
- **Una sola cola de fallidos `perfil.dlq`.** Mezcla orígenes y obliga a inspeccionar cada mensaje para saber a qué cola reenviarlo.
- **Consulta síncrona a Cuentas.** Descartada por el Product Owner (ver Contexto).

## Consecuencias

- Una creación de perfil o una escritura que necesite la fecha antes de que llegue el evento responde 503 `BIRTH_DATE_UNAVAILABLE` y se puede reintentar.
- Un orden invertido (`cuenta.eliminada` antes de `cuenta.creada`) puede dejar una fila de una cuenta ya eliminada. Para reenviar a mano la cola de fallidos hay que comprobar antes en Cuentas que la cuenta sigue existiendo.
- Un mensaje en fallidos no se reintenta solo: lo revisa una persona.
- En staging y producción la conexión usa AMQPS (`SPRING_RABBITMQ_SSL_ENABLED=true`). Una instancia que solo atiende la API puede apagar los consumidores con `SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP=false`.
