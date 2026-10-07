# Errores del servicio

## Formato

Las respuestas de error siguen la [sección 6 del estándar](estandar-backend.md#6-errores) y el [ADR 0001](adr/0001-codigo-de-error-y-request-id.md). Toda respuesta de error es `application/problem+json` (RFC 9457) y lleva:

- `code`: código estable. El cliente decide qué mostrar a partir de él, no del texto.
- `requestId`: identificador de la petición. Si llega un `X-Request-Id` válido (`[A-Za-z0-9._-]{1,64}`) se devuelve igual; si no, se genera un UUID. Va también en el encabezado `X-Request-Id` de la respuesta.
- `errors[]` (solo en `VALIDATION_FAILED`): un elemento por campo, con `field`, `code` y `message`.

Un fallo no controlado responde siempre el mensaje genérico de `INTERNAL_ERROR`; el detalle queda solo en el log del servidor.

## Códigos que el servicio emite

| Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba |
|---|---|---|---|---|---|---|
| `VALIDATION_FAILED` | 422 | `PATCH /api/v1/profiles/{id}`, `POST …/work-experiences`, `POST …/educations`, `POST …/skills`, `POST …/target-roles`, `PATCH …/target-roles/{roleId}` | — | «Revisa los campos marcados.» | `ApiExceptionHandler` (Bean Validation) | `ProfileControllerTest.postSkills_returns422WhenLevelIsMissing` |
| `REQUEST_BODY_INVALID_FORMAT` | 422 | Los mismos endpoints con cuerpo | — | «Revisa el formato de los datos enviados.» | `ApiExceptionHandler` | `ProfileControllerTest.patchProfile_shouldReturn422_whenBodyIsMalformed` |
| `REQUEST_INVALID_VALUE` | 422 | Los mismos endpoints con cuerpo | — | «Revisa los datos enviados.» (nunca el mensaje de la excepción) | `ApiExceptionHandler` (respaldo de `IllegalArgumentException`) | `ApiExceptionHandlerTest.illegalArgument_shouldHideMessage_whenThrown` |
| `IDENTITY_REQUIRED` | 401 / 400 | 401 en todo endpoint con `{id}` sin `X-User-Id`; 400 en `POST /api/v1/profiles` sin `X-User-Id` | — | «Identidad del usuario requerida» | `IdentityRequiredException`; `ApiExceptionHandler` (encabezado ausente) | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `PROFILE_NOT_FOUND` | 404 | Todo endpoint con `{id}` | — | «No se encontró el perfil con id …» | `ProfileNotFoundException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `PROFILE_NOT_ALLOWED` | 403 | Todo endpoint con `{id}` | — | «No tienes permiso para acceder a este perfil» | `ProfileAccessDeniedException` | `ProfileControllerTest` (403) |
| `PROFILE_LIMIT_REACHED` | 409 | `POST /api/v1/profiles` | — | Texto vigente de `ProfileAlreadyExistsException` | `ProfileAlreadyExistsException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `PROFILE_ALREADY_COMPLETED` | 409 | `POST …/completion` | — | «El perfil ya está en estado COMPLETED» | `ProfileAlreadyCompletedException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `PROFILE_INCOMPLETE` | 422 | `POST …/completion`, `POST …/review-requests` | — | Cuerpo propio `{missingRequirements}`; **todavía sin `code`** (lo cambia CM-67) | `IncompleteProfileException` | `ApiExceptionHandlerTest.incompleteProfile_shouldReturn422WithRequestIdHeader_whenThrown` |
| `PROFESSIONAL_ROLE_NOT_FOUND` | 404 | `POST …/target-roles`, `PATCH …/target-roles/{roleId}` | — | «Rol profesional no encontrado: …» | `ProfessionalRoleNotFoundException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `TARGET_ROLE_LIMIT_REACHED` | 422 | `POST …/target-roles` | — | «El perfil ya tiene el máximo de N roles objetivo permitidos» | `MaxTargetRolesExceededException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `TARGET_ROLE_ALREADY_EXISTS` | 409 | `POST …/target-roles` | — | «Ya existe un rol objetivo con el rol '…' en este perfil» | `DuplicateTargetRoleException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `TARGET_ROLE_NOT_ALLOWED` | 422 | `DELETE …/target-roles/{roleId}` | — | «El perfil debe tener al menos un rol objetivo; no se puede eliminar el último» | `LastTargetRoleException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `SKILL_ALREADY_EXISTS` | 409 | `POST …/skills` | — | «La habilidad '…' ya está asociada al perfil» | `DuplicateSkillException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `INTERNAL_ERROR` | 500 | Cualquiera | — | «Ocurrió un error. Inténtalo de nuevo.» | `ApiExceptionHandler` | `ApiExceptionHandlerTest.unexpectedException_shouldReturnGeneric500_whenThrown` |

### Códigos de campo (`errors[].code`)

| Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba |
|---|---|---|---|---|---|---|
| `COMPANY_REQUIRED` | 422 | `POST …/work-experiences` | `company` | «Ingresa la empresa.» | `AddWorkExperienceRequest` | `ApiExceptionHandlerTest.everyFieldConstraint_shouldHaveCode` |
| `POSITION_REQUIRED` | 422 | `POST …/work-experiences` | `position` | «Ingresa el cargo.» | `AddWorkExperienceRequest` | `ApiExceptionHandlerTest.everyFieldConstraint_shouldHaveCode` |
| `START_DATE_REQUIRED` | 422 | `POST …/work-experiences` | `startDate` | «Ingresa la fecha de inicio.» | `AddWorkExperienceRequest` | `ApiExceptionHandlerTest.everyFieldConstraint_shouldHaveCode` |
| `EMPLOYMENT_STATUS_REQUIRED` | 422 | `POST …/work-experiences` | `employmentStatus` | «Selecciona una opción.» | `AddWorkExperienceRequest` | `ApiExceptionHandlerTest.everyFieldConstraint_shouldHaveCode` |
| `PROVENANCE_REQUIRED` | 422 | `POST …/work-experiences`, `POST …/skills`, `POST …/target-roles` | `provenance` | «Selecciona una opción.» | `AddWorkExperienceRequest`, `AddSkillRequest`, `AddTargetRoleRequest` | `ApiExceptionHandlerTest.everyFieldConstraint_shouldHaveCode` |
| `SKILL_NAME_REQUIRED` | 422 | `POST …/skills` | `skillName` | «Ingresa una habilidad.» | `AddSkillRequest` | `ProfileControllerTest.postSkills_returns422WhenLevelIsMissing` (mismo mecanismo) |
| `SKILL_LEVEL_REQUIRED` | 422 | `POST …/skills` | `level` | «Elige un nivel.» | `AddSkillRequest` | `ProfileControllerTest.postSkills_returns422WhenLevelIsMissing` |
| `PROFESSIONAL_ROLE_ID_REQUIRED` | 422 | `POST …/target-roles` | `professionalRoleId` | «Selecciona una opción.» | `AddTargetRoleRequest` | `ApiExceptionHandlerTest.everyFieldConstraint_shouldHaveCode` |

Una restricción de Bean Validation sin fila en esta tabla responde `VALIDATION_FAILED` con «Revisa este campo.»; la prueba `everyFieldConstraint_shouldHaveCode` hace fallar el build hasta que se agregue.

## Respuestas sin código

Hoy quedan estas respuestas sin `code`; cada una tiene dueño y fecha en otra tarea.

| HTTP | Título | Cuándo | Quién lo corrige |
|---|---|---|---|
| 400 | Parámetro inválido | El parámetro `lang` del catálogo de roles no es `es` ni `en`; lo arma `ProfessionalRoleController` a mano, sin `code` ni `requestId` y refleja el valor recibido en el `detail` | CM-283 Parte 2 (decidido en CM-271, pregunta 4) |
| 422 | Finalización incompleta | Finalizar el perfil sin cumplir los requisitos. El cuerpo no es un `ProblemDetail` sino `CompletionErrorResponse`: `{"missingRequirements": [...]}`; lleva el encabezado `X-Request-Id` | CM-67 |

Las excepciones estándar de Spring MVC que no figuran aquí (método no permitido, tipo de contenido no soportado, parámetro ausente) las responde la clase base `ResponseEntityExceptionHandler` con sus títulos estándar y sin `code`.

## Cómo se agrega un código

Un código nuevo se agrega aquí con la spec que lo introduce, junto a su excepción de negocio, su estado, su mensaje y su prueba. Un código publicado no se reutiliza ni se renombra.
