# Evento `perfil.profesional-actualizado` versión 1

Perfil publica este evento cada vez que un Perfil Profesional activo (`COMPLETED`) queda guardado: al finalizarlo y en cada cambio posterior
de su información, experiencia, formación, habilidades o roles objetivo. Lo consume Entrevista para mantener su copia del perfil sin
consultar a Perfil en línea. El cuerpo es siempre el estado completo del perfil, nunca un cambio parcial.

## Destino

| Dato | Valor |
|---|---|
| Exchange | `perfil.events` (tipo `topic`, durable, no se borra solo) |
| Clave de enrutamiento | `perfil.profesional-actualizado` |
| Cola | `entrevista.perfil-profesional-actualizado` (durable) |
| Argumentos de la cola | `x-dead-letter-exchange` = `entrevista.dlx` y `x-dead-letter-routing-key` = `entrevista.perfil-profesional-actualizado` |
| Quién la declara | Perfil y Entrevista, con exactamente los mismos argumentos, para que el orden de arranque no importe. Si los argumentos difieren, el broker rechaza la declaración de quien llegue segundo |

## Cuerpo

`application/json`, UTF-8. Los valores opcionales viajan como `null`: ningún campo se omite.

```json
{
  "usuarioId": "6f1d2c3b4a5e4f60718293a4b5c6d7e8",
  "contextoProfesional": {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "nombre": "Analista de datos",
    "estado": "COMPLETED",
    "fecha_archivado": null,
    "resumen": "Analista con tres años de experiencia en SQL y Python.",
    "experiencias": [
      { "empresa": "ACME", "cargo": "Analista", "descripcion": null, "inicio": "2022-01", "fin": null, "estadoEmpleo": "CURRENT" }
    ],
    "formaciones": [
      { "institucion": "Universidad del Cauca", "titulo": "Ingeniería de Sistemas", "nivel": "UNDERGRADUATE",
        "inicio": "2017-02", "fin": "2021-12", "enCurso": false }
    ],
    "habilidades": [ { "nombre": "Python", "nivel": "INTERMEDIATE" } ]
  },
  "rolesAspirados": [ { "codigo": "a0000001-0000-0000-0000-000000000002", "nombre": "Desarrollador Backend" } ],
  "actualizadoEn": "2026-10-15T12:00:00.123Z"
}
```

| Campo | Tipo y formato | Regla |
|---|---|---|
| `usuarioId` | texto, 1 a 128 caracteres | `firebaseUid` del dueño: la identidad que todos los servicios reciben en `X-User-Id`. Siempre el dueño guardado en el perfil, nunca el de la petición |
| `contextoProfesional.id` | UUID en texto | Identificador del perfil |
| `contextoProfesional.nombre` | texto, 1 a 255 caracteres | Nombre del perfil |
| `contextoProfesional.estado` | `PENDING`, `IN_PROGRESS`, `IN_REVIEW` o `COMPLETED` | Hoy siempre `COMPLETED`. El enumerado completo evita una versión 2 cuando exista la reactivación de perfiles archivados |
| `contextoProfesional.fecha_archivado` | instante ISO-8601 o `null` | Hoy siempre `null`: el archivado de perfiles no existe todavía |
| `contextoProfesional.resumen` | texto, 1 a 2000 caracteres | Resumen guardado, literal |
| `contextoProfesional.experiencias[]` | 0 a 4 elementos | En el orden del perfil |
| ↳ `empresa`, `cargo` | texto, 1 a 100 caracteres | |
| ↳ `descripcion` | texto de 1 a 500 caracteres, o `null` | |
| ↳ `inicio`, `fin` | `AAAA-MM` o `null` | |
| ↳ `estadoEmpleo` | `CURRENT`, `UNKNOWN_END` o `ENDED` | |
| `contextoProfesional.formaciones[]` | 1 a 5 elementos | |
| ↳ `institucion`, `titulo` | texto, 1 a 150 caracteres | |
| ↳ `nivel` | `TECHNICAL`, `UNDERGRADUATE` o `POSTGRADUATE` | |
| ↳ `inicio` | `AAAA-MM` | Obligatoria: una formación no se guarda sin fecha de inicio |
| ↳ `fin` | `AAAA-MM` o `null` | |
| ↳ `enCurso` | booleano | |
| `contextoProfesional.habilidades[]` | 1 a 20 elementos | |
| ↳ `nombre` | texto, 1 a 60 caracteres | |
| ↳ `nivel` | `BASIC`, `INTERMEDIATE` o `ADVANCED` | |
| `rolesAspirados[]` | 1 a 5 elementos | |
| ↳ `codigo` | UUID en texto | Identificador del rol en el catálogo de roles profesionales de Perfil: es el único identificador estable del catálogo |
| ↳ `nombre` | texto, 1 a 255 caracteres | |
| `actualizadoEn` | instante ISO-8601 en UTC con milisegundos y sufijo `Z` | Instante del cambio, truncado a milisegundos. Dos eventos del mismo perfil nunca tienen `actualizadoEn` decreciente |

El texto viaja literal: `<script>`, comillas, emoji y tildes salen como se guardaron, escapados solo por la serialización JSON.

`embedding` no viaja: lo calcula Entrevista.

El esquema formal está en [`perfil-profesional-actualizado-v1.schema.json`](perfil-profesional-actualizado-v1.schema.json) (JSON Schema 2020-12,
`additionalProperties: false`): una prueba compara la carga real con él y con los límites del dominio.

## Propiedades AMQP

Los metadatos viajan en las propiedades del mensaje; el cuerpo lleva solo los campos de la tabla anterior.

| Propiedad | Valor | Para qué |
|---|---|---|
| `message_id` | identificador del evento (UUID v4 en texto) | Identificador único; un reenvío lleva el mismo |
| `type` | `perfil.profesional-actualizado` | Tipo del evento |
| `headers.x-event-version` | `1` (entero) | Versión del contrato |
| `app_id` | `cameia-perfil` | Productor |
| `timestamp` | `actualizadoEn`, con precisión de segundos (límite de AMQP) | Instante |
| `correlation_id` | `X-Request-Id` de la petición si cumple `^[A-Za-z0-9._-]{1,64}$`; si falta o no cumple, el `message_id` | Hilo para depurar entre el Gateway, Perfil y Entrevista |
| `headers.x-causation-id` | igual que `correlation_id` | La causa es la petición HTTP |
| `content_type` y `content_encoding` | `application/json` y `UTF-8` | El consumidor solo lee JSON con este tipo |
| `delivery_mode` | `2` (persistente) | Sobrevive a un reinicio del broker |

## Cuándo se publica

- Se publica tras cada una de estas escrituras confirmadas sobre un perfil activo: cambio de nombre o resumen, agregar o quitar una
  experiencia, una formación o una habilidad, agregar, cambiar o quitar un rol objetivo, y la finalización del perfil.
- No se publica con el perfil en Borrador, cuando la petición se rechaza, cuando la escritura se revierte ni cuando solo cambia la
  expectativa salarial (no forma parte de la carga).
- Repetir una escritura idéntica publica otro evento, con `actualizadoEn` creciente y el mismo contenido.

## Garantías

- **Al menos una vez.** El mismo evento (mismo `message_id`) puede llegar dos veces; el consumidor debe ser idempotente.
- **Sin orden** garantizado entre eventos. El consumidor descarta lo que tenga `actualizadoEn` anterior al de su copia. Como la carga
  siempre es el estado completo, dos escrituras del mismo perfil en el mismo milisegundo (solo en teoría) dejan una copia correcta.
- **Sin pérdida por caída del broker.** El evento se guarda en la misma transacción que el cambio; se intenta publicar justo después del
  commit y una tarea periódica reintenta lo que el broker no confirmó.
- **Sin evento de una escritura revertida.** Si el cambio no se confirma, el evento desaparece con él.

## Privacidad

El nombre, el resumen y la experiencia son datos personales. Ningún registro de Perfil escribe la carga: solo el identificador y el tipo
del evento. La carga se borra de la tabla de salida en cuanto el broker confirma la publicación.

## Cómo se versiona

- Un campo nuevo **opcional** actualiza el esquema de la misma versión: los consumidores ignoran los campos que no usan.
- Quitar o cambiar un campo crea `perfil.profesional-actualizado` **versión 2** en paralelo, con su propio esquema, y la versión 1 se
  mantiene hasta que todos los consumidores migren.
