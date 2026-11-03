# Evento `perfil.catalogo-roles-actualizado` versión 1

Perfil publica este evento cada vez que arranca: una instantánea completa del catálogo de roles profesionales, con los roles activos y los
inactivos y sus dos idiomas. Lo consume Entrevista para validar el rol objetivo de una sesión contra el catálogo sin consultar a Perfil en
línea. Un servicio nuevo, con la tabla vacía, se llena con un solo mensaje.

## Destino

| Dato | Valor |
|---|---|
| Exchange | `perfil.events` (tipo `topic`, durable, no se borra solo) |
| Clave de enrutamiento | `perfil.catalogo-roles-actualizado` |
| Cola | `entrevista.catalogo-roles-actualizado` (durable) |
| Argumentos de la cola | `x-dead-letter-exchange` = `entrevista.dlx` y `x-dead-letter-routing-key` = `entrevista.catalogo-roles-actualizado` |
| Quién la declara | Perfil y Entrevista, con exactamente los mismos argumentos, para que el orden de arranque no importe. Si los argumentos difieren, el broker rechaza la declaración de quien llegue segundo |

## Cuerpo

`application/json`, UTF-8.

```json
{
  "actualizadoEn": "2026-10-24T12:00:00.123Z",
  "roles": [
    { "codigo": "a0000001-0000-0000-0000-000000000001", "nombreEs": "Desarrollador Frontend", "nombreEn": "Frontend Developer", "estado": "ACTIVE" },
    { "codigo": "a0000001-0000-0000-0000-000000000002", "nombreEs": "Desarrollador Backend", "nombreEn": "Backend Developer", "estado": "INACTIVE" }
  ]
}
```

| Campo | Tipo y formato | Regla |
|---|---|---|
| `actualizadoEn` | instante ISO-8601 en UTC con milisegundos y sufijo `Z` | Instante de la instantánea, truncado a milisegundos |
| `roles` | arreglo de 1 a 200 elementos | Todos los roles del catálogo (hoy 46), sin repetir `codigo` |
| ↳ `codigo` | UUID en texto | Identificador del rol en el catálogo de Perfil; es el mismo `codigo` que viaja en `rolesAspirados[]` de `perfil.profesional-actualizado` |
| ↳ `nombreEs` | texto, 1 a 255 caracteres | Nombre en español |
| ↳ `nombreEn` | texto, 1 a 255 caracteres | Nombre en inglés. Si el rol no tiene uno propio (valor en blanco), Perfil envía el nombre en español, para que la instantánea nunca viole su propio contrato |
| ↳ `estado` | `ACTIVE` o `INACTIVE` | `ACTIVE` si el catálogo ofrece el rol hoy; `INACTIVE` si se desactivó |

El texto viaja literal: comillas, emoji y tildes salen como se guardaron, escapados solo por la serialización JSON. Ningún campo se omite.

El esquema formal está en [`perfil-catalogo-roles-actualizado-v1.schema.json`](perfil-catalogo-roles-actualizado-v1.schema.json) (JSON Schema
2020-12, `additionalProperties: false`): una prueba compara la carga real con él y con los límites del dominio.

## Propiedades AMQP

| Propiedad | Valor | Para qué |
|---|---|---|
| `message_id` | identificador del evento (UUID v4 en texto) | Identificador único; un reenvío lleva el mismo |
| `type` | `perfil.catalogo-roles-actualizado` | Tipo del evento |
| `headers.x-event-version` | `1` (entero) | Versión del contrato |
| `app_id` | `cameia-perfil` | Productor |
| `timestamp` | `actualizadoEn`, con precisión de segundos (límite de AMQP) | Instante |
| `correlation_id` | `perfil-catalogo-` + los 8 primeros caracteres del `message_id` | Hilo para depurar; no hay una petición HTTP que origine el evento. Cumple `^[A-Za-z0-9._-]{1,64}$` |
| `headers.x-causation-id` | igual que `correlation_id` | |
| `content_type` y `content_encoding` | `application/json` y `UTF-8` | El consumidor solo lee JSON con este tipo |
| `delivery_mode` | `2` (persistente) | Sobrevive a un reinicio del broker |

## Cuándo se publica

- Al terminar de arrancar la API de Perfil (evento `ApplicationReadyEvent`), salvo que ya haya una instantánea del catálogo sin publicar.
  El catálogo solo cambia con una migración y un despliegue (ninguna ruta crea, edita ni desactiva roles), así que publicar al arrancar la
  versión nueva cubre «cada vez que un rol cambia».
- Arranques seguidos con el broker caído dejan una sola fila pendiente; el Job de relevo la publica cuando el broker vuelve.
- La tarea de relevo (`profile-events-relay`) no anota el catálogo: solo publica lo pendiente. Si lo anotara, lo publicaría en cada corrida.
- `perfil.events.announce-role-catalog-on-startup=false` apaga la anotación.
- Si la base no está disponible al arrancar, la anotación se omite con un `ERROR` en el log y se repite en el siguiente arranque.

## Garantías

- **Al menos una vez.** El mismo mensaje (mismo `message_id`) puede llegar dos veces; el consumidor debe ser idempotente.
- **Sin orden** garantizado entre instantáneas. El consumidor aplica un rol solo si `actualizadoEn` es igual o posterior al guardado en su fila.
- **Los roles ausentes no se borran.** Perfil nunca borra un rol: lo desactiva. Un rol que una instantánea no trae se conserva tal cual en el consumidor.
- **Sin pérdida por caída del broker.** La instantánea se guarda en la tabla de salida; se intenta publicar justo después del commit y una
  tarea periódica reintenta lo que el broker no confirmó.
- **Varias instancias que arrancan a la vez** pueden anotar una instantánea cada una (un candado de la base hace que la instancia que llega mientras otra anota se salte, pero no garantiza una sola). No es un error: cada
  mensaje lleva su `message_id` y el consumidor aplica el más reciente.

## Privacidad

El catálogo no contiene datos personales. Aun así, ningún registro de Perfil escribe los nombres de los roles: solo el identificador del
evento y la cantidad de roles. La carga se borra de la tabla de salida en cuanto el broker confirma la publicación.

## Cómo se versiona

- Un campo nuevo **opcional** actualiza el esquema de la misma versión: los consumidores ignoran los campos que no usan.
- Quitar o cambiar un campo crea `perfil.catalogo-roles-actualizado` **versión 2** en paralelo, con su propio esquema, y la versión 1 se
  mantiene hasta que todos los consumidores migren.
