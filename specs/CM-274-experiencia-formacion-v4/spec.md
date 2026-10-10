# Spec — CM-274: experiencia laboral y formación académica (HU-2.4)

- **Tarea:** CM-274 · subtarea «Ajustes v4 – Backend Perfil (HU-2.4)» · padre CM-18 «HU-2.4 Llenado Manual: Experiencia Laboral y Educación» · responsable: Paula Andrea Muñoz Delgado
- **Repositorio:** `cameia-perfil`. Código de referencia: `origin/develop` `2a54ca7` (9-oct-2026). Toda ancla `archivo:método` de esta spec se comprobó en ese SHA.
- **Requisitos:** backlog `09102026_01_Backlog_v6.xlsx`, hoja HE-02, HU-2.4 (59 CA; CA-2.4.4 no existe), HT-04 y las reglas RT-01, RT-02, RT-03, RT-05, RT-06 y RT-07. Los CA son la única fuente de requisitos y de textos.
- **Estado:** pendiente de aprobación de Paula. Puerta de listo: ver la sección 23. Las decisiones de Paula están en la sección 17 con su fecha; lo que sigue abierto está en la sección 18.
- **Dependencias:** en Perfil, el bloque P1 de CM-279 se fusiona antes que esta tarea (decisiones P-09 y P-14 de CM-279): deja la migración V5, el reloj `ClockConfig`, el código `BIRTH_DATE_UNAVAILABLE` y el registro de los 5xx en `ERROR`. El bloque F usa además su puerto `BirthDateReplica`. CM-54, CM-66 y CM-67 reutilizan la base del bloque A (sección 19).

## 1. Objetivo

Que `POST` y `DELETE` de `/api/v1/profiles/{id}/work-experiences` y `/api/v1/profiles/{id}/educations` cumplan cada CA de HU-2.4 con el estado, el `code`, el campo y el mensaje literal del CA; que ninguna entrada del cliente produzca 500; que los máximos (4 y 5) y el mínimo de formación en un perfil Activo se respeten con dos pestañas abiertas; y que las fechas se comparen por mes y año en UTC con un reloj inyectable.

## 2. Estado real del código y causa raíz de cada defecto

Cada defecto se reproduce **antes** con una prueba automática y una petición de Postman que fallan (tarjeta T-A0, con la salida real pegada en el PR) y se demuestra **después** con las mismas pruebas en verde.

| # | Defecto | Causa raíz comprobada | Reproducción antes (resultado esperado por lectura del código) | CA |
|---|---|---|---|---|
| D1 | Una experiencia sin fecha de inicio se rechaza | `AddWorkExperienceRequest.startDate` tiene `@NotBlank`; detrás, `AddWorkExperienceCommand` lanza `IllegalArgumentException`, `WorkExperience` hace `Objects.requireNonNull(startDate)`, `ProfessionalProfileMapping.toWorkExpEntity`/`toWorkExpDomain` llaman `toFirstDayOfMonth`/`toYearMonth` sin comprobar nulo, `ProfileResponse.WorkExperienceItem.from` llama `formatYearMonth(null)` y la columna `fecha_inicio` es `NOT NULL`. Quitar solo el `@NotBlank` lleva a **500** (`IllegalArgumentException` y `NullPointerException` caen en `ApiExceptionHandler.handleUnexpected`) | `POST …/work-experiences` `{"company":"Acme","position":"Dev","employmentStatus":"UNKNOWN_END","provenance":"MANUAL"}` → hoy 422 `START_DATE_REQUIRED`; se espera 201 | 2.4.15, 2.4.22, 2.4.23, 2.4.42, 2.4.53 |
| D2 | Longitudes de 500 (empresa, cargo, institución, título) y 2000 (descripción) | `WorkExperience.MAX_TEXT_LENGTH = 500`, `MAX_DESCRIPTION_LENGTH = 2000`, `Education.MAX_TEXT_LENGTH = 500`; columnas `VARCHAR(500)`/`VARCHAR(2000)` en V1 | Empresa de 101 → hoy 201; se espera 422 `COMPANY_TOO_LONG` «La empresa no puede superar los 100 caracteres.» | 2.4.12, 2.4.17 a 2.4.19, 2.4.27, 2.4.28 |
| D3 | La longitud se cuenta en unidades UTF-16, sin recortar y sin NFC | `FieldRules.optionalText` usa `value.length()` y no recorta; no hay normalización en ningún punto de Perfil (`git grep Normalizer` vacío) | Empresa de 99 letras + «😀» (100 caracteres) → con el límite de 100 se rechazaría por contar 101; «  Acme  » se guarda con los espacios | RT-01 (C-07) |
| D4 | No hay máximo de 4 experiencias ni de 5 formaciones | `ProfessionalProfile.addWorkExperience`/`addEducation` agregan sin comprobar | Quinta experiencia → hoy 201; se espera 409 `WORK_EXPERIENCE_LIMIT_REACHED` | 2.4.47 a 2.4.49, 2.4.10 |
| D5 | Se puede eliminar la última formación de un perfil Activo | `ProfessionalProfile.removeEducation` solo comprueba la pertenencia | Perfil `COMPLETED` con 1 formación, `DELETE` → hoy 204 y el perfil queda Activo sin formación; se espera 409 `EDUCATION_NOT_ALLOWED` | 2.4.37, 2.4.51 |
| D6 | Dos pestañas pueden saltarse máximos y mínimos | `ProfileAppService.loadForUser` lee con `repository.findById` sin bloqueo; dos transacciones leen el mismo estado y ambas escriben | Dos `DELETE` simultáneos sobre un perfil Activo con 2 formaciones → hoy pueden responder 204 los dos | 2.4.48, 2.4.51 |
| D7 | No se rechazan fechas posteriores al mes actual | No hay regla ni reloj (`git grep Clock` vacío) | Inicio `2099-01` → hoy 201; se espera 422 `START_DATE_IN_THE_FUTURE` | 2.4.24 a 2.4.26, 2.4.33 a 2.4.35, 2.4.54, 2.4.55 |
| D8 | Los errores del dominio llegan de a uno | `InvalidFieldsException.of` se lanza en la primera regla que falla (`FieldRules`, `validateEndDate`) | Empresa de 101 y cargo de 101 → hoy un solo elemento en `errors` | RT-01-CA04 |
| D9 | La formación acepta `"2024"` como enero de 2024 | `CommandValues.yearMonth(value, true, …)` desde `ProfileAppService.startDate(…, true)` | `startDate: "2024"` en formación → hoy 201; se espera 422 `START_DATE_INVALID_FORMAT` (decisión de Paula del 6-oct) | 2.4.44 |
| D10 | Un carácter de control en un texto produce 500 | PostgreSQL rechaza U+0000 en `VARCHAR` (`SQLState 22021`); la excepción llega a `handleUnexpected`. No hay regla de caracteres | `company: "Acme\u0000"` → 500 `INTERNAL_ERROR` | RT-01, R3 (texto del mensaje: D11) |
| D11 | `employmentStatus: ""` y `provenance: ""` en la experiencia se informan como valor inválido y no como faltante | `@NotNull` en lugar de `@NotBlank` en `AddWorkExperienceRequest` | `employmentStatus: ""` → hoy `EMPLOYMENT_STATUS_INVALID_VALUE`; se espera `EMPLOYMENT_STATUS_REQUIRED` | RT-01-CA02 |
| D12 | Reconstruir desde la base vuelve a validar | `ProfessionalProfileMapping.toWorkExpDomain`/`toEducationDomain` usan el constructor que valida: al bajar el límite a 100, una fila guardada con 101 haría fallar el `GET` del perfil | Fila con empresa de 150 caracteres → `GET` responde 422 | Fiabilidad |
| D13 | Esquema sin restricciones nombradas, sin `CHECK` y sin índice en las claves foráneas | V1 crea `experiencia_laboral` y `educacion` con PK y FK sin nombre propio (`experiencia_laboral_pkey`, `experiencia_laboral_perfil_id_fkey`), sin `CHECK` de enumerados, fechas ni coherencia estado–fin, sin índice en `perfil_id` | Consulta a `pg_constraint` | Estándar de base de datos |
| D14 | Los DTO no tienen `@Schema` | `AddWorkExperienceRequest`, `AddEducationRequest`, `ProfileResponse.WorkExperienceItem` y `EducationItem` sin anotaciones de OpenAPI | `GET /v3/api-docs` sin límites ni ejemplos en esos campos | Documentación |
| D15 | Ninguna prueba vigila que cada `ErrorCode` esté documentado y probado | `ErrorCodeTest` solo comprueba forma y unicidad | — | Documentación |

Lo que **ya cumple** en `2a54ca7` (se conserva y se prueba con el `code`): 204 al eliminar (CA-2.4.11, 2.4.38, 2.4.46); 404 `WORK_EXPERIENCE_NOT_FOUND` y `EDUCATION_NOT_FOUND` con «No encontramos lo que buscabas.» para un registro ajeno o inexistente (CA-2.4.58, 2.4.59); 403 `PROFILE_NOT_ALLOWED` (CA-2.4.39); `END_DATE_REQUIRED`, `END_DATE_NOT_ALLOWED`, `END_DATE_BEFORE_START_DATE`, formato estricto `AAAA-MM` y opciones con «Selecciona una opción.» (`CommandValues`).

## 3. Bloques de PR (stack corto)

| PR | Bloque | Rama | Contenido | Líneas (estimadas) | Horas |
|---|---|---|---|---|---|
| 0 | Spec | `CM-274-experiencia-formacion-v4` | `spec.md`, `plan.md` y `tasks.md` aprobados (solo documentos; precedente: spec de CM-36 en PR propio, #38) | ≈ 1430 | 0,2 |
| 1 | A · Base de escritura | `CM-274-base-escritura-perfil` | Texto normalizado, restricciones de borde reutilizables, acumulador de errores, bloqueo del perfil con espera máxima, reloj UTC inyectado, `FieldValues` en el dominio, creación alineada, prueba de catálogo documentado, ADR 0005 | ≈ 900 | 4,5 |
| 2 | B · Experiencia laboral | `CM-274-experiencia-laboral` | Reglas de la experiencia, máximo de 4, migración de `experiencia_laboral`, OpenAPI | ≈ 800 | 4 |
| 3 | C · Formación académica | `CM-274-formacion-academica` | Reglas de la formación, máximo de 5, mínimo en perfil Activo con concurrencia, migración de `educacion`, OpenAPI | ≈ 800 | 4 |
| 4 | D · Postman de la experiencia | `CM-274-postman-experiencia` | Carpeta HU-2.4 con las peticiones de `work-experiences` | ≈ 700 | 2 |
| 5 | E · Postman de la formación y cierre | `CM-274-postman-formacion` | Peticiones de `educations`, entorno local, corrida de Newman completa | ≈ 700 | 2 |
| 6 | F · Fecha de nacimiento | `CM-274-fecha-nacimiento` | Reglas de los 15 años y del nacimiento, 503 sin réplica; **después de que CM-279 fusione su réplica** | ≈ 600 | 3 |

Total: ≈ 19,7 h (el backlog estima 3,5 h; la diferencia se informa a Vela con la estimación real cuando la spec esté aprobada). Los PR 1 a 5 forman un stack de 5; el PR 0 se fusiona antes y el 6 sale aparte de `develop`.

## 4. Trazabilidad CA → requisito → prueba → Postman

Clases de prueba (todas nuevas o existentes en `src/test/java/co/edu/unicauca/cameia/perfil/`): `WorkExperienceTest` (dominio), `EducationTest` (dominio, nueva), `ProfessionalProfileTest` (dominio), `ProfileAppServiceTest` (servicio), `ProfileControllerTest` (web), `WorkHistoryEndToEndIT` (HTTP con puerto aleatorio y PostgreSQL real, nueva), `ProfileWriteLockIT` (concurrencia, nueva), `WorkHistorySchemaIT` (migraciones, nueva). En Postman, carpeta `HU-2.4` con subcarpetas `POST work-experiences`, `DELETE work-experiences/{expId}`, `POST educations`, `DELETE educations/{eduId}`.

| CA | Qué exige (literal) | REQ | Prueba (nombre exacto) | Petición de Postman |
|---|---|---|---|---|
| 2.4.1 | 201; `CURRENT`, `endDate` null, procedencia MANUAL | REQ-EF-20 | `WorkHistoryEndToEndIT.addWorkExperience_shouldReturn201WithNullEndDate_whenCurrent` | `201 actual sin fecha de fin (CA-2.4.1)` |
| 2.4.2 | 422 en `endDate` «Ingresa la fecha de fin.» | REQ-EF-23 | `WorkExperienceTest.create_shouldRejectEndDateRequired_whenEndedWithoutEndDate` · `ProfileControllerTest.addWorkExperience_shouldReturnEndDateRequired_whenEndedWithoutEndDate` | `422 terminada sin fecha de fin (CA-2.4.2)` |
| 2.4.3 | 422 en `company` «Ingresa la empresa.» o en `position` «Ingresa el cargo.», vacío o solo espacios | REQ-EF-21 | `ProfileControllerTest.addWorkExperience_shouldReturnRequired_whenTextIsMissingOrBlank` (parametrizada: ausente, `null`, `""`, `"   "`, `"\t\n"`, `" "`) | `422 empresa vacía`, `422 empresa solo espacios`, `422 cargo vacío` |
| 2.4.5 | 201 con periodos solapados | REQ-EF-20 | `WorkHistoryEndToEndIT.addWorkExperience_shouldAcceptOverlap_whenPeriodsOverlap` | `201 periodo solapado (CA-2.4.5)` |
| 2.4.6 | 422 en `institution`, `degree`, `level`, `startDate` con sus cuatro textos | REQ-EF-31 | `ProfileControllerTest.addEducation_shouldReturnAllRequiredFields_whenAllAreMissing` (los cuatro a la vez) | `422 formación sin los cuatro obligatorios (CA-2.4.6)` |
| 2.4.7 | 201, `endDate` null, en curso | REQ-EF-30 | `WorkHistoryEndToEndIT.addEducation_shouldReturn201WithNullEndDate_whenInProgress` | `201 formación en curso (CA-2.4.7)` |
| 2.4.8 | 201 sin en curso y sin fin | REQ-EF-30 | `EducationTest.create_shouldAccept_whenNotInProgressWithoutEndDate` | `201 formación sin fecha de fin (CA-2.4.8)` |
| 2.4.9 | 422 en `level` con `DOCTORATE` | REQ-EF-32 | `ProfileControllerTest.addEducation_shouldReturnLevelInvalid_whenLevelIsUnknown` (parametrizada: `DOCTORATE`, `undergraduate`) | `422 nivel DOCTORATE (CA-2.4.9)` |
| 2.4.10 | 201 la quinta formación | REQ-EF-35 | `ProfessionalProfileTest.addEducation_shouldAcceptFifth_whenProfileHasFour` | `201 quinta formación (CA-2.4.10)` |
| 2.4.11 | 204; Borrador con una formación, sin mínimo | REQ-EF-36 | `ProfessionalProfileTest.removeEducation_shouldRemoveLast_whenProfileIsInProgress` · `WorkHistoryEndToEndIT.removeEducation_shouldReturn204_whenDraftHasOnlyOne` | `204 única formación en Borrador (CA-2.4.11)` |
| 2.4.12 | 422 en `description` «La descripción no puede superar los 500 caracteres.» con 501 | REQ-EF-22 | `ProfileControllerTest.addWorkExperience_shouldReturnTooLong_whenTextExceedsLimit` (fila `description` 501) | `422 descripción de 501 (CA-2.4.12)` |
| 2.4.13 | 201 con fin igual al inicio | REQ-EF-24 | `WorkExperienceTest.create_shouldAccept_whenEndEqualsStart` | `201 fin igual al inicio (CA-2.4.13)` |
| 2.4.14 | 422 en `endDate` «La fecha de fin no puede ser anterior a la de inicio.» | REQ-EF-24 | `WorkExperienceTest.create_shouldRejectEndBeforeStart_whenEndIsOneMonthEarlier` | `422 fin antes del inicio (CA-2.4.14)` |
| 2.4.15 | 201 `UNKNOWN_END` sin fechas ni descripción | REQ-EF-20, REQ-EF-25 | `WorkHistoryEndToEndIT.addWorkExperience_shouldReturn201WithoutDates_whenUnknownEndHasNoDates` | `201 sin fechas ni descripción (CA-2.4.15)` |
| 2.4.16 | 422 en `endDate` con `CURRENT` y fin | REQ-EF-23 | `WorkExperienceTest.create_shouldRejectEndDateNotAllowed_whenStatusIsNotEnded` (parametrizada: `CURRENT`, `UNKNOWN_END`) | `422 actual con fecha de fin (CA-2.4.16)` |
| 2.4.17 | 201 empresa y cargo de 100 | REQ-EF-21 | `ProfileControllerTest.addWorkExperience_shouldAccept_whenTextIsAtLimit` | `201 empresa y cargo de 100 (CA-2.4.17)` |
| 2.4.18 | 422 «La empresa no puede superar los 100 caracteres.» / «El cargo no puede superar los 100 caracteres.» | REQ-EF-21 | `ProfileControllerTest.addWorkExperience_shouldReturnTooLong_whenTextExceedsLimit` | `422 empresa de 101`, `422 cargo de 101` |
| 2.4.19 | 201 descripción de 500 | REQ-EF-22 | `ProfileControllerTest.addWorkExperience_shouldAccept_whenTextIsAtLimit` (fila `description` 500) | `201 descripción de 500 (CA-2.4.19)` |
| 2.4.20 | 201 inicio en el mes de los 15 años | REQ-EF-60 | `WorkExperienceTest.create_shouldAccept_whenStartIsMonthOfFifteenthBirthday` | `201 inicio a los 15 años (CA-2.4.20)` (PR 6) |
| 2.4.21 | 422 en `startDate` «La experiencia laboral no puede iniciar antes del mes en que cumpliste 15 años.» | REQ-EF-60 | `WorkExperienceTest.create_shouldRejectStart_whenBeforeFifteenthBirthday` | `422 inicio antes de los 15 años (CA-2.4.21)` (PR 6) |
| 2.4.22 | 201 sin inicio, fin en el mes de los 15 años | REQ-EF-61 | `WorkExperienceTest.create_shouldAcceptEnd_whenNoStartAndEndIsMonthOfFifteenthBirthday` | `201 sin inicio, fin a los 15 años (CA-2.4.22)` (PR 6) |
| 2.4.23 | 422 en `endDate` «La fecha de fin no puede ser anterior al mes en que cumpliste 15 años.» | REQ-EF-61 | `WorkExperienceTest.create_shouldRejectEnd_whenNoStartAndEndIsBeforeFifteenthBirthday` | `422 sin inicio, fin antes de los 15 años (CA-2.4.23)` (PR 6) |
| 2.4.24 | 201 inicio en el mes actual | REQ-EF-26 | `WorkExperienceTest.create_shouldAccept_whenStartIsCurrentMonth` · `WorkHistoryEndToEndIT.addWorkExperience_shouldAcceptNovember_whenUtcIsAlreadyNovember` (RT-07-CA04) | `201 inicio en el mes actual (CA-2.4.24)` |
| 2.4.25 | 422 en `startDate` «La fecha no puede ser posterior al mes actual.» | REQ-EF-26 | `WorkExperienceTest.create_shouldRejectStartInTheFuture_whenStartIsNextMonth` | `422 inicio en el mes siguiente (CA-2.4.25)` |
| 2.4.26 | 422 en `endDate` «La fecha no puede ser posterior al mes actual.» | REQ-EF-26 | `WorkExperienceTest.create_shouldRejectEndInTheFuture_whenEndIsNextMonth` | `422 fin en el mes siguiente (CA-2.4.26)` |
| 2.4.27 | 201 institución y título de 150 | REQ-EF-31 | `ProfileControllerTest.addEducation_shouldAccept_whenTextIsAtLimit` | `201 institución y título de 150 (CA-2.4.27)` |
| 2.4.28 | 422 «La institución no puede superar los 150 caracteres.» / «El título obtenido no puede superar los 150 caracteres.» | REQ-EF-31 | `ProfileControllerTest.addEducation_shouldReturnTooLong_whenTextExceedsLimit` | `422 institución de 151`, `422 título de 151` |
| 2.4.29 | 201 fin igual al inicio | REQ-EF-33 | `EducationTest.create_shouldAccept_whenEndEqualsStart` | `201 formación fin igual al inicio (CA-2.4.29)` |
| 2.4.30 | 422 en `endDate` «La fecha de fin no puede ser anterior a la de inicio.» | REQ-EF-33 | `EducationTest.create_shouldRejectEndBeforeStart_whenEndIsOneMonthEarlier` | `422 formación fin antes del inicio (CA-2.4.30)` |
| 2.4.31 | 201 inicio en el mes de nacimiento | REQ-EF-62 | `EducationTest.create_shouldAccept_whenStartIsBirthMonth` | `201 formación en el mes de nacimiento (CA-2.4.31)` (PR 6) |
| 2.4.32 | 422 en `startDate` «La fecha no puede ser anterior a tu fecha de nacimiento.» | REQ-EF-62 | `EducationTest.create_shouldRejectStart_whenBeforeBirthMonth` | `422 formación antes del nacimiento (CA-2.4.32)` (PR 6) |
| 2.4.33 | 201 inicio en el mes actual | REQ-EF-34 | `EducationTest.create_shouldAccept_whenStartIsCurrentMonth` | `201 formación que inicia este mes (CA-2.4.33)` |
| 2.4.34 | 422 en `startDate` «La fecha no puede ser posterior al mes actual.» | REQ-EF-34 | `EducationTest.create_shouldRejectStartInTheFuture_whenStartIsNextMonth` | `422 formación que inicia el mes siguiente (CA-2.4.34)` |
| 2.4.35 | 422 en `endDate` «La fecha no puede ser posterior al mes actual.» | REQ-EF-34 | `EducationTest.create_shouldRejectEndInTheFuture_whenEndIsNextMonth` | `422 formación que termina el mes siguiente (CA-2.4.35)` |
| 2.4.36 | 503 «Ocurrió un error. Inténtalo de nuevo.» sin réplica, no guarda | REQ-EF-63 | `ProfileAppServiceTest.addWorkExperience_shouldThrowBirthDateUnavailable_whenReplicaIsEmptyAndRecordHasDates` · `ProfileControllerTest.addWorkExperience_shouldReturn503_whenBirthDateIsUnavailable` | `503 experiencia sin réplica (CA-2.4.36)` (PR 6) |
| 2.4.37 | 409 «No puedes quedarte sin formación académica con el perfil activo. Agrega otra antes de eliminar esta.» | REQ-EF-36 | `ProfessionalProfileTest.removeEducation_shouldRejectLast_whenProfileIsCompleted` · `ProfileControllerTest.removeEducation_shouldReturn409_whenLastEducationOfActiveProfile` | `409 última formación de un perfil Activo (CA-2.4.37)` |
| 2.4.38 | 204 única experiencia de un perfil Activo; sigue Activo | REQ-EF-27 | `WorkHistoryEndToEndIT.removeWorkExperience_shouldReturn204AndKeepActive_whenOnlyExperienceOfActiveProfile` | `204 única experiencia de un perfil Activo (CA-2.4.38)` |
| 2.4.39 | 403 en las cuatro peticiones, sin modificar | REQ-EF-11 | `ProfileControllerTest.workHistory_shouldReturn403_whenProfileBelongsToAnotherUser` (parametrizada: las 4 rutas) · `WorkHistoryEndToEndIT.workHistory_shouldNotChangeOtherProfile_whenUserIsNotOwner` | `403 POST experiencia ajena`, `403 DELETE experiencia ajena`, `403 POST formación ajena`, `403 DELETE formación ajena` |
| 2.4.40 | 422 en `endDate` con en curso y fin | REQ-EF-30 | `EducationTest.create_shouldRejectEndDateNotAllowed_whenInProgress` | `422 en curso con fecha de fin (CA-2.4.40)` |
| 2.4.41 | 422 en `endDate` con `UNKNOWN_END` y fin | REQ-EF-23 | `WorkExperienceTest.create_shouldRejectEndDateNotAllowed_whenStatusIsNotEnded` (fila `UNKNOWN_END`) | `422 sin fin conocido con fecha de fin (CA-2.4.41)` |
| 2.4.42 | Solo Frontend; el backend devuelve `startDate` null sin error | REQ-EF-28 | `WorkHistoryEndToEndIT.getProfile_shouldReturnNullStartDate_whenExperienceHasNoStart` | `200 GET perfil con experiencia sin inicio` |
| 2.4.43 | 422 en `startDate` «Ingresa una fecha válida con el formato mm/aaaa.» | REQ-EF-12 | `ProfileControllerTest.addWorkExperience_shouldReturnDateInvalidFormat_whenDateIsMalformed` (parametrizada, sección 5) | `422 inicio 2024-13 (CA-2.4.43)`, `422 inicio 13/2024` |
| 2.4.44 | 422 en `startDate` de la formación, mismo texto | REQ-EF-12 | `ProfileControllerTest.addEducation_shouldReturnDateInvalidFormat_whenDateIsMalformed` | `422 formación inicio 2024-13 (CA-2.4.44)`, `422 formación inicio 2024` |
| 2.4.45 | `Idempotency-Key` | Fuera de alcance (sección 16) | — | — |
| 2.4.46 | 204 una de dos formaciones de un perfil Activo | REQ-EF-36 | `ProfessionalProfileTest.removeEducation_shouldRemove_whenCompletedProfileHasTwo` | `204 una de dos formaciones en Activo (CA-2.4.46)` |
| 2.4.47 | 201 la cuarta experiencia | REQ-EF-27 | `ProfessionalProfileTest.addWorkExperience_shouldAcceptFourth_whenProfileHasThree` | `201 cuarta experiencia (CA-2.4.47)` |
| 2.4.48 | 409 «Ya tienes el máximo de 4 experiencias laborales.», también desde una segunda pestaña | REQ-EF-27, REQ-EF-13 | `ProfessionalProfileTest.addWorkExperience_shouldRejectFifth_whenProfileHasFour` · `ProfileWriteLockIT.addWorkExperience_shouldKeepFour_whenTwoRequestsArriveWithThree` | `409 quinta experiencia (CA-2.4.48)` |
| 2.4.49 | 409 «Ya tienes el máximo de 5 formaciones académicas.» | REQ-EF-35 | `ProfessionalProfileTest.addEducation_shouldRejectSixth_whenProfileHasFive` | `409 sexta formación (CA-2.4.49)` |
| 2.4.50 | `Idempotency-Key` | Fuera de alcance (sección 16) | — | — |
| 2.4.51 | En paralelo: la primera 204 y la segunda 409 con el texto de CA-2.4.37 | REQ-EF-36, REQ-EF-13 | `ProfileWriteLockIT.removeEducation_shouldRejectSecond_whenTwoTabsDeleteBothEducationsOfActiveProfile` | No se reproduce en Newman (secuencial); se cubre con la IT y con `409 última formación…` |
| 2.4.52 | 503 sin réplica al agregar formación | REQ-EF-63 | `ProfileAppServiceTest.addEducation_shouldThrowBirthDateUnavailable_whenReplicaIsEmpty` | `503 formación sin réplica (CA-2.4.52)` (PR 6) |
| 2.4.53 | 201 sin fechas y sin leer la réplica | REQ-EF-64 | `ProfileAppServiceTest.addWorkExperience_shouldNotReadReplica_whenRecordHasNoDates` | `201 sin fechas con réplica vacía (CA-2.4.53)` (PR 6) |
| 2.4.54 | 201 fin en el mes actual | REQ-EF-26 | `WorkExperienceTest.create_shouldAccept_whenEndIsCurrentMonth` | `201 fin en el mes actual (CA-2.4.54)` |
| 2.4.55 | 201 formación que termina en el mes actual | REQ-EF-34 | `EducationTest.create_shouldAccept_whenEndIsCurrentMonth` | `201 formación que termina este mes (CA-2.4.55)` |
| 2.4.56 | Solo Frontend; el backend devuelve `UNKNOWN_END` con `startDate` y `endDate` null | REQ-EF-28 | `WorkHistoryEndToEndIT.addWorkExperience_shouldReturn201WithoutDates_whenUnknownEndHasNoDates` (mismo cuerpo con inicio `2024-06`) | `201 sin fin conocido con inicio 2024-06` |
| 2.4.57 | 422 en `employmentStatus` con `FREELANCE` | REQ-EF-20 | `ProfileControllerTest.addWorkExperience_shouldReturnStatusInvalid_whenStatusIsUnknown` (parametrizada: `FREELANCE`, `current`, `Current`) | `422 estado FREELANCE (CA-2.4.57)`, `422 estado current` |
| 2.4.58 | 404 «No encontramos lo que buscabas.», no elimina | REQ-EF-14 | `ProfileControllerTest.removeEducation_shouldReturn404_whenEducationIsNotInProfile` · `WorkHistoryEndToEndIT.removeEducation_shouldKeepOtherProfileEducation_whenIdBelongsToAnotherProfile` | `404 formación de otro perfil (CA-2.4.58)`, `404 formación inexistente` |
| 2.4.59 | 404 «No encontramos lo que buscabas.», no elimina | REQ-EF-14 | `ProfileControllerTest.removeWorkExperience_shouldReturn404_whenExperienceIsNotInProfile` (existe) · `WorkHistoryEndToEndIT.removeWorkExperience_shouldKeepOtherProfileExperience_whenIdBelongsToAnotherProfile` | `404 experiencia de otro perfil (CA-2.4.59)`, `404 experiencia inexistente` |
| 2.4.60 | 422 en `endDate` «Ingresa una fecha válida con el formato mm/aaaa.» en experiencia o formación | REQ-EF-12 | Las dos parametrizadas de 2.4.43 y 2.4.44 (filas `endDate`) | `422 fin 13/2024 en experiencia (CA-2.4.60)`, `422 fin 13/2024 en formación` |
| RT-01-CA04 | Varios campos inválidos a la vez | REQ-EF-10 | `ProfileControllerTest.addWorkExperience_shouldReturnEveryInvalidField_whenSeveralFail` · `WorkExperienceTest.create_shouldReportEveryField_whenSeveralRulesFail` | `422 tres campos a la vez` |
| RT-01-CA06 | Campos no esperados ignorados | REQ-EF-15 | `WorkHistoryEndToEndIT.addWorkExperience_shouldIgnoreUnexpectedFields_whenBodyHasThem` | `201 ignora id, profileId, status y firebaseUid` |
| RT-01-CA08 | `<script>` se guarda y se devuelve literal | REQ-EF-16 | `WorkHistoryEndToEndIT.addWorkExperience_shouldReturnTextLiterally_whenTextHasMarkup` | `201 descripción con <script>` |
| RT-07-CA04 | Mes actual en UTC | REQ-EF-26 | `WorkHistoryEndToEndIT.addWorkExperience_shouldAcceptNovember_whenUtcIsAlreadyNovember` | — (reloj fijo; Newman usa el mes real) |

## 5. Requisitos (EARS)

### 5.1 Base (bloque A)

- **REQ-EF-01 (normalización).** Cuando llegue un texto de una línea (`company`, `position`, `institution`, `degree`, `startDate`, `endDate`, `employmentStatus`, `level`), Perfil debe recortar los espacios de los extremos (los de `Character.isWhitespace`, la categoría Unicode Zs, incluido U+00A0, y U+FEFF), normalizar a NFC y contar la longitud en puntos de código; los espacios internos se conservan. La descripción, que admite varias líneas, sigue la misma regla en sus extremos (recorte y NFC) y conserva los saltos de línea internos; si solo quedan espacios, no hay descripción (D13).
- **REQ-EF-02 (borde).** El cuerpo de cada `POST` debe validarse con Bean Validation y `@Valid` antes de llegar al servicio, con una restricción por causa y **a lo sumo una violación por campo**: presencia (`@NotBlank`) y longitud en puntos de código (`@CodePointSize`), con código y mensaje desde `ErrorCatalog`; caracteres de control, formato de mes y opción del enumerado con `@DomainRule`, que ejecuta la regla escrita una sola vez en el dominio (`SingleLineText` o `FieldValues`) y toma de su rechazo el código y el mensaje (D3). Cada restricción devuelve `true` para un valor ausente o en blanco, para que solo `@NotBlank` informe la ausencia.
- **REQ-EF-03 (dominio).** Las fábricas `WorkExperience.create` y `Education.create` deben aplicar todas las reglas del registro (las de la sección 6 que no son de forma: coherencia estado–fin, fin ≥ inicio, mes actual y, en el bloque F, nacimiento) y repetir las de forma como invariante; deben acumular un error por campo (el primero en el orden de la sección 6) y lanzar una sola `InvalidFieldsException` con todos.
- **REQ-EF-04 (reconstrucción).** `WorkExperience.rebuild` y `Education.rebuild` deben reconstruir un registro guardado sin aplicar las reglas de longitud ni de fechas, solo los no nulos de la base.
- **REQ-EF-05 (bloqueo).** Mientras se ejecute un caso de uso que modifica un perfil (`updateProfileInfo`, `addWorkExperience`, `removeWorkExperience`, `addEducation`, `removeEducation`, `updateSalaryExpectation`, `addSkill`, `removeSkill`, `requestReview`, `addTargetRole`, `updateTargetRole`, `removeTargetRole`, `completeProfile`), Perfil debe leer el perfil con `SELECT … FOR UPDATE` dentro de la transacción del caso de uso, de modo que dos operaciones sobre el mismo perfil se ejecuten una después de la otra. `getProfile` no bloquea.
- **REQ-EF-06 (espera máxima).** Si la fila del perfil sigue bloqueada después de 2 s, Perfil debe responder **409** `PROFILE_UPDATE_IN_PROGRESS` con «Estamos guardando otro cambio de tu perfil. Inténtalo de nuevo en unos segundos.» y no modificar nada (D12).
- **REQ-EF-08 (lectura de valores).** Convertir un texto en una opción de un enumerado o en un mes se hace solo con `domain/model/FieldValues` (hoy `application/command/CommandValues`); `ArquitecturaTest` prohíbe `Enum.valueOf` y `YearMonth.parse` en `application` (hallazgo de CM-271 con destino en esta tarea).
- **REQ-EF-09 (creación alineada).** La creación del perfil (`POST /api/v1/profiles`) espera también como máximo 2 s y, si otra creación del mismo Usuario sigue en curso, responde **409** `PROFILE_CREATION_IN_PROGRESS` con «Estamos creando tu perfil. Inténtalo de nuevo en unos segundos.». El código publicado `PROFILE_CREATION_TIMEOUT` (503, 5 s) no se renombra: se **retira** y queda en `docs/errores.md` como «Ya no se emite» (R3). `cameia-web` no usa ninguno de los dos; se avisa a Frontend.
- **REQ-EF-07 (reloj).** Toda comparación con «el mes actual» debe usar `YearMonth.now(clock)` con el `Clock` inyectado en UTC que publica `ClockConfig` (CM-279 P1; `Clock.systemUTC()` en producción). Esta tarea no crea otro `@Bean Clock`.

### 5.2 Comunes a los cuatro endpoints

- **REQ-EF-10 (RT-01-CA04).** Cuando varios campos fallen en la misma fase, Perfil debe responder 422 `VALIDATION_FAILED`, `detail` «Revisa los campos marcados.» y un elemento por campo en `errors[]` (`field`, `code`, `message`). Las reglas de forma (fase de borde) se responden todas a la vez; las reglas del registro (fase de dominio) se responden todas a la vez si la forma pasó (D3).
- **REQ-EF-11 (CA-2.4.39).** Cuando el perfil sea de otro Usuario, las cuatro operaciones deben responder 403 `PROFILE_NOT_ALLOWED` «No encontramos lo que buscabas.» sin modificar nada.
- **REQ-EF-12 (CA-2.4.43, 2.4.44, 2.4.60).** Una fecha que no sea exactamente `AAAA-MM` con año de 0001 a 9999 y mes de 01 a 12 debe responder 422 en su campo con `START_DATE_INVALID_FORMAT` o `END_DATE_INVALID_FORMAT` y «Ingresa una fecha válida con el formato mm/aaaa.». Una fecha vacía o solo con espacios cuenta como ausente.
- **REQ-EF-13 (concurrencia).** Los máximos y el mínimo se comprueban con el perfil bloqueado (REQ-EF-05).
- **REQ-EF-14 (CA-2.4.58, 2.4.59).** Cuando se elimine un identificador que no pertenece al perfil de la ruta (de otro perfil o inexistente), Perfil debe responder 404 con `WORK_EXPERIENCE_NOT_FOUND` o `EDUCATION_NOT_FOUND` y «No encontramos lo que buscabas.», sin eliminar nada en ningún perfil. Un segundo `DELETE` del mismo identificador responde 404.
- **REQ-EF-15 (RT-01-CA06, RT-03-CA04).** Los campos no esperados (`id`, `profileId`, `status`, `firebaseUid`, `seniority`, `fieldOfStudy`, `provenance`) se ignoran; la procedencia de lo que se agrega por estas rutas es siempre `MANUAL` (D20); el dueño sale solo de `X-User-Id`.
- **REQ-EF-16 (RT-01-CA08).** Todo texto se guarda y se devuelve tal como quedó tras REQ-EF-01; nunca se interpreta como HTML.
- **REQ-EF-17 (orden de fases).** El orden de respuesta es: cuerpo ilegible o con tipo incorrecto (422 `REQUEST_BODY_INVALID_FORMAT`) → forma (422) → identidad (401) → perfil ocupado más de 2 s (409 `PROFILE_UPDATE_IN_PROGRESS`) → perfil inexistente (404) o ajeno (403) → reglas del registro (422) → fecha de nacimiento no disponible (503, bloque F) → máximo o mínimo (409). El perfil ocupado va antes que 404/403 porque la existencia y el dueño se leen con la fila ya bloqueada (`loadForUserForUpdate`); un perfil inexistente no tiene fila que esperar y responde 404 sin espera.

### 5.3 Experiencia laboral (bloque B)

- **REQ-EF-20 (CA-2.4.1, 2.4.5, 2.4.15, 2.4.57).** `employmentStatus` es obligatorio y uno de `CURRENT`, `ENDED`, `UNKNOWN_END`, comparado exacto (sensible a mayúsculas). Ausente o en blanco → `EMPLOYMENT_STATUS_REQUIRED`; otro valor → `EMPLOYMENT_STATUS_INVALID_VALUE`; ambos con «Selecciona una opción.». Los periodos pueden solaparse.
- **REQ-EF-21 (CA-2.4.3, 2.4.17, 2.4.18).** `company` y `position`: obligatorios, 1 a 100 caracteres.
- **REQ-EF-22 (CA-2.4.12, 2.4.19).** `description`: opcional, hasta 500 caracteres; vacía tras normalizar se guarda como `null`.
- **REQ-EF-23 (CA-2.4.2, 2.4.16, 2.4.41).** Con `ENDED`, `endDate` es obligatoria (`END_DATE_REQUIRED` «Ingresa la fecha de fin.»); con `CURRENT` o `UNKNOWN_END`, `endDate` debe faltar (`END_DATE_NOT_ALLOWED` «La fecha de fin debe quedar vacía.»).
- **REQ-EF-24 (CA-2.4.13, 2.4.14).** Si hay inicio y fin, el fin no puede ser anterior al inicio por mes y año (`END_DATE_BEFORE_START_DATE` «La fecha de fin no puede ser anterior a la de inicio.»); el mismo mes se acepta.
- **REQ-EF-25 (CA-2.4.15).** `startDate` es opcional en todos los estados.
- **REQ-EF-26 (CA-2.4.24 a 2.4.26, 2.4.54, RT-07-CA04).** Ninguna fecha puede ser posterior a `YearMonth.now(clock)`: `START_DATE_IN_THE_FUTURE` / `END_DATE_IN_THE_FUTURE` con «La fecha no puede ser posterior al mes actual.». El mes actual se acepta.
- **REQ-EF-27 (CA-2.4.38, 2.4.47, 2.4.48).** Con 4 experiencias, agregar otra responde **409** `WORK_EXPERIENCE_LIMIT_REACHED` con `detail` «Ya tienes el máximo de 4 experiencias laborales.», sin `errors`, sin guardar. La experiencia no tiene mínimo: eliminar la única de un perfil Activo responde 204 y el perfil sigue `COMPLETED`.
- **REQ-EF-28 (CA-2.4.42, 2.4.56).** La respuesta del perfil devuelve `startDate` y `endDate` como `"AAAA-MM"` o `null`, sin error.

### 5.4 Formación académica (bloque C)

- **REQ-EF-30 (CA-2.4.7, 2.4.8, 2.4.40).** `inProgress` es opcional (`null` o ausente = `false`); con `true`, `endDate` debe faltar (`END_DATE_NOT_ALLOWED`); con `false`, `endDate` es opcional.
- **REQ-EF-31 (CA-2.4.6, 2.4.27, 2.4.28).** `institution` y `degree`: obligatorios, 1 a 150 caracteres. `level` obligatorio. `startDate` obligatoria.
- **REQ-EF-32 (CA-2.4.9).** `level` es uno de `TECHNICAL`, `UNDERGRADUATE`, `POSTGRADUATE`, exacto; otro → `EDUCATION_LEVEL_INVALID_VALUE` «Selecciona una opción.».
- **REQ-EF-33 (CA-2.4.29, 2.4.30).** Si hay fin, no puede ser anterior al inicio (`END_DATE_BEFORE_START_DATE`).
- **REQ-EF-34 (CA-2.4.33 a 2.4.35, 2.4.55).** Ninguna fecha posterior al mes actual (mismos códigos y texto de REQ-EF-26).
- **REQ-EF-35 (CA-2.4.10, 2.4.49).** Con 5 formaciones, agregar otra responde **409** `EDUCATION_LIMIT_REACHED` «Ya tienes el máximo de 5 formaciones académicas.».
- **REQ-EF-36 (CA-2.4.11, 2.4.37, 2.4.46, 2.4.51).** Si el perfil está `COMPLETED` y tiene una sola formación, eliminarla responde **409** `EDUCATION_NOT_ALLOWED` «No puedes quedarte sin formación académica con el perfil activo. Agrega otra antes de eliminar esta.» sin eliminar ni cambiar el estado. En `IN_PROGRESS` o `IN_REVIEW` no hay mínimo. La pertenencia se comprueba antes que el mínimo (un identificador ajeno en un perfil Activo con una formación responde 404).
- **REQ-EF-37 (v6: «Sin "Campo de estudio"»).** `fieldOfStudy` deja de existir: se quita de `AddEducationRequest`, `AddEducationCommand`, `Education`, `EducationEntity`, `ProfessionalProfileMapping` y `ProfileResponse.EducationItem`; el código `FIELD_OF_STUDY_TOO_LONG` se elimina, y la migración V6 borra la columna `area_estudio` (D14). Si un cliente lo envía, se ignora como cualquier campo no esperado.

### 5.5 Fecha de nacimiento (bloque F, después de CM-279)

- **REQ-EF-60 (CA-2.4.20, 2.4.21).** El inicio de una experiencia no puede ser anterior a `YearMonth.from(fechaNacimiento).plusYears(15)` → `START_DATE_BEFORE_MINIMUM_AGE` «La experiencia laboral no puede iniciar antes del mes en que cumpliste 15 años.».
- **REQ-EF-61 (CA-2.4.22, 2.4.23).** Una experiencia sin inicio no puede terminar antes de ese mes → `END_DATE_BEFORE_MINIMUM_AGE` «La fecha de fin no puede ser anterior al mes en que cumpliste 15 años.».
- **REQ-EF-62 (CA-2.4.31, 2.4.32).** El inicio de una formación no puede ser anterior a `YearMonth.from(fechaNacimiento)` → `START_DATE_BEFORE_BIRTH` «La fecha no puede ser anterior a tu fecha de nacimiento.». La formación no tiene edad mínima.
- **REQ-EF-63 (CA-2.4.36, 2.4.52, CA-HT04.2).** Si el registro trae alguna fecha y la réplica no tiene la fecha de nacimiento del Usuario, Perfil debe responder **503** `BIRTH_DATE_UNAVAILABLE` «Ocurrió un error. Inténtalo de nuevo.» y no guardar.
- **REQ-EF-64 (CA-2.4.53).** Si el registro no trae ninguna fecha, Perfil no debe leer la réplica.
- **REQ-EF-65 (CA-HT04.1).** La fecha sale solo de la réplica local (puerto `BirthDateReplica`); Perfil no llama a Cuentas. La fecha nunca se escribe en el log.

## 6. Matriz de validación por campo (R2)

Una fila por causa. «Fase»: B = borde (Bean Validation), D = dominio. Estado 422 salvo que se diga otro. `errors[].field` es el nombre de la columna «Campo». Las pruebas parametrizadas están en `ProfileControllerTest` (fase B) y en `WorkExperienceTest`/`EducationTest` (fase D).

### 6.1 `POST /api/v1/profiles/{id}/work-experiences`

| Campo | Causa (en orden) | Valores literales de prueba | Fase | `code` | Mensaje |
|---|---|---|---|---|---|
| `company` | ausente, `null`, `""`, `"   "`, `"\t\n"`, `" "`, `"﻿"` | los siete | B | `COMPANY_REQUIRED` | «Ingresa la empresa.» |
| `company` | carácter de control (categoría Cc) | `"Acme\u0000"`, `"Acme\u0007"`, `"A\u007Fcme"`, `"A\u0085"` | B (`@DomainRule`) | `COMPANY_INVALID_CHARACTERS` | «La empresa tiene caracteres no permitidos.» |
| `company` | más de 100 | 101 `"a"`; 100 `"a"` + `"😀"` (101 puntos de código) | B | `COMPANY_TOO_LONG` | «La empresa no puede superar los 100 caracteres.» |
| `company` | válido (límites) | 99 y 100 `"a"`; 99 `"ñ"` + `"😀"` (100); `"é"` × 100 (NFD: 200 unidades, 100 en NFC) → 201 y se guarda `"é"` × 100; `"  Acme  "` → se guarda `"Acme"`; `"Acme  S.A."` conserva el doble espacio | B | — | — |
| `position` | igual que `company` | mismos valores | B | `POSITION_REQUIRED` / `POSITION_INVALID_CHARACTERS` / `POSITION_TOO_LONG` | «Ingresa el cargo.» / «El cargo tiene caracteres no permitidos.» / «El cargo no puede superar los 100 caracteres.» |
| `description` | ausente, `null`, `""`, `"   "` | → 201 con `description` `null` | B | — | — |
| `description` | carácter de control distinto de `\n`, `\r`, `\t` | `"Hola\u0000"`; `"a\nb\tc"` se acepta | B (`@DomainRule`) | `DESCRIPTION_INVALID_CHARACTERS` | «La descripción tiene caracteres no permitidos.» |
| `description` | más de 500 | 501 `"a"`; 499 `"a"` + `"😀😀"` (501) | B | `DESCRIPTION_TOO_LONG` | «La descripción no puede superar los 500 caracteres.» |
| `description` | válido | 499, 500 `"a"`; 500 con saltos de línea internos; `"<script>alert(1)</script>"`; `"100 % _de_ \"comillas\""` | B | — | — |
| `employmentStatus` | ausente, `null`, `""`, `"  "` | los cuatro | B | `EMPLOYMENT_STATUS_REQUIRED` | «Selecciona una opción.» |
| `employmentStatus` | fuera del conjunto | `"FREELANCE"`, `"current"`, `"Current"`, `"ENDED "` (con espacio: tras recortar es válido → 201) | B | `EMPLOYMENT_STATUS_INVALID_VALUE` | «Selecciona una opción.» |
| `startDate` | formato | `"2024-13"`, `"2024-00"`, `"2024-1"`, `"24-01"`, `"13/2024"`, `"2024"`, `"2024-01-01"`, `"abc"`, `"0000-01"`, `"２０２４-０１"` (dígitos de ancho completo) | B | `START_DATE_INVALID_FORMAT` | «Ingresa una fecha válida con el formato mm/aaaa.» |
| `startDate` | ausente, `null`, `""`, `"  "` | → se acepta sin inicio | B | — | — |
| `startDate` | posterior al mes actual | reloj `2026-10-15T12:00:00Z`: `"2026-11"`, `"9999-12"` | D | `START_DATE_IN_THE_FUTURE` | «La fecha no puede ser posterior al mes actual.» |
| `startDate` | antes de los 15 años (F) | nacimiento `2008-03-15`: `"2023-02"` rechaza; `"2023-03"` acepta; nacimiento `2008-02-29`: `"2023-02"` acepta | D | `START_DATE_BEFORE_MINIMUM_AGE` | «La experiencia laboral no puede iniciar antes del mes en que cumpliste 15 años.» |
| `endDate` | formato | mismos valores de `startDate` | B | `END_DATE_INVALID_FORMAT` | «Ingresa una fecha válida con el formato mm/aaaa.» |
| `endDate` | presente con `CURRENT` o `UNKNOWN_END` | `"2026-09"` | D | `END_DATE_NOT_ALLOWED` | «La fecha de fin debe quedar vacía.» |
| `endDate` | ausente con `ENDED` | ausente, `null`, `""` | D | `END_DATE_REQUIRED` | «Ingresa la fecha de fin.» |
| `endDate` | posterior al mes actual | `"2026-11"` | D | `END_DATE_IN_THE_FUTURE` | «La fecha no puede ser posterior al mes actual.» |
| `endDate` | anterior al inicio | inicio `"2024-05"`, fin `"2024-04"`; cambio de año: inicio `"2025-01"`, fin `"2024-12"` | D | `END_DATE_BEFORE_START_DATE` | «La fecha de fin no puede ser anterior a la de inicio.» |
| `endDate` | sin inicio y antes de los 15 años (F) | nacimiento `2008-03-15`: `"2023-02"` rechaza; `"2023-03"` acepta | D | `END_DATE_BEFORE_MINIMUM_AGE` | «La fecha de fin no puede ser anterior al mes en que cumpliste 15 años.» |
| `provenance` | cualquier valor o ausente | `"AI_SUGGESTED"`, `"HUMAN"`, sin clave | — | se ignora; se guarda y se devuelve `MANUAL` (D20) | — |
| cuerpo | JSON mal formado, cuerpo vacío, arreglo en vez de objeto, objeto en un campo de texto (`"company": {}`), arreglo en un texto (`"company": ["a"]`) | — | Jackson | `REQUEST_BODY_INVALID_FORMAT` | «Revisa el formato de los datos enviados.» |
| cuerpo | número en un campo de texto (`"company": 123`) | se convierte en `"123"` y se valida como texto (comportamiento de Jackson; **verificación V1**) | B | — | — |
| cuerpo | `Content-Type: text/plain` | — | Spring | `MEDIA_TYPE_NOT_ALLOWED` (415) | «Tipo de contenido no admitido.» |
| cuerpo | campos no esperados | `"id"`, `"profileId"`, `"status":"COMPLETED"`, `"firebaseUid":"otro"`, `"seniority":"JUNIOR"` | — | se ignoran → 201 | — |

**Orden por campo** (solo se informa el primero): `company`/`position`: requerido → caracteres → largo. `startDate`: formato → futuro → 15 años. `endDate`: formato → no permitido por el estado → requerido por el estado → futuro → anterior al inicio → 15 años sin inicio. Como la fase B corre antes, un formato inválido de `endDate` se informa aunque el estado no admita fecha (por ejemplo, `CURRENT` con `"09/2026"` → `END_DATE_INVALID_FORMAT`; `CURRENT` con `"2026-09"` → `END_DATE_NOT_ALLOWED`).

### 6.2 `POST /api/v1/profiles/{id}/educations`

| Campo | Causa | Valores | Fase | `code` | Mensaje |
|---|---|---|---|---|---|
| `institution` | presencia (los siete de 6.1) | — | B | `INSTITUTION_REQUIRED` | «Ingresa la institución.» |
| `institution` | control | `"U\u0000"` | B (`@DomainRule`) | `INSTITUTION_INVALID_CHARACTERS` | «La institución tiene caracteres no permitidos.» |
| `institution` | más de 150 | 151 `"a"`; 149 `"a"` + `"😀😀"` | B | `INSTITUTION_TOO_LONG` | «La institución no puede superar los 150 caracteres.» |
| `institution` | válido | 149, 150; `"Universidad del Cauca"`; `"é"` × 150 | B | — | — |
| `degree` | igual que `institution` | — | B | `DEGREE_REQUIRED` / `DEGREE_INVALID_CHARACTERS` / `DEGREE_TOO_LONG` | «Ingresa el título obtenido.» / «El título obtenido tiene caracteres no permitidos.» / «El título obtenido no puede superar los 150 caracteres.» |
| `level` | presencia | ausente, `null`, `""`, `"  "` | B | `EDUCATION_LEVEL_REQUIRED` | «Elige un nivel educativo.» |
| `level` | fuera del conjunto | `"DOCTORATE"`, `"undergraduate"` | B | `EDUCATION_LEVEL_INVALID_VALUE` | «Selecciona una opción.» |
| `startDate` | presencia | ausente, `null`, `""`, `"  "` | B | `START_DATE_REQUIRED` | «Ingresa la fecha de inicio.» |
| `startDate` | formato | los de 6.1, incluido `"2024"` | B | `START_DATE_INVALID_FORMAT` | «Ingresa una fecha válida con el formato mm/aaaa.» |
| `startDate` | futuro | `"2026-11"` | D | `START_DATE_IN_THE_FUTURE` | «La fecha no puede ser posterior al mes actual.» |
| `startDate` | antes del nacimiento (F) | nacimiento `2008-03-15`: `"2008-02"` rechaza, `"2008-03"` acepta | D | `START_DATE_BEFORE_BIRTH` | «La fecha no puede ser anterior a tu fecha de nacimiento.» |
| `endDate` | formato | los de 6.1 | B | `END_DATE_INVALID_FORMAT` | «Ingresa una fecha válida con el formato mm/aaaa.» |
| `endDate` | presente con `inProgress: true` | `"2026-09"` | D | `END_DATE_NOT_ALLOWED` | «La fecha de fin debe quedar vacía.» |
| `endDate` | futuro | `"2026-11"` | D | `END_DATE_IN_THE_FUTURE` | «La fecha no puede ser posterior al mes actual.» |
| `endDate` | anterior al inicio | inicio `"2025-02"`, fin `"2025-01"` | D | `END_DATE_BEFORE_START_DATE` | «La fecha de fin no puede ser anterior a la de inicio.» |
| `inProgress` | `true`, `false`, `null`, ausente | → aceptados (`null` y ausente = `false`) | — | — | — |
| `inProgress` | `"abc"`, `{}`, `[]` | — | Jackson | `REQUEST_BODY_INVALID_FORMAT` | «Revisa el formato de los datos enviados.» |
| `inProgress` | `"true"`, `1`, `0` | **verificación V1**: la prueba fija lo que Jackson 3 hace hoy; si acepta `1` o `0`, detenerse y reportar | Jackson | — | — |
| `provenance` | cualquier valor o ausente | igual que 6.1 | — | se ignora; se guarda `MANUAL` (D20) | — |
| `fieldOfStudy` | cualquier valor | `"Sistemas"`, 600 caracteres | — | se ignora (campo no esperado, REQ-EF-37) | — |

**Orden por campo:** `institution`/`degree`: requerido → caracteres → largo. `startDate`: requerido → formato → futuro → nacimiento. `endDate`: formato → no permitido → futuro → anterior al inicio.

### 6.3 Parámetros de ruta y encabezados (los cuatro endpoints)

| Entrada | Causa | Valor | Estado | `code` | Mensaje |
|---|---|---|---|---|---|
| `{id}` | no es UUID | `"no-es-uuid"`, `"123"` | 422 | `PROFILE_ID_INVALID_FORMAT` | «El identificador del perfil no es válido.» |
| `{id}` | no existe | `00000000-0000-0000-0000-000000000000` | 404 | `PROFILE_NOT_FOUND` | «No encontramos lo que buscabas.» |
| `{expId}` / `{eduId}` | no es UUID | `"x"` | 422 | `WORK_EXPERIENCE_ID_INVALID_FORMAT` / `EDUCATION_ID_INVALID_FORMAT` | «El identificador de la experiencia no es válido.» / «El identificador de la formación no es válido.» |
| `X-User-Id` | ausente, en blanco, 129 caracteres | — | 401 | `IDENTITY_REQUIRED` | «Tu sesión expiró. Inicia sesión de nuevo.» |
| `X-Request-Id` | ausente o no UUID | — | — | se genera uno y se devuelve en el encabezado y en `requestId` | — |

## 7. Base de datos (R4)

Migraciones nuevas con número fijo por el orden de fusión de Perfil (sección 19): V5 es de CM-279 P1; esta tarea crea V6 (experiencia) y V7 (formación). Nunca se edita V1 a V5. Se prueban con Testcontainers sobre una base con filas previas (`WorkHistorySchemaIT`).

### 7.1 `V6__alinear_experiencia_laboral.sql` (PR 2)

```sql
ALTER TABLE experiencia_laboral RENAME CONSTRAINT experiencia_laboral_pkey TO pk_experiencia_laboral;
ALTER TABLE experiencia_laboral RENAME CONSTRAINT experiencia_laboral_perfil_id_fkey TO fk_experiencia_laboral_perfil;
ALTER TABLE experiencia_laboral ALTER COLUMN empresa TYPE VARCHAR(100);
ALTER TABLE experiencia_laboral ALTER COLUMN cargo TYPE VARCHAR(100);
ALTER TABLE experiencia_laboral ALTER COLUMN descripcion TYPE VARCHAR(500);
ALTER TABLE experiencia_laboral ALTER COLUMN fecha_inicio DROP NOT NULL;
ALTER TABLE experiencia_laboral
    ADD CONSTRAINT ck_experiencia_laboral_empresa_presente CHECK (length(btrim(empresa)) > 0),
    ADD CONSTRAINT ck_experiencia_laboral_cargo_presente CHECK (length(btrim(cargo)) > 0),
    ADD CONSTRAINT ck_experiencia_laboral_estado_empleo CHECK (estado_empleo IN ('CURRENT', 'ENDED', 'UNKNOWN_END')),
    ADD CONSTRAINT ck_experiencia_laboral_procedencia CHECK (procedencia IN ('MANUAL', 'AI_SUGGESTED', 'AI_EDITED')),
    ADD CONSTRAINT ck_experiencia_laboral_fin_segun_estado
        CHECK ((estado_empleo = 'ENDED') = (fecha_fin IS NOT NULL)),
    ADD CONSTRAINT ck_experiencia_laboral_fechas
        CHECK (fecha_fin IS NULL OR fecha_inicio IS NULL OR fecha_fin >= fecha_inicio),
    ADD CONSTRAINT ck_experiencia_laboral_primer_dia
        CHECK ((fecha_inicio IS NULL OR extract(day FROM fecha_inicio) = 1)
           AND (fecha_fin IS NULL OR extract(day FROM fecha_fin) = 1));
CREATE INDEX ix_experiencia_laboral_perfil_id ON experiencia_laboral (perfil_id);
```

### 7.2 `V7__alinear_educacion.sql` (PR 3)

```sql
ALTER TABLE educacion RENAME CONSTRAINT educacion_pkey TO pk_educacion;
ALTER TABLE educacion RENAME CONSTRAINT educacion_perfil_id_fkey TO fk_educacion_perfil;
ALTER TABLE educacion ALTER COLUMN institucion TYPE VARCHAR(150);
ALTER TABLE educacion ALTER COLUMN titulo TYPE VARCHAR(150);
ALTER TABLE educacion DROP COLUMN area_estudio;
ALTER TABLE educacion
    ADD CONSTRAINT ck_educacion_institucion_presente CHECK (length(btrim(institucion)) > 0),
    ADD CONSTRAINT ck_educacion_titulo_presente CHECK (length(btrim(titulo)) > 0),
    ADD CONSTRAINT ck_educacion_nivel CHECK (nivel IN ('TECHNICAL', 'UNDERGRADUATE', 'POSTGRADUATE')),
    ADD CONSTRAINT ck_educacion_procedencia CHECK (procedencia IN ('MANUAL', 'AI_SUGGESTED', 'AI_EDITED')),
    ADD CONSTRAINT ck_educacion_fin_en_curso CHECK (NOT en_curso OR fecha_fin IS NULL),
    ADD CONSTRAINT ck_educacion_fechas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio),
    ADD CONSTRAINT ck_educacion_primer_dia
        CHECK (extract(day FROM fecha_inicio) = 1 AND (fecha_fin IS NULL OR extract(day FROM fecha_fin) = 1));
CREATE INDEX ix_educacion_perfil_id ON educacion (perfil_id);
```

`area_estudio` se elimina (D14): la v6 retiró el campo y ninguna otra parte la lee (la base es solo de Perfil). Consecuencias, aceptadas: los valores guardados en staging se pierden, y una versión anterior de la aplicación ya no arranca sobre la base migrada (`ddl-auto=validate` exige la columna que su entidad declara), así que un problema después de desplegar el PR 3 se corrige con una versión nueva, no volviendo a la imagen anterior.

### 7.3 Reglas de coherencia

- **Largo:** `@CodePointSize(max)`, la constante del dominio (`WorkExperience.MAX_COMPANY_LENGTH = 100`, `MAX_POSITION_LENGTH = 100`, `MAX_DESCRIPTION_LENGTH = 500`, `Education.MAX_INSTITUTION_LENGTH = 150`, `MAX_DEGREE_LENGTH = 150`), `@Column(length)`, `@Schema(maxLength)` y `VARCHAR(n)` tienen el mismo `n`. `ColumnLengthConsistencyIT` lee `information_schema.columns.character_maximum_length` y lo compara con las constantes del dominio.
- **PostgreSQL cuenta caracteres** en `VARCHAR(n)` con codificación UTF8 (puntos de código), igual que el dominio tras NFC.
- **Restricciones con prueba que las viola** (`WorkHistorySchemaIT.constraint_shouldReject_whenRowBreaksIt`, parametrizada por nombre): cada `ck_` con un `INSERT` por JDBC que la viola y comprueba `SQLState 23514` y el nombre de la restricción en el mensaje de PostgreSQL. Como el dominio valida antes, ninguna violación llega por la API; si llegara, sería un defecto y respondería el 500 genérico con el nombre de la restricción solo en el log. Se prueba también que la migración corre sobre una base con una fila V4 válida (inicio `2023-06-01`, `ENDED`, fin `2024-12-01`) y la conserva.
- **Verificación previa en staging (la ejecuta Paula, antes de desplegar el PR 2 y el PR 3).** Completa el pedido 17 ya respondido (máximos 9, 8, 30, 9, 21; 0 filas con fin antes del inicio). **Cómo:** conéctate a la base de **Perfil** de staging con la conexión de solo lectura que ya usas (sin imprimir credenciales ni pegarlas en el chat), abre `BEGIN READ ONLY;`, ejecuta cada consulta y termina con `ROLLBACK;`. Anota el número de cada una y la fecha en `ESTADO.md`. Si alguna no da lo esperado, **no se despliega** el PR indicado: se reporta la fila (sin datos personales) y se decide si se corrige el dato antes o se ajusta la migración.

  | # | Antes de | Consulta (todas de solo lectura) | Esperado |
  |---|---|---|---|
  | 1 | PR 2 | `select count(*) from experiencia_laboral where (estado_empleo = 'ENDED') <> (fecha_fin is not null);` | 0 |
  | 2 | PR 2 | `select count(*) from experiencia_laboral where extract(day from fecha_inicio) <> 1 or extract(day from fecha_fin) <> 1;` | 0 |
  | 3 | PR 2 | `select count(*) from experiencia_laboral where estado_empleo not in ('CURRENT','ENDED','UNKNOWN_END') or procedencia not in ('MANUAL','AI_SUGGESTED','AI_EDITED');` | 0 |
  | 4 | PR 3 | `select count(*) from educacion where en_curso and fecha_fin is not null;` | 0 |
  | 5 | PR 3 | `select count(*) from educacion where length(btrim(institucion)) = 0 or length(btrim(titulo)) = 0;` | 0 |
  | 6 | PR 3 | `select count(*) from educacion where nivel not in ('TECHNICAL','UNDERGRADUATE','POSTGRADUATE') or procedencia not in ('MANUAL','AI_SUGGESTED','AI_EDITED');` | 0 |
  | 7 | PR 3 | `select count(*) from educacion where area_estudio is not null;` | Informativo: son los valores que se pierden al eliminar `area_estudio` (D14). Si no es 0, anota la hora del último respaldo de la base de Perfil antes de desplegar |

  Los ajustes de valores (`UNKNOWN_END`, `AI_*`) se comprueban con las listas de las restricciones `ck_` de la sección 7.1 y 7.2; si una lista cambia en la spec, cambia aquí.
- **Concurrencia:** bloqueo pesimista de la fila `perfil_profesional` (REQ-EF-05). No se agrega `@Version` (decisión D2).

## 8. Errores (R3)

### 8.1 Códigos nuevos

| `code` | HTTP | Excepción (nueva, `domain.exception`, hereda de `BusinessException`) | Mensaje (`detail` o `errors[].message`) | Bloque |
|---|---|---|---|---|
| `WORK_EXPERIENCE_LIMIT_REACHED` | 409 | `WorkExperienceLimitReachedException` | «Ya tienes el máximo de 4 experiencias laborales.» | B |
| `EDUCATION_LIMIT_REACHED` | 409 | `EducationLimitReachedException` | «Ya tienes el máximo de 5 formaciones académicas.» | C |
| `EDUCATION_NOT_ALLOWED` | 409 | `EducationNotAllowedException` | «No puedes quedarte sin formación académica con el perfil activo. Agrega otra antes de eliminar esta.» | C |
| `START_DATE_IN_THE_FUTURE` | 422 (campo) | `InvalidFieldsException` | «La fecha no puede ser posterior al mes actual.» | B, C |
| `END_DATE_IN_THE_FUTURE` | 422 (campo) | `InvalidFieldsException` | «La fecha no puede ser posterior al mes actual.» | B, C |
| `PROFILE_UPDATE_IN_PROGRESS` | 409 | `ProfileUpdateInProgressException` | «Estamos guardando otro cambio de tu perfil. Inténtalo de nuevo en unos segundos.» (D12) | A |
| `COMPANY_INVALID_CHARACTERS`, `POSITION_INVALID_CHARACTERS`, `DESCRIPTION_INVALID_CHARACTERS`, `INSTITUTION_INVALID_CHARACTERS`, `DEGREE_INVALID_CHARACTERS` | 422 (campo) | `@DomainRule` en el borde y `InvalidFieldsException` en el dominio (constantes `WorkExperience.*_CHARACTERS` y `Education.*_CHARACTERS`) | «La empresa tiene caracteres no permitidos.», «El cargo tiene caracteres no permitidos.», «La descripción tiene caracteres no permitidos.», «La institución tiene caracteres no permitidos.», «El título obtenido tiene caracteres no permitidos.» (D11) | A |
| `START_DATE_BEFORE_MINIMUM_AGE` | 422 (campo) | `InvalidFieldsException` | «La experiencia laboral no puede iniciar antes del mes en que cumpliste 15 años.» | F |
| `END_DATE_BEFORE_MINIMUM_AGE` | 422 (campo) | `InvalidFieldsException` | «La fecha de fin no puede ser anterior al mes en que cumpliste 15 años.» | F |
| `START_DATE_BEFORE_BIRTH` | 422 (campo) | `InvalidFieldsException` | «La fecha no puede ser anterior a tu fecha de nacimiento.» | F |
| `BIRTH_DATE_UNAVAILABLE` | 503 | `BirthDateUnavailableException` (la crea CM-279, bloque P1; aquí solo se lanza) | «Ocurrió un error. Inténtalo de nuevo.» | F |
| `PROFILE_CREATION_IN_PROGRESS` | 409 | `ProfileCreationInProgressException` (nueva; `ProfileCreationTimeoutException` se borra) | «Estamos creando tu perfil. Inténtalo de nuevo en unos segundos.» | A |

Las causas `BEFORE_MINIMUM_AGE` y `BEFORE_BIRTH` las aprobó Paula el 6-oct-2026 para el vocabulario cerrado. `BIRTH_DATE_UNAVAILABLE` reemplaza al `DEPENDENCY_UNAVAILABLE` que se pensó para una llamada a Cuentas que ya no existe (v5): el sujeto es el dato que falta, no una dependencia, y así el log distingue la réplica vacía de otro 503.

### 8.2 Códigos que cambian de texto

`COMPANY_TOO_LONG`, `POSITION_TOO_LONG` (500 → 100), `INSTITUTION_TOO_LONG`, `DEGREE_TOO_LONG` (500 → 150), `DESCRIPTION_TOO_LONG` (2000 → 500). El código no cambia; el texto pasa a ser el literal del CA. **Códigos retirados** (no se renombran ni se reutilizan, R3): `FIELD_OF_STUDY_TOO_LONG` (su causa deja de existir con el campo, D14) y `PROFILE_CREATION_TIMEOUT` (REQ-EF-09). Se quitan del `enum`, de `ErrorCatalog` y de las pruebas, y `docs/errores.md` los conserva en una sección nueva «Códigos retirados» (después de «Códigos que el servicio emite») con «Ya no se emite» y el código que lo reemplaza, si hay. `cameia-web` no nombra ninguno de los dos.

### 8.3 Rutas que hoy llegan al 500 y su destino

| Ruta | Hoy | Después | Prueba con petición real |
|---|---|---|---|
| Experiencia sin `startDate` tras quitar `@NotBlank` | 500 (`IllegalArgumentException`, `NullPointerException`) | 201 (D1) | `WorkHistoryEndToEndIT.addWorkExperience_shouldReturn201WithoutDates_whenUnknownEndHasNoDates` y Postman |
| Texto con U+0000 | 500 (`SQLState 22021`) | 422 `*_INVALID_CHARACTERS` | `WorkHistoryEndToEndIT.addWorkExperience_shouldRejectControlCharacter_whenCompanyHasNul` y Postman |
| Fila bloqueada más de 2 s | espera sin límite | 409 `PROFILE_UPDATE_IN_PROGRESS` | `ProfileWriteLockIT.write_shouldFailFast_whenProfileRowIsLockedLongerThanTimeout` |
| Violación de un `ck_` | inalcanzable por la API | 500 con la restricción en el log | `WorkHistorySchemaIT` (JDBC) |
| `AddWorkExperienceCommand`/`AddEducationCommand` con `null` en un obligatorio | `IllegalArgumentException`/`NullPointerException` → 500 | los constructores solo exigen `profileId`; el dominio responde 422 por campo | `ProfileAppServiceTest.addWorkExperience_shouldThrowFieldErrors_whenCommandHasNullRequiredFields` |

### 8.4 Registro

Cada rechazo 4xx (incluidos los 409 de bloqueo) pasa por `ApiExceptionHandler.reject`: `WARN` con `code`, `requestId` y `firebaseUid`, sin traza ni valores. El 503 de negocio que queda (`BIRTH_DATE_UNAVAILABLE`) se registra en `ERROR` sin traza, como pide el estándar para los 5xx y como ya hace Cuentas con `DEPENDENCY_UNAVAILABLE` (D19). Nunca se registran empresa, cargo, institución, título, descripción ni la fecha de nacimiento.

## 9. Casos borde (R5), celda por celda

| Grupo | Caso | Cómo se cubre |
|---|---|---|
| Presencia | ausente, `null`, `""`, espacios, `\t\n`, U+00A0, U+FEFF | Sección 6, parametrizadas |
| Texto | n−1, n, n+1 con ASCII, `ñ`, `ü`, emoji y combinantes; espacios en extremos (se recortan) e internos (se conservan); control, `<script>`, comillas, `%`, `_` | Sección 6. `%` y `_` no tienen efecto: Perfil no hace búsquedas `LIKE` sobre estos campos; se prueban como texto literal |
| Números | No hay campos numéricos; un número en un campo de texto se convierte (V1) | `ProfileControllerTest.addWorkExperience_shouldTreatNumberAsText_whenCompanyIsNumber` |
| Fechas | 31/02 no aplica (sin día); mes 00 y 13; año 0000 y 9999; mes actual y siguiente con reloj fijo `2026-10-31T23:59:59Z` y `2026-11-01T00:00:00Z`; cambio de año (inicio 2025-01, fin 2024-12); bisiesto (nacimiento 29/02/2008 → 15 años en 02/2023) | `WorkExperienceTest`, `EducationTest` con `@ParameterizedTest` |
| Enumerados | válido, desconocido, minúsculas, vacío, con espacios | Sección 6 |
| Duplicados | No hay unicidad: dos experiencias iguales se aceptan (CA-2.4.5 admite solapes; el doble clic es CA-2.4.45, fuera de alcance) | `WorkHistoryEndToEndIT.addWorkExperience_shouldAcceptIdenticalRecords_whenSentTwice` |
| Estado | eliminar dos veces el mismo id (404); eliminar en `IN_REVIEW` (sin mínimo); última formación en `COMPLETED` (409) | `ProfessionalProfileTest` |
| Propiedad | perfil ajeno (403); registro de otro perfil (404); `firebaseUid` en el cuerpo (ignorado); sin identidad (401) | Secciones 4 y 6.3 |
| Colecciones | 0→1, 3→4 (201), 4→5 (409); 4→5 formaciones (201), 5→6 (409); eliminar con 1 y 2 | `ProfessionalProfileTest` |
| Concurrencia | dos altas con 3 experiencias (4 en total, una 409); dos bajas de formaciones distintas en Activo con 2 (204 y 409); dos bajas del mismo id (204 y 404); actualización perdida (el bloqueo la impide) | `ProfileWriteLockIT` |
| Falla parcial | error de base a mitad de la transacción: nada queda guardado (una sola transacción, sin efectos externos) | `ProfileWriteLockIT.write_shouldFailFast_whenProfileRowIsLockedLongerThanTimeout` comprueba que el perfil no cambió |
| Dependencias | réplica vacía (503, F); base caída: fuera de alcance (CM-290 propone el 503 de base caída para todos los servicios) | Bloque F |
| Carga | cuerpo de 1 MiB en `description` → 422 `DESCRIPTION_TOO_LONG` sin 500; JSON mal formado; `Content-Type` erróneo; campos extra. El límite de tamaño del cuerpo es otra tarea (sección 16) | `WorkHistoryEndToEndIT.addWorkExperience_shouldReturn422_whenDescriptionIsOneMebibyte` |

## 10. Seguridad (R6)

### 10.1 Plan de seguridad: ASVS 5.0.0 nivel 1

Fuente: `cameia-infra/docs/seguridad/matriz-asvs-nivel1.md` (corte 25-sep-2026, `4c46e89`; de DevOps, no se edita). IDs que el cambio toca:

| ID | Aplica | Qué parte de CM-274 lo cumple | Prueba o petición |
|---|---|---|---|
| 1.2.3 Salida JSON sin armar a mano | Sí | Respuestas por `ProfileResponse` y `ApiErrorResponse` serializados por Jackson | `ProfileControllerTest` (cuerpo JSON válido en cada caso) |
| 1.2.4 Consultas parametrizadas | Sí | `findLockedById` es JPQL con parámetro; el `set local lock_timeout` usa una constante, nunca un dato recibido | `ProfileWriteLockIT`; revisión: `git grep -n "createNativeQuery" -- src/main` solo con literales o `setParameter` |
| 1.3.2 Sin evaluación dinámica | Sí | `DomainRuleValidator` escapa `{`, `}`, `$` y `\` antes de pasar el mensaje a la plantilla de Hibernate Validator, para que nunca se interprete como expresión EL | `DomainRuleValidatorTest` (mensaje literal con tildes; ningún `${`) |
| 2.1.1 Reglas de validación documentadas | Sí | Secciones 5 y 6 de esta spec y `docs/errores.md` | V-16 de la lista de verificación |
| 2.2.1 Validación contra lo esperado (lista permitida) | Sí | Enumerados exactos, formato `AAAA-MM`, largos en puntos de código, caracteres de control rechazados | Matriz de la sección 6 |
| 2.2.2 Validación en el servidor | Sí | Bean Validation en el borde y reglas en el dominio, independientes de `cameia-web` | `WorkHistoryEndToEndIT` (HTTP real) |
| 2.3.1 Flujo de negocio en orden | Sí (parcial) | El mínimo de formación en un perfil Activo y los máximos se comprueban con la fila bloqueada: no se pueden saltar con dos pestañas | `ProfileWriteLockIT` |
| 4.1.1 `Content-Type` con `charset` | Sí | `server.servlet.encoding.force-response` ya lo fuerza en Perfil | `ProfileControllerTest` y Newman (`expectProblem` comprueba `charset=UTF-8`) |
| 8.2.2 Acceso a datos ajenos (BOLA) | Sí | `loadForUserForUpdate` compara el dueño antes de escribir; registro de otro perfil → 404 sin tocarlo | Las pruebas de SEG-02 de la sección 15 |
| 8.3.1 Autorización en el servidor | Sí | Toda comprobación en `ProfileAppService` | Las mismas |
| 14.2.1 Datos sensibles fuera de la URL | Sí | La ruta solo lleva UUID; la identidad viaja en `X-User-Id` | Revisión del controlador |
| 15.3.1 Respuesta con los campos necesarios | Sí | `ProfileResponse.WorkExperienceItem` y `EducationItem` sin `perfil_id` ni datos internos; `fieldOfStudy` sale | `WorkHistoryEndToEndIT.addEducation_shouldIgnoreFieldOfStudy_whenSent` (la respuesta no tiene la propiedad) |

Los demás IDs de nivel 1 no los toca este cambio (autenticación, sesión, tokens, criptografía, archivos, comunicación: son del Gateway, de Cuentas o de la plataforma). **Hallazgos para la matriz (destino: DevOps vía Vela, no se edita aquí):** la evidencia de 1.2.4 dice «`nativeQuery = true`: cero resultados», pero Perfil ya usa `createNativeQuery` con constantes y parámetros (CM-271) y esta tarea agrega otro; la de 8.2.2 cita `loadForUser`, que en las escrituras pasa a `loadForUserForUpdate`.

### 10.2 OWASP API Security Top 10 por endpoint

Los cuatro endpoints (`POST`/`DELETE …/work-experiences`, `POST`/`DELETE …/educations`) comparten controlador, servicio y forma de error, así que cada fila vale para los cuatro salvo donde se dice:

| Riesgo | Cómo se cumple | Prueba |
|---|---|---|
| API1 BOLA | Dueño comprobado con `X-User-Id` antes de leer o escribir; registro de otro perfil → 404 sin tocarlo | `ProfileControllerTest.workHistory_shouldReturn403_whenProfileBelongsToAnotherUser` (las 4 rutas); `WorkHistoryEndToEndIT.removeEducation_shouldKeepOtherProfileEducation_whenIdBelongsToAnotherProfile` y `…removeWorkExperience_shouldKeepOtherProfileExperience_…` |
| API2 Autenticación | Identidad solo de `X-User-Id` (lo pone el Gateway); ausente, en blanco o de más de 128 → 401; `firebaseUid` en el cuerpo se ignora | `ProfileControllerTest` (401 en las 4 rutas); `WorkHistoryEndToEndIT.addWorkExperience_shouldIgnoreUnexpectedFields_whenBodyHasThem` |
| API3 Propiedades del objeto | `record` de entrada sin `id`, `status`, `provenance` ni dueño; salida por DTO | La misma prueba de campos no esperados; `…addWorkExperience_shouldSaveManual_whenBodyDeclaresAiProvenance` |
| API4 Consumo de recursos | Largos por campo, 4 y 5 registros por perfil, espera máxima de 2 s; cuerpo de 1 MiB rechazado sin 500 (el límite de tamaño del cuerpo es la tarea aparte de la sección 16) | `ProfessionalProfileTest` (máximos); `ProfileWriteLockIT`; `WorkHistoryEndToEndIT.addWorkExperience_shouldReturn422_whenDescriptionIsOneMebibyte` |
| API5 Autorización por función | Solo el dueño del perfil puede agregar o eliminar; no hay roles en Perfil | Las pruebas de API1 |
| API6 Flujos sensibles | Eliminar la última formación de un perfil Activo está protegido con bloqueo; la repetición del `POST` (CA-2.4.45/2.4.50) es Sprint 3 | `ProfileWriteLockIT.removeEducation_shouldRejectSecond_…`; FIA-02 de la sección 15 |
| API7 SSRF | No aplica: ninguna URL sale de la entrada | — |
| API8 Configuración | Sin traza, SQL ni nombre de clase en errores; `Content-Type` con charset | V-08 de la lista de verificación |
| API9 Inventario | Las cuatro rutas siguen bajo `/api/v1/profiles/{id}/…` y quedan en OpenAPI con todos sus estados | `OpenApiDocumentIT` |
| API10 Consumo inseguro | No aplica en los PR 1 a 5 (no consumen otro servicio); en el PR 6 la fecha sale de la réplica local, ya validada por CM-279 al consumir el evento | — |

### 10.3 Resumen

API1 (BOLA): dueño comprobado con `X-User-Id` antes de leer o escribir (`loadForUserForUpdate`); registro de otro perfil → 404 sin tocarlo. API2: identidad solo de `X-User-Id` (lo pone el Gateway; el ingreso es interno). API3: DTO `record` de entrada y de salida, sin entidades; campos no esperados ignorados. API4: largo por campo, 4 y 5 elementos por perfil, espera máxima del bloqueo de 2 s; tamaño del cuerpo: otra tarea (sección 16). API8: ninguna respuesta lleva traza, SQL, nombre de clase ni texto de Jackson. Consultas parametrizadas (JPQL con parámetro; el `set local lock_timeout` usa una constante, nunca un dato recibido). `Content-Type` con `charset=UTF-8` (ya forzado por `server.servlet.encoding.force-response`; se comprueba en `ProfileControllerTest`). Logs sin datos personales (sección 8.4).

## 11. Código y patrones (R7)

- **Reutilizar:** `InvalidFieldsException` (se le agrega un acumulador), `CommandValues` (se quita `yearOnly`), `ErrorCatalog` (claves y mensajes de campo), `ProblemResponses`, `ProfessionalProfileRepositoryAdapter.isLockNotAvailable` (detección de `55P03`), `ProfileCreationConcurrencyIT` (forma de las pruebas concurrentes), las excepciones `*NotFoundException` existentes.
- **Extender:** `ProfessionalProfile` (máximos y mínimo), `WorkExperience` y `Education` (fábricas `create` y `rebuild`), `ProfessionalProfileRepository` (`findByIdForUpdate`), `ProfileAppService` (bloqueo, reloj).
- **Mover:** `application/command/CommandValues` → `domain/model/FieldValues` (REQ-EF-08).
- **Crear, y por qué:** `domain/model/SingleLineText` (no hay normalización en Perfil; mismo comportamiento que el de Cuentas, escrito aquí porque los servicios no comparten código); `presentation/dto/validation/CodePointSize` (`@Size` cuenta unidades UTF-16) y `DomainRule` con sus validadores (la regla de forma se escribe una vez en el dominio o en `CommandValues` y el borde la ejecuta: patrón de Cuentas, D3); `domain/model/WorkExperienceDraft` y `EducationDraft` (`record` con los datos de la fábrica: más de 3 parámetros); `domain/model/DateBounds` (`record` con el mes actual y el mes de nacimiento); las excepciones de la sección 8.1 salvo `BirthDateUnavailableException`. El reloj **se reutiliza**: `infrastructure/config/ClockConfig` de CM-279 P1. `rebuild` recibe `(UUID, WorkExperienceDraft, DataProvenance)` y `(UUID, EducationDraft, DataProvenance)`: tres parámetros, reutilizando el borrador como forma de la fila. En F se **reutilizan** de CM-279 (bloque P1): el puerto `domain/port/BirthDateReplica.findBirthDate(FirebaseUid)`, la tabla `fecha_nacimiento_usuario` (clave `firebase_uid`), su adaptador, `BirthDateUnavailableException` y el doble `InMemoryBirthDateReplica`.
- **Patrones** (ya usados en el repo): objeto de valor, fábrica en la entidad, puerto y adaptador, `record` como comando. Ninguno nuevo.
- **Reglas:** `domain` sin Spring ni JPA (`ArquitecturaTest`); `@Transactional` solo en `application.service` (el adaptador conserva los suyos actuales); métodos de ≈ 20 líneas y ≤ 3 parámetros; sin `Utils`.
- **Idioma (R1, confirmado por Paula el 9-oct-2026):** en inglés los identificadores, los nombres de métodos de prueba y los códigos de error; en español el Javadoc, los comentarios (también los de SQL), `@DisplayName`, las descripciones de OpenAPI, los logs, los textos de ArchUnit y los mensajes para la persona. Lo ajeno no se renombra.

## 12. Documentación (R8)

- **OpenAPI:** `@Operation`, `@Parameter` y `@ApiResponse` para 201/204, 401, 403, 404, 409, 422, 503 (y 415) en los cuatro endpoints, con ejemplo de error con `code`. `@Schema` en cada campo de `AddWorkExperienceRequest`, `AddEducationRequest`, `ProfileResponse.WorkExperienceItem` y `EducationItem`: descripción, ejemplo, `requiredMode`, `minLength`/`maxLength`, `pattern = "^\\d{4}-(0[1-9]|1[0-2])$"` en las fechas, `allowableValues` en los enumerados, `nullable` donde aplique. `OpenApiDocumentIT` comprueba `maxLength` 100/100/500/150/150 y `allowableValues`.
- **Javadoc** en toda clase, `record`, `enum` y método nuevo o modificado; `package-info.java` en `presentation/dto/validation`.
- **Comentarios de bloque** sobre el bloqueo y su espera, el orden de fases y la regla del mínimo de formación.
- **`docs/errores.md`:** filas de la sección 8 con `Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba`; el pendiente de «última formación 422» se reemplaza por la fila de `EDUCATION_NOT_ALLOWED` 409; el libro citado pasa a `09102026_01_Backlog_v6.xlsx`.
- **ADR 0005** `docs/adr/0005-bloqueo-del-perfil-en-escrituras.md` (el 0004 es el de la réplica de CM-279 P2): bloqueo pesimista, espera de 2 s, 409 y por qué no `@Version`; el ADR 0003 (creación) se actualiza a 2 s y 409.
- `ErrorCodeDocumentationTest` comprueba que cada valor de `ErrorCode` aparece en `docs/errores.md` y en al menos un archivo de `src/test/java` distinto de `ErrorCodeTest`.

## 13. Pruebas por capa (R9) y cobertura (R10)

- **Dominio** (JUnit 5 + AssertJ, sin Spring): `SingleLineTextTest`, `WorkExperienceTest`, `EducationTest`, `ProfessionalProfileTest`, `InvalidFieldsExceptionTest`. Límites con `@ParameterizedTest`.
- **Validadores del borde:** `CodePointSizeValidatorTest` y `DomainRuleValidatorTest` (con un validador real de Hibernate Validator, para comprobar el código y el mensaje que viajan en la violación).
- **Aplicación** (Mockito, como hoy): `ProfileAppServiceTest` con `Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)`; verifica `findByIdForUpdate` en cada escritura y que no se llama `save` cuando hay error.
- **Web** (`MockMvc` `standaloneSetup` con `ApiExceptionHandler`, como hoy): `ProfileControllerTest`; cada caso comprueba estado, `Content-Type` `application/problem+json;charset=UTF-8`, `code`, `detail`, `errors[*].field`, `errors[*].code`, `errors[*].message` y la ausencia de `trace`, `exception` y nombres de clase.
- **Integración** (Testcontainers `postgres:16-alpine`, puerto aleatorio, Failsafe): `WorkHistoryEndToEndIT` (`@SpringBootTest(webEnvironment = RANDOM_PORT)` con `TestRestTemplate` o `RestClient`, reloj fijo por `@TestConfiguration` con `@Primary`), `ProfileWriteLockIT`, `WorkHistorySchemaIT`, `ColumnLengthConsistencyIT`. Ninguna usa H2.
- **Arquitectura:** `ArquitecturaTest` en verde.
- Toda prueba puede fallar: afirma valores concretos.
- **Cobertura:** `./mvnw.cmd -B clean verify` → `target/site/jacoco/jacoco.csv`; ≥ 90 % de líneas y de ramas en cada clase nueva o modificada: `SingleLineText`, `WorkExperience`, `WorkExperienceDraft`, `Education`, `EducationDraft`, `DateBounds`, `ProfessionalProfile`, `InvalidFieldsException`, las cinco excepciones nuevas (incluida `ProfileCreationInProgressException`), `FieldValues`, `ProfileAppService`, `ProfessionalProfileRepositoryAdapter`, `ProfessionalProfileMapping`, `ApiExceptionHandler`, `ErrorCatalog`, `ProfileController`, `CodePointSizeValidator`, `DomainRuleValidator`, `AddWorkExperienceRequest`, `AddEducationRequest`, `ProfileResponse`. La cobertura global de `cameia-perfil` no baja de la medida antes del PR 1 (se anota en el PR 1). Cada línea o rama sin cubrir se reporta con su razón.

## 14. Postman y Newman (R11)

- Colección `docs/CAMEIA_Perfil_Sprint1.postman_collection.json` (v2.1, UTF-8). Carpeta nueva `HU-2.4 — Experiencia laboral y formación académica` con una subcarpeta por endpoint. Las carpetas existentes (`CM-18`, `CM-271`) se conservan; reorganizar toda la colección por recurso es una tarea aparte (sección 16).
- Cada petición crea su propio Usuario (`X-User-Id: uid-pm-{{$guid}}` en el `pre-request` de la carpeta) y su perfil, para que la colección se pueda repetir sin borrar la base. El mes actual y el siguiente se calculan en el `pre-request` en UTC (`new Date().toISOString().slice(0, 7)`).
- Un script de prueba de la colección define `expectProblem(status, code, detail)` y `expectFieldError(field, code, message)`; cada petición comprueba estado, `Content-Type` con `charset=UTF-8`, `code`, `detail`/`errors[]`, `requestId` y que el cuerpo no contiene `Exception`, `at co.edu`, `SQL` ni `org.`.
- Peticiones: las de la columna «Postman» de la sección 4 y, por campo, una por causa de la sección 6 (vacío, `null`, formato, n−1, n, n+1, fuera del enumerado), más cuerpo ilegible (422), `text/plain` (415), campo no esperado (201), sin identidad (401), perfil ajeno (403), inexistente (404), 409 de máximos y mínimo.
- Entorno `docs/perfil-local.postman_environment.json` con `base_url` `http://localhost:8082`.
- Corrida (PR 5): `docker compose down -v`, `docker compose up -d --build` (aplicación empaquetada por el `Dockerfile`, PostgreSQL y RabbitMQ del compose), luego `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json -e docs/perfil-local.postman_environment.json --reporters cli,junit --reporter-junit-export target/newman/perfil.xml`. Se pega la salida completa en el PR. Si algo falla en Newman y no en las pruebas automáticas, primero se agrega la prueba automática que falta.

## 15. Atributos de calidad (R13)

Fuente: `Anexo_Restricciones_Atributos_Calidad` (Entrega 1), cuadro 9: la épica **HE-02** declara **AC-0001, AC-0003, AC-0006 y MAN-02**. Se agregan las métricas que el cambio toca (DES-02 por la espera del bloqueo; FIA-01 y FIA-02 por los máximos, el mínimo y las eliminaciones; MAN-01, MAN-03, IOP-01; REST-0002 y REST-0003). AC-0005 no existe en el anexo. La columna «Resultado real» la llena quien ejecuta (prompt B) y la repite quien verifica (prompt C); ninguna fila se declara cumplida sin ejecución.

| Atributo | Métrica (ID del anexo) | Límite | Cómo lo cumple CM-274 | Prueba o comando | Resultado real |
|---|---|---|---|---|---|
| AC-0001 (HE-02) | IA-01 | 100 % de lo que cambia estado con procedencia y estado válidos | Las rutas manuales ignoran `provenance` y guardan `MANUAL` (D20); `CHECK` de procedencia en las dos tablas | `ProfileControllerTest.addWorkExperience_shouldSaveManual_whenBodyDeclaresAiProvenance`; `WorkHistorySchemaIT.constraint_shouldReject_whenRowBreaksIt` (filas `ck_*_procedencia`) | PENDIENTE |
| AC-0001 (HE-02) | REST-0004 | Ninguna salida de IA pasa a dato confirmado sin validación ni revisión | No aplica a esta tarea: no hay salida de IA en estas rutas; la IA (HU-2.8) usa sus propias rutas | — | No aplica |
| AC-0003 (HE-02) | SEG-01 | ASVS 5.0.0 N1 aplicable al 100 % | Identidad solo de `X-User-Id`, dueño comprobado, DTO explícitos, `charset=UTF-8`, sin traza ni SQL en errores, consultas parametrizadas | `ProfileControllerTest` (forma de error y `Content-Type`); petición de Newman `expectProblem` en cada error | PENDIENTE |
| AC-0003 (HE-02) | SEG-02 | 100 % de accesos cruzados rechazados, con dos usuarios por recurso | 403 en las cuatro rutas sobre perfil ajeno; 404 sobre registro de otro perfil, sin modificarlo | `ProfileControllerTest.workHistory_shouldReturn403_whenProfileBelongsToAnotherUser`; `WorkHistoryEndToEndIT.workHistory_shouldNotChangeOtherProfile_whenUserIsNotOwner`, `…removeEducation_shouldKeepOtherProfileEducation_whenIdBelongsToAnotherProfile`, `…removeWorkExperience_shouldKeepOtherProfileExperience_whenIdBelongsToAnotherProfile`; Newman `403 …` y `404 … de otro perfil` | PENDIENTE |
| AC-0003 (HE-02) | SEG-03 | Cero vulnerabilidades críticas; dependencia nueva alta o crítica bloquea | Sin dependencias nuevas | `git diff origin/develop -- pom.xml` vacío; CodeQL del PR en verde | PENDIENTE |
| AC-0003 (HE-02) | SEG-04 | Cero datos sensibles en logs, eventos y trazas | Rechazos en `WARN` con `code`, `requestId`, `firebaseUid`; nunca textos del registro ni fecha de nacimiento | Tras la corrida de Newman: `docker compose logs app` filtrado con `Select-String -Pattern 'Bancolombia\|Universidad\|<script>\|2008-03-15'` → 0 coincidencias (los valores literales que usa la colección) | PENDIENTE |
| AC-0006 (HE-02) | INT-06 (parte de Backend) | El cliente puede mostrar el estado y la acción sin ayuda | Mensaje literal del CA por campo, todos los de forma a la vez; 409 que dicen qué pasó y si se puede reintentar | `ProfileControllerTest` (mensajes literales por campo); `FieldMessagesConsistencyTest`; Newman `expectFieldError` | PENDIENTE |
| MAN-02 (HE-02) | MAN-02 | Cada adaptador con pruebas de éxito, formato inválido, tiempo de espera y cuota | El adaptador que se toca es `ProfessionalProfileRepositoryAdapter`: éxito y espera agotada (409) se prueban; formato inválido no aplica (lo valida el dominio antes); cuota no aplica (no hay proveedor externo) | `ProfileWriteLockIT.write_shouldWaitForOtherWrite_whenSameProfileIsLocked`, `…write_shouldFailFast_whenProfileRowIsLockedLongerThanTimeout` | PENDIENTE |
| AC-0002 (cambio) | DES-02 | p95 ≤ 2 s; 100 observaciones válidas por operación tras calentamiento | Índices `ix_*_perfil_id`; transacción corta; espera máxima del bloqueo de 2 s | Carpeta de Newman `DES-02 — HU-2.4` (alta y baja de experiencia con Usuario nuevo por iteración): `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json --folder "DES-02 — HU-2.4" -n 105 --reporters cli,json --reporter-json-export target/newman/des02.json`; se descartan las 5 primeras y se calcula el p95 de `POST` y `DELETE` con el script de T-E3. Ambiente local (aplicación empaquetada); staging queda PENDIENTE de DevOps | PENDIENTE |
| AC-0004 (cambio) | FIA-01 | 100 % de los fallos conservan el último estado confirmado | Una transacción por operación; espera agotada → nada cambia; 409 de máximos y mínimo → nada cambia | `ProfileWriteLockIT.write_shouldFailFast_whenProfileRowIsLockedLongerThanTimeout`; `ProfessionalProfileTest.addWorkExperience_shouldRejectFifth_whenProfileHasFour` (la lista sigue con 4); `…removeEducation_shouldRejectLast_whenProfileIsCompleted` | PENDIENTE |
| AC-0004 (cambio) | FIA-02 | Cero efectos adicionales en 20 repeticiones con el mismo identificador | Repetir `DELETE` del mismo id: un 204 y diecinueve 404, sin cambios; la repetición del `POST` es CA-2.4.45/2.4.50 (Sprint 3, sección 16) | `WorkHistoryEndToEndIT.removeWorkExperience_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes` y `…removeEducation_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes` | PENDIENTE |
| Control | MAN-01 | > 70 % (rúbrica); umbral de Paula ≥ 90 % de líneas y ramas en lo nuevo o modificado | Pruebas por capa (sección 13) | `./mvnw.cmd -B clean verify` y `target/site/jacoco/jacoco.csv` por clase de la sección 13; global no baja | PENDIENTE |
| Control | MAN-03 | Cero FK o accesos entre bases de contextos | Las FK nuevas son dentro de Perfil; la fecha sale de la réplica local | `WorkHistorySchemaIT.constraints_shouldHaveStandardNames_whenSchemaIsMigrated` (solo `fk_*_perfil`) | PENDIENTE |
| Control | IOP-01 | 100 % de APIs con OpenAPI | `@Schema` y `@ApiResponse` por estado | `OpenApiDocumentIT.addWorkExperienceRequest_shouldDocumentLimits_whenApiDocsAreGenerated` y la de formación | PENDIENTE |
| Control | IOP-04 | Productores con Outbox | No aplica en esta tarea: CM-67 crea el outbox y publica en toda escritura de un perfil `COMPLETED`, incluidas las de esta tarea (D16) | — | No aplica |
| Restricción | REST-0002 | Capacidad limitada: tiempos de espera, límites por Usuario, sin estado en memoria | Espera máxima de 2 s; 4 y 5 registros por perfil; largo por campo; sin estado entre peticiones | `ProfileWriteLockIT`; `ProfessionalProfileTest` (máximos); `ProfileControllerTest` (largos) | PENDIENTE |
| Restricción | REST-0003 | Datos personales mínimos | No se agregan datos; `area_estudio` se borra (D14) | `WorkHistorySchemaIT.educacion_shouldNotHaveFieldOfStudyColumn_whenMigrated` | PENDIENTE |

## 16. Fuera de alcance (con destino)

| Qué | Destino |
|---|---|
| CA-2.4.45 y 2.4.50 (`Idempotency-Key`) | Sprint 3 según RT-06 (v6); tarea a pedir a Vela |
| Publicar `perfil.profesional-actualizado` al editar un perfil `COMPLETED` (HT-03) | CM-67 (D16) |
| Límite de tamaño del cuerpo (413) | Tarea aparte común a Cuentas y Perfil, ya pedida a Vela con CM-36 (pregunta 14); Perfil se suma a ella (D17) |
| Reorganizar la colección de Postman por recurso | Tarea de deuda técnica (Vela) |
| `ProfessionalProfileMapping.toTargetRoleEntity` reescribe `creado_en` y `estado_revision` de cada rol objetivo en cada guardado (defecto hallado al leer el mapeo) | Corrección de código para CM-70/72 (roles objetivo, ajustes de la v6); se agrega a la comunicación con Vela |
| 503 por base de datos caída | CM-290 |
| `POST …/target-roles` aún acepta `provenance` del cliente (mismo criterio que D20) | Tarea de ajustes de HU-2.11; propuesto al Product Owner |

## 17. Decisiones

| # | Decisión | Porqué | Alternativas descartadas | Decisión humana |
|---|---|---|---|---|
| D1 | Bloqueo pesimista de la fila del perfil en toda escritura, con espera máxima de 2 s | Los máximos y el mínimo cruzan varias filas hijas; el estándar pide bloquear el padre; la espera evita retener conexiones | `@Version` con reintentos; bloquear solo al eliminar | Propuesta de Backend; queda decidida al aprobar Paula la spec |
| D2 | Sin `@Version` | El bloqueo ya serializa; `@Version` exige cambiar el `save` que hace `merge` | Agregarlo | Propuesta de Backend; queda decidida al aprobar Paula la spec |
| D3 | Reglas de forma en el borde y reglas del registro en el dominio, con el dominio como invariante completo; las reglas de forma del dominio (caracteres, mes, opciones) se ejecutan en el borde con `@DomainRule` | Es lo que Paula ya decidió para Cuentas: dos fases (D3 de CM-36) y reglas de forma escritas una sola vez y ejecutadas en el borde (D18 de CM-36); CM-271 dejó en esta tarea juntar los errores de todos los campos | Todo en el dominio en una pasada (sin Bean Validation de campos); copiar cada regla en una anotación propia (dos fuentes de verdad) | Paula, 6 y 7-oct-2026 (D3 y D18 de CM-36) |
| D4 | La base (bloqueo, normalización, restricciones, reloj) vive en el PR 1 de esta tarea | La usan CM-54, CM-66 y CM-67 | Repetirla en cada tarea | Propuesta de Backend; queda decidida al aprobar Paula la spec |
| D5 | Una migración por tabla (V6 y V7) | Cada PR queda bajo 1000 líneas y se despliega solo | Una sola migración | Propuesta de Backend; queda decidida al aprobar Paula la spec |
| D6 | 204 al eliminar | Literal de los CA | — | Paula, 6-oct-2026 |
| D7 | La formación deja de aceptar `"2024"` | Un formato, un contrato | Mantenerlo | Paula, 6-oct-2026 |
| D8 | «La fecha de fin debe quedar vacía.» para `END_DATE_NOT_ALLOWED` | Ningún CA lo fija | — | Paula, 6-oct-2026 |
| D9 | Causas `BEFORE_MINIMUM_AGE` y `BEFORE_BIRTH` | No hay causa existente que lo diga | — | Paula, 6-oct-2026 |
| D10 | Testcontainers para las pruebas de concurrencia | Base real | H2 | Paula, 6-oct-2026 |
| D11 | Los caracteres de control (categoría Cc) se rechazan con un código por campo, `<CAMPO>_INVALID_CHARACTERS`, y el texto «{El dato} tiene caracteres no permitidos.»; la descripción admite tabulador, salto de línea y retorno. Los de formato (Cf) se aceptan | U+0000 hoy termina en 500; Cuentas ya rechaza los invisibles en el correo; un código por causa. Cf queda fuera porque U+200D forma parte de emojis compuestos | Un código común `TEXT_INVALID_CHARACTERS`; quitarlos en silencio (se guarda algo distinto de lo enviado) | Paula, 9-oct-2026 |
| D12 | Espera máxima de 2 s y 409 `PROFILE_UPDATE_IN_PROGRESS` con «Estamos guardando otro cambio de tu perfil. Inténtalo de nuevo en unos segundos.»; la creación se alinea (REQ-EF-09) | 2 s es el presupuesto de DES-02 y una escritura normal dura milisegundos; el 409 dice que hay otra escritura en curso, no un servicio caído, y no cuenta en DES-04 ni FIA-06. El texto se pide a Vela para el backlog | 5 s y 503 (D22 y D28 de CM-271); 2 s y 503 | Paula, 9-oct-2026 |
| D19 | Los 503 de negocio se registran en `ERROR` sin traza; los 4xx en `WARN` | Regla del estándar para los 5xx; Cuentas ya lo hace con `DEPENDENCY_UNAVAILABLE`; con D12 el único 503 de negocio de Perfil es la réplica vacía | `WARN` para todo rechazo previsto | Backend, 9-oct-2026 (Paula pidió la mejor opción) |
| D13 | La descripción se recorta en sus extremos y se normaliza a NFC; solo espacios = sin descripción | Igual que el resumen; los espacios sobrantes no cuentan para los 500 | Solo NFC | Paula, 9-oct-2026 |
| D14 | `fieldOfStudy` se elimina del contrato y la columna `area_estudio` se borra en la V6 | La v6 lo retiró; dejar la columna sin escribir guarda un dato muerto | Ignorarlo y conservar la columna; quitarlo en dos pasos (primero dejar de escribir, después borrar) | Paula, 9-oct-2026 («¿no es mejor cambiar la base si sabemos que se eliminó?») |
| D15 | El cuerpo ilegible sigue en 422 `REQUEST_BODY_INVALID_FORMAT` | Contrato publicado y ya avisado a Frontend | 400 | Paula, 6-oct-2026 (D4 de CM-36) |
| D16 | El evento `perfil.profesional-actualizado` lo implementa CM-67 (outbox y publicación en toda escritura de un perfil `COMPLETED`) | Una sola implementación del outbox | Tarea propia de Perfil; publicar en cada tarea | Paula, 9-oct-2026 |
| D20 | Las rutas manuales ignoran `provenance` y guardan `MANUAL` | IA-01 exige procedencia confiable y los CA dicen «procedencia MANUAL» (CA-2.4.1, 2.4.7); la IA (HU-2.8, fuera del MVP) escribe por sus propias rutas (`POST …/confirm`, `PATCH …/review`) con su origen. Mismo criterio que el `PATCH` (D5 de CM-54) | Aceptar solo `MANUAL` y rechazar los demás con 422; dejarlo como está | Paula, 9-oct-2026 |
| D17 | El límite de tamaño del cuerpo va en la tarea aparte ya pedida con CM-36 | Es transversal a los servicios | Incluirlo en el PR 1 | Paula, 6-oct-2026 (pregunta 14 de CM-36) |

## 18. Preguntas abiertas

| # | Pregunta | Para | Bloquea |
|---|---|---|---|
| V1 | Qué hace Jackson 3 con `1`, `0` y `"true"` en `inProgress` y con un número en un texto | Verificación de Backend (T-B1 y T-C1) | Nada: se fija con una prueba; si acepta `1` o `0`, se detiene y se reporta |

**Acciones con destino.** Pedir a Vela, en el documento al Product Owner, que el backlog incorpore los textos que no tiene: el del 409 de D12 y los de D11. Avisar a Frontend, en su documento único, de los 409 de máximo y mínimo, del inicio opcional de la experiencia y de que `fieldOfStudy` desaparece de la respuesta (`cameia-web` en `develop` todavía lo envía y lo lee en `profile.mapper.ts`).

## 19. Integración con las otras CM de Perfil

Perfil se ejecuta **en serie** para que ningún archivo compartido tenga que rebasarse con conflictos: cada CM sale de `origin/develop` con la anterior ya fusionada; si Paula aún no fusionó la anterior, la rama sale de la última rama de la anterior (stack) y se rebasa con `git rebase --onto` cuando se fusione. Orden: **CM-279 P1 → CM-279 P2 → CM-274 (PR 0 a 5) → CM-274 PR 6 → CM-54 → CM-66 → CM-67**.

| Archivo o pieza compartida | Quién lo siembra | Qué agregan las demás | Regla |
|---|---|---|---|
| Migraciones Flyway | CM-279 P1: `V5__replica_fecha_nacimiento.sql` | CM-274: `V6__alinear_experiencia_laboral.sql`, `V7__alinear_educacion.sql`; CM-66: `V8__alinear_habilidad_perfil.sql`; CM-67: `V9__evento_saliente.sql` | Números fijos; se fusionan en ese orden (Flyway rechaza uno menor después de uno mayor ya aplicado). Si el orden cambia, se renumera antes del push |
| `Clock` | CM-279 P1: `infrastructure/config/ClockConfig` | Todas lo inyectan | Un solo `@Bean Clock` |
| ADR | CM-279 P2: `0004-replica-de-fecha-de-nacimiento-por-evento.md` | CM-274: `0005-bloqueo-del-perfil-en-escrituras.md` | El número sigue el orden de fusión |
| `ErrorCode`, `ErrorCatalog`, `docs/errores.md` | CM-279 P1 (`BIRTH_DATE_UNAVAILABLE`, `reject` por estado); CM-274 PR 1 (sección «Códigos retirados», `ErrorCodeDocumentationTest`) | Cada CM agrega sus códigos al final del `enum` y su fila en `docs/errores.md`; la prueba de documentación obliga a hacerlo en el mismo PR | En serie no hay conflictos |
| `@DomainRule.Rule`, `SingleLineText`, `@CodePointSize`, acumulador, bloqueo | CM-274 PR 1 | CM-54 y CM-66 agregan constantes al final de `Rule`; CM-54, CM-66 y CM-67 usan `loadForUserForUpdate` | Punto de extensión: `Rule` |
| Colección de Postman | Existe (`docs/CAMEIA_Perfil_Sprint1.postman_collection.json`, variable `base_url`); CM-279 P2 crea `docs/perfil-local.postman_environment.json`; CM-274 PR 4 crea `expectProblem` y `expectFieldError` a nivel de colección | Una carpeta por HU (`HU-2.3 — Información general`, `HU-2.4 — …`, `HU-2.5 — Habilidades`, `HU-2.5 — Finalización`) | Nadie renombra carpetas ajenas |
| `RabbitConfig` | CM-279 P2 (topología de `cuentas.events`) | CM-67 PR 3 (`perfil.events`) | — |
| `ProfileAppService` (constructor) | CM-274 PR 1 agrega `Clock`; PR 6 agrega `BirthDateReplica` | CM-67 agrega su puerto de salida | Cada una actualiza `ProfileAppServiceTest.setUp` |

## 20. Línea base verde (antes de tocar nada)

Se corre en la base de la rama del PR 1 (`origin/develop` con CM-279 P1 y el PR 0 fusionados) y se pega la salida en `analisis/ejecucion_CM-274.md`. Si algo no está en verde, se detiene la ejecución y se reporta: es un hallazgo con destino antes de empezar.

| Comprobación | Comando (PowerShell, raíz del worktree) | Resultado esperado |
|---|---|---|
| Herramientas | `java -version`; `docker version`; `node -v`; `npx newman --version` | JDK 21; Docker Desktop encendido; Node ≥ 18; Newman 6 |
| Build y pruebas | `./mvnw.cmd -B clean verify` | `BUILD SUCCESS`; 0 fallos y 0 omitidas en Surefire y Failsafe. En `2a54ca7` fueron 310 pruebas unitarias y 20 de integración; con P1 son más (se anota el número real) |
| Cobertura de partida | `target/site/jacoco/jacoco.csv` (sumar `LINE_*` y `BRANCH_*`) | Se anota el global (en `5f2c088`: 93,1 % líneas, 85,3 % ramas): es el piso que ningún PR puede bajar |
| Migraciones | `git ls-tree --name-only HEAD src/main/resources/db/migration/` | V1 a V5; V5 es `V5__replica_fecha_nacimiento.sql` |
| Postman | `docker compose down -v`; `docker compose up -d --build`; esperar `GET http://localhost:8082/actuator/health` 200; `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json -e docs/perfil-local.postman_environment.json --reporters cli` | 0 fallos (en `2a54ca7`: 77 peticiones, 215 aserciones) |

**Trampas del entorno:** el `docker-compose.yml` publica PostgreSQL en el puerto `5432`; si otro proceso lo ocupa en esta máquina (pasó con CM-36), se arranca con `$env:POSTGRES_PORT='55433'; docker compose up -d --build`. Testcontainers usa puertos aleatorios y no tiene ese problema. Las `*IT` las corre Failsafe en `verify`; `./mvnw.cmd test` no las corre.

## 21. Regla → evidencia (R1 a R14)

| Regla | Evidencia prevista (prueba, comando o petición) | Estado en la spec |
|---|---|---|
| R1 Idioma | Revisión del diff: identificadores y nombres de prueba en inglés; Javadoc, comentarios, `@DisplayName`, OpenAPI y logs en español (sección 11) | Cubierta |
| R2 Validación | Matriz de la sección 6; `ProfileControllerTest` (borde), `WorkExperienceTest` y `EducationTest` (dominio), `DomainRuleValidatorTest`, `CodePointSizeValidatorTest` | Cubierta |
| R3 Errores | Sección 8; ruta al 500 cerrada (8.3) con `WorkHistoryEndToEndIT.addWorkExperience_shouldRejectControlCharacter_whenCompanyHasNul` y `…shouldReturn201WithoutDates_whenUnknownEndHasNoDates`; `ErrorCodeDocumentationTest`; Newman sin `INTERNAL_ERROR` | Cubierta |
| R4 Base de datos | V6 y V7 (sección 7); `WorkHistorySchemaIT` (cada `ck_` violado, nombres, migración con datos), `ColumnLengthConsistencyIT` | Cubierta |
| R5 Casos borde | Sección 9, celda por celda | Cubierta |
| R6 Seguridad | Plan de seguridad de la sección 10 (IDs de ASVS y OWASP API por endpoint); SEG-01 a SEG-04 de la sección 15 | Cubierta |
| R7 Código | Sección 11; `ArquitecturaTest` (incluida la regla nueva de T-A9) | Cubierta |
| R8 Documentación | Sección 12; `OpenApiDocumentIT`; `ErrorCodeDocumentationTest`; ADR 0005 | Cubierta |
| R9 Pruebas | Sección 13 y tabla de la sección 4 | Cubierta |
| R10 Cobertura | `jacoco.csv` por clase de la sección 13, ≥ 90 % líneas y ramas; global ≥ línea base (sección 20) | Cubierta |
| R11 Postman | Sección 14; carpeta `HU-2.4`; Newman contra la aplicación empaquetada (T-E3) | Cubierta |
| R12 Backlog | Tabla de la sección 4 (59 CA de HU-2.4 más RT-01 y RT-07); CA-2.4.45 y 2.4.50 con destino (sección 16) | Cubierta |
| R13 Atributos | Tabla de la sección 15 con IDs del anexo | Cubierta |
| R14 Prohibiciones | Sin push, PR ni Jira fuera de lo que Paula autorice; nada inventado | Cubierta |

## 22. Lista de verificación de la CM

Quien ejecuta (prompt B) la corre entera antes de entregar y pega el resultado de cada fila; quien verifica (prompt C) la repite como piso y busca además lo que no se previó.

| # | Comprobación | Cómo | Resultado esperado |
|---|---|---|---|
| V-01 | Cada CA de la sección 4 tiene su prueba | Para cada nombre de la columna «Prueba»: `git grep -n "<método>" -- src/test` | Una coincidencia por nombre; ninguna falta |
| V-02 | Cada CA tiene su petición | Nombres de la columna «Postman» buscados en la colección | Todos presentes en la carpeta `HU-2.4 — Experiencia laboral y formación académica` |
| V-03 | Textos literales | `ProfileControllerTest` y Newman comparan `message`/`detail` con el texto entre « » de la sección 6 y 8 | Iguales carácter por carácter (tildes y punto final) |
| V-04 | Suite completa | `./mvnw.cmd -B clean verify` | `BUILD SUCCESS`, 0 fallos, 0 omitidas |
| V-05 | Arquitectura | `ArquitecturaTest` dentro de V-04; además, la prueba temporal de T-A9 que demuestra que la regla nueva muerde | Verde; la temporal falló y se quitó |
| V-06 | Cobertura | `jacoco.csv` de cada clase de la sección 13 | ≥ 90 % líneas y ramas cada una; global ≥ línea base; lo no cubierto, con su razón |
| V-07 | Ninguna ruta al 500 | Peticiones reales (Newman o `curl`) de la sección 8.3 y una con cada valor raro de la sección 6 (`"company": {}`, `"inProgress": "abc"`, U+0000, cuerpo de 1 MiB, `text/plain`) | Ninguna responde 500 ni `INTERNAL_ERROR` |
| V-08 | Forma de error | Cualquier 4xx de la corrida | `application/problem+json;charset=UTF-8`, `type`, `title`, `status`, `detail`, `instance`, `code`, `requestId`; en 422, `errors[]` con `field`, `code`, `message` y `detail` «Revisa los campos marcados.»; sin `trace`, `exception`, `co.edu`, `SQL` ni `org.` |
| V-09 | Migraciones | `WorkHistorySchemaIT` | V6 y V7 corren sobre una base con filas V5; cada `ck_` rechaza su fila; nombres `pk_`, `fk_`, `ix_`; sin `area_estudio` |
| V-10 | Largos coherentes | `ColumnLengthConsistencyIT` y `OpenApiDocumentIT` | 100/100/500/150/150 en columna, dominio y `@Schema` |
| V-11 | Concurrencia | `ProfileWriteLockIT` | Espera y luego aplica; 409 entre 1,5 s y 3,5 s; 4 experiencias como máximo; la segunda baja de la última formación en Activo da 409 |
| V-12 | Repetición | `…shouldHaveNoExtraEffect_whenRepeatedTwentyTimes` | Un 204 y diecinueve 404; nada más cambia |
| V-13 | Postman y Newman | T-E3 completo contra la aplicación empaquetada | 0 fallos; tiempo máximo por petición anotado |
| V-14 | DES-02 | Carpeta `DES-02 — HU-2.4`, 105 iteraciones, p95 sin las 5 primeras | p95 ≤ 2 s en `POST` y `DELETE` |
| V-15 | Logs | `docker compose logs app` tras V-13 | Ningún `ERROR` salvo los 503 provocados a propósito; ningún dato de la sección 15 (SEG-04); cada rechazo con `code` y `requestId` |
| V-16 | Catálogo | `ErrorCodeDocumentationTest`; `docs/errores.md` | Cada código vivo documentado y probado; `FIELD_OF_STUDY_TOO_LONG` y `PROFILE_CREATION_TIMEOUT` en «Códigos retirados» |
| V-17 | Idioma y comentarios | `git diff origin/develop -- src` revisado; `git grep -n "CM-[0-9]" -- src` | Sin identificadores en español nuevos; Javadoc en español; ningún `CM-NNN` en el código |
| V-18 | Sin dependencias nuevas | `git diff origin/develop -- pom.xml` | Vacío |
| V-19 | Tamaño de cada PR | `git diff --shortstat <base>...<rama>` | ≤ 1000 líneas (≤ 950 en el PR 1, si no, regla del plan) |
| V-20 | Revisiones | `/simplify`, `/code-review high`, `/security-review` (toca identidad y datos personales) | Hallazgos corregidos o descartados con razón |

## 23. Puerta de listo

| Condición (`reglas-obligatorias.md`) | Estado |
|---|---|
| 1. Tabla CA → requisito → prueba → Postman y tabla de atributos | Secciones 4 y 15 |
| 2. Matriz por campo, casos borde, ruta al 500 cerrada y plan de seguridad (ASVS y OWASP API por endpoint) | Secciones 6, 8.3, 9 y 10 |
| 3. Contraste con `origin/develop` `2a54ca7` y ensayo en seco de las tarjetas | Hecho el 9-oct-2026 (`analisis/revision-spec_CM-274.md`) |
| 4. Integración con las otras CM | Sección 19 |
| 5. Línea base | Sección 20 |
| 6. Lista de verificación | Sección 22 |
| 7. Dudas | Ninguna bloquea (sección 18) |

**Dictamen:** LISTA PARA EJECUTAR, condicionada a que CM-279 P1 esté fusionado (o a que Paula elija el otro orden de Perfil; ver `analisis/revision-spec_CM-274.md`). La aprueba solo Paula.
