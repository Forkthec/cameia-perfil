# ADR 0005 · Bloqueo del Perfil Profesional en toda escritura

## Estado

Aceptada. Decisión de Paula Andrea Muñoz Delgado, 9 de octubre de 2026.

## Contexto

Varias reglas del perfil cruzan filas: el máximo de 4 experiencias y de 5 formaciones, el mínimo de una formación y de un rol objetivo en un perfil activo, y el máximo de 5 roles objetivo. Cada escritura carga el perfil con sus hijos, aplica la regla en memoria y guarda. Con dos pestañas abiertas, o con un doble envío, dos peticiones del mismo perfil leen el mismo estado, las dos pasan la regla y las dos guardan: el perfil termina con 5 experiencias, o sin la última formación que una de las peticiones debía impedir quitar.

La tabla `perfil_profesional` ya tiene una fila por perfil, que es el padre de todo lo que se cuenta.

## Decisión

Todo caso de uso que modifica un perfil (`PATCH`, `POST` y `DELETE` sobre `/api/v1/profiles/{id}…`) empieza leyéndolo con `findByIdForUpdate`, que toma un bloqueo pesimista de escritura sobre la fila del perfil (`SELECT … FOR UPDATE`) y lo conserva hasta que la transacción confirma o se deshace. Dos escrituras sobre el mismo perfil se ejecutan una después de la otra, y la segunda ve lo que confirmó la primera.

- **Espera máxima de 2 s** (`lock_timeout`, solo mientras se espera el bloqueo y solo en esa transacción). Es el presupuesto de DES-02 (p95 de 2 s en operaciones JSON propias) y evita que una escritura atascada retenga la conexión de cada una de las que esperan.
- **Si se agota, responde 409 `PROFILE_UPDATE_IN_PROGRESS`** («Estamos guardando otro cambio de tu perfil. Inténtalo de nuevo en unos segundos.») y no cambia nada. No es un fallo del servicio y por eso no es un 5xx.
- **El bloqueo exige una transacción abierta** (`Propagation.MANDATORY`); sin ella se soltaría al terminar la sentencia.
- **Las lecturas no esperan**: `GET /api/v1/profiles/{id}` sigue leyendo sin bloquear.
- La creación del perfil usa el mismo límite de 2 s y el mismo estado 409, con su propio código `PROFILE_CREATION_IN_PROGRESS` (ADR 0003).

## Alternativas descartadas

- **`@Version` con reintentos.** La petición que pierde repite la regla con el estado nuevo, pero el `save` de Spring Data hace `merge` del agregado completo y reescribe las filas hijas: el control de versión del padre no impide que dos escrituras sobre hijos distintos se pisen, y los reintentos mueven la complejidad a cada caso de uso.
- **Bloquear solo al eliminar.** No cubre los máximos: dos altas simultáneas superan el límite sin que ninguna elimine nada.
- **Bloqueo por Usuario (`pg_advisory_xact_lock`).** Serializaría también perfiles distintos del mismo Usuario cuando Premium admita varios; la fila del perfil es el recurso exacto que se protege.

## Consecuencias

- Las escrituras de un mismo perfil se serializan; las de perfiles distintos y las lecturas no se esperan entre sí.
- Una persona que envía dos cambios a la vez puede recibir un 409 en el segundo; el cliente puede reintentar el mismo cuerpo.
- `ProfileWriteLockIT` comprueba contra PostgreSQL real que una escritura espera a la otra, que se corta a los 2 s con su código y que la lectura no espera.
