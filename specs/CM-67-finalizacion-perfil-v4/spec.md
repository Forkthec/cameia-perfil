# Spec — CM-67: finalización del Perfil Profesional (HU-2.5)

- **Tarea:** CM-67 · subtarea «HU-2.5 – Backend: finalización del Perfil Profesional» · padre CM-19 · responsable: Paula Andrea Muñoz Delgado. Jira la muestra «En revisión» sin que los ajustes estén hechos (se corrige a «En curso» al empezar la ejecución, si es subtarea de Paula).
- **Repositorio:** `cameia-perfil`. Código de referencia: `origin/develop` `2a54ca7` (9-oct-2026). Cliente revisado (solo lectura): `cameia-web` `origin/develop`, `src/features/professional-profile/model/missingRequirements.ts` y `pages/EditProfilePage.tsx`.
- **Requisitos:** backlog `09102026_01_Backlog_v6.xlsx`, HU-2.5: CA-2.5.4 a 2.5.6, 2.5.11, 2.5.13 a 2.5.15, 2.5.18, 2.5.21, 2.5.22 (los de habilidades son de CM-66), RT-01, RT-03, RT-05.
- **Estado:** pendiente de aprobación de Paula. Puerta de listo: sección 23 (PR 1 y PR 2 listos; PR 3 bloqueado por dos respuestas). Decisiones en la sección 16; lo abierto, en la sección 17. Incluye la publicación de `perfil.profesional-actualizado` (sección 18, decisión de Paula del 9-oct-2026).
- **Depende de:** CM-274 (bloqueo `loadForUserForUpdate`), CM-54 (los objetos de valor se reconstruyen sin validar) y CM-66 (409 `SKILL_NOT_ALLOWED` al eliminar la última habilidad de un perfil Activo, que usa CA-2.5.22), todas fusionadas: es la última CM del orden de Perfil (sección 19).

## 1. Objetivo

Que `POST /api/v1/profiles/{id}/completion` pase el perfil de Borrador a Activo solo si cumple los 5 requisitos, en el orden del CA; que el 422 traiga la lista en la forma que el cliente lee (`errors[].field`) además de los códigos del CA; que un resumen guardado de más de 2000 cuente como requisito incumplido; que finalizar y eliminar la única habilidad a la vez nunca deje un perfil Activo sin habilidades; y que una falla interna no deje cambios.

## 2. Estado real del código y causa raíz

| # | Situación en `2a54ca7` | Causa comprobada | Reproducción antes | Después |
|---|---|---|---|---|
| E1 | 200 al finalizar, 409 `PROFILE_ALREADY_COMPLETED` «Este perfil ya está activo.», 422 `PROFILE_INCOMPLETE` «Todavía no cumples estos requisitos:» con `missingRequirements` `["NAME","SUMMARY","EDUCATION","SKILLS","TARGET_ROLES"]` en orden | CM-271 | Postman `ERR POST /completion — 422 con los cinco requisitos en códigos (CA-2.5.4)`, `OK POST /completion — 200`, `ERR POST /completion — 409` | Se conservan |
| D1 | **`cameia-web` no muestra la lista de requisitos:** lee `error.errors[].field` (`name`, `summary`, `education`, `skills`, `targetRoles`) y el 422 no trae `errors` | `ApiExceptionHandler.handleBusiness` solo agrega `missingRequirements` | `POST …/completion` de un perfil recién creado → sin `errors` | `errors[]` con un elemento por requisito, en orden (decisión de Paula del 6-oct-2026: las dos listas) |
| D2 | Un resumen guardado de 2001 caracteres (CA-2.5.18) responde 422 `VALIDATION_FAILED` con `errors[summary] SUMMARY_TOO_LONG`, no `PROFILE_INCOMPLETE` con `SUMMARY` | `ProfessionalProfileMapping.toSummary` llama `new ProfessionalSummary(stored)`, que valida el largo al **leer**; además `getMissingRequirements` solo mira `summary == null` | `UPDATE perfil_profesional SET resumen = repeat('a', 2001)` y `POST …/completion` → 422 `VALIDATION_FAILED` | 422 `PROFILE_INCOMPLETE` con `SUMMARY`; el `GET` del perfil responde 200 |
| D3 | Sin bloqueo: finalizar y eliminar la única habilidad a la vez pueden dejar un perfil Activo sin habilidades | `completeProfile` y `removeSkill` leen con `loadForUser` sin `FOR UPDATE` | Carrera en `ProfileWriteLockIT` | Lo resuelve CM-274 PR 1; aquí se prueba CA-2.5.22 en los dos órdenes |
| D4 | `POST …/review-requests` (fuera del OpenAPI, sin HU) pasa el perfil a `IN_REVIEW`, que la v6 declara «no alcanzable en el MVP»; desde `IN_REVIEW` la finalización lo pasa a `COMPLETED` | `ProfileController.requestReview`, `ProfessionalProfile.requestReview` | `POST …/review-requests` → 201 y `status` `IN_REVIEW` | Se borra la ruta y `requestReview` (D6); el estado `IN_REVIEW` se conserva porque HU-2.8 (fuera del MVP) lo usa al confirmar una importación |
| D5 | No hay prueba de CA-2.5.14 con una falla real | — | — | Prueba con un disparador de PostgreSQL que falla al actualizar el estado |

## 3. Trazabilidad CA → requisito → prueba → Postman

Carpeta de Postman `HU-2.5 — Finalización` / `POST profiles/{id}/completion`.

| CA | Exige | REQ | Prueba (nombre exacto) | Postman |
|---|---|---|---|---|
| 2.5.4 | 422; lista `SUMMARY`, `SKILLS`; sigue `IN_PROGRESS` | REQ-FN-02 | `CompletionEndToEndIT.completeProfile_shouldListSummaryAndSkills_whenBothAreMissing` | `422 faltan resumen y habilidades (CA-2.5.4)` (se amplía la existente con `errors[]`) |
| 2.5.5 | 200 sin experiencia ni CV | REQ-FN-01 | `ProfessionalProfileTest.complete_shouldSucceed_whenProfileHasNoWorkExperience` · `CompletionEndToEndIT.completeProfile_shouldActivate_whenNoWorkExperience` | `200 sin experiencia laboral (CA-2.5.5)` |
| 2.5.6 | 200; `IN_PROGRESS` → `COMPLETED` sin `IN_REVIEW` | REQ-FN-01 | `CompletionEndToEndIT.completeProfile_shouldReturn200AndCompleted_whenAllRequirementsAreMet` | `200 finaliza (CA-2.5.6)` |
| 2.5.11 | 409 «Este perfil ya está activo.», sin cambios | REQ-FN-04 | `ProfileControllerTest.postCompletion_shouldReturn409_whenProfileIsAlreadyActive` | `409 ya activo (CA-2.5.11)` (existe) |
| 2.5.13 | 200 con resumen de 2000 | REQ-FN-01 | `ProfessionalProfileTest.complete_shouldSucceed_whenSummaryHasExactly2000CodePoints` (incluye 2000 emojis) | `200 resumen de 2000 (CA-2.5.13)` |
| 2.5.14 | 500 «Ocurrió un error. Inténtalo de nuevo.»; sigue `IN_PROGRESS` | REQ-FN-06 | `CompletionEndToEndIT.completeProfile_shouldRollBack_whenDatabaseFailsDuringCompletion` | No se reproduce en Newman (necesita un disparador en la base); se cubre con la IT |
| 2.5.15 | 403 en `POST …/completion` (y en `POST`/`DELETE …/skills`, que prueba CM-66) | REQ-FN-07 | `ProfileControllerTest.postCompletion_shouldReturn403_whenProfileBelongsToAnotherUser` | `403 finalizar un perfil ajeno (CA-2.5.15)` |
| 2.5.18 | 422 con `SUMMARY` si el resumen guardado tiene 2001 | REQ-FN-03 | `CompletionEndToEndIT.completeProfile_shouldListSummary_whenStoredSummaryHas2001Characters` | No se reproduce en Newman (el dato se inserta en la base); se cubre con la IT |
| 2.5.21 | 422; los cinco en orden | REQ-FN-02 | `ProfileControllerTest.postCompletion_shouldListFiveRequirementsInOrder_whenProfileIsNew` | `422 perfil recién creado (CA-2.5.21)` |
| 2.5.22 | En paralelo: si gana finalizar, 200 y eliminar 409; si gana eliminar, 204 y finalizar 422 con `SKILLS` | REQ-FN-05 | `ProfileWriteLockIT.completeAndRemoveSkill_shouldRejectRemoval_whenCompletionWinsTheLock` y `…_shouldRejectCompletion_whenRemovalWinsTheLock` | Secuencial en Newman: `200 finaliza` y luego `409 eliminar la única habilidad` (la regla es de CM-66) |

## 4. Requisitos (EARS)

- **REQ-FN-01 (CA-2.5.5, 2.5.6, 2.5.13).** Cuando un perfil `IN_PROGRESS` cumpla los 5 requisitos, `POST …/completion` debe pasarlo a `COMPLETED` y responder **200** con el perfil. La experiencia laboral no es requisito. Un resumen de 2000 puntos de código cumple.
- **REQ-FN-02 (CA-2.5.4, 2.5.21).** Cuando falte uno o más requisitos, Perfil debe responder 422 con `code` `PROFILE_INCOMPLETE`, `detail` «Todavía no cumples estos requisitos:», `missingRequirements` con los códigos del CA de los que faltan en el orden `NAME`, `SUMMARY`, `EDUCATION`, `SKILLS`, `TARGET_ROLES`, y `errors[]` con un elemento por requisito faltante en el mismo orden (§5), sin cambiar el estado.
- **REQ-FN-03 (CA-2.5.18).** Un resumen guardado vacío o de más de 2000 puntos de código cuenta como `SUMMARY` faltante. Un nombre guardado vacío o solo con espacios cuenta como `NAME` faltante. Leer el perfil nunca valida el largo de lo guardado (CM-54).
- **REQ-FN-04 (CA-2.5.11).** Si el perfil ya está `COMPLETED`, 409 `PROFILE_ALREADY_COMPLETED` «Este perfil ya está activo.», sin cambios; se comprueba antes que los requisitos.
- **REQ-FN-05 (CA-2.5.22).** La comprobación de requisitos y el cambio de estado ocurren con el perfil bloqueado (CM-274 PR 1). Si la eliminación de la única habilidad toma el bloqueo primero: 204 y la finalización 422 con `SKILLS`. Si la finalización lo toma primero: 200 y la eliminación 409 `SKILL_NOT_ALLOWED` (CM-66). Nunca queda un perfil `COMPLETED` sin habilidades.
- **REQ-FN-06 (CA-2.5.14).** Si la base de datos falla mientras se guarda la finalización, la transacción se revierte, el perfil sigue `IN_PROGRESS` y la respuesta es 500 `INTERNAL_ERROR` «Ocurrió un error. Inténtalo de nuevo.», con la causa solo en el log (`ERROR` con traza, `requestId`, `firebaseUid`, sin el SQL con parámetros).
- **REQ-FN-07 (CA-2.5.15).** Perfil ajeno → 403 `PROFILE_NOT_ALLOWED` «No encontramos lo que buscabas.»; inexistente → 404 `PROFILE_NOT_FOUND`; identificador mal escrito → 422 `PROFILE_ID_INVALID_FORMAT`; sin identidad → 401 `IDENTITY_REQUIRED`.
- **REQ-FN-08.** La petición no tiene cuerpo: un cuerpo enviado se ignora (incluido `{"status":"COMPLETED"}`).

## 5. Forma del 422

```json
{
  "type": "about:blank",
  "title": "Finalización incompleta",
  "status": 422,
  "detail": "Todavía no cumples estos requisitos:",
  "instance": "/api/v1/profiles/7c9e…/completion",
  "code": "PROFILE_INCOMPLETE",
  "requestId": "3c14c237-9fb8-4c25-86c6-cd81501d44f2",
  "missingRequirements": ["SUMMARY", "SKILLS"],
  "errors": [
    { "field": "summary", "code": "COMPLETION_SUMMARY_REQUIRED", "message": "Resumen profesional" },
    { "field": "skills", "code": "COMPLETION_SKILLS_REQUIRED", "message": "Habilidades" }
  ]
}
```

| Requisito | `errors[].field` (lo que lee `cameia-web`) | `errors[].code` (D4) | `errors[].message` (rótulo del CA) |
|---|---|---|---|
| `NAME` | `name` | `COMPLETION_NAME_REQUIRED` | «Nombre del perfil» |
| `SUMMARY` | `summary` | `COMPLETION_SUMMARY_REQUIRED` | «Resumen profesional» |
| `EDUCATION` | `education` | `COMPLETION_EDUCATION_REQUIRED` | «Formación académica» |
| `SKILLS` | `skills` | `COMPLETION_SKILLS_REQUIRED` | «Habilidades» |
| `TARGET_ROLES` | `targetRoles` | `COMPLETION_TARGET_ROLES_REQUIRED` | «Roles objetivo» |

El `detail` aquí no es «Revisa los campos marcados.»: es el literal de CA-2.5.4 y CA-2.5.21, porque este 422 no es de validación de un formulario sino de requisitos del perfil (`code` `PROFILE_INCOMPLETE`, no `VALIDATION_FAILED`). El orden de `errors[]` es el de la tabla y se prueba.

## 5.1 Matriz de validación (R2)

| Entrada | Causa | Valores | Estado · `code` |
|---|---|---|---|
| ruta `{id}` | no UUID | `"x"` | 422 `PROFILE_ID_INVALID_FORMAT` |
| ruta `{id}` | inexistente | `00000000-0000-0000-0000-000000000000` | 404 `PROFILE_NOT_FOUND` |
| `X-User-Id` | ausente, en blanco, 129 caracteres | — | 401 `IDENTITY_REQUIRED` |
| `X-User-Id` | otro Usuario | `uid-otro` | 403 `PROFILE_NOT_ALLOWED` |
| cuerpo | cualquier contenido (`{}`, `{"status":"COMPLETED"}`, JSON mal formado) | — | se ignora; el resultado depende solo del perfil |
| método | `GET`, `PUT` | — | 405 `METHOD_NOT_ALLOWED` con `Allow: POST` |
| estado | `COMPLETED` | — | 409 `PROFILE_ALREADY_COMPLETED` |
| estado | `IN_REVIEW` (solo alcanzable con HU-2.8, fuera del MVP; se arma en las pruebas) | — | se finaliza igual que `IN_PROGRESS` |
| nombre | `null`, `""` o solo espacios guardado | — | `NAME` en la lista |
| resumen | `null`, `""` o solo espacios; 2001 o más puntos de código guardados; 2000 → cumple | — | `SUMMARY` en la lista |
| formación, habilidad, rol | 0 → falta; 1 → cumple | — | `EDUCATION`, `SKILLS`, `TARGET_ROLES` |

**Verificación V1 (de Backend, en T-0):** comprobar que el `POST …/completion` con un cuerpo JSON mal formado no responde 422 `REQUEST_BODY_INVALID_FORMAT` (el método no declara `@RequestBody`); si Spring lo lee igual, fijar el comportamiento real en la prueba y reportarlo.

## 6. Base de datos

Sin migración. El disparador de la prueba de CA-2.5.14 se crea y se borra dentro de la prueba (`create function … raise exception`, `create trigger … before update of estado on perfil_profesional for each row when (new.firebase_uid = '<uid de la prueba>')`); no va a `db/migration`.

## 7. Errores (R3)

| `code` | HTTP | Origen | Mensaje | Estado |
|---|---|---|---|---|
| `PROFILE_INCOMPLETE` | 422 | `IncompleteProfileException` | «Todavía no cumples estos requisitos:» con `missingRequirements` y `errors` | Existe; se agrega `errors` |
| `COMPLETION_NAME_REQUIRED`, `COMPLETION_SUMMARY_REQUIRED`, `COMPLETION_EDUCATION_REQUIRED`, `COMPLETION_SKILLS_REQUIRED`, `COMPLETION_TARGET_ROLES_REQUIRED` | (elementos de `errors`) | `ErrorCatalog.COMPLETION_REQUIREMENTS` | Los rótulos de §5 | Nuevos (D4) |
| `PROFILE_ALREADY_COMPLETED` | 409 | `ProfileAlreadyCompletedException` | «Este perfil ya está activo.» | Existe |
| `INTERNAL_ERROR` | 500 | `ApiExceptionHandler.handleUnexpected` | «Ocurrió un error. Inténtalo de nuevo.» | Existe; aquí es el resultado correcto (CA-2.5.14 lo pide para una falla que nadie puede prever) |
| `PROFILE_UPDATE_IN_PROGRESS` | 409 | CM-274 PR 1 | «Estamos guardando otro cambio de tu perfil. Inténtalo de nuevo en unos segundos.» | De CM-274 |

Ninguna entrada del cliente llega al 500: la petición no tiene cuerpo y la ruta y el encabezado ya tienen sus códigos.

## 8. Casos borde (R5)

| Grupo | Caso | Cómo |
|---|---|---|
| Presencia | perfil recién creado (los cinco faltan) | CA-2.5.21 |
| Texto | resumen de 2000 (cumple, también con emoji) y 2001 guardado (falta); nombre guardado solo con espacios (falta) | `ProfessionalProfileTest`, `CompletionEndToEndIT` |
| Números, fechas, enumerados | No aplican: la petición no trae datos | Justificado |
| Colecciones | 0 y 1 de formación, habilidad y rol; experiencia 0 (no se exige) | `ProfessionalProfileTest.complete_shouldListOnlyMissing_whenSomeCollectionsAreEmpty` (parametrizada por las 8 combinaciones de formación/habilidad/rol vacías) |
| Estado | `IN_PROGRESS` → `COMPLETED`; `COMPLETED` → 409; finalizar dos veces seguidas (200 y 409); `IN_REVIEW` → `COMPLETED` | `ProfessionalProfileTest`, Postman |
| Propiedad | ajeno 403, inexistente 404, sin identidad 401 | §3 |
| Concurrencia | finalizar contra eliminar la única habilidad, en los dos órdenes; dos finalizaciones simultáneas (una 200 y otra 409) | `ProfileWriteLockIT` |
| Falla parcial | la base falla al actualizar el estado → 500 y sigue `IN_PROGRESS` | `CompletionEndToEndIT.completeProfile_shouldRollBack_whenDatabaseFailsDuringCompletion` |
| Dependencias | Ninguna externa | Justificado |
| Carga | cuerpo enviado se ignora (V1) | `ProfileControllerTest.postCompletion_shouldIgnoreBody_whenBodyIsSent` |

## 9. Seguridad (R6)

### 9.1 ASVS 5.0.0 nivel 1 (`cameia-infra/docs/seguridad/matriz-asvs-nivel1.md`, `4c46e89`)

| ID | Aplica | Qué lo cumple | Prueba |
|---|---|---|---|
| 1.2.3 Salida JSON sin armar a mano | Sí | `ProfileResponse` y `ApiErrorResponse` por Jackson; la carga del evento por `ProfileUpdatedPayloadV1` | `ProfileControllerTest`; `ProfileUpdatedEventContractTest` |
| 1.2.4 Consultas parametrizadas | Sí | `lockPending` es SQL nativo sin datos recibidos (solo el parámetro `limit`) | `OutboxRepositoryAdapterIT` |
| 2.3.1 Flujo de negocio en orden | Sí | `IN_PROGRESS` → `COMPLETED` solo con los cinco requisitos, con la fila bloqueada; se borra la ruta `review-requests` que saltaba a `IN_REVIEW` | `ProfileWriteLockIT` (CA-2.5.22); `ProfileControllerTest.postReviewRequests_shouldReturn404_whenRouteDoesNotExist` |
| 4.1.1 `charset` | Sí | Forzado en Perfil | `ProfileControllerTest` |
| 8.2.2 / 8.3.1 BOLA | Sí | `loadForUserForUpdate` | `ProfileControllerTest.postCompletion_shouldReturn403_whenProfileBelongsToAnotherUser`; `CompletionEndToEndIT.completeProfile_shouldReturn403_whenProfileBelongsToAnotherUser` |
| 12.2.1 TLS en conexiones externas | Sí (evento) | AMQPS hacia el broker de staging: configuración de DevOps (`spring.rabbitmq.ssl.enabled` por variable de entorno) | Pedido a DevOps; en local el broker de compose es sin TLS |
| 15.3.1 Respuesta con lo necesario | Sí | El 422 lista requisitos, no datos del perfil; el evento lleva solo lo de HT-03 | `ProfileControllerTest`; `ProfileUpdatedEventContractTest` |

### 9.2 OWASP API Security Top 10 — `POST /api/v1/profiles/{id}/completion` (y el evento)

| Riesgo | Cómo | Prueba |
|---|---|---|
| API1 BOLA | Dueño por `X-User-Id` con la fila bloqueada | Las de 8.2.2 |
| API2 Autenticación | Sin `X-User-Id` → 401 | `ProfileControllerTest` (401) |
| API3 Propiedades del objeto | El cuerpo se ignora: `{"status":"COMPLETED"}` no cambia nada | `ProfileControllerTest.postCompletion_shouldIgnoreBody_whenBodyIsSent` |
| API4 Consumo de recursos | Una lectura con bloqueo de 2 s; publicación con espera máxima de 500 ms; lotes de 50 en el Job | `RabbitEventPublisherTest.publish_shouldReturnFalse_whenConfirmationTakesLongerThanLimit` |
| API5 Autorización por función | Solo el dueño finaliza | Las de API1 |
| API6 Flujos sensibles | La transición es de una sola vez (segunda → 409) y no deja un Activo sin habilidades | `ProfileWriteLockIT.completeProfile_shouldActivateOnce_whenTwoTabsCompleteTogether` |
| API7 SSRF | No aplica | — |
| API8 Configuración | Sin traza ni SQL en el 500 de CA-2.5.14; el Job no abre puerto ni consume colas | `CompletionEndToEndIT.completeProfile_shouldRollBack_…`; `ProfileEventsRelayJobRunnerTest` |
| API9 Inventario | Se borra `POST …/review-requests` (oculta y sin HU) | `ProfileControllerTest.postReviewRequests_shouldReturn404_whenRouteDoesNotExist` |
| API10 Consumo inseguro | El publicador valida la confirmación y la devolución del broker antes de marcar | `RabbitEventPublisherTest` |

### 9.3 Resumen

API1: dueño por `X-User-Id` con el perfil bloqueado. API3: el cuerpo no cambia el estado (REQ-FN-08). API5: solo el dueño finaliza. API6: transición de estado idempotente en efecto (la segunda responde 409 sin cambios). Logs: el 500 en `ERROR` con traza y sin datos del perfil (lo hace `RedactedException`); los 4xx en `WARN`.

## 10. Código y patrones (R7)

- **Reutiliza:** `IncompleteProfileException`, `ApiExceptionHandler.handleBusiness` (punto donde ya se agrega `missingRequirements`), `ErrorCatalog`, `loadForUserForUpdate`, `ProfileWriteLockIT`.
- **Modifica:** `ProfessionalProfile.getMissingRequirements` (devuelve `List<CompletionRequirement>`; el resumen cuenta como faltante si `summary == null` o su largo supera `ProfessionalSummary.MAX_LENGTH`; el nombre, si es `null` o en blanco), `IncompleteProfileException` (lleva `List<CompletionRequirement>`), `ApiExceptionHandler` (agrega `errors[]` desde la tabla del catálogo), `ErrorCatalog` (tabla `COMPLETION_REQUIREMENTS`: requisito → `field`, `code`, `message`), `ErrorCode`, `ProfileController` (OpenAPI), `docs/errores.md`.
- **Crea:** `domain/model/CompletionRequirement` (`enum` `NAME`, `SUMMARY`, `EDUCATION`, `SKILLS`, `TARGET_ROLES` en el orden del CA; reemplaza los textos), `CompletionEndToEndIT`.
- El dominio no conoce los rótulos ni los nombres de campo del contrato: viven en `ErrorCatalog` (presentación).
- **Idioma (R1):** identificadores y nombres de prueba en inglés; Javadoc, comentarios, OpenAPI y logs en español.

## 11. Documentación (R8)

`@Operation` del `POST …/completion` con la descripción de los requisitos y su orden; `@ApiResponse` 200, 401, 403, 404, 409, 422 (con el ejemplo completo de §5), 409 `PROFILE_UPDATE_IN_PROGRESS`, 500. `ApiErrorResponse` (esquema de error) documenta `missingRequirements` como opcional. `docs/errores.md`: fila de `PROFILE_INCOMPLETE` con `errors[]` y los cinco códigos de requisito. Javadoc en español en lo tocado.

## 12. Pruebas (R9) y cobertura (R10)

- Dominio: `ProfessionalProfileTest` (requisitos por combinación, resumen 2000/2001, nombre en blanco, `COMPLETED` → 409).
- Web: `ProfileControllerTest` (`errors[]` en orden con `field`, `code`, `message`; `missingRequirements` igual; 403; 409; cuerpo ignorado).
- Integración: `CompletionEndToEndIT` (PostgreSQL real, puerto aleatorio): éxito, faltantes, 2001 guardado por SQL, falla con disparador; `ProfileWriteLockIT` (CA-2.5.22 en los dos órdenes, dos finalizaciones).
- Cobertura ≥ 90 % de líneas y ramas en `ProfessionalProfile`, `CompletionRequirement`, `IncompleteProfileException`, `ApiExceptionHandler`, `ErrorCatalog`, `ProfileAppService`; global sin bajar. `./mvnw.cmd -B clean verify` → `target/site/jacoco/jacoco.csv`.

## 13. Postman y Newman (R11)

Carpeta `HU-2.5 — Finalización` con: perfil recién creado (422 con cinco en orden: `missingRequirements` y `errors[*].field` = `name, summary, education, skills, targetRoles`); faltan resumen y habilidades (CA-2.5.4); resumen de 2000 (200); sin experiencia (200); éxito (200, `status` `COMPLETED`); finalizar otra vez (409); ajeno (403); inexistente (404); identificador mal escrito (422); sin identidad (401); `GET` → 405; eliminar la única habilidad tras finalizar (409, cuando CM-66 esté en `develop`). Corrida con `docker compose up -d --build` y `npx newman run … --folder "HU-2.5 — Finalización" --reporters cli,junit`; se pega la salida.

## 14. Atributos de calidad (R13)

Anexo, cuadro 9: **HE-02** declara AC-0001, AC-0003, AC-0006 y MAN-02; se agregan las que el cambio toca. Las del evento están en la sección 18.6. «Resultado real» lo llenan la ejecución y la verificación.

| Atributo | Métrica (ID) | Límite | Cómo lo cumple | Prueba o comando | Resultado real |
|---|---|---|---|---|---|
| AC-0001 (HE-02) | IA-01 | 100 % con procedencia válida | La finalización no crea datos ni usa IA | — | No aplica |
| AC-0003 (HE-02) | SEG-01 | ASVS N1 aplicable | Sección 9.1 | Las de la sección 9.1 | PENDIENTE |
| AC-0003 (HE-02) | SEG-02 | 100 % de accesos cruzados rechazados | 403 sin cambios, con dos Usuarios; cuerpo ignorado | `CompletionEndToEndIT.completeProfile_shouldReturn403_whenProfileBelongsToAnotherUser`; `ProfileControllerTest.postCompletion_shouldIgnoreBody_whenBodyIsSent` | PENDIENTE |
| AC-0003 (HE-02) | SEG-03 | Sin dependencias nuevas | — | `git diff origin/develop -- pom.xml` vacío | PENDIENTE |
| AC-0003 (HE-02) | SEG-04 | Cero datos sensibles en logs y eventos | El 500 sin SQL ni datos; ningún log con la carga | `CompletionEndToEndIT.completeProfile_shouldRollBack_…` (cuerpo sin `forced failure`); `docker compose logs app \| Select-String -Pattern 'Resumen\|forced failure'` → 0 en el cuerpo de las respuestas | PENDIENTE |
| AC-0006 (HE-02) | INT-06 (parte de Backend) | El cliente muestra estado y acción | `errors[].field` en el orden que lee `cameia-web` y `missingRequirements` | `ProfileControllerTest.postCompletion_shouldListFiveRequirementsInOrder_whenProfileIsNew` | PENDIENTE |
| MAN-02 (HE-02) | MAN-02 | Adaptador con éxito, formato inválido, tiempo de espera y cuota | `RabbitEventPublisher` (PR 2): confirmado, `nack`, devuelto, sin confirmación en 500 ms; cuota no aplica (el broker no tiene cuota) | `RabbitEventPublisherTest` (cuatro casos) | PENDIENTE |
| AC-0002 (cambio) | DES-02 | p95 ≤ 2 s, 100 observaciones | Una lectura con bloqueo y una escritura; el evento espera ≤ 500 ms después del commit | Carpeta `DES-02 — HU-2.5 finalización` (armar perfil completo y finalizar), `-n 105` y el script de p95 de CM-274 T-E3 | PENDIENTE |
| AC-0004 (cambio) | FIA-01 | Todo fallo conserva el último estado | Rollback ante falla real; nunca un Activo sin habilidades | `CompletionEndToEndIT.completeProfile_shouldRollBack_whenDatabaseFailsDuringCompletion`; `ProfileWriteLockIT` | PENDIENTE |
| AC-0004 (cambio) | FIA-02 | Cero efectos en 20 repeticiones | Finalizar 20 veces: un 200 y diecinueve 409, un solo evento | `CompletionEndToEndIT.completeProfile_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes` | PENDIENTE |
| Control | MAN-01 | ≥ 90 % líneas y ramas en lo tocado | Sección 12 | `jacoco.csv` | PENDIENTE |
| Control | IOP-01 | OpenAPI y JSON Schema | Sección 11 y 18.2 | `OpenApiDocumentIT`; `ProfileUpdatedEventContractTest` | PENDIENTE |

## 15. Fuera de alcance (con destino)

Habilidades (CM-66) · Roles Objetivo (HU-2.11) · `PATCH …/salary-expectation` (sin HU, fuera del MVP; hallazgo I-024) · publicar al archivar o reactivar (HU-2.14, aún sin implementar: reutilizará `saveAndAnnounce`) · las colas que consume Perfil con nombres distintos de los de la v6 (`cuenta-eliminada`, `suscripcion-actualizada`, `consumo-registrado` en `cameia.events`): CM-279 y la tarea de cada consumidor · los mocks de `cameia-web` usan otro `detail` («Todavía no cumples los requisitos para finalizar.») y `message` «Obligatorio»: aviso a Frontend.

## 16. Decisiones

| # | Decisión | Porqué | Alternativas | Decisión humana |
|---|---|---|---|---|
| D1 | El 422 lleva `missingRequirements` y `errors[]` | Cumple el CA y lo que lee el cliente | Una sola lista | Paula, 6-oct-2026 |
| D2 | Éxito en 200 | Literal de CA-2.5.6 | 201 | Paula, 6-oct-2026 (ya en `develop`) |
| D3 | `CompletionRequirement` como `enum` del dominio | Orden fijo y códigos estables, sin textos en el dominio | Textos (`List<String>`) | Pendiente de la aprobación de la spec |
| D4 | Códigos de `errors[]` propios de la finalización | Un código ↔ un mensaje: `PROFILE_NAME_REQUIRED` ya tiene «Ingresa un nombre para el perfil.» | Reutilizar los códigos de campo | Paula, 9-oct-2026 |
| D5 | CA-2.5.14 se prueba con un disparador real | Una falla real de la base, sin dobles que oculten el rollback | Simular con Mockito | Pendiente de la aprobación de la spec |
| D6 | Se borran `POST …/review-requests`, `ProfileAppService.requestReview` y `ProfessionalProfile.requestReview`; `IN_REVIEW`, `markAsReviewed` y `returnForRevision` se conservan si HU-2.8 los usa (`git grep`), y si no, también se borran | La v6 dice que `IN_REVIEW` no se alcanza en el MVP por esa ruta; CM-271 (D26) la dejó oculta hasta esta tarea | Dejar la ruta | Paula, 7-oct-2026 (D26 de CM-271: «hasta que CM-67 la quite») |
| D7 | Esta tarea implementa el outbox y la publicación de `perfil.profesional-actualizado` para toda escritura de un perfil `COMPLETED` (sección 18) | Una sola implementación del outbox en Perfil | Tarea propia de Perfil; publicar dentro de cada tarea | Paula, 9-oct-2026 |

## 17. Preguntas abiertas

| # | Pregunta | Para | Bloquea |
|---|---|---|---|
| Q-67-1 | **`embedding`**: quién lo genera (decisión 1 del documento enviado al Product Owner el 9-oct; recomendación B: se publica sin `embedding` y Entrevistas lo calcula) | Juan David Vela Coronado (ya preguntado; su respuesta del 9-oct no la contesta) | PR 3 |
| Q-67-2 (RESUELTA, Paula, 9-oct-2026: opción a) | **Composición de `contextoProfesional` y forma de `rolesAspirados`.** HT-03 fija solo el mínimo (`id`, `nombre`, `estado`, `fecha_archivado`); Backend es dueño del productor y del consumidor (CM-209), y HU-4.4/4.5 guardan «una copia fija del Perfil Profesional» para preparar las preguntas. Recomendación: `contextoProfesional` = `id`, `nombre`, `estado`, `fecha_archivado`, `resumen`, `experiencias[]` (`empresa`, `cargo`, `descripcion`, `inicio`, `fin`, `estadoEmpleo`), `formaciones[]` (`institucion`, `titulo`, `nivel`, `inicio`, `fin`, `enCurso`), `habilidades[]` (`nombre`, `nivel`); `rolesAspirados[]` = `codigo`, `nombre` | Paula (respondió a: la recomendación) | Resuelta; el PR 3 espera solo Q-67-1 |
| Job de pendientes | Programación del Job `profile-events-relay` cada 5 min en Cloud Run | DevOps, por tarea de Vela | La activación en staging, no el código |

## 18. Evento `perfil.profesional-actualizado` (PR 2 y PR 3)

Paula decidió el 9-oct-2026 que esta tarea implementa la publicación del evento que HT-03 pide a Perfil (CM-209 es solo el consumidor de Entrevistas). Lo hace una sola vez, para toda escritura que deje un perfil en `COMPLETED`.

### 18.1 Estado real y causa

| Situación en `2a54ca7` | Causa | Antes | Después |
|---|---|---|---|
| Perfil no publica nada | `ProfileEventPublisher.publishProfileUpdated` existe pero nadie lo llama (`TODO`); no hay tabla de salida | Finalizar un perfil: ningún mensaje en RabbitMQ (`rabbitmqadmin list exchanges` sin `perfil.events`) | Un mensaje por escritura confirmada de un perfil `COMPLETED` |
| El contrato publicado no coincide con HT-03 | `RabbitConfig` declara el exchange `cameia.events` y la cola `perfil-profesional-actualizado` (que es del consumidor); `ProfileUpdatedPayloadV1` tiene `profileId`, `firebaseUid`, `name`, `status` | Lectura del código | Exchange `perfil.events`, clave `perfil.profesional-actualizado`, carga de HT-03 |

### 18.2 Contrato (HT-03)

- **Destino.** Exchange `perfil.events` (`topic`, durable, no se borra solo), clave de enrutamiento `perfil.profesional-actualizado`. La cola `entrevista.perfil-profesional-actualizado` la declara el consumidor; Perfil deja de declarar la cola `perfil-profesional-actualizado` y su binding.
- **Carga v1** (`application/json`, UTF-8): `usuarioId`, `contextoProfesional` (con al menos `id`, `nombre`, `estado` y `fecha_archivado`), `rolesAspirados`, `embedding`, `actualizadoEn` (instante UTC con milisegundos y sufijo `Z`, tomado del reloj inyectado en el momento de la escritura). La composición de `contextoProfesional` y de `rolesAspirados` la fijó Paula el 9-oct-2026 (Q-67-2, opción a: la propuesta de la sección 17); **PENDIENTE de Vela:** quién calcula `embedding` (Q-67-1). `usuarioId` es el `firebaseUid` (P-01 de CM-279, decidido).
- **Propiedades AMQP:** las mismas de `cuenta.creada` (P-02 de CM-279, decidido: metadatos en propiedades AMQP): `message_id`, `type`, `x-event-version`, `app_id` `cameia-perfil`, `timestamp`, `correlation_id`, `x-causation-id`, `content_type`, `delivery_mode` 2.
- **Contrato versionado (IOP-01).** `docs/eventos/perfil-profesional-actualizado-v1.schema.json` (JSON Schema 2020-12, `additionalProperties: false`). `ProfileUpdatedEventContractTest` serializa una carga de ejemplo y comprueba, leyendo el esquema con Jackson (sin dependencias nuevas), que los nombres, los tipos y los obligatorios coinciden.
- **Garantías.** Al menos una vez (el mismo `message_id` puede llegar dos veces); Entrevistas descarta un evento con `actualizadoEn` anterior al de su réplica (HT-03). Como las escrituras de un mismo perfil se serializan con el bloqueo (CM-274), `actualizadoEn` no retrocede para un perfil.

### 18.3 Requisitos (EARS)

- **REQ-EV-01.** Cuando una escritura deje el perfil en `COMPLETED` (finalizar; editar información general, experiencia, formación o roles objetivo de un perfil `COMPLETED`), Perfil debe guardar en `evento_saliente`, en la misma transacción que el cambio, una fila con `tipo` `perfil.profesional-actualizado`, `version` 1, `agregado_id` = id del perfil, la carga de 18.2 y `fecha_publicacion` nula.
- **REQ-EV-02.** Editar las habilidades de un perfil `COMPLETED` publica también (decisión de Paula del 9-oct-2026: HT-03 nombra HU-2.3, 2.4 y 2.11, pero sin esto la réplica de Entrevistas quedaría con habilidades viejas; se pide a Vela agregar HU-2.5 a la nota de HT-03).
- **REQ-EV-03.** Una escritura rechazada (4xx) o revertida (500) no guarda evento.
- **REQ-EV-04.** Tras el commit, Perfil intenta publicar la fila una vez, esperando la confirmación del broker como máximo 500 ms (igual que Cuentas, P-03 de CM-279: así la petición sigue dentro de DES-02). Con la confirmación marca `fecha_publicacion` y borra la carga (P-05 de CM-279, decisión de Paula del 9-oct-2026 para los dos servicios). Sin confirmación, suma un intento y la fila queda pendiente; nunca se publica dentro de la transacción de la escritura.
- **REQ-EV-05.** Un Job cada 5 min (como el de Cuentas, P-03 de CM-279) publica las filas pendientes en orden de `fecha_creacion`, leyéndolas con `FOR UPDATE SKIP LOCKED` para que dos ejecuciones no publiquen la misma fila a la vez. El Job es la misma imagen de Perfil con el perfil de Spring `profile-events-relay`; su programación en Cloud Run se pide a DevOps por Vela.
- **REQ-EV-06.** Al décimo intento fallido de una fila se registra un `ERROR` con el id del evento y su tipo (sin la carga) una sola vez; la fila sigue pendiente.
- **REQ-EV-07.** Ningún log escribe la carga, el nombre del perfil, los roles ni el `embedding`.

### 18.4 Base de datos (`V9__evento_saliente.sql`, número fijo del orden de Perfil)

```sql
CREATE TABLE evento_saliente (
    id                UUID          NOT NULL,
    tipo              VARCHAR(64)   NOT NULL,
    version           SMALLINT      NOT NULL,
    agregado_id       UUID          NOT NULL,
    carga             JSONB         NULL,
    id_correlacion    VARCHAR(64)   NOT NULL,
    fecha_creacion    TIMESTAMPTZ   NOT NULL,
    fecha_publicacion TIMESTAMPTZ   NULL,
    intentos          INTEGER       NOT NULL DEFAULT 0,
    CONSTRAINT pk_evento_saliente PRIMARY KEY (id),
    CONSTRAINT ck_evento_saliente_tipo CHECK (tipo IN ('perfil.profesional-actualizado')),
    CONSTRAINT ck_evento_saliente_version CHECK (version >= 1),
    CONSTRAINT ck_evento_saliente_id_correlacion CHECK (id_correlacion ~ '^[A-Za-z0-9._-]{1,64}$'),
    CONSTRAINT ck_evento_saliente_intentos CHECK (intentos >= 0),
    CONSTRAINT ck_evento_saliente_carga_pendiente CHECK ((fecha_publicacion IS NULL) = (carga IS NOT NULL)),
    CONSTRAINT ck_evento_saliente_fecha_publicacion CHECK (fecha_publicacion IS NULL OR fecha_publicacion >= fecha_creacion)
);
CREATE INDEX ix_evento_saliente_pendiente ON evento_saliente (fecha_creacion) WHERE fecha_publicacion IS NULL;
CREATE INDEX ix_evento_saliente_agregado_id ON evento_saliente (agregado_id);
```

Sin clave foránea a `perfil_profesional`: el evento es inmutable y sobrevive al borrado del perfil. `ck_evento_saliente_carga_pendiente` refleja P-05 de CM-279 (la carga se borra al publicar). Ninguna restricción es alcanzable desde una petición: una violación es un defecto y termina en 500 con la restricción en el log.

### 18.5 Diseño

| Pieza | Capa | Por qué |
|---|---|---|
| `ProfileUpdated` (record) | `domain/event` | El hecho de negocio, sin JSON ni AMQP |
| `OutboundEvent` (record) | `domain/event` | Lo que el relevo necesita de una fila pendiente |
| `OutboxRepository`, `EventPublisher` | `domain/port` | Puertos de la tabla y del broker; `publish` devuelve `true` solo con confirmación |
| `CorrelationId` (record) | `domain/event` | Regla de `X-Request-Id` (`^[A-Za-z0-9._-]{1,64}$`; si no cumple, el id del evento) |
| `ProfileAppService.saveAndAnnounce(profile, correlation)` | `application/service` | Un solo punto: guarda y, si el perfil queda `COMPLETED`, agrega el evento en la misma transacción |
| `OutboxRelayService` | `application/service` | Publica y marca después del commit (un intento, confirmación en ≤ 500 ms) y en el Job de pendientes |
| `OutboxEventEntity`, `OutboxEventJpaRepository`, `OutboxRepositoryAdapter` | `infrastructure/persistence` | Patrón de tres piezas del repositorio |
| `ProfileUpdatedPayloadV1` (reemplaza al actual) | `infrastructure/messaging/payload` | Contrato JSON; nombres en español solo en `@JsonProperty` |
| `RabbitEventPublisher` (reemplaza a `ProfileEventPublisher`) | `infrastructure/messaging/publisher` | Confirmación (`publisher-confirm-type: correlated`) y devolución (`mandatory`) |
| `RabbitConfig` | `infrastructure/messaging/config` | Declara `perfil.events`; deja de declarar la cola y el binding del consumidor |

Mismo diseño que la tabla de salida de Cuentas (spec de CM-279, «PublicarCuentaCreada»), con sus decisiones del 9-oct-2026: P-01 `usuarioId` = `firebaseUid`; P-02 metadatos en propiedades AMQP; P-03 intento tras el commit con 500 ms y Job cada 5 min; P-05 borrar la carga al publicar; P-12 `@Transactional` en los métodos del adaptador.

### 18.6 Atributos de calidad del evento

| Atributo | Métrica | Cumplimiento | Prueba |
|---|---|---|---|
| AC-0002 | DES-02 | La espera por el broker tras el commit está acotada a 500 ms | `RabbitEventPublisherTest.publish_shouldReturnFalse_whenConfirmationTakesLongerThanLimit` |
| AC-0004 | FIA-01, FIA-02, FIA-05 | Evento y cambio en la misma transacción; republicación desde la tabla; el mismo `message_id` en cada reintento. Una escritura repetida de un perfil `COMPLETED` publica un evento por escritura confirmada con `actualizadoEn` creciente: la réplica de Entrevistas queda igual (descarta lo anterior y es idempotente), así que no hay efecto adicional en el consumidor | `OutboxRelayServiceTest`, `ProfileEventsEndToEndIT` (broker detenido y vuelto a levantar) |
| MAN-02 | Adaptador del broker | Éxito, `nack`, devuelto y sin confirmación en 500 ms | `RabbitEventPublisherTest` |
| AC-0003 | SEG-04 (AMQPS) | En staging, conexión AMQPS por variable de entorno (DevOps) | Pedido a DevOps; local sin TLS |
| IOP-01 | Contrato versionado | JSON Schema v1 y prueba de contrato | `ProfileUpdatedEventContractTest` |
| IOP-03 | Topología | `perfil.events` y la clave de HT-03 | `ProfileEventsEndToEndIT` |
| IOP-04 | Outbox en el productor | 100 % de las escrituras de un perfil `COMPLETED` pasan por `saveAndAnnounce` | `ProfileAppServiceTest.writes_shouldAnnounce_whenProfileIsCompleted` (parametrizada por caso de uso) |
| AC-0003 | SEG-04 | Ningún log con la carga; AMQPS lo da la configuración del broker (DevOps) | Revisión del log de `ProfileEventsEndToEndIT` |
| REST-0003 | Datos mínimos | La carga solo lleva lo que fija HT-03 y se borra al publicarse (P-05) | `OutboxRepositoryAdapterIT` |

## 19. Integración con las otras CM de Perfil

Orden de Perfil, en serie: **CM-279 P1 → CM-279 P2 → CM-274 → CM-54 → CM-66 → CM-67** (detalle en la sección 19 de la spec de CM-274). Esta CM es la última:

| Pieza compartida | Qué hace CM-67 |
|---|---|
| Migraciones | `V9__evento_saliente.sql` (PR 2); V5 a V8 ya están |
| `ErrorCode`, `ErrorCatalog`, `docs/errores.md` | Agrega los cinco `COMPLETION_*_REQUIRED` y la tabla `COMPLETION_REQUIREMENTS`; el PR 3 no agrega códigos |
| `RabbitConfig` | CM-279 P2 ya agregó la topología de `cuentas.events` en su propia clase; aquí se cambia el exchange de salida a `perfil.events` y se quita la cola del consumidor. No se tocan las colas que consume Perfil |
| `ProfileAppService` | `saveAndAnnounce` reemplaza `repository.save` en las once escrituras (PR 3); el constructor recibe `OutboxRepository` y `OutboxRelayService` |
| `PerfilApplication.main` | Salida del Job `profile-events-relay` (PR 2); si CM-279 ya agregó una condición de salida para otro perfil, se agrega este a la misma |
| Colección de Postman | Carpetas `HU-2.5 — Finalización` y `DES-02 — HU-2.5 finalización` |

## 20. Línea base verde

En la base de la rama del PR 1 (`origin/develop` con CM-66 y el PR 0), antes de editar; salida pegada en `analisis/ejecucion_CM-67.md`.

| Comprobación | Comando | Esperado |
|---|---|---|
| Herramientas | `java -version`; `docker version`; `npx newman --version` | JDK 21, Docker encendido, Newman 6 |
| Build y pruebas | `./mvnw.cmd -B clean verify` | `BUILD SUCCESS`, 0 fallos y 0 omitidas |
| Cobertura de partida | `target/site/jacoco/jacoco.csv` | Global anotado |
| Migraciones | `git ls-tree --name-only HEAD src/main/resources/db/migration/` | V1 a V8 |
| Postman | `docker compose down -v`; `docker compose up -d --build`; Newman de la colección completa con `-e docs/perfil-local.postman_environment.json` | 0 fallos |

Trampa: si el puerto 5432 está ocupado, `$env:POSTGRES_PORT='55433'` antes de `docker compose up`.

## 21. Regla → evidencia (R1 a R14)

| Regla | Evidencia prevista | Estado en la spec |
|---|---|---|
| R1 | Diff revisado; Javadoc y comentarios en español | Cubierta |
| R2 | Sección 5.1 (ruta, encabezado, cuerpo ignorado, método) | Cubierta |
| R3 | Sección 7; el único 500 es el de CA-2.5.14, que el CA pide literal; `ErrorCodeDocumentationTest` | Cubierta (ver nota) |
| R4 | V9 con sus `ck_` violados en `OutboxRepositoryAdapterIT` | Cubierta |
| R5 | Sección 8 | Cubierta |
| R6 | Sección 9 (ASVS y OWASP API) | Cubierta |
| R7 | Sección 10; `ArquitecturaTest` | Cubierta |
| R8 | Sección 11; JSON Schema del evento | Cubierta |
| R9 | Sección 12 | Cubierta |
| R10 | `jacoco.csv` de las clases de cada PR | Cubierta |
| R11 | Sección 13 y Newman completo | Cubierta |
| R12 | Sección 3 (CA de finalización de HU-2.5) | Cubierta |
| R13 | Secciones 14 y 18.6 | Cubierta |
| R14 | Sin push, PR ni Jira sin el sí de Paula | Cubierta |

**Nota sobre R3.** R3 dice que el `detail` de todo 422 con `errors` es «Revisa los campos marcados.»; CA-2.5.4 y CA-2.5.21 fijan «Todavía no cumples estos requisitos:» para este 422, que no es de validación (`code` `PROFILE_INCOMPLETE`). Manda el backlog (R12); el texto de R3 se precisa en el estándar («salvo que el CA fije otro»). Lo mismo con el 500 de CA-2.5.14: el CA lo pide literal; el 503 por base caída para todos los servicios es CM-290.

## 22. Lista de verificación de la CM

| # | Comprobación | Cómo | Esperado |
|---|---|---|---|
| V-01 | Cada CA de la sección 3 tiene su prueba | `git grep -n "<método>" -- src/test` | Todas existen |
| V-02 | Cada CA tiene su petición (salvo 2.5.14 y 2.5.18, cubiertos por IT) | Carpeta `HU-2.5 — Finalización` | Presentes |
| V-03 | Forma del 422 | `POST …/completion` de un perfil recién creado | `missingRequirements` y `errors[*].field` = `name, summary, education, skills, targetRoles` en orden; `detail` literal |
| V-04 | Suite completa | `./mvnw.cmd -B clean verify` | Verde, 0 omitidas |
| V-05 | Rollback real | `CompletionEndToEndIT.completeProfile_shouldRollBack_whenDatabaseFailsDuringCompletion` | 500 sin `forced failure`; perfil `IN_PROGRESS` |
| V-06 | Resumen guardado de 2001 | `…shouldListSummary_whenStoredSummaryHas2001Characters` | 422 `SUMMARY`; el `GET` responde 200 |
| V-07 | CA-2.5.22 | `ProfileWriteLockIT` (dos órdenes y doble finalización) | Nunca `COMPLETED` sin habilidades |
| V-08 | `review-requests` | `POST …/review-requests` | 404 `ROUTE_NOT_FOUND` |
| V-09 | Repetición | `…shouldHaveNoExtraEffect_whenRepeatedTwentyTimes` | Un 200 y diecinueve 409 |
| V-10 | Tabla de salida | `OutboxRepositoryAdapterIT` | Cada `ck_` rechaza su fila; `SKIP LOCKED` funciona |
| V-11 | Publicador | `RabbitEventPublisherTest` | Confirmado, `nack`, devuelto y 500 ms |
| V-12 | Job | `ProfileEventsRelayJobRunnerTest` y la corrida manual | Sin servidor web, sin consumidores, código de salida 0 |
| V-13 | Evento (PR 3) | `ProfileEventsEndToEndIT` y `ProfileUpdatedEventContractTest` | Un mensaje por escritura confirmada de un perfil `COMPLETED`; ninguno en borrador ni en rollback; mismo `message_id` al republicar |
| V-14 | Cobertura | `jacoco.csv` | ≥ 90 % por clase tocada; global ≥ línea base |
| V-15 | Postman y DES-02 | T-6 | 0 fallos; p95 de la finalización ≤ 2000 ms |
| V-16 | Logs | `docker compose logs app` | Sin la carga, nombres ni resumen; un `ERROR` solo en el caso de CA-2.5.14 |
| V-17 | Comentarios y dependencias | `git grep -n "CM-[0-9]" -- src`; `git diff origin/develop -- pom.xml` | Ninguno; vacío |
| V-18 | Tamaño | `git diff --shortstat <base>...<rama>` | ≤ 1000 por PR |
| V-19 | Revisiones | `/simplify`, `/code-review high`, `/security-review` | Hallazgos corregidos o descartados con razón |

## 23. Puerta de listo

| Condición | Estado |
|---|---|
| 1. Trazabilidad y atributos | Secciones 3, 14 y 18.6 |
| 2. Matriz, casos borde, ruta al 500 y plan de seguridad | Secciones 5.1, 7, 8 y 9 |
| 3. Contraste con `2a54ca7` y ensayo en seco | Hecho el 9-oct-2026 (`analisis/revision-spec_CM-67.md`) |
| 4. Integración | Sección 19 |
| 5. Línea base | Sección 20 |
| 6. Lista de verificación | Sección 22 |
| 7. Dudas | Q-67-1 (Vela) bloquea solo el PR 3; Q-67-2 quedó resuelta (Paula, 9-oct-2026, opción a) |

**Dictamen:** PR 1 y PR 2 **LISTOS PARA EJECUTAR** después de CM-66; PR 3 **NO LISTO** hasta Q-67-1 (Vela). La aprueba solo Paula.
