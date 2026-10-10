# Errores del servicio

## Formato

Las respuestas de error siguen la [sección 6 del estándar](estandar-backend.md#6-errores) y el [ADR 0001](adr/0001-codigo-de-error-y-request-id.md). Toda respuesta de error es `application/problem+json;charset=UTF-8` (RFC 9457) y lleva:

- `type`, `title`, `status`, `detail` e `instance`, de la norma. `instance` es la ruta de la petición y la pone Spring; las rutas de Perfil solo llevan identificadores UUID, así que no expone datos personales, y el cuerpo es JSON, que el navegador no interpreta como página.
- `code`: código estable. El cliente decide qué mostrar a partir de él, no del texto.
- `requestId`: identificador de la petición. Si llega un `X-Request-Id` válido (`[A-Za-z0-9._-]{1,64}`) se devuelve igual; si no, se genera un UUID. Va también en el encabezado `X-Request-Id` de la respuesta.
- `errors[]` (solo en `VALIDATION_FAILED`): un elemento por campo, con `field`, `code` y `message`. Lo arman Bean Validation y los campos que rechaza el dominio.
- `missingRequirements[]` (solo en `PROFILE_INCOMPLETE`): los requisitos que faltan para finalizar el perfil.

Los mensajes para la persona son los literales del backlog vigente (`09102026_01_Backlog_v6.xlsx`), que es la única fuente de los textos; cada fila cita el CA o la regla de donde sale. Los pocos que el backlog no fija (los errores del framework, el idioma del catálogo y «La fecha de fin debe quedar vacía.») solo los recibe quien llama a la API sin pasar por la interfaz, que impide esos casos; siguen RT-01 y RT-05. El `detail` nunca repite el valor recibido ni el mensaje de una excepción de librería. Un fallo no controlado responde siempre el mensaje genérico de `INTERNAL_ERROR`; el detalle queda solo en el registro del servidor.

Cada rechazo se registra en nivel `WARN` con `code`, `requestId` y `firebaseUid`. `firebaseUid` es el valor de `X-User-Id`, o `-` si falta, está en blanco, mide más de 128 caracteres o trae caracteres de control. El registro nunca lleva nombres, resúmenes, correos ni el valor rechazado. Un fallo no controlado se registra en `ERROR` con `requestId`, `firebaseUid`, el `SQLState` si lo hay, y la clase y la traza de cada excepción de la cadena, sin sus mensajes (`RedactedException`): el de una excepción de persistencia puede traer los valores de las columnas.

## Códigos que el servicio emite

`…` abrevia `/api/v1/profiles/{id}`.

| Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba |
|---|---|---|---|---|---|---|
| `VALIDATION_FAILED` | 422 | `PATCH …`, `POST …/work-experiences`, `POST …/educations`, `POST …/skills`, `POST …/target-roles`, `PATCH …/target-roles/{roleId}` | — | «Revisa los campos marcados.» | `ApiExceptionHandler` (Bean Validation) | `ProfileControllerTest.addToProfile_shouldReturnFieldCode_whenRequiredFieldIsMissingOrBlank` |
| `REQUEST_BODY_INVALID_FORMAT` | 422 | Los mismos endpoints con cuerpo | — | «Revisa el formato de los datos enviados.» | `ApiExceptionHandler` (`HttpMessageNotReadableException`) | `ProfileControllerTest.patchProfile_shouldReturn422_whenBodyIsMalformed` |
| `REQUEST_INVALID_VALUE` | 422 | Cualquier endpoint al que le falte un encabezado obligatorio distinto de `X-User-Id`, y los rechazos del framework sin código propio | — | «Revisa los datos enviados.» | `ApiExceptionHandler` (`MissingRequestHeaderException` y respaldo de los demás rechazos del framework) | `ApiExceptionHandlerTest.missingHeader_shouldReturn422InvalidValue_whenNotTheIdentityHeader`, `ApiExceptionHandlerTest.bindingError_shouldReturn422InvalidValue_whenNotTheIdentityHeader` |
| `IDENTITY_REQUIRED` | 401 | Todo endpoint de `/api/v1/profiles` salvo el catálogo de roles | — | «Tu sesión expiró. Inicia sesión de nuevo.» (RT-02-CA02) | `IdentityRequiredException` (identidad ausente, en blanco o de más de 128 caracteres); `ApiExceptionHandler` (falta `X-User-Id`) | `ProfileControllerTest.postProfiles_shouldReturn401_whenXUserIdHeaderIsMissing`, `ProfileAppServiceTest.getProfile_shouldThrowIdentityRequired_whenUidIsTooLong` |
| `PROFILE_NOT_FOUND` | 404 | Todo endpoint con `{id}` | — | «No encontramos lo que buscabas.» (RT-03) | `ProfileNotFoundException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `PROFILE_NOT_ALLOWED` | 403 | Todo endpoint con `{id}` | — | «No encontramos lo que buscabas.» (RT-03: el mismo texto que el perfil inexistente; el código distingue la causa) | `ProfileAccessDeniedException` | `ProfileControllerTest.getProfile_returns403WhenProfileIsOwnedByAnotherUser` |
| `PROFILE_LIMIT_REACHED` | 409 | `POST /api/v1/profiles` | — | «Tu Plan Free permite 1 Perfil Profesional.» | `ProfileLimitReachedException` (el Usuario ya tenía el máximo de perfiles antes de pedir) | `ProfileAppServiceTest.createProfile_shouldThrowLimitReached_whenUserAlreadyHadOne`, `ProfileControllerTest.postProfiles_shouldReturn409WithPlanMessage_whenUserAlreadyHasProfile` |
| `PROFILE_CREATION_TIMEOUT` | 503 | `POST /api/v1/profiles` | — | «Estamos creando tu perfil. Inténtalo de nuevo en unos segundos.» | `ProfileCreationTimeoutException` (otra creación del mismo Usuario no terminó en 5 s y PostgreSQL cortó la espera con el estado `55P03`; no se creó nada) | `ProfileCreationConcurrencyIT.createProfile_shouldFailFast_whenLockIsHeldLongerThanTimeout`, `ProfileControllerTest.postProfiles_shouldReturn503_whenCreationLockTimesOut` |
| `PROFILE_UPDATE_IN_PROGRESS` | 409 | Todo `PATCH`, `POST` y `DELETE` sobre `/api/v1/profiles/{id}…` | — | «Estamos guardando otro cambio de tu perfil. Inténtalo de nuevo en unos segundos.» | `ProfileUpdateInProgressException` (otra escritura retuvo la fila del perfil más de 2 s y PostgreSQL cortó la espera con el estado `55P03`; no cambió nada) | `ProfileWriteLockIT.write_shouldFailFast_whenProfileRowIsLockedLongerThanTimeout`, `ApiExceptionHandlerTest.profileUpdateInProgress_shouldReturn409_whenThrown` |
| `BIRTH_DATE_UNAVAILABLE` | 503 | `POST /api/v1/profiles/{id}/work-experiences` y `/educations` (desde la validación de fechas con la fecha de nacimiento) | — | «Ocurrió un error. Inténtalo de nuevo.» | `BirthDateUnavailableException`: la réplica local aún no tiene la fecha de nacimiento del Usuario (evento `cuenta.creada` sin procesar); no se guarda nada | `ApiExceptionHandlerTest.birthDateUnavailable_shouldReturn503_whenThrown` |
| `PROFILE_ALREADY_COMPLETED` | 409 | `POST …/completion` | — | «Este perfil ya está activo.» (CA-2.5.11) | `ProfileAlreadyCompletedException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `PROFILE_INCOMPLETE` | 422 | `POST …/completion` (y `POST …/review-requests`, sin HU y fuera del OpenAPI) | — | «Todavía no cumples estos requisitos:», con `missingRequirements[]` | `IncompleteProfileException` | `ProfileControllerTest.postCompletion_shouldReturn422WithCommonShape_whenSummaryAndSkillsAreMissing` |
| `PROFESSIONAL_ROLE_NOT_FOUND` | 404 | `POST …/target-roles`, `PATCH …/target-roles/{roleId}` | — | «No encontramos ese rol en el catálogo.» (CA-2.11.12) | `ProfessionalRoleNotFoundException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE` | 422 | `GET /api/v1/profiles/professional-roles?lang=` | — | «Elige un idioma disponible: español o inglés.» | `UnsupportedLanguageException` | `ProfessionalRoleControllerTest.listAll_shouldReturn422_whenLangIsUnsupported` |
| `TARGET_ROLE_LIMIT_REACHED` | 422 | `POST …/target-roles` | — | «Ya tienes el máximo de 5 roles objetivo.» (CA-2.11.5) | `MaxTargetRolesExceededException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `TARGET_ROLE_ALREADY_EXISTS` | 409 | `POST …/target-roles`, `PATCH …/target-roles/{roleId}` | — | «Ese rol objetivo ya está en tu perfil.» (CA-2.11.4, CA-2.11.8) | `DuplicateTargetRoleException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `TARGET_ROLE_NOT_ALLOWED` | 422 | `DELETE …/target-roles/{roleId}` | — | «No puedes quedarte sin roles objetivo con el perfil activo.» (CA-2.11.3) | `LastTargetRoleException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `SKILL_ALREADY_EXISTS` | 409 | `POST …/skills` | — | «Esa habilidad ya está en tu perfil.» (CA-2.5.2) | `DuplicateSkillException` | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` |
| `ROUTE_NOT_FOUND` | 404 | Cualquier ruta que no existe | — | «No existe la ruta solicitada.» | `ApiExceptionHandler` (`NoResourceFoundException`, `NoHandlerFoundException`) | `ProfileControllerTest.frameworkError_shouldReturnCommonShape_whenRequestIsRejected`, `ResponseCharsetIT.unknownRoute_shouldReturn404WithUtf8_whenRequested` |
| `METHOD_NOT_ALLOWED` | 405 | Cualquier ruta con un método que no admite; conserva el encabezado `Allow` | — | «Método no permitido.» | `ApiExceptionHandler` (`HttpRequestMethodNotSupportedException`) | `ProfileControllerTest.frameworkError_shouldReturnCommonShape_whenRequestIsRejected`, `ProfileControllerTest.deleteProfiles_shouldKeepAllowHeader_whenMethodNotAllowed` |
| `MEDIA_TYPE_NOT_ALLOWED` | 415 | Los endpoints con cuerpo, con un `Content-Type` distinto de `application/json` | — | «Tipo de contenido no admitido.» | `ApiExceptionHandler` (`HttpMediaTypeNotSupportedException`) | `ProfileControllerTest.frameworkError_shouldReturnCommonShape_whenRequestIsRejected` |
| `PROFILE_ID_INVALID_FORMAT` | 422 | Todo endpoint con `{id}` | — | «El identificador del perfil no es válido.» | `ApiExceptionHandler` (`MethodArgumentTypeMismatchException` de `id`) | `ProfileControllerTest.frameworkError_shouldReturnCommonShape_whenRequestIsRejected` |
| `WORK_EXPERIENCE_ID_INVALID_FORMAT` | 422 | `DELETE …/work-experiences/{expId}` | — | «El identificador de la experiencia no es válido.» | `ApiExceptionHandler` (`expId`) | `ErrorCatalogTest.everyErrorCode_shouldBeResponseOrField_whenCatalogLoaded` |
| `EDUCATION_ID_INVALID_FORMAT` | 422 | `DELETE …/educations/{eduId}` | — | «El identificador de la formación no es válido.» | `ApiExceptionHandler` (`eduId`) | `ErrorCatalogTest.everyErrorCode_shouldBeResponseOrField_whenCatalogLoaded` |
| `SKILL_ID_INVALID_FORMAT` | 422 | `DELETE …/skills/{skillId}` | — | «El identificador de la habilidad no es válido.» | `ApiExceptionHandler` (`skillId`) | `ProfileControllerTest.frameworkError_shouldReturnCommonShape_whenRequestIsRejected` |
| `TARGET_ROLE_ID_INVALID_FORMAT` | 422 | `PATCH` y `DELETE …/target-roles/{roleId}` | — | «El identificador del rol objetivo no es válido.» | `ApiExceptionHandler` (`roleId`) | `ErrorCatalogTest.everyErrorCode_shouldBeResponseOrField_whenCatalogLoaded` |
| `MEDIA_TYPE_NOT_ACCEPTABLE` | 406 | Cualquier endpoint, con un `Accept` que no admite JSON | — | «Tipo de respuesta no admitido.» | `ApiExceptionHandler` (`HttpMediaTypeNotAcceptableException`) | `ProfileControllerTest.getProfile_shouldReturn406_whenClientAcceptsOnlyXml` |
| `INTERNAL_ERROR` | 500 | Cualquiera | — | «Ocurrió un error. Inténtalo de nuevo.» | `ApiExceptionHandler` (fallo no controlado, incluida toda `IllegalArgumentException`, y fallos del framework del lado del servidor) | `ApiExceptionHandlerTest.unexpectedException_shouldReturnGeneric500_whenThrown`, `ApiExceptionHandlerTest.illegalArgument_shouldReturnGeneric500_whenThrown`, `ApiExceptionHandlerTest.frameworkServerError_shouldReturnGeneric500_whenThrown` |
| `TARGET_ROLE_NOT_FOUND` | 404 | `PATCH` y `DELETE …/target-roles/{roleId}` | — | «No encontramos lo que buscabas.» (RT-03) | `TargetRoleNotFoundException` | `ProfessionalProfileTest.updateTargetRole_shouldThrowTargetRoleNotFound_whenRoleIsNotInProfile`, `ProfileControllerTest.updateTargetRole_shouldReturn404_whenRoleIsNotInProfile` |
| `WORK_EXPERIENCE_NOT_FOUND` | 404 | `DELETE …/work-experiences/{expId}` | — | «No encontramos lo que buscabas.» (CA-2.4.59) | `WorkExperienceNotFoundException` (ajena o inexistente) | `ProfessionalProfileTest.remove_shouldThrowNotFound_whenElementIsNotInProfile`, `ProfileControllerTest.removeWorkExperience_shouldReturn404_whenExperienceIsNotInProfile` |
| `EDUCATION_NOT_FOUND` | 404 | `DELETE …/educations/{eduId}` | — | «No encontramos lo que buscabas.» (CA-2.4.58) | `EducationNotFoundException` (ajena o inexistente) | `ProfessionalProfileTest.remove_shouldThrowNotFound_whenElementIsNotInProfile` |
| `SKILL_NOT_FOUND` | 404 | `DELETE …/skills/{skillId}` | — | «No encontramos lo que buscabas.» (CA-2.5.23) | `SkillNotFoundException` (ajena o inexistente) | `ProfessionalProfileTest.remove_shouldThrowNotFound_whenElementIsNotInProfile` |

**Pendiente con destino:** quitar la última formación o habilidad de un perfil `COMPLETED` responde hoy 204 y deja el perfil sin ellas. El backlog pide 422 (CA-2.4.37, 2.4.51 y 2.5.12): `EDUCATION_NOT_ALLOWED` es de CM-274 y `SKILL_NOT_ALLOWED`, de CM-66. Hasta entonces los `DELETE` responden 204 o 404.

Un identificador de la ruta mal escrito tiene su propio código por parámetro y responde 422, no 404: así se distingue «mal escrito» de «no existe».

### Campos rechazados por el dominio (`errors[].code`)

Responden igual que Bean Validation: 422 `VALIDATION_FAILED` con «Revisa los campos marcados.» y un elemento por campo en `errors[]`. Los lanza `InvalidFieldsException`: los objetos de valor y las entidades del dominio para obligatorios, largos, signo y fechas, y `CommandValues` para las opciones y el formato de las fechas. Ningún mensaje repite el valor recibido. Los nombres y textos son los que fijan las specs de CM-54, CM-66 y CM-274; los límites son los vigentes (500, 255 y 2000) hasta que esas historias los cambien. Los obligatorios que también vigila Bean Validation (`COMPANY_REQUIRED`, `POSITION_REQUIRED`, `SKILL_NAME_REQUIRED`, `INSTITUTION_REQUIRED` y `DEGREE_REQUIRED`) están en la tabla siguiente, con el mismo texto.

| Código | Endpoints | Campo | Mensaje | Origen | Prueba |
|---|---|---|---|---|---|
| `PROFILE_NAME_REQUIRED` | `PATCH …` | `name` | «Ingresa un nombre para el perfil.» | `ProfileName` | `ProfileValueRulesTest.constructor_shouldRejectField_whenRuleIsBroken` |
| `PROFILE_NAME_TOO_LONG` | `PATCH …` | `name` | «El nombre no puede superar los 255 caracteres.» | `ProfileName` | La misma |
| `SUMMARY_NOT_ALLOWED` | `PATCH …` | `summary` | «No puedes quedarte sin resumen profesional con el perfil activo.» (CA-2.3.12) | `ProfessionalProfile.updateSummary` | `ProfessionalProfileTest.updateSummary_shouldRejectClear_whenProfileIsCompleted` |
| `SUMMARY_TOO_LONG` | `PATCH …` | `summary` | «El resumen no puede superar los 2000 caracteres.» | `ProfessionalSummary` | La misma |
| `SALARY_EXPECTATION_OUT_OF_RANGE` | `PATCH …/salary-expectation` | `amount` | «La expectativa salarial no puede ser negativa.» o «La expectativa salarial no puede tener más de 13 dígitos.» | `SalaryExpectation` | La misma |
| `COMPANY_TOO_LONG` | `POST …/work-experiences` | `company` | «La empresa no puede superar los 500 caracteres.» | `WorkExperience` | La misma |
| `POSITION_TOO_LONG` | `POST …/work-experiences` | `position` | «El cargo no puede superar los 500 caracteres.» | `WorkExperience` | La misma |
| `INSTITUTION_TOO_LONG` | `POST …/educations` | `institution` | «La institución no puede superar los 500 caracteres.» | `Education` | La misma |
| `DEGREE_TOO_LONG` | `POST …/educations` | `degree` | «El título obtenido no puede superar los 500 caracteres.» | `Education` | La misma |
| `SKILL_NAME_TOO_LONG` | `POST …/skills` | `skillName` | «La habilidad no puede superar los 255 caracteres.» | `ProfileSkill` | La misma |
| `DESCRIPTION_TOO_LONG` | `POST …/work-experiences` | `description` | «La descripción no puede superar los 2000 caracteres.» (el criterio CA-2.4.12 pide 500, que llega con CM-274) | `WorkExperience` | La misma |
| `FIELD_OF_STUDY_TOO_LONG` | `POST …/educations` | `fieldOfStudy` | «El área de estudio no puede superar los 500 caracteres.» | `Education` | La misma |
| `END_DATE_REQUIRED` | `POST …/work-experiences` | `endDate` | «Ingresa la fecha de fin.» | `WorkExperience` (estado `ENDED`) | `WorkExperienceTest.ended_requiresEndDate` |
| `END_DATE_NOT_ALLOWED` | `POST …/work-experiences`, `POST …/educations` | `endDate` | «La fecha de fin debe quedar vacía.» | `WorkExperience` (`CURRENT`, `UNKNOWN_END`), `Education` (en curso) | `WorkExperienceTest.current_throwsWhenEndDateIsProvided`, `ProfileValueRulesTest.constructor_shouldRejectField_whenRuleIsBroken` |
| `END_DATE_BEFORE_START_DATE` | `POST …/work-experiences`, `POST …/educations` | `endDate` | «La fecha de fin no puede ser anterior a la de inicio.» | `WorkExperience`, `Education` | `WorkExperienceTest.ended_throwsWhenEndDateBeforeStartDate`, `ProfileValueRulesTest.constructor_shouldRejectField_whenRuleIsBroken`, `ProfileAppServiceTest.addWorkExperience_shouldThrowEndDateBeforeStart_whenEndIsBeforeStart` |
| `START_DATE_INVALID_FORMAT` | `POST …/work-experiences`, `POST …/educations` | `startDate` | «Ingresa una fecha válida con el formato mm/aaaa.» | `CommandValues.yearMonth` | `CommandValuesTest.yearMonth_shouldRejectField_whenFormatIsInvalid`, `ProfileControllerTest.addWorkExperience_shouldReturnStartDateInvalid_whenStartDateIsMalformed` |
| `END_DATE_INVALID_FORMAT` | `POST …/work-experiences`, `POST …/educations` | `endDate` | «Ingresa una fecha válida con el formato mm/aaaa.» | `CommandValues.yearMonth` | La misma de `CommandValuesTest` |
| `PREFERRED_MODALITY_INVALID_VALUE` | `PATCH …` | `preferredModality` | «Selecciona una opción.» | `CommandValues.option` | `ProfileAppServiceTest.updateProfileInfo_shouldThrowModalityInvalid_whenModalityIsUnknown`, `CommandValuesTest.option_shouldRejectField_whenValueIsNotAnOption` |
| `PROVENANCE_INVALID_VALUE` | `PATCH …`, `POST …/work-experiences`, `POST …/educations`, `POST …/skills`, `POST …/target-roles` | `provenance` | «Selecciona una opción.» | `CommandValues.option` | `ProfileAppServiceTest.addSkill_shouldThrowProvenanceInvalid_whenProvenanceIsUnknown` |
| `EMPLOYMENT_STATUS_INVALID_VALUE` | `POST …/work-experiences` | `employmentStatus` | «Selecciona una opción.» | `CommandValues.option` | `ProfileAppServiceTest.addWorkExperience_shouldThrowEmploymentStatusInvalid_whenStatusIsUnknown` |
| `EDUCATION_LEVEL_INVALID_VALUE` | `POST …/educations` | `level` | «Selecciona una opción.» | `CommandValues.option` | `ProfileAppServiceTest.addEducation_shouldThrowEducationLevelInvalid_whenLevelIsUnknown` |
| `SKILL_LEVEL_INVALID_VALUE` | `POST …/skills` | `level` | «Selecciona una opción.» | `CommandValues.option` | `CommandValuesTest.option_shouldRejectField_whenValueIsNotAnOption` |
| `COMPANY_INVALID_CHARACTERS` | `POST …/work-experiences` | `company` | «La empresa tiene caracteres no permitidos.» | `@DomainRule` en el borde y `WorkExperience` en el dominio | `DomainRuleValidatorTest.company_shouldReject_whenTextHasControlCharacter` |
| `POSITION_INVALID_CHARACTERS` | `POST …/work-experiences` | `position` | «El cargo tiene caracteres no permitidos.» | `@DomainRule` en el borde y `WorkExperience` en el dominio | `DomainRuleValidatorTest` |
| `DESCRIPTION_INVALID_CHARACTERS` | `POST …/work-experiences` | `description` | «La descripción tiene caracteres no permitidos.» (admite tabulador, salto de línea y retorno) | `@DomainRule` en el borde y `WorkExperience` en el dominio | `DomainRuleValidatorTest` |
| `INSTITUTION_INVALID_CHARACTERS` | `POST …/educations` | `institution` | «La institución tiene caracteres no permitidos.» | `@DomainRule` en el borde y `Education` en el dominio | `DomainRuleValidatorTest` |
| `DEGREE_INVALID_CHARACTERS` | `POST …/educations` | `degree` | «El título obtenido tiene caracteres no permitidos.» | `@DomainRule` en el borde y `Education` en el dominio | `DomainRuleValidatorTest` |

### Campos rechazados por Bean Validation (`errors[].code`)

La clave es `ClaseDelDto.campo.Restricción`, porque `level` y `provenance` se repiten en varios DTO. Cada `NotBlank` se prueba ausente, `null`, vacío, con espacios y con tabulador; cada `NotNull`, ausente y `null`.

| Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba |
|---|---|---|---|---|---|---|
| `COMPANY_REQUIRED` | 422 | `POST …/work-experiences` | `company` | «Ingresa la empresa.» | `AddWorkExperienceRequest.company.NotBlank` | `ProfileControllerTest.addToProfile_shouldReturnFieldCode_whenRequiredFieldIsMissingOrBlank` |
| `POSITION_REQUIRED` | 422 | `POST …/work-experiences` | `position` | «Ingresa el cargo.» | `AddWorkExperienceRequest.position.NotBlank` | La misma |
| `START_DATE_REQUIRED` | 422 | `POST …/work-experiences`, `POST …/educations` | `startDate` | «Ingresa la fecha de inicio.» | `AddWorkExperienceRequest.startDate.NotBlank`, `AddEducationRequest.startDate.NotBlank` | La misma |
| `EMPLOYMENT_STATUS_REQUIRED` | 422 | `POST …/work-experiences` | `employmentStatus` | «Selecciona una opción.» | `AddWorkExperienceRequest.employmentStatus.NotNull` | La misma |
| `PROVENANCE_REQUIRED` | 422 | `POST …/work-experiences`, `POST …/educations`, `POST …/skills`, `POST …/target-roles` | `provenance` | «Selecciona una opción.» | `AddWorkExperienceRequest.provenance.NotNull`, `AddEducationRequest.provenance.NotBlank`, `AddSkillRequest.provenance.NotBlank`, `AddTargetRoleRequest.provenance.NotNull` | La misma |
| `INSTITUTION_REQUIRED` | 422 | `POST …/educations` | `institution` | «Ingresa la institución.» | `AddEducationRequest.institution.NotBlank` | La misma |
| `DEGREE_REQUIRED` | 422 | `POST …/educations` | `degree` | «Ingresa el título obtenido.» | `AddEducationRequest.degree.NotBlank` | La misma |
| `EDUCATION_LEVEL_REQUIRED` | 422 | `POST …/educations` | `level` | «Elige un nivel educativo.» | `AddEducationRequest.level.NotBlank` | La misma |
| `SALARY_EXPECTATION_REQUIRED` | 422 | `PATCH …/salary-expectation` | `amount` | «Ingresa la expectativa salarial.» | `UpdateSalaryExpectationRequest.amount.NotNull` | `ProfileControllerTest.updateSalaryExpectation_shouldReturnAmountRequired_whenBodyIsEmpty` |
| `SKILL_NAME_REQUIRED` | 422 | `POST …/skills` | `skillName` | «Ingresa una habilidad.» | `AddSkillRequest.skillName.NotBlank` | La misma |
| `SKILL_LEVEL_REQUIRED` | 422 | `POST …/skills` | `level` | «Elige un nivel.» | `AddSkillRequest.level.NotBlank` | La misma |
| `PROFESSIONAL_ROLE_ID_REQUIRED` | 422 | `POST …/target-roles`, `PATCH …/target-roles/{roleId}` | `professionalRoleId` | «Selecciona una opción.» | `AddTargetRoleRequest.professionalRoleId.NotNull`, `UpdateTargetRoleRequest.professionalRoleId.NotNull` | La misma y `ProfileControllerTest.updateTargetRole_shouldReturnRoleIdRequired_whenBodyIsEmpty` |

Una restricción de Bean Validation sin fila en esta tabla responde `VALIDATION_FAILED` con «Revisa este campo.»; la prueba `ErrorCatalogTest.everyFieldConstraint_shouldHaveCode_whenDtosAreScanned` hace fallar el build hasta que se agregue.

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

Toda respuesta de error lleva `code` y ninguna devuelve el texto de Spring. Las excepciones de Spring MVC sin fila propia en el catálogo se resuelven por su estado:

- **400** (parámetro obligatorio ausente, error de enlace, validación de un parámetro del método): es un dato del cliente y responde 422 `REQUEST_INVALID_VALUE`, con el origen en `WARN`.
- **5xx** (cuerpo que no se pudo escribir, conversión no soportada): 500 `INTERNAL_ERROR`.
- **Cualquier otro estado** (por ejemplo, un 409 o un 413 que ningún endpoint produce hoy): no se disfraza de error del cliente. Responde 500 `INTERNAL_ERROR` y se registra en `ERROR` con el estado y el origen, para darle su código en el catálogo.

`ApiExceptionHandlerTest.springExceptions_shouldAllBeKnown_whenFrameworkIsUpgraded` lista cada excepción que resuelve Spring MVC con la respuesta decidida; si una versión nueva de Spring agrega otra, la prueba falla antes de que llegue a producción.

## Cómo se agrega un código

Un código nuevo se agrega con la spec que lo introduce, en el mismo cambio que su excepción de negocio, su fila en `ErrorCatalog` (estado, título y mensaje), su prueba y su fila en este archivo. `ErrorCatalogTest` hace fallar el build si un código queda sin fila. Un código publicado no se reutiliza ni se renombra.
