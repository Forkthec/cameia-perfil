# Errores del servicio

## Formato

Las respuestas de error siguen la [sección 6 del estándar](estandar-backend.md#6-errores) y el [ADR 0001](adr/0001-codigo-de-error-y-request-id.md). Toda respuesta de error es `application/problem+json;charset=UTF-8` (RFC 9457) y lleva:

- `type`, `title`, `status`, `detail` e `instance`, de la norma. `instance` es la ruta de la petición y la pone Spring; las rutas de Perfil solo llevan identificadores UUID, así que no expone datos personales, y el cuerpo es JSON, que el navegador no interpreta como página.
- `code`: código estable. El cliente decide qué mostrar a partir de él, no del texto.
- `requestId`: identificador de la petición. Si llega un `X-Request-Id` válido (`[A-Za-z0-9._-]{1,64}`) se devuelve igual; si no, se genera un UUID. Va también en el encabezado `X-Request-Id` de la respuesta.
- `errors[]` (solo en `VALIDATION_FAILED`): un elemento por campo, con `field`, `code` y `message`.
- `missingRequirements[]` (solo en `PROFILE_INCOMPLETE`): los requisitos que faltan para finalizar el perfil.

El `detail` nunca repite el valor recibido ni el mensaje de una excepción de librería. Un fallo no controlado responde siempre el mensaje genérico de `INTERNAL_ERROR`; el detalle queda solo en el registro del servidor.

Cada rechazo se registra en nivel `WARN` con `code`, `requestId` y `firebaseUid`. `firebaseUid` es el valor de `X-User-Id`, o `-` si falta, está en blanco, mide más de 128 caracteres o trae caracteres de control. El registro nunca lleva nombres, resúmenes, correos ni el valor rechazado.

## Códigos que el servicio emite

`…` abrevia `/api/v1/profiles/{id}`.

| Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba |
|---|---|---|---|---|---|---|
| `VALIDATION_FAILED` | 422 | `PATCH …`, `POST …/work-experiences`, `POST …/educations`, `POST …/skills`, `POST …/target-roles`, `PATCH …/target-roles/{roleId}` | — | «Revisa los campos marcados.» | `ApiExceptionHandler` (Bean Validation) | `ProfileControllerTest.addToProfile_shouldReturnFieldCode_whenRequiredFieldIsMissingOrBlank` |
| `REQUEST_BODY_INVALID_FORMAT` | 422 | Los mismos endpoints con cuerpo | — | «Revisa el formato de los datos enviados.» | `ApiExceptionHandler` (`HttpMessageNotReadableException`) | `ProfileControllerTest.patchProfile_shouldReturn422_whenBodyIsMalformed` |
| `REQUEST_INVALID_VALUE` | 422 | Los mismos endpoints con cuerpo; cualquier endpoint al que le falte un encabezado obligatorio distinto de `X-User-Id` | — | «Revisa los datos enviados.» | `ApiExceptionHandler` (`IllegalArgumentException`, `DateTimeException`, `MissingRequestHeaderException` y respaldo de los demás rechazos del framework) | `ProfileControllerTest.addWorkExperience_shouldReturn422_whenStartDateIsMalformed`, `ApiExceptionHandlerTest.illegalArgument_shouldHideMessage_whenThrown` |
| `IDENTITY_REQUIRED` | 401 | Todo endpoint de `/api/v1/profiles` salvo el catálogo de roles | — | «Identidad del usuario requerida» | `IdentityRequiredException` (identidad ausente, en blanco o de más de 128 caracteres); `ApiExceptionHandler` (falta `X-User-Id`) | `ProfileControllerTest.postProfiles_shouldReturn401_whenXUserIdHeaderIsMissing`, `ProfileAppServiceTest.getProfile_shouldThrowIdentityRequired_whenUidIsTooLong` |
| `PROFILE_NOT_FOUND` | 404 | Todo endpoint con `{id}` | — | «No se encontró el perfil con id …» | `ProfileNotFoundException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `PROFILE_NOT_ALLOWED` | 403 | Todo endpoint con `{id}` | — | «No tienes permiso para acceder a este perfil» | `ProfileAccessDeniedException` | `ProfileControllerTest.getProfile_returns403WhenProfileIsOwnedByAnotherUser` |
| `PROFILE_LIMIT_REACHED` | 409 | `POST /api/v1/profiles` | — | «Tu Plan Free permite 1 Perfil Profesional.» | `ProfileLimitReachedException` (el Usuario ya tenía el máximo de perfiles antes de pedir) | `ProfileAppServiceTest.createProfile_shouldThrowLimitReached_whenUserAlreadyHadOne`, `ProfileControllerTest.postProfiles_shouldReturn409WithPlanMessage_whenUserAlreadyHasProfile` |
| `PROFILE_ALREADY_COMPLETED` | 409 | `POST …/completion` | — | «El perfil ya está en estado COMPLETED» | `ProfileAlreadyCompletedException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `PROFILE_INCOMPLETE` | 422 | `POST …/completion`, `POST …/review-requests` | — | «Todavía no cumples estos requisitos:», con `missingRequirements[]` | `IncompleteProfileException` | `ProfileControllerTest.postCompletion_shouldReturn422WithCommonShape_whenSummaryAndSkillsAreMissing` |
| `PROFESSIONAL_ROLE_NOT_FOUND` | 404 | `POST …/target-roles`, `PATCH …/target-roles/{roleId}` | — | «Rol profesional no encontrado: …» | `ProfessionalRoleNotFoundException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE` | 422 | `GET /api/v1/profiles/professional-roles?lang=` | — | «Elige un idioma disponible: español o inglés.» | `UnsupportedLanguageException` | `ProfessionalRoleControllerTest.listAll_shouldReturn422_whenLangIsUnsupported` |
| `TARGET_ROLE_LIMIT_REACHED` | 422 | `POST …/target-roles` | — | «El perfil ya tiene el máximo de N roles objetivo permitidos» | `MaxTargetRolesExceededException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `TARGET_ROLE_ALREADY_EXISTS` | 409 | `POST …/target-roles` | — | «Ya existe un rol objetivo con el rol '…' en este perfil» | `DuplicateTargetRoleException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `TARGET_ROLE_NOT_ALLOWED` | 422 | `DELETE …/target-roles/{roleId}` | — | «El perfil debe tener al menos un rol objetivo; no se puede eliminar el último» | `LastTargetRoleException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `SKILL_ALREADY_EXISTS` | 409 | `POST …/skills` | — | «La habilidad '…' ya está asociada al perfil» | `DuplicateSkillException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `ROUTE_NOT_FOUND` | 404 | Cualquier ruta que no existe | — | «La ruta solicitada no existe.» | `ApiExceptionHandler` (`NoResourceFoundException`, `NoHandlerFoundException`) | `ProfileControllerTest.frameworkError_shouldReturnCommonShape_whenRequestIsRejected`, `ResponseCharsetIT.unknownRoute_shouldReturn404WithUtf8_whenRequested` |
| `METHOD_NOT_ALLOWED` | 405 | Cualquier ruta con un método que no admite; conserva el encabezado `Allow` | — | «La operación no está permitida en esta ruta.» | `ApiExceptionHandler` (`HttpRequestMethodNotSupportedException`) | `ProfileControllerTest.frameworkError_shouldReturnCommonShape_whenRequestIsRejected`, `ProfileControllerTest.deleteProfiles_shouldKeepAllowHeader_whenMethodNotAllowed` |
| `CONTENT_TYPE_NOT_ALLOWED` | 415 | Los endpoints con cuerpo, con un `Content-Type` distinto de `application/json` | — | «Envía los datos en formato JSON.» | `ApiExceptionHandler` (`HttpMediaTypeNotSupportedException`) | `ProfileControllerTest.frameworkError_shouldReturnCommonShape_whenRequestIsRejected` |
| `PROFILE_ID_INVALID_FORMAT` | 422 | Todo endpoint con `{id}` | — | «El identificador del perfil no es válido.» | `ApiExceptionHandler` (`MethodArgumentTypeMismatchException` de `id`) | `ProfileControllerTest.frameworkError_shouldReturnCommonShape_whenRequestIsRejected` |
| `WORK_EXPERIENCE_ID_INVALID_FORMAT` | 422 | `DELETE …/work-experiences/{expId}` | — | «El identificador de la experiencia no es válido.» | `ApiExceptionHandler` (`expId`) | `ErrorCatalogTest.everyErrorCode_shouldBeResponseOrField_whenCatalogLoaded` |
| `EDUCATION_ID_INVALID_FORMAT` | 422 | `DELETE …/educations/{eduId}` | — | «El identificador de la formación no es válido.» | `ApiExceptionHandler` (`eduId`) | `ErrorCatalogTest.everyErrorCode_shouldBeResponseOrField_whenCatalogLoaded` |
| `SKILL_ID_INVALID_FORMAT` | 422 | `DELETE …/skills/{skillId}` | — | «El identificador de la habilidad no es válido.» | `ApiExceptionHandler` (`skillId`) | `ProfileControllerTest.frameworkError_shouldReturnCommonShape_whenRequestIsRejected` |
| `TARGET_ROLE_ID_INVALID_FORMAT` | 422 | `PATCH` y `DELETE …/target-roles/{roleId}` | — | «El identificador del rol objetivo no es válido.» | `ApiExceptionHandler` (`roleId`) | `ErrorCatalogTest.everyErrorCode_shouldBeResponseOrField_whenCatalogLoaded` |
| `ACCEPT_TYPE_NOT_ALLOWED` | 406 | Cualquier endpoint, con un `Accept` que no admite JSON | — | «La respuesta solo está disponible en formato JSON.» | `ApiExceptionHandler` (`HttpMediaTypeNotAcceptableException`) | `ProfileControllerTest.getProfile_shouldReturn406_whenClientAcceptsOnlyXml` |
| `INTERNAL_ERROR` | 500 | Cualquiera | — | «Ocurrió un error. Inténtalo de nuevo.» | `ApiExceptionHandler` (fallo no controlado y fallos del framework del lado del servidor) | `ApiExceptionHandlerTest.unexpectedException_shouldReturnGeneric500_whenThrown`, `ApiExceptionHandlerTest.frameworkServerError_shouldReturnGeneric500_whenThrown` |

Un identificador de la ruta mal escrito tiene su propio código por parámetro y responde 422, no 404: así se distingue «mal escrito» de «no existe».

### Códigos de campo (`errors[].code`)

La clave es `ClaseDelDto.campo.Restricción`, porque `level` y `provenance` se repiten en varios DTO. Cada `NotBlank` se prueba ausente, `null`, vacío, con espacios y con tabulador; cada `NotNull`, ausente y `null`.

| Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba |
|---|---|---|---|---|---|---|
| `COMPANY_REQUIRED` | 422 | `POST …/work-experiences` | `company` | «Ingresa la empresa.» | `AddWorkExperienceRequest.company.NotBlank` | `ProfileControllerTest.addToProfile_shouldReturnFieldCode_whenRequiredFieldIsMissingOrBlank` |
| `POSITION_REQUIRED` | 422 | `POST …/work-experiences` | `position` | «Ingresa el cargo.» | `AddWorkExperienceRequest.position.NotBlank` | La misma |
| `START_DATE_REQUIRED` | 422 | `POST …/work-experiences` | `startDate` | «Ingresa la fecha de inicio.» | `AddWorkExperienceRequest.startDate.NotBlank` | La misma |
| `EMPLOYMENT_STATUS_REQUIRED` | 422 | `POST …/work-experiences` | `employmentStatus` | «Selecciona una opción.» | `AddWorkExperienceRequest.employmentStatus.NotNull` | La misma |
| `PROVENANCE_REQUIRED` | 422 | `POST …/work-experiences`, `POST …/skills`, `POST …/target-roles` | `provenance` | «Selecciona una opción.» | `AddWorkExperienceRequest.provenance.NotNull`, `AddSkillRequest.provenance.NotBlank`, `AddTargetRoleRequest.provenance.NotNull` | La misma |
| `SKILL_NAME_REQUIRED` | 422 | `POST …/skills` | `skillName` | «Ingresa una habilidad.» | `AddSkillRequest.skillName.NotBlank` | La misma |
| `SKILL_LEVEL_REQUIRED` | 422 | `POST …/skills` | `level` | «Elige un nivel.» | `AddSkillRequest.level.NotBlank` | La misma |
| `PROFESSIONAL_ROLE_ID_REQUIRED` | 422 | `POST …/target-roles` | `professionalRoleId` | «Selecciona una opción.» | `AddTargetRoleRequest.professionalRoleId.NotNull` | La misma |

Una restricción de Bean Validation sin fila en esta tabla responde `VALIDATION_FAILED` con «Revisa este campo.»; la prueba `ErrorCatalogTest.everyFieldConstraint_shouldHaveCode` hace fallar el build hasta que se agregue.

## Estados que difieren del mapa base

| Caso | Mapa base del estándar | Perfil | Por qué |
|---|---|---|---|
| Validación de campos (`VALIDATION_FAILED`) | 422 | 422 | Igual al mapa base y a Cuentas |
| Cuerpo ilegible (`REQUEST_BODY_INVALID_FORMAT`) | 400 | 422 | Para el Usuario es el mismo problema que un campo inválido: revisar lo enviado. Ver [ADR 0002](adr/0002-validacion-en-422.md) |

## Causas nuevas del vocabulario

Este catálogo usa dos causas que el vocabulario común de códigos no tenía:

- `ALREADY_COMPLETED`: la operación pide pasar a un estado final en el que el recurso ya está (`PROFILE_ALREADY_COMPLETED`).
- `INCOMPLETE`: el recurso no cumple los requisitos para pasar al estado pedido (`PROFILE_INCOMPLETE`).

## Respaldo para los errores del framework

Toda respuesta de error lleva `code`. Las excepciones de Spring MVC sin fila propia en el catálogo (por ejemplo, un parámetro obligatorio ausente o un error de enlace) responden 422 `REQUEST_INVALID_VALUE`; las del lado del servidor (por ejemplo, un cuerpo que no se pudo escribir), 500 `INTERNAL_ERROR`. Ninguna devuelve el texto de Spring. El registro guarda la clase y el método donde se originó el rechazo, con `requestId` y `firebaseUid`, para poder darle un código propio si aparece en el uso real.

## Cómo se agrega un código

Un código nuevo se agrega con la spec que lo introduce, en el mismo cambio que su excepción de negocio, su fila en `ErrorCatalog` (estado, título y mensaje), su prueba y su fila en este archivo. `ErrorCatalogTest` hace fallar el build si un código queda sin fila. Un código publicado no se reutiliza ni se renombra.
