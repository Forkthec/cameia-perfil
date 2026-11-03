# Spec — CM-67: evento `perfil.profesional-actualizado` sin `embedding` y prueba de punta a punta de CA-2.5.18

| Dato | Valor |
|---|---|
| Tarea Jira | CM-67 «HU-2.5 – Backend: finalización del Perfil Profesional», subtarea de CM-19 (HU-2.5), responsable Paula Andrea Muñoz Delgado, estado «En revisión», Sprint 3 (Jira, leído el 13-oct-2026) |
| Repositorio | `cameia-perfil`, base `origin/develop` `504f838` (árbol `1699baf6d979d27ffe292a8e3ec3c096c41b5614`; sobre `9355f1a`, verificado el 11-oct, solo suma un archivo de despliegue) |
| Requisitos | Backlog `12102026_01_Backlog_v7.xlsx`: HU-2.5 (23 CA, hoja HE-02), HT-03 (hoja Backlog, fila 19: contrato del evento), RT-01, RT-03, RT-05, RT-06 |
| Alcance | La publicación del evento que HT-03 pide a Perfil y la prueba de punta a punta de CA-2.5.18. La finalización, el 422 con requisitos, la tabla de salida, el publicador y el Job ya están en `develop` (PR #94) |
| Estado de la spec | Aprobada por Paula Andrea Muñoz Delgado el 13-oct-2026, en el chat. Puerta de listo en la sección 22 |
| Rama y PR | `CM-67-evento-perfil-actualizado` desde `develop`; un PR hacia `develop`, sin apilar (no depende de nada sin fusionar) |

Marcas usadas en esta spec: **[H]** hecho confirmado con su fuente; **[I]** inferencia justificada; **[D]** decisión humana (quién y cuándo) o pendiente de decidir.

## 1. Objetivo

1. Que toda escritura confirmada que deje un Perfil Profesional en Activo (`COMPLETED`) guarde, en la misma transacción, un evento
   `perfil.profesional-actualizado` versión 1 y lo publique en el exchange `perfil.events` justo después del commit, para que
   Entrevista mantenga su copia del perfil sin llamar a Perfil (HT-03).
2. Que la carga sea la que fijó Backend (composición del 9-oct) **sin el campo `embedding`**, que calcula Entrevista.
3. Que CA-2.5.18 tenga una prueba que recorra el camino completo base de datos → lectura del perfil → `POST …/completion`.

## 2. Fuentes y hechos de partida

| # | Hecho | Fuente |
|---|---|---|
| H1 | HT-03 fija: exchange `perfil.events`, clave `perfil.profesional-actualizado`, cola `entrevista.perfil-profesional-actualizado`, carga `usuarioId`, `contextoProfesional` (con `id`, `nombre`, `estado`, `fecha_archivado`), `rolesAspirados`, `embedding`, `actualizadoEn`; Perfil publica al finalizar (HU-2.5), al editar un perfil `COMPLETED` (HU-2.3, 2.4, 2.11) y al archivar o reactivar (HU-2.14); Entrevista descarta eventos con `actualizadoEn` anterior y consume de forma idempotente | [H] Backlog v7, hoja Backlog, fila 19 (HT-03) |
| H2 | El `embedding` lo calcula Entrevista y Perfil publica sin él (opción B) | [H] Documento enviado a Vela el 11/12-oct, §3; respuesta de Juan David Vela Coronado «3. OK» (`comunicaciones/recibidas/respuestas vela.txt`, 13-oct); `comunicaciones/seguimiento/respuestas-y-pendientes.md` líneas 27 y 141 |
| H3 | Composición de `contextoProfesional` y de `rolesAspirados` (Q-67-2, opción a) | [D] Paula, 9-oct-2026 (`specs/CM-67-finalizacion-perfil-v4/spec.md` §17) |
| H4 | Editar habilidades de un perfil `COMPLETED` también publica | [D] Paula, 9-oct-2026 (spec de finalización, REQ-EV-02); se pidió a Vela agregar HU-2.5 a la nota de HT-03 (`09102026_v1_perfil-profesional-textos-evento-y-atributos-para-po.md` §3.2); la v7 aún no lo recoge (fila abierta en el seguimiento, línea 33) |
| H5 | Ya existen en `develop`: `V9__evento_saliente.sql`, `OutboundEvent`, `OutboxRepository` (`append`, `lockPending`, `markPublished`, `addAttempt`, `countPending`), `OutboxRepositoryAdapter`, `EventPublisher`, `RabbitEventPublisher` (confirmación en 500 ms, `mandatory`), `OutboxRelayAppService` (`relayAfterCommit`, `relayPending`), `ProfileEventsRelayJobRunner`, `application-profile-events-relay.yml`, el exchange `perfil.events` en `RabbitConfig` | [H] `git ls-tree origin/develop` (13-oct) |
| H6 | Ningún caso de uso llama hoy a la tabla de salida: `ProfileAppService` guarda con `repository.save` en doce escrituras y no publica nada | [H] `ProfileAppService.java:101,138,178,188,200,207,226,232,253,271,276,283` |
| H7 | Nadie recibe hoy lo que se publica en `perfil.events`: Perfil no declara la cola de Entrevista («la cola que los recibe la declara cada consumidor») y Entrevista no existe aún (HT-03 y CM-209 en el Sprint 4). Con `mandatory`, el broker devuelve el mensaje y `RabbitEventPublisher.publish` responde `false` | [H] `RabbitConfig.java:22-24,61-74`; `RabbitEventPublisher.java:73-80`; Jira CM-209 (Sprint 4, «Por hacer») |
| H8 | El Job `profile-events-relay` no está programado en staging; CM-299 cubre solo el de Cuentas | [H] `respuestas-y-pendientes.md` línea 67 («Abierta»); `respuestas vela.txt` («CM-299 solo cubre el Job de reintento de eventos de cuenta») |
| H9 | La columna `perfil_profesional.resumen` es `VARCHAR(2000)` desde V1; `UPDATE … SET resumen = repeat('a', 2001)` falla con `value too long for type character varying(2000)` | [H] `V1__crear_tablas_perfil_profesional.sql:19`; `specs/CM-67-finalizacion-perfil-v4/evidencia-antes.md` fila b |
| H10 | La regla de CA-2.5.18 ya está en el dominio: `getMissingRequirements` cuenta como `SUMMARY` un resumen de más de 2000 puntos de código; la lectura no valida el largo | [H] `ProfessionalProfile.java:299-310`; `ProfessionalProfileTest.complete_shouldListSummary_whenStoredSummaryHas2001CodePoints` |
| H11 | La prueba `CompletionEndToEndIT.completeProfile_shouldListSummary_whenStoredSummaryHas2001Characters` que citaba la spec de finalización no existe | [H] `git grep` en `origin/develop`; `analisis/revisiones/check-ca_cadena-perfil_10oct2026.md` H-CA-1 |
| H12 | La línea base de `develop` está verificada: `clean verify` con 846 unitarias y 270 de integración sin fallos, cobertura 97,4 % de líneas y 98,3 % de ramas, Newman 605 peticiones sin fallos, sobre el mismo árbol `5045085d…` | [H] `analisis/revisiones/verificacion-final-cadena-perfil_11oct2026.md`; `git rev-parse origin/develop^{tree}` (13-oct) |
| H13 | El `X-Request-Id` válido cumple `^[A-Za-z0-9._-]{1,64}$`; si no, Perfil genera uno | [H] `docs/errores.md` («requestId»); `ProblemResponses.java` |
| H14 | `application.service` solo admite clases de primer nivel terminadas en `AppService` | [H] `ArquitecturaTest.java:103-104` |
| H15 | Cuentas serializa la carga de su evento dentro del adaptador de la tabla de salida (`appendAccountCreated`), con todos los valores como texto y el instante con el formato `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` | [H] `cameia-cuentas` `OutboxRepositoryAdapter.java:53-70`, `AccountCreatedPayloadV1.java` |

## 3. Trazabilidad: cada CA de HU-2.5 y HT-03 → requisito → prueba → Postman

Columna «Prueba»: las existentes siguen en verde (regresión); las **nuevas** van en negrita. Carpeta de Postman nueva: `HT-03 — Evento del perfil (local)`.
Carpetas existentes: `HU-2.5 — Habilidades`, `HU-2.5 — Finalización`.

| CA | Exige (literal del CA) | Requisito | Prueba (nombre exacto) | Postman |
|---|---|---|---|---|
| 2.5.1 | 201, `INTERMEDIATE`, `MANUAL` | Regresión; REQ-EV-01 si el perfil es `COMPLETED` | `SkillsEndToEndIT.addSkill_shouldReturn201WithManualProvenance_whenSkillIsValid`; **`ProfileEventsEndToEndIT.addSkill_shouldPublishOneEvent_whenProfileIsCompleted`** | `201 Python intermedio (CA-2.5.1)`; **`E-10 201 agregar SQL a un perfil activo`** y **`E-11 un evento con dos habilidades`** |
| 2.5.2 | 409 «Esa habilidad ya está en tu perfil.» | Regresión; REQ-EV-03 | `ProfileControllerTest.addSkill_shouldReturn409_whenSkillIsDuplicated`; **`ProfileAppServiceTest.writes_shouldNotAnnounce_whenWriteIsRejected`** | `409 machine learning repetida (CA-2.5.2)`; **`E-12 409 java repetida`** y **`E-13 ningún evento tras el 409`** |
| 2.5.3 | 201 la vigésima | Regresión; REQ-EV-01 | `ProfessionalProfileTest.addSkill_shouldAcceptTwentieth_whenProfileHasNineteen`; **`ProfileAppServiceTest.writes_shouldAnnounceAfterSave_whenWriteSucceeds[addSkill]`** | `201 vigésima habilidad (CA-2.5.3)` |
| 2.5.4 | 422 con `SUMMARY`, `SKILLS`; sigue `IN_PROGRESS` | Regresión; REQ-EV-03 | `CompletionEndToEndIT.completeProfile_shouldListSummaryAndSkills_whenBothAreMissing`; **`ProfileEventsEndToEndIT.completeProfile_shouldPublishNothing_whenRequirementsAreMissing`** | `422 faltan resumen y habilidades (CA-2.5.4)` |
| 2.5.5 | 200 sin experiencia ni CV | Regresión; REQ-EV-01 | `CompletionEndToEndIT.completeProfile_shouldActivate_whenNoWorkExperience`; **`ProfileEventsEndToEndIT.completeProfile_shouldPublishSchemaPayload_whenProfileIsActivated`** (sin experiencia: `experiencias` vacío) | `200 sin experiencia laboral (CA-2.5.5)`; **`E-08`/`E-09`** |
| 2.5.6 | 200, `IN_PROGRESS` → `COMPLETED` | Regresión; REQ-EV-01, REQ-EV-04 | `CompletionEndToEndIT.completeProfile_shouldReturn200AndCompleted_whenAllRequirementsAreMet`; **`ProfileEventsEndToEndIT.completeProfile_shouldPublishSchemaPayload_whenProfileIsActivated`** | `200 finaliza (CA-2.5.6)`; **`E-08 200 finalizar`** y **`E-09 un evento con la carga del contrato`** |
| 2.5.7 | 422 «Ingresa una habilidad.» / «Elige un nivel.» | Regresión; REQ-EV-03 | `ProfileControllerTest.addSkill_shouldReturnRequired_whenFieldIsMissingOrBlank`; **`ProfileAppServiceTest.writes_shouldNotAnnounce_whenWriteIsRejected`** | `422 habilidad vacía (CA-2.5.7)` |
| 2.5.8 | 201 con 60 | Regresión; REQ-EV-01 (la carga lleva los 60 sin cortar) | `ProfileControllerTest.addSkill_shouldAccept_whenNameIsAtLimit`; **`ProfileUpdatedPayloadV1Test.from_shouldKeepTextLiteral_whenValuesAreAtTheirLimits`** | `201 habilidad de 60 (CA-2.5.8)` |
| 2.5.9 | 422 «La habilidad no puede superar los 60 caracteres.» | Regresión; REQ-EV-03 | `ProfileControllerTest.addSkill_shouldReturnTooLong_whenNameExceedsLimit` | `422 habilidad de 61 (CA-2.5.9)` |
| 2.5.10 | 422 en `level` con `EXPERT` | Regresión; REQ-EV-03 | `ProfileControllerTest.addSkill_shouldReturnLevelInvalid_whenLevelIsUnknown` | `422 nivel EXPERT (CA-2.5.10)` |
| 2.5.11 | 409 «Este perfil ya está activo.» | Regresión; REQ-EV-03 | `ProfileControllerTest.postCompletion_shouldReturn409_whenProfileIsAlreadyActive`; **`ProfileEventsEndToEndIT.completeProfile_shouldPublishOnlyOnce_whenRepeatedTwentyTimes`** | `409 ya activo (CA-2.5.11)`; **`E-14 409 finalizar otra vez`** y **`E-15 ningún evento tras el 409`** |
| 2.5.12 | 409 al eliminar la única habilidad de un Activo | Regresión; REQ-EV-03 | `ProfileControllerTest.removeSkill_shouldReturn409_whenLastSkillOfActiveProfile` | `409 última habilidad de un perfil Activo (CA-2.5.12)` |
| 2.5.13 | 200 con resumen de 2000 | Regresión; REQ-EV-01 (la carga lleva los 2000) | `ProfessionalProfileTest.complete_shouldSucceed_whenSummaryHasExactly2000CodePoints`; `CompletionEndToEndIT.completeProfile_shouldActivate_whenStoredSummaryHasTheMaximumLength`; **`ProfileUpdatedPayloadV1Test.from_shouldKeepTextLiteral_whenValuesAreAtTheirLimits`** | `200 resumen de 2000 (CA-2.5.13)` |
| 2.5.14 | 500 «Ocurrió un error. Inténtalo de nuevo.»; sigue `IN_PROGRESS` | Regresión; REQ-EV-03 (sin evento) | `CompletionEndToEndIT.completeProfile_shouldRollBack_whenDatabaseFailsDuringCompletion`; **`ProfileEventsEndToEndIT.completeProfile_shouldPublishNothing_whenDatabaseFailsDuringCompletion`** | No se reproduce en Newman (necesita un disparador en la base) |
| 2.5.15 | 403 en `POST …/skills`, `DELETE …/skills/{skillId}`, `POST …/completion` | Regresión; REQ-EV-03 | `ProfileControllerTest.skills_shouldReturn403_whenProfileBelongsToAnotherUser`; `ProfileControllerTest.postCompletion_shouldReturn403_whenProfileBelongsToAnotherUser` | `403 finalizar un perfil ajeno (CA-2.5.15)` |
| 2.5.16 | 204 en Borrador con una; sigue Borrador | Regresión; REQ-EV-05 (Borrador no publica) | `SkillsEndToEndIT.removeSkill_shouldReturn204_whenDraftHasOnlyOne`; **`ProfileEventsAppServiceTest.announceIfCompleted_shouldDoNothing_whenProfileIsNotCompleted`** | `204 única habilidad en Borrador (CA-2.5.16)`; **`E-07 ningún evento con el perfil en Borrador`** |
| 2.5.17 | 204 con dos en Activo; sigue Activo | Regresión; REQ-EV-01 | `ProfessionalProfileTest.removeSkill_shouldRemove_whenCompletedProfileHasTwo`; **`ProfileAppServiceTest.writes_shouldAnnounceAfterSave_whenWriteSucceeds[removeSkill]`** | `204 una de dos en Activo (CA-2.5.17)` |
| **2.5.18** | 422 con `SUMMARY` si el resumen **guardado** tiene 2001; sigue Borrador | **REQ-FN-18** | **`ColumnLengthConsistencyIT.summaryColumn_shouldReject2001Characters_whenWritten`** (la base rechaza 2001 y acepta 2000); `ProfessionalProfileTest.complete_shouldListSummary_whenStoredSummaryHas2001CodePoints` (la finalización informa `SUMMARY`); D-17 = B | No se reproduce en Newman ni en staging: la base no admite 2001 (H9). Justificado en §4.2 |
| 2.5.19 | 409 «Ya tienes el máximo de 20 habilidades.» | Regresión; REQ-EV-03 | `ProfessionalProfileTest.addSkill_shouldRejectTwentyFirst_whenProfileHasTwenty` | `409 habilidad 21 (CA-2.5.19)` |
| 2.5.20 | 409 «programacion» frente a «Programación» | Regresión; REQ-EV-03 | `SkillsEndToEndIT.addSkill_shouldReturn409_whenNameDiffersOnlyInAccents` | `409 programacion sin tilde (CA-2.5.20)` |
| 2.5.21 | 422 con los cinco en orden | Regresión; REQ-EV-03 | `ProfileControllerTest.postCompletion_shouldListFiveRequirementsInOrder_whenProfileIsNew` | `422 perfil recién creado (CA-2.5.21)` |
| 2.5.22 | En paralelo: gana finalizar → 200 y eliminar 409; gana eliminar → 204 y finalizar 422 `SKILLS`; nunca Activo sin habilidades | Regresión; REQ-EV-06 (la carga nunca sale sin habilidades) | `ProfileWriteLockIT.completeAndRemoveSkill_shouldRejectRemoval_whenCompletionWinsTheLock`, `…_shouldRejectCompletion_whenRemovalWinsTheLock`; **`ProfileEventsEndToEndIT.completeAndRemoveSkill_shouldPublishOneEventWithSkills_whenBothRunAtOnce`** | Secuencial: `200 finaliza` y `409 eliminar la única habilidad` |
| 2.5.23 | 404 habilidad de otro perfil | Regresión; REQ-EV-03 | `SkillsEndToEndIT.removeSkill_shouldKeepOtherProfileSkill_whenIdBelongsToAnotherProfile` | `404 habilidad de otro perfil (CA-2.5.23)` |
| HT-03 destino | `perfil.events`, clave `perfil.profesional-actualizado`, cola `entrevista.perfil-profesional-actualizado` | REQ-EV-07 | **`ProfileEventsEndToEndIT.topology_shouldRouteToInterviewQueue_whenServiceStarts`** | **`E-01 la cola de Entrevista existe y está ligada`** |
| HT-03 carga | `usuarioId`, `contextoProfesional` (`id`, `nombre`, `estado`, `fecha_archivado`), `rolesAspirados`, `actualizadoEn`; sin `embedding` (H2) | REQ-EV-02 | **`ProfileUpdatedEventContractTest`** (5 pruebas, §11) | **`E-09`** |
| HT-03 edición de un Activo | HU-2.3, 2.4, 2.11 (y HU-2.5 por H4) publican | REQ-EV-01 | **`ProfileAppServiceTest.writes_shouldAnnounceAfterSave_whenWriteSucceeds`** (11 casos) | **`E-10`/`E-11`** |
| HT-03 orden | `actualizadoEn` no retrocede para un perfil | REQ-EV-08 | **`ProfileEventsEndToEndIT.addSkill_shouldPublishOneEvent_whenProfileIsCompleted`** (compara con el anterior) | **`E-11 actualizadoEn mayor que el anterior`** |
| HT-03 archivar/reactivar (HU-2.14) | Publicar al archivar o reactivar | Fuera de alcance (§15) | — | — |

Ningún requisito de esta spec queda sin CA o sin razón: REQ-EV-09 (correlación) y REQ-EV-10 (registros) salen de las reglas de eventos del
proyecto (`estandar-estricto.md` §F «Eventos») y de SEG-04; REQ-EV-11 (habilidades) de H4.

## 4. Requisitos (EARS)

### 4.1 Evento

- **REQ-EV-01 (HT-03, H4).** Cuando una de estas once escrituras termine sin error y el perfil quede en `COMPLETED` —`completeProfile`,
  `updateProfileInfo`, `addWorkExperience`, `removeWorkExperience`, `addEducation`, `removeEducation`, `addSkill`, `removeSkill`,
  `addTargetRole`, `updateTargetRole`, `removeTargetRole`—, Perfil debe guardar en `evento_saliente`, **en la misma transacción** que el
  cambio, una fila con `tipo` = `perfil.profesional-actualizado`, `version` = 1, `agregado_id` = id del perfil, `carga` = §5 y
  `fecha_publicacion` nula.
- **REQ-EV-02 (HT-03, H2, H3).** La carga es exactamente la de §5: sin `embedding`, sin campos adicionales y sin datos de otros Usuarios.
- **REQ-EV-03.** Cuando una escritura se rechace (4xx) o se revierta (500), no debe quedar ninguna fila nueva en `evento_saliente` ni
  publicarse ningún mensaje.
- **REQ-EV-04.** Después del commit, Perfil debe intentar publicar la fila una vez con `OutboxRelayAppService.relayAfterCommit` (plazo de
  confirmación 500 ms, ya implementado). Sin confirmación, la fila queda pendiente para el Job y la respuesta HTTP no cambia.
- **REQ-EV-05.** Mientras el perfil no esté en `COMPLETED` (`IN_PROGRESS`, `IN_REVIEW`, `PENDING`), ninguna escritura debe guardar evento.
- **REQ-EV-06 (CA-2.5.22).** El evento se arma con el perfil ya modificado, dentro del bloqueo de su fila: nunca sale una carga
  `COMPLETED` sin habilidades, sin formación, sin roles, sin nombre o sin resumen.
- **REQ-EV-07 (HT-03, D-16).** Perfil debe declarar al arrancar, con atributos fijos, el exchange `perfil.events` (`topic`, durable, sin
  borrado automático; ya existe), la cola `entrevista.perfil-profesional-actualizado` (durable, argumentos
  `x-dead-letter-exchange` = `entrevista.dlx` y `x-dead-letter-routing-key` = `entrevista.perfil-profesional-actualizado`) y su enlace
  con la clave `perfil.profesional-actualizado`. **Depende de D-16** (§17): con la opción B este requisito no existe.
- **REQ-EV-08 (HT-03).** `actualizadoEn` es el instante del reloj inyectado en UTC al guardar el evento, truncado a milisegundos. Como
  las escrituras de un perfil se serializan con el bloqueo de su fila, dos eventos del mismo perfil nunca tienen `actualizadoEn`
  decreciente.
- **REQ-EV-09 (correlación).** `correlation_id` y `x-causation-id` del mensaje son el `X-Request-Id` de la petición si cumple
  `^[A-Za-z0-9._-]{1,64}$`; si falta o no cumple, el id del evento. Un encabezado hostil nunca llega a la tabla ni al registro.
- **REQ-EV-10 (SEG-04).** Ningún registro escribe la carga, el nombre del perfil, el resumen, las experiencias, las formaciones, las
  habilidades ni los roles; solo `eventId`, `type` y la clase de la causa (ya así en `OutboxRelayAppService` y `RabbitEventPublisher`).
- **REQ-EV-11.** `updateSalaryExpectation` no publica: la expectativa salarial no forma parte de la carga y su ruta está fuera del MVP
  (hallazgo I-024). [I]

### 4.2 CA-2.5.18

- **REQ-FN-18 (CA-2.5.18).** La base de datos debe rechazar un resumen de más de 2000 caracteres (columna `VARCHAR(2000)`, SQLState
  `22001`) y aceptar uno de 2000. Si un perfil en Borrador tuviera un resumen guardado de más de 2000 caracteres, `POST …/completion` debe
  responder 422 `PROFILE_INCOMPLETE` con `SUMMARY` y dejarlo en Borrador (regla del dominio, ya en `develop`, H10).
- **Por qué se cumple así (D-17 = B, Paula, 13-oct-2026).** El «Dado» del CA (un resumen guardado de 2001) no puede darse en ningún
  ambiente: la base, el `PATCH` (CA-2.3.3) y la pantalla lo impiden (H9). El CA queda demostrado con dos pruebas: la base rechaza 2001 y la
  finalización informa el requisito si el dato existiera. No se prueba en staging; se propone ajustar la nota técnica del CA en el backlog (A-3).

## 5. Contrato del evento v1

### 5.1 Destino y propiedades AMQP

| Dato | Valor |
|---|---|
| Exchange | `perfil.events` (`topic`, durable, no se borra solo) — [H] ya declarado |
| Clave | `perfil.profesional-actualizado` — [H] HT-03 |
| Cola | `entrevista.perfil-profesional-actualizado` — la declara Perfil si D-16 = A (REQ-EV-07); con D-16 = B la declara Entrevista |
| Propiedades | Las de `RabbitEventPublisher` (ya en `develop`): `message_id` = id del evento, `type` = `perfil.profesional-actualizado`, `headers.x-event-version` = 1, `app_id` = `cameia-perfil`, `timestamp` = instante del evento (segundos), `correlation_id` y `headers.x-causation-id` = REQ-EV-09, `content_type` = `application/json`, `content_encoding` = `UTF-8`, `delivery_mode` = 2 |
| Garantías | Al menos una vez (mismo `message_id` en cada reintento); sin orden entre mensajes; el consumidor descarta lo que tenga `actualizadoEn` anterior a su copia y es idempotente (HT-03) |

### 5.2 Carga (`application/json`, UTF-8)

Todos los valores son texto salvo `enCurso` (booleano) y las listas; los opcionales viajan como `null` (nunca se omiten), para que el
consumidor no dependa de los valores por omisión del serializador. Los nombres los fija el contrato (H1, H3): en español, `camelCase`, y
`fecha_archivado` tal como lo escribe HT-03.

| Campo | Tipo | Regla | Origen |
|---|---|---|---|
| `usuarioId` | texto 1–128 | `firebaseUid` del dueño | `ProfessionalProfile.getFirebaseUid().value()` |
| `contextoProfesional.id` | UUID en texto | id del perfil | `getId().value()` |
| `contextoProfesional.nombre` | texto 1–255 | nombre del perfil | `getName().value()` |
| `contextoProfesional.estado` | `PENDING` · `IN_PROGRESS` · `IN_REVIEW` · `COMPLETED` | Hoy siempre `COMPLETED`; el enumerado completo evita una v2 cuando HU-2.14 publique la reactivación [I] | `getStatus().name()` |
| `contextoProfesional.fecha_archivado` | `null` o instante | Hoy siempre `null`: el archivado no existe (HU-2.14 fuera de alcance) | constante `null` |
| `contextoProfesional.resumen` | texto 1–2000 | resumen guardado | `getSummary().value()` |
| `contextoProfesional.experiencias[]` | 0–4 elementos | en el orden de `getWorkExperiences()` | — |
| ↳ `empresa`, `cargo` | texto 1–100 | | `getCompany()`, `getPosition()` |
| ↳ `descripcion` | texto 1–500 o `null` | | `getDescription()` |
| ↳ `inicio`, `fin` | `AAAA-MM` o `null` | | `getStartDate()`, `getEndDate()` con `YearMonth.toString()` |
| ↳ `estadoEmpleo` | `CURRENT` · `UNKNOWN_END` · `ENDED` | | `getEmploymentStatus().name()` |
| `contextoProfesional.formaciones[]` | 1–5 elementos | | — |
| ↳ `institucion`, `titulo` | texto 1–150 | | `getInstitution()`, `getDegree()` |
| ↳ `nivel` | `TECHNICAL` · `UNDERGRADUATE` · `POSTGRADUATE` | | `getLevel().name()` |
| ↳ `inicio` | `AAAA-MM` | Obligatoria: el backlog exige «Ingresa la fecha de inicio.» y la base la declara `NOT NULL` (decisión de Paula, 15-oct-2026) | `getStartDate()` |
| ↳ `fin` | `AAAA-MM` o `null` | | `getEndDate()` |
| ↳ `enCurso` | booleano | | `isInProgress()` |
| `contextoProfesional.habilidades[]` | 1–20 elementos | | — |
| ↳ `nombre` | texto 1–60 | | `getSkillName()` |
| ↳ `nivel` | `BASIC` · `INTERMEDIATE` · `ADVANCED` | | `getLevel().name()` |
| `rolesAspirados[]` | 1–5 elementos | | — |
| ↳ `codigo` | UUID en texto | id del rol en el catálogo de Perfil: es el único identificador estable del catálogo [I] | `getProfessionalRoleId()` |
| ↳ `nombre` | texto 1–255 | | `getRoleTitle()` |
| `actualizadoEn` | `AAAA-MM-DDTHH:mm:ss.SSSZ` | REQ-EV-08 | reloj inyectado |

Los mínimos de 1 en formaciones, habilidades y roles y la presencia de nombre y resumen valen porque solo se publica un perfil `COMPLETED`
(REQ-EV-05, REQ-EV-06). Los máximos son los del dominio (`ProfileName.MAX_LENGTH` 255, `ProfessionalSummary.MAX_LENGTH` 2000,
`WorkExperience.MAX_COMPANY_LENGTH` y `MAX_POSITION_LENGTH` 100, `MAX_DESCRIPTION_LENGTH` 500, `Education.MAX_INSTITUTION_LENGTH` y
`MAX_DEGREE_LENGTH` 150, `SkillName.MAX_LENGTH` 60, título de rol 255, `ProfessionalProfile.MAX_WORK_EXPERIENCES` 4, `MAX_EDUCATIONS` 5,
`MAX_SKILLS` 20, `MAX_TARGET_ROLES` 5), y una prueba compara el esquema con esas constantes (§11). El texto viaja literal: `<script>`,
comillas, emoji y tildes salen como se guardaron, escapados solo por la serialización JSON.

Ejemplo (el mismo que lleva el esquema en `examples[0]`):

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

Esquema formal: `docs/eventos/perfil-profesional-actualizado-v1.schema.json` (JSON Schema 2020-12, `additionalProperties: false` en cada
objeto, `required` con todos los campos). Documento legible: `docs/eventos/perfil-profesional-actualizado-v1.md`.

## 6. Diseño

| Pieza | Capa | Acción | Por qué |
|---|---|---|---|
| `CorrelationId` (record) | `domain/event` | Crear (copia de la de Cuentas) | REQ-EV-09; reutiliza la regla probada en Cuentas |
| `ProfileUpdated` (record) | `domain/event` | Crear | El hecho de negocio: id del evento, perfil, instante, correlación; sin JSON |
| `RequestCorrelation` (interfaz) | `domain/port` | Crear | El servicio necesita el `X-Request-Id` sin cambiar once firmas (la regla de ≤ 3 parámetros impide sumarlo a `removeX(profileId, uid, id)`) |
| `OutboxRepository.appendProfileUpdated` | `domain/port` | Agregar método | Patrón de Cuentas (H15): el adaptador serializa y devuelve el `OutboundEvent` guardado para el relevo |
| `ProfileEventsAppService` | `application/service` | Crear | Un solo motivo de cambio: decidir si se anuncia y guardar el evento en la transacción del cambio; `ProfileAppService` gana una sola dependencia |
| `ProfileAppService` | `application/service` | Modificar | `saveAndAnnounce` reemplaza `repository.save` en las once escrituras de REQ-EV-01 |
| `ProfileUpdatedPayloadV1` | `infrastructure/messaging/payload` | Reemplazar (hoy es un esqueleto con `TODO` que nadie usa) | Contrato JSON v1 de §5.2 |
| `OutboxRepositoryAdapter` | `infrastructure/persistence/repository` | Modificar | Implementa `appendProfileUpdated` con `@Transactional(propagation = MANDATORY)` |
| `HttpRequestCorrelation` | `infrastructure/web` (paquete nuevo con `package-info.java`; **D-19**) | Crear | Lee `X-Request-Id` de la petición en curso con `RequestContextHolder`; fuera de una petición devuelve vacío. El `CLAUDE.md` del repo pide preguntar antes de crear una carpeta nueva |
| `RabbitConfig` | `infrastructure/messaging/config` | Modificar (si D-16 = A) | Declara la cola de Entrevista y su enlace (REQ-EV-07) |

Reutiliza sin cambios: `OutboundEvent`, `OutboxRelayAppService`, `RabbitEventPublisher`, `ProfileEventsRelayJobRunner`, `V9`,
`MessagingIntegrationSupport`, `ReplicatedBirthDates`, `ApiTestClient`, `ColumnLengthConsistencyIT`. No hay migración, dependencia ni código de
error nuevo.

**Alternativas descartadas.** (1) Pasar el `X-Request-Id` en cada comando: obliga a cambiar once firmas y tres de ellas pasarían de 3
parámetros. (2) Serializar en el servicio de aplicación: `application` no debe conocer Jackson ni el contrato de mensajería. (3) Publicar
dentro de la transacción: una caída del broker revertiría la escritura del Usuario (FIA-01). (4) Llamar al publicador desde cada caso de uso:
once puntos de falla; `saveAndAnnounce` es uno.

## 7. Validación por campo (R2)

El cambio no agrega campos de entrada HTTP. La única entrada nueva que se lee es el encabezado `X-Request-Id`:

| Entrada | Caso | Valor literal | Resultado | Prueba |
|---|---|---|---|---|
| `X-Request-Id` | ausente | (sin encabezado) | correlación = id del evento | `ProfileEventsAppServiceTest.announceIfCompleted_shouldUseEventId_whenRequestIdIsMissing` |
| | válido en el límite | `"a".repeat(64)` | se usa tal cual | `CorrelationIdTest.fromRequestIdOrElse_shouldKeepRequestId_whenItHas64AllowedCharacters` |
| | uno más | `"a".repeat(65)` | id del evento | `CorrelationIdTest.fromRequestIdOrElse_shouldUseFallback_whenRequestIdIsInvalid` (fila 65) |
| | vacío | `""` | id del evento | misma prueba (fila vacío) |
| | espacio interno | `"req 1"` | id del evento | misma prueba (fila espacio) |
| | hostil | `"<script>alert(1)</script>"` | id del evento | misma prueba (fila script); Postman `E-10` |
| | salto de línea | `"abc\ndef"` | id del evento | misma prueba (fila salto) |
| | ñ o tilde | `"petición-1"` | id del evento | misma prueba (fila tilde) |

Los campos de la carga salen de datos ya validados por HU-2.3, 2.4, 2.5 y 2.11; no se vuelven a validar al publicar. Ningún 422 nuevo.

## 8. Base de datos (R4)

- Sin migración: la tabla `evento_saliente` (V9) ya admite el tipo `perfil.profesional-actualizado` (`ck_evento_saliente_tipo`), la
  versión 1, la correlación de hasta 64 caracteres `[A-Za-z0-9._-]` y la carga `JSONB` sin límite de tamaño [H] `V9__evento_saliente.sql`.
  Tamaño máximo de una carga: ≈ 13 kB (2000 + 4 × 700 + 5 × 300 + 20 × 60 + 5 × 255 + 255 caracteres, hasta 4 bytes cada uno con emoji).
- Coherencia de largos: la base (`ColumnLengthConsistencyIT`), el dominio y el esquema del evento (`ProfileUpdatedEventContractTest`,
  §11) usan las mismas constantes.
- La base rechaza un resumen de 2001: **`ColumnLengthConsistencyIT.summaryColumn_shouldReject2001Characters_whenWritten`** (SQLState
  `22001`).
- Ninguna restricción de `evento_saliente` es alcanzable desde una petición (los valores los arma el servidor): una violación sería un
  defecto y terminaría en 500 con la restricción en el registro; ya probado por `OutboxRepositoryAdapterIT.constraint_shouldReject_whenRowBreaksIt`.

## 9. Casos borde (R5), con datos literales

| Grupo | Caso | Dato | Esperado | Prueba |
|---|---|---|---|---|
| Presencia | Perfil `COMPLETED` sin experiencia ni descripción ni fechas | `experiencias` `[]`; formación con `fin` `null` | `experiencias: []`, `fin: null`, campos presentes | `ProfileUpdatedPayloadV1Test.from_shouldWriteNulls_whenOptionalValuesAreMissing` |
| Texto | Límites exactos | nombre 255 `a`, resumen 2000 `😀`, habilidad 60 `a`, descripción 500 `a` | Carga con el texto completo, sin cortar | `ProfileUpdatedPayloadV1Test.from_shouldKeepTextLiteral_whenValuesAreAtTheirLimits` |
| Texto | Hostil y Unicode | resumen `<script>alert(1)</script> "comillas" ñandú 😀 é` | Igual al leer el JSON | misma prueba (segunda carga) |
| Fechas | Instante con microsegundos | reloj `2026-10-15T12:00:00.123456Z` | `actualizadoEn` `2026-10-15T12:00:00.123Z` | `ProfileEventsAppServiceTest.announceIfCompleted_shouldTruncateToMilliseconds_whenClockHasMicros` |
| Fechas | Instante en milisegundo cero | reloj `2026-10-15T12:00:00Z` | `2026-10-15T12:00:00.000Z` | `ProfileUpdatedPayloadV1Test.from_shouldFormatInstantWithMillis_whenMillisAreZero` |
| Fechas | Mes de año | `YearMonth.of(2021, 12)` | `"2021-12"` | `…from_shouldKeepTextLiteral_whenValuesAreAtTheirLimits` |
| Enumerados | Cada nivel y estado | `BASIC`, `INTERMEDIATE`, `ADVANCED`; `CURRENT`, `UNKNOWN_END`, `ENDED`; `TECHNICAL`, `UNDERGRADUATE`, `POSTGRADUATE` | El nombre del enumerado | `ProfileUpdatedEventContractTest.schema_shouldListEveryDomainValue_whenEnumsAreCompared` |
| Duplicados | La misma escritura dos veces | `PATCH` con el mismo nombre dos veces sobre un Activo | Dos eventos con `actualizadoEn` creciente; la copia de Entrevista queda igual (idempotente) | `ProfileEventsEndToEndIT.updateProfileInfo_shouldPublishTwoEvents_whenSameChangeIsSentTwice` |
| Estado | Finalizar un Activo | 20 `POST …/completion` | Un 200, diecinueve 409, **una** fila en `evento_saliente` | `ProfileEventsEndToEndIT.completeProfile_shouldPublishOnlyOnce_whenRepeatedTwentyTimes` |
| Estado | Borrador | Agregar habilidad a un `IN_PROGRESS` | Ninguna fila | `ProfileEventsEndToEndIT.addSkill_shouldPublishNothing_whenProfileIsDraft` |
| Estado | `IN_REVIEW` | Perfil reconstituido en `IN_REVIEW` | Ninguna fila | `ProfileEventsAppServiceTest.announceIfCompleted_shouldDoNothing_whenProfileIsNotCompleted` (parametrizada: `PENDING`, `IN_PROGRESS`, `IN_REVIEW`) |
| Propiedad | Perfil ajeno | Usuario B agrega habilidad al Activo de A | 403 y ninguna fila | `ProfileEventsEndToEndIT.addSkill_shouldPublishNothing_whenProfileBelongsToAnotherUser` |
| Propiedad | `usuarioId` | Siempre el dueño guardado, nunca el de la petición | `usuarioId` = `firebaseUid` del perfil | `ProfileUpdatedPayloadV1Test.from_shouldTakeOwnerFromProfile_whenBuilt` |
| Colecciones | Máximos | 4 experiencias, 5 formaciones, 20 habilidades, 5 roles | Todas en la carga, en orden | `ProfileUpdatedPayloadV1Test.from_shouldKeepEveryItemInOrder_whenCollectionsAreFull` |
| Concurrencia | CA-2.5.22 | finalizar y eliminar la única habilidad a la vez | Exactamente una de: (200 + 409, una fila con 1 habilidad) o (204 + 422, ninguna fila) | `ProfileEventsEndToEndIT.completeAndRemoveSkill_shouldPublishOneEventWithSkills_whenBothRunAtOnce` |
| Falla parcial | Falla de la base al finalizar | disparador `RAISE EXCEPTION` sobre `estado` | 500, `IN_PROGRESS`, ninguna fila ni mensaje | `ProfileEventsEndToEndIT.completeProfile_shouldPublishNothing_whenDatabaseFailsDuringCompletion` |
| Falla parcial | Falla al guardar el evento | `OutboxRepository.appendProfileUpdated` lanza `RuntimeException` | La escritura se revierte (misma transacción), 500 genérico | `ProfileEventsAppServiceTest.announceIfCompleted_shouldPropagate_whenOutboxFails` |
| Dependencias | Broker sin el exchange (equivale a broker caído) | `rabbitAdmin.deleteExchange("perfil.events")` antes de finalizar | 200; la fila queda pendiente con `intentos` 1; al volver el exchange, `relayPending()` publica **el mismo `message_id`** | `ProfileEventsEndToEndIT.completeProfile_shouldKeepEventPending_whenBrokerRejectsAndRelayLater` |
| Dependencias | Plazo de confirmación | confirmación a los 600 ms | `false` en ≤ 550 ms | Ya existe: `RabbitEventPublisherTest.publish_shouldReturnFalse_whenConfirmationTakesLongerThanLimit` |
| Dependencias | Sin cola de destino (D-16 = B) | nadie ligado a la clave | Devuelto: `false`, fila pendiente | Ya existe: `RabbitEventPublisherTest` (caso devuelto) |
| Carga | Campos extra en la petición | `{"skillName":"Go","level":"BASIC","usuarioId":"otro","embedding":[1]}` | 201; la carga no lleva ni `embedding` ni otro `usuarioId` | Postman `E-10` (cuerpo con extras) |
| Números, paginación | — | — | No aplican: la carga no tiene números ni paginación | — |

## 10. Errores (R3)

El cambio no agrega códigos ni respuestas HTTP: `docs/errores.md` no suma filas. Rutas que podrían llegar al 500 genérico, revisadas una por una:

| Ruta | ¿Previsible? | Resultado | Prueba |
|---|---|---|---|
| Falla de la base al guardar el evento (base caída a mitad) | Sí, pero la causa es la base, no el evento | 500 `INTERNAL_ERROR` con rollback completo hoy; pasará a 503 `DATABASE_UNAVAILABLE` con CM-290 (decisión D-4), que cubre toda escritura | `ProfileEventsAppServiceTest.announceIfCompleted_shouldPropagate_whenOutboxFails` |
| Falla al serializar la carga | No: registros de texto ya validados; Jackson no falla con ellos | 500 genérico con rollback | Cubierta por la misma prueba (cualquier `RuntimeException` del adaptador) |
| Llamar `appendProfileUpdated` sin transacción | No: error de programación | `IllegalTransactionStateException` | `OutboxRepositoryAdapterIT.appendProfileUpdated_shouldFail_whenNoTransactionIsActive` |
| Broker caído, lento, `nack` o mensaje devuelto | Sí | Nunca llega a la respuesta: `WARN` con `eventId` y `type`; fila pendiente | Ya existen: `OutboxRelayAppServiceTest.relayAfterCommit_shouldKeepRowPending_whenBrokerFails` y `RabbitEventPublisherTest` |

Comprobación con petición real: Newman de la colección completa contra la aplicación empaquetada (ninguna respuesta 500 salvo las
esperadas de CA-2.5.14, que no está en Newman) y la carpeta `HT-03 — Evento del perfil (local)`.

## 11. Pruebas por capa (R9)

| Capa | Clase | Pruebas nuevas (nombre exacto) |
|---|---|---|
| Dominio | `CorrelationIdTest` | `fromRequestIdOrElse_shouldKeepRequestId_whenItHas64AllowedCharacters`; `fromRequestIdOrElse_shouldUseFallback_whenRequestIdIsInvalid` (parametrizada: `null`, `""`, 65 `a`, `"req 1"`, `"<script>alert(1)</script>"`, `"abc\ndef"`, `"petición-1"`); `constructor_shouldReject_whenValueIsInvalid` |
| Dominio | `ProfileUpdatedTest` | `constructor_shouldRejectNulls_whenAnyComponentIsMissing` (parametrizada por componente); `constants_shouldMatchTheContract` (`TYPE` = `perfil.profesional-actualizado`, `VERSION` = 1) |
| Aplicación | `ProfileEventsAppServiceTest` | `announceIfCompleted_shouldAppendAndRelay_whenProfileIsCompleted`; `announceIfCompleted_shouldDoNothing_whenProfileIsNotCompleted` (parametrizada); `announceIfCompleted_shouldUseRequestId_whenItIsValid`; `announceIfCompleted_shouldUseEventId_whenRequestIdIsMissing`; `announceIfCompleted_shouldTruncateToMilliseconds_whenClockHasMicros`; `announceIfCompleted_shouldPropagate_whenOutboxFails` |
| Aplicación | `ProfileAppServiceTest` | `writes_shouldAnnounceAfterSave_whenWriteSucceeds` (parametrizada, 11 casos); `updateSalaryExpectation_shouldNotAnnounce_whenSaved`; `writes_shouldNotAnnounce_whenWriteIsRejected` (parametrizada: `addSkill` sin nombre, `completeProfile` de un perfil nuevo, perfil ajeno) |
| Infraestructura | `ProfileUpdatedPayloadV1Test` | `from_shouldTakeOwnerFromProfile_whenBuilt`; `from_shouldWriteNulls_whenOptionalValuesAreMissing`; `from_shouldKeepTextLiteral_whenValuesAreAtTheirLimits`; `from_shouldKeepEveryItemInOrder_whenCollectionsAreFull`; `from_shouldFormatInstantWithMillis_whenMillisAreZero` |
| Infraestructura | `ProfileUpdatedEventContractTest` | `serializedPayload_shouldHaveExactlyTheSchemaProperties_whenProfileIsCompleted`; `serializedPayload_shouldNotContainEmbedding_whenSerialized`; `schema_shouldMatchDomainLimits_whenLengthsAndItemsAreCompared`; `schema_shouldListEveryDomainValue_whenEnumsAreCompared`; `schemaExample_shouldDeserialize_whenReadWithPayloadRecord` |
| Infraestructura | `HttpRequestCorrelationTest` | `currentRequestId_shouldReturnHeader_whenRequestHasIt`; `currentRequestId_shouldBeEmpty_whenHeaderIsMissing`; `currentRequestId_shouldBeEmpty_whenNoRequestIsBound` |
| Persistencia (Testcontainers) | `OutboxRepositoryAdapterIT` | `appendProfileUpdated_shouldStorePendingRowWithContractPayload_whenCalledInTransaction`; `appendProfileUpdated_shouldFail_whenNoTransactionIsActive` |
| Persistencia | `ColumnLengthConsistencyIT` | `summaryColumn_shouldReject2001Characters_whenWritten` |
| Integración (PostgreSQL y RabbitMQ reales) | `ProfileEventsEndToEndIT` (extiende `MessagingIntegrationSupport`) | `topology_shouldRouteToInterviewQueue_whenServiceStarts` (D-16 = A); `completeProfile_shouldPublishSchemaPayload_whenProfileIsActivated`; `completeProfile_shouldPublishNothing_whenRequirementsAreMissing`; `completeProfile_shouldPublishOnlyOnce_whenRepeatedTwentyTimes`; `completeProfile_shouldPublishNothing_whenDatabaseFailsDuringCompletion`; `addSkill_shouldPublishOneEvent_whenProfileIsCompleted`; `addSkill_shouldPublishNothing_whenProfileIsDraft`; `addSkill_shouldPublishNothing_whenProfileBelongsToAnotherUser`; `updateProfileInfo_shouldPublishTwoEvents_whenSameChangeIsSentTwice`; `completeAndRemoveSkill_shouldPublishOneEventWithSkills_whenBothRunAtOnce`; `completeProfile_shouldKeepEventPending_whenBrokerRejectsAndRelayLater` |
| Arquitectura | `ArquitecturaTest` | Sin cambios; debe seguir en verde con las clases nuevas |

Toda prueba nueva tiene aserciones concretas y debe fallar antes del cambio (ensayo en seco en `tasks.md`).

## 12. Seguridad (R6)

### 12.1 ASVS 5.0.0 nivel 1 (matriz `cameia-infra/docs/seguridad/matriz-asvs-nivel1.md`, solo lectura)

| ID | ¿Aplica? | Cómo lo cumple el cambio | Prueba o petición |
|---|---|---|---|
| 1.2.3 Salida JSON sin armar a mano | Sí | La carga la serializa Jackson desde `ProfileUpdatedPayloadV1`; ningún `StringBuilder` de JSON | `ProfileUpdatedEventContractTest.serializedPayload_shouldHaveExactlyTheSchemaProperties_whenProfileIsCompleted`; `ProfileUpdatedPayloadV1Test.from_shouldKeepTextLiteral_whenValuesAreAtTheirLimits` (texto hostil) |
| 1.2.4 Consultas parametrizadas | Sí | `appendProfileUpdated` usa el `insert` nativo ya existente, con parámetros enlazados | `OutboxRepositoryAdapterIT.appendProfileUpdated_shouldStorePendingRowWithContractPayload_whenCalledInTransaction`. Hallazgo para DevOps: la evidencia de la matriz dice «`nativeQuery = true`: cero resultados» y Perfil ya tiene SQL nativo parametrizado desde el PR #94 (A-5) |
| 2.2.1 Entrada contra lista permitida | Sí (`X-Request-Id`) | Patrón `^[A-Za-z0-9._-]{1,64}$`; lo demás se descarta | `CorrelationIdTest.fromRequestIdOrElse_shouldUseFallback_whenRequestIdIsInvalid`; Postman `E-10` |
| 8.2.2 Acceso a datos por objeto | Sí | Solo una escritura del dueño (comprobado en `loadForUserForUpdate`) genera evento; `usuarioId` sale del perfil guardado | `ProfileEventsEndToEndIT.addSkill_shouldPublishNothing_whenProfileBelongsToAnotherUser`; `ProfileUpdatedPayloadV1Test.from_shouldTakeOwnerFromProfile_whenBuilt` |
| 12.2.1 TLS en conexiones externas | Sí | AMQPS en staging con `SPRING_RABBITMQ_SSL_ENABLED=true` (configuración de DevOps, ya en uso por los consumidores de Perfil); local sin TLS | Configuración de Cloud Run (DevOps); sin prueba automática |
| 15.3.1 Solo los campos necesarios | Sí | La carga lleva lo de §5.2: sin salario, procedencia, modalidad ni ids internos de experiencias, formaciones y habilidades | `ProfileUpdatedEventContractTest.serializedPayload_shouldHaveExactlyTheSchemaProperties_whenProfileIsCompleted` |
| 1.2.1, 1.2.2, 1.2.5, 1.3.x, 1.5.1, 4.1.1, 6.x, 7.x, 9.x, 11.x, 14.3.1 | No | Sin HTML, URL, comandos, XML, respuestas nuevas, contraseñas, sesiones, tokens ni cifrado propio | — |

### 12.2 OWASP API Security Top 10 (el evento no es un endpoint; se revisa el flujo de las once escrituras)

| Riesgo | Revisión | Prueba |
|---|---|---|
| API1 BOLA | Una escritura sobre un perfil ajeno responde 403 antes de guardar: no hay evento | `ProfileEventsEndToEndIT.addSkill_shouldPublishNothing_whenProfileBelongsToAnotherUser` |
| API2 Autenticación | Identidad solo de `X-User-Id`; el cuerpo no puede cambiar `usuarioId` | Postman `E-10` (cuerpo con `usuarioId` extra) |
| API3 Propiedades | Carga por DTO explícito con `additionalProperties: false` | `ProfileUpdatedEventContractTest` |
| API4 Recursos | Carga acotada por los máximos del dominio (≈ 13 kB); un intento tras el commit, en un ejecutor de 2 hilos con cola de 100 y confirmación de 500 ms; lotes de 50 en el Job | `ProfileUpdatedPayloadV1Test.from_shouldKeepEveryItemInOrder_whenCollectionsAreFull`; `RabbitEventPublisherTest` |
| API5 Función | Sin operaciones nuevas | — |
| API6 Flujos sensibles | Finalizar repetido no duplica eventos | `ProfileEventsEndToEndIT.completeProfile_shouldPublishOnlyOnce_whenRepeatedTwentyTimes` |
| API7 SSRF | Ningún destino sale de la entrada | — |
| API8 Configuración | AMQPS por variable; la consola de administración del broker no la expone Perfil | — |
| API9 Inventario | Contrato versionado en `docs/eventos/` | `ProfileUpdatedEventContractTest` |
| API10 Consumo inseguro | Lo valida el consumidor (CM-209, Sprint 4) contra este esquema | Dependencia §14 |

SEG-02 (pruebas negativas entre dos usuarios): `addSkill_shouldPublishNothing_whenProfileBelongsToAnotherUser` y la petición `E-10` con `usuarioId` en el cuerpo.

## 13. Atributos de calidad (R13)

HE-02 declara AC-0001, AC-0003, AC-0006 y MAN-02 (anexo, cuadro 9); HT-03 no tiene épica. Se suman las que el cambio toca. «Resultado
real» lo llenan la ejecución y la verificación.

| Atributo | Métrica | Límite | Cómo lo cumple | Prueba o comando | Resultado real |
|---|---|---|---|---|---|
| AC-0001 | IA-01, REST-0004 | 100 % | No aplica: el evento no lleva salidas de IA (`embedding` fuera, H2) | `serializedPayload_shouldNotContainEmbedding_whenSerialized` | PENDIENTE |
| AC-0002 | DES-02 | p95 ≤ 2 s, n = 100 | El intento posterior al commit corre en un hilo aparte con confirmación acotada a 500 ms, así que la petición no espera al broker ni a una segunda conexión (medido con el pool lleno en `ProfileEventsSmallPoolIT`: ninguna escritura llega a 1,5 s); serializar ≈ 13 kB cuesta milisegundos | Carpeta `DES-02 — HU-2.5 finalización`, `-n 105`, script del README de Perfil | PENDIENTE |
| AC-0002 | DES-04 | 5xx propios < 1 % | Ningún fallo del broker llega a la respuesta | Newman completo: 0 respuestas 5xx inesperadas | PENDIENTE |
| AC-0003 | SEG-01 | ASVS N1 aplicable 100 % | §12.1 | Las de §12.1 | PENDIENTE |
| AC-0003 | SEG-02 | 100 % de accesos cruzados rechazados | §12.2 | `addSkill_shouldPublishNothing_whenProfileBelongsToAnotherUser` | PENDIENTE |
| AC-0003 | SEG-03 | Cero dependencias nuevas | Sin cambios en `pom.xml` | `git diff origin/develop -- pom.xml` vacío | PENDIENTE |
| AC-0003 | SEG-04 | Cero datos personales en registros y trazas | REQ-EV-10; la carga sí lleva datos del perfil porque es la copia que HT-03 exige (REST-0003) | `docker compose logs app` filtrado por el resumen y el nombre de la carpeta `E-*` → 0 coincidencias | PENDIENTE |
| AC-0004 | FIA-01 | Todo fallo conserva el último estado confirmado | Evento y cambio en la misma transacción; broker caído no revierte la escritura | `completeProfile_shouldPublishNothing_whenDatabaseFailsDuringCompletion`; `completeProfile_shouldKeepEventPending_whenBrokerRejectsAndRelayLater` | PENDIENTE |
| AC-0004 | FIA-02 | Cero efectos adicionales en 20 repeticiones | 20 finalizaciones → una fila | `completeProfile_shouldPublishOnlyOnce_whenRepeatedTwentyTimes` | PENDIENTE |
| AC-0004 | FIA-05 | Recuperación sin duplicados | Republicación con el mismo `message_id` | `completeProfile_shouldKeepEventPending_whenBrokerRejectsAndRelayLater` | PENDIENTE |
| AC-0006 | INT (parte de Backend) | El cliente muestra estado y acción | El 422 de finalización conserva la forma que lee `cameia-web` (sin cambios en esta tarea) | `ProfileControllerTest.postCompletion_shouldListFiveRequirementsInOrder_whenProfileIsNew` (regresión) | PENDIENTE |
| Control | MAN-01 | ≥ 90 % líneas y ramas en lo tocado | §18 | `target/site/jacoco/jacoco.csv` | PENDIENTE |
| Control | MAN-02 | Adaptador con éxito, formato inválido, tiempo y cuota | El adaptador del broker no cambia: sus cuatro casos ya existen; cuota no aplica | `RabbitEventPublisherTest` (regresión) | PENDIENTE |
| Control | MAN-03 | Cero FK entre bases | La copia viaja por evento; ninguna FK | Revisión de V9 (sin FK) | PENDIENTE |
| Control | IOP-01 | Contrato versionado | JSON Schema v1 y prueba | `ProfileUpdatedEventContractTest` | PENDIENTE |
| Control | IOP-02 | Integración en un adaptador | Broker solo en `RabbitEventPublisher` y `RabbitConfig` | `ArquitecturaTest` | PENDIENTE |
| Control | IOP-03 | Topología del MVP | `perfil.events`, clave y cola de HT-03 | `topology_shouldRouteToInterviewQueue_whenServiceStarts` | PENDIENTE |
| Control | IOP-04 | Outbox en el productor | 100 % de las once escrituras pasan por `saveAndAnnounce` | `writes_shouldAnnounceAfterSave_whenWriteSucceeds` | PENDIENTE |
| Restricción | REST-0002 | Sin estado en memoria; esperas acotadas | La correlación se lee de la petición en curso; 500 ms de espera | `HttpRequestCorrelationTest`; `RabbitEventPublisherTest` | PENDIENTE |
| Restricción | REST-0003 | Datos mínimos | Solo lo de §5.2; la carga se borra al publicarse (ya en `develop`) | `OutboxRepositoryAdapterIT.markPublished_shouldClearPayload_whenConfirmed` (regresión) | PENDIENTE |

## 14. Dependencias

| Con | Qué | Cómo se prueba mientras no exista |
|---|---|---|
| Entrevista (CM-209, Sprint 4) | Consume la cola; debe declararla con **los mismos argumentos** de REQ-EV-07 (si D-16 = A), validar la carga contra el esquema, traducir `COMPLETED` → `COMPLETE` y usar `rolesAspirados[].codigo` como id del catálogo de Perfil | El IT lee la cola con `RabbitTemplate.receive`; Postman con la API de administración de RabbitMQ local |
| DevOps | Job `profile-events-relay` cada 5 minutos en staging (H8) | Local: `java -jar … --spring.profiles.active=profile-events-relay` (ya probado en el PR #94); sin el Job, una fila que falle tras el commit queda pendiente |
| Vela (backlog) | Nota de HT-03: quitar `embedding` de la carga y agregar HU-2.5; texto técnico de CA-2.5.18 | No bloquea el código |

## 15. Fuera de alcance (con destino)

| Qué | Destino |
|---|---|
| Publicar al archivar o reactivar | HU-2.14 (CM-301, sin sprint): reutilizará `ProfileEventsAppService` y ya tiene `estado` y `fecha_archivado` en el contrato |
| Reemplazar `Instant.now()` por el reloj inyectado en `ProfessionalProfile.touch()` | CM-296 tanda 2 (alineación del estándar), con su prueba; esta tarea no usa `updatedAt` |
| `TODO CM-XXX` y nombres en español en `RabbitConfig` (colas de suscripción y consumo) | CM-296; los alinea la tarea de cada consumidor |
| 503 `DATABASE_UNAVAILABLE` cuando la base cae al guardar | CM-290 (D-4) |
| Declarar la cola de fallidos de Entrevista y `entrevista.dlx` | CM-209 |

## 16. Hallazgos y acciones (cada uno con destino)

| # | Hallazgo | Tipo | Destino |
|---|---|---|---|
| A-1 | CM-67 está «En revisión» sin el evento, que la decisión D7 del 9-oct le asignó | Decisión de Paula | D-18 |
| A-2 | HT-03 sigue listando `embedding` en la carga y no menciona HU-2.5 | Acción de Vela | Fila abierta del seguimiento (línea 33); se agrega `embedding` al mismo pedido |
| A-3 | El «Dado» de CA-2.5.18 es imposible (H9): Testing no puede reproducirlo en staging | Cambio puntual del backlog con aviso (precedente del 13-oct) | Ajustar la nota técnica de CA-2.5.18 (hoja HE-02, columna «Técnico») y avisar a Vela con Testing y Frontend en copia; PENDIENTE del visto bueno de Paula al texto |
| A-4 | El Job `profile-events-relay` no está pedido en ninguna tarea | Acción de DevOps por una tarea que abre Vela | Fila del 9-oct del seguimiento (línea 67) |
| A-5 | La matriz ASVS dice que ningún repo usa SQL nativo; Perfil lo usa (parametrizado) desde el PR #94 | Acción de DevOps por Vela (actualizar evidencia de 1.2.4) | Mismo documento a DevOps |
| A-6 | (Paula, 13-oct-2026: se corrige en este PR) `CLAUDE.md` de Perfil nombra `ProfileEventPublisher`, que ya no existe; dice que el publicador es un esqueleto (§2 y §10); lista la ruta `review-requests`, borrada en el PR #94 (§4); su tabla de migraciones termina en V5_1 (faltan V6 a V9) | Corrección de documentación | Esta tarea (T-8) |
| A-10 | `CLAUDE.md` de Perfil §9 dice «no pasa de 1000 líneas entre agregadas y eliminadas»; la regla vigente de Paula (10-oct) es un PR por CM sin tope, y el validador `pr_policy.py` no impone ninguno. Este PR estima ≈ 1 100 líneas | Cambio sin alternativa (la decisión ya existe; el documento del repo se corrige en un PR) | Esta tarea (T-8): la línea pasa a «Un PR por tarea de Jira, sin tope de líneas, con un commit por capacidad» |
| A-7 | `docs/errores.md` cita el backlog `09102026_01_Backlog_v6.xlsx`; el vigente es la v7 | Corrección de documentación (hallazgo C-8 del 11-oct) | Esta tarea (T-8) |
| A-8 | La entrada PP-013 de la bitácora (decisión D5 de CM-54) debía ir «en el siguiente PR de Perfil» (D-9) | Acción de Backend | Esta tarea (T-8) |
| A-9 | El título de CM-209 en Jira aún es el de la llamada síncrona | Ya pedido a Vela el 13-oct (plazo 16-oct) | Sin acción nueva |

## 17. Decisiones

| # | Decisión | Porqué | Alternativas | Estado |
|---|---|---|---|---|
| D1 | Sin `embedding` en la carga | H2 | Calcularlo en Perfil (opción A del 9-oct) | [D] Vela «OK» a la opción B, 13-oct |
| D2 | Composición de la carga de §5.2 | H3 | Solo el mínimo de HT-03 | [D] Paula, 9-oct (Q-67-2 a) |
| D3 | Las habilidades también publican | H4 | Publicar solo lo que lista HT-03 | [D] Paula, 9-oct |
| D4 | Correlación por un puerto que lee la petición en curso | Regla de ≤ 3 parámetros; once firmas intactas | Pasar el `X-Request-Id` en cada comando | [I] técnica; se aprueba con la spec |
| D5 | Serializar en el adaptador de la tabla de salida | Patrón de Cuentas (H15); `application` sin Jackson | Serializar en el servicio | [I] técnica; se aprueba con la spec |
| D6 | `estado` con los cuatro valores del dominio; opcionales como `null` | Evita una v2 con HU-2.14; el consumidor no depende del serializador | Solo `COMPLETED`; omitir nulos | [I] técnica; se aprueba con la spec |
| **D-16** | **¿Quién declara la cola de Entrevista mientras Entrevista no existe?** | H7: hoy cada evento se devuelve y queda pendiente | **A (recomendada):** Perfil la declara (REQ-EV-07). Los mensajes esperan en la cola hasta el Sprint 4, se ven en la consola de CloudAMQP (prueba en staging sin acceso a la base) y no hay alertas falsas. Consecuencia: CM-209 debe declararla con argumentos idénticos o el broker la rechaza (`PRECONDITION_FAILED`); queda escrito en el contrato. **B:** la declara solo Entrevista. Cada evento queda pendiente en la tabla; sin el Job (H8) no se reintenta nunca; con el Job, a los 10 intentos (≈ 50 min) cada evento escribe un `ERROR`; en staging nadie puede comprobar la publicación porque no hay acceso a la base | [D] Paula, 13-oct-2026: A (Perfil crea la cola y Entrevista la escucha) |
| **D-17** | **¿Cómo se prueba CA-2.5.18 de punta a punta?** | H9 | **A (recomendada):** prueba propia que amplía la columna a 2001 solo en su contenedor, inserta el dato y llama a la API real (recorre base → lectura → finalización), más la prueba de que la base real rechaza 2001, más aviso a Vela y Testing (A-3). **B:** solo la prueba de rechazo de la base y la de dominio que ya existe; el CA se declara no alcanzable. Consecuencia de B: no se prueba la lectura del resumen guardado, que fue la causa del defecto D2 de la finalización | [D] Paula, 13-oct-2026: B (basta que la base rechace 2001 y la prueba de dominio; se propone ajustar la nota del CA en el backlog) |
| **D-18** | **Estado de CM-67 en Jira durante la ejecución** | A-1 | **A (recomendada):** pasa a «En curso» al empezar (regla: «En revisión» solo con el DoD cumplido), con un comentario para el tester: lo ya entregado de la finalización no cambia y se puede seguir probando. **B:** se queda en «En revisión» con un comentario; el tester podría cerrarla sin el evento | [D] Paula, 13-oct-2026: «todavía no» se cambia; sigue «En revisión» y se vuelve a preguntar al empezar la ejecución (T-0) |
| **D-19** | **Carpeta nueva `infrastructure/web` para `HttpRequestCorrelation`** (el `CLAUDE.md` del repo pide preguntar) | D4 | **A (recomendada):** crearla, con `package-info.java`: el adaptador lee la petición HTTP en curso y no es configuración, persistencia ni mensajería. **B:** ponerlo en `infrastructure/config`, sin carpeta nueva; mezcla un adaptador con la configuración de Spring | [D] Paula, 13-oct-2026: A |

## 18. Cobertura (R10) y verificación

- Clases a medir (≥ 90 % de líneas y de ramas cada una): `CorrelationId`, `ProfileUpdated`, `ProfileEventsAppService`, `ProfileAppService`,
  `ProfileUpdatedPayloadV1` y sus registros anidados, `OutboxRepositoryAdapter`, `HttpRequestCorrelation`, `RabbitConfig`.
- Comando: `./mvnw.cmd -B clean verify`; números con
  `python -I -c "import csv;[print(r['CLASS'],r['LINE_MISSED'],r['LINE_COVERED'],r['BRANCH_MISSED'],r['BRANCH_COVERED']) for r in csv.DictReader(open('target/site/jacoco/jacoco.csv',encoding='utf-8')) if r['CLASS'].split('.')[0] in ('CorrelationId','ProfileUpdated','ProfileEventsAppService','ProfileAppService','ProfileUpdatedPayloadV1','OutboxRepositoryAdapter','HttpRequestCorrelation','RabbitConfig')]"`.
- La cobertura global no baja de 97,4 % de líneas y 98,3 % de ramas (H12).
- Newman: `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json -e docs/perfil-local.postman_environment.json --reporters cli,junit --reporter-junit-export target/newman/perfil.xml`
  contra `docker compose up -d --build` (aplicación empaquetada, PostgreSQL y RabbitMQ reales).

## 19. Integración con las otras tareas de Perfil

| Pieza | Esta tarea | Después |
|---|---|---|
| Migraciones | Ninguna (la última sigue siendo V9) | CM-296 (restricción `CHECK` de `perfil_profesional`) toma V10 |
| `ErrorCode`, `docs/errores.md` | Sin códigos; solo la cita del backlog (A-7) | CM-290 agrega `DATABASE_UNAVAILABLE`; CM-296 alinea el resto |
| Colección de Postman | Carpeta nueva `HT-03 — Evento del perfil (local)`, al final | Las demás tareas agregan sus carpetas al final |
| `RabbitConfig` | Cola de Entrevista (D-16 = A) | CM-296 revisa los `TODO` |
| Orden | Primera de Perfil en el Sprint 3 (`ESTADO.md`, «Qué sigue», orden 1) | CM-296 tanda 1 y CM-290 salen de `develop` después de fusionar esta |

Un PR, sin tope de líneas, con un commit por capacidad en el orden de `tasks.md`. Estimación honesta: ≈ 1 100 líneas (≈ 450 de producción y
documentación del contrato, ≈ 650 de pruebas y Postman), unas 7 h de ejecución y verificación.

## 20. Línea base

`git rev-parse origin/develop^{tree}` debe dar `1699baf6d979d27ffe292a8e3ec3c096c41b5614` (`504f838`). La línea base verificada el 11-oct (H12) era `9355f1a`, árbol `5045085d63f3d269de1322ec8da6236223f49ab0`; `504f838` solo suma un archivo de CI, así que el
resultado se hereda sin volver a correr. Si da otro valor, antes de tocar nada: `./mvnw.cmd -B clean verify` (JDK 21, Docker en marcha
para Testcontainers) y Newman de la colección completa con `docker compose up -d --build`; si algo falla, se detiene y se reporta.

## 21. Lista de verificación de la CM

El ejecutor la corre antes de entregar; el verificador la repite como piso y busca lo que no prevé.

| # | Qué | Comando, prueba o petición | Esperado |
|---|---|---|---|
| V-01 | Los 23 CA de HU-2.5 (regresión) | `./mvnw.cmd -B clean verify` | 0 fallos; las pruebas de la tabla §3 presentes y en verde |
| V-02 | CA-2.5.18 (regla de dominio) | `ProfessionalProfileTest.complete_shouldListSummary_whenStoredSummaryHas2001CodePoints` | `SUMMARY` en la lista y el perfil en Borrador |
| V-03 | La base rechaza 2001 | `ColumnLengthConsistencyIT.summaryColumn_shouldReject2001Characters_whenWritten` | SQLState `22001` |
| V-04 | Carga = esquema | `ProfileUpdatedEventContractTest` | 5 en verde; sin `embedding` |
| V-05 | Once escrituras publican; salario no | `ProfileAppServiceTest.writes_shouldAnnounceAfterSave_whenWriteSucceeds` y `updateSalaryExpectation_shouldNotAnnounce_whenSaved` | 11 + 1 en verde |
| V-06 | Rechazo y rollback no publican | `writes_shouldNotAnnounce_whenWriteIsRejected`; `completeProfile_shouldPublishNothing_whenDatabaseFailsDuringCompletion` | Ninguna fila ni mensaje |
| V-07 | Broker caído y recuperación (FIA-01, FIA-05) | `completeProfile_shouldKeepEventPending_whenBrokerRejectsAndRelayLater` | 200; fila pendiente; mismo `message_id` |
| V-08 | Repetición (FIA-02) | `completeProfile_shouldPublishOnlyOnce_whenRepeatedTwentyTimes` | 1 fila |
| V-09 | Concurrencia (CA-2.5.22) | `completeAndRemoveSkill_shouldPublishOneEventWithSkills_whenBothRunAtOnce` (10 repeticiones con `@RepeatedTest(10)`) | Nunca una carga sin habilidades |
| V-10 | Recurso ajeno (SEG-02) | `addSkill_shouldPublishNothing_whenProfileBelongsToAnotherUser` | 403 y nada publicado |
| V-11 | Correlación hostil | `CorrelationIdTest`; Postman `E-10`/`E-11` | `correlation_id` = `message_id` |
| V-12 | Topología (D-16 = A) | `topology_shouldRouteToInterviewQueue_whenServiceStarts`; Postman `E-01` | Cola con `entrevista.dlx` y enlace |
| V-13 | Arquitectura | `ArquitecturaTest` | Verde |
| V-14 | Cobertura | §18 | ≥ 90 % por clase; global ≥ 97,4 / 98,3 |
| V-15 | Newman completo y carpeta nueva | §18 | 0 fallos; la carpeta `HT-03 — Evento del perfil (local)` con sus `pm.test` en verde |
| V-16 | DES-02 | `--folder "DES-02 — HU-2.5 finalización" -n 105` y el script del README | p95 ≤ 2000 ms por petición |
| V-17 | Registros sin datos (SEG-04) | `docker compose logs app \| Select-String -Pattern 'Analista con tres|Universidad del Cauca|<script>'` tras la carpeta | 0 coincidencias |
| V-18 | Sin dependencias (SEG-03) | `git diff origin/develop -- pom.xml` | Vacío |
| V-19 | Sin `CM-NNN` en comentarios nuevos | `git diff origin/develop -- src \| Select-String -Pattern '^\+.*CM-[0-9]'` | 0 coincidencias |
| V-20 | Job local | `java -jar target\cameia-perfil-0.0.1-SNAPSHOT.jar --spring.profiles.active=profile-events-relay` con compose arriba | Termina con código 0 y la línea «Relevo de eventos de Perfil terminado» |

## 22. Puerta de listo

| Condición | Estado |
|---|---|
| Tabla CA → requisito → prueba → Postman y tabla de atributos | Completas (§3, §13) |
| Validación por campo, casos borde y rutas al 500 | Completas (§7, §9, §10) |
| Plan de seguridad ASVS y OWASP | Completo (§12) |
| Contraste con el código real y ensayo en seco | Hecho (`tasks.md`, «Contraste»; `analisis/revisiones/revision-spec_CM-67.md`) |
| Integración con las otras tareas de Perfil | Hecha (§19) |
| Línea base | Heredada (§20) |
| Lista de verificación | Existe (§21) |
| Dudas | Ninguna abierta: D-16 a D-19 respondidas por Paula el 13-oct |

**Dictamen: LISTA PARA EJECUTAR.** Aprobada por Paula el 13-oct-2026. Solo Paula aprueba.
