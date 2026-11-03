# Plan — CM-274 (cameia-perfil)

Base: `origin/develop` `2a54ca7` más el bloque P1 de CM-279 fusionado (orden de Perfil del plan de ejecución global: CM-279 P1 → CM-279 P2 → **CM-274** → CM-54 → CM-66 → CM-67). Estado: pendiente de aprobación de Paula. La rama de la spec (`CM-274-experiencia-formacion-v4`) va 24 commits detrás de `develop`: se rebasa sobre `origin/develop` antes del PR 0.

## 1. Stack

| PR | Rama | Base | Tarjetas | Líneas | Horas | Requisito previo |
|---|---|---|---|---|---|---|
| 0 | `CM-274-experiencia-formacion-v4` (la de la spec, rebasada) | `develop` | — (solo `spec.md`, `plan.md`, `tasks.md`) | ≈ 1430, solo documentos | 0,2 | Spec, plan y tarjetas aprobados por Paula. Precedente: la spec de CM-36 fue un PR propio de 1526 líneas (#38). Paula lo fusiona antes del stack de código |
| 1 | `CM-274-base-escritura-perfil` | `develop` con el PR 0 y CM-279 P1 | T-A0 a T-A10 | ≈ 900 | 4,5 | CM-279 P1 fusionado (V5, `ClockConfig`, `BIRTH_DATE_UNAVAILABLE`, `reject` por estado) |
| 2 | `CM-274-experiencia-laboral` | PR 1 | T-B1 a T-B7 | ≈ 780 | 4 | — |
| 3 | `CM-274-formacion-academica` | PR 2 | T-C1 a T-C7 | ≈ 830 | 4 | — |
| 4 | `CM-274-postman-experiencia` | PR 3 | T-D1, T-D2 | ≈ 700 | 2 | — |
| 5 | `CM-274-postman-formacion` | PR 4 | T-E1 a T-E3 | ≈ 700 | 2 | — |
| 6 | `CM-274-fecha-nacimiento` | `develop` con los PR 1 a 5 fusionados | T-F1 a T-F4 | ≈ 600 | 3 | — (P1 ya está en la base) |

El stack de código tiene 5 PR (1 a 5); el 6 sale aparte de `develop`. **Regla de tamaño del PR 1:** si al cerrar T-A8 el diff del PR 1 pasa de 950 líneas (agregadas + eliminadas contra su base), T-A10 sale del PR 1 y pasa a ser el primer commit del PR 2; se anota en `tasks.md`. Revisión independiente temprana: al terminar el PR 2 (estándar de stacks). Tras rebasar, si el árbol de una rama no cambió (`git rev-parse <rama>^{tree}`), hereda la verificación. Total: ≈ 19,7 h (el backlog estima 3,5 h).

## 2. Diseño

### 2.1 Dos fases de validación (D3)

1. **Borde.** Los `record` de petición normalizan en su constructor compacto con `SingleLineText.normalize` (recorte de los espacios de JavaScript y NFC); un opcional que queda vacío pasa a `null`. Luego Bean Validation aplica `@NotBlank` y `@CodePointSize(max)` (código y mensaje desde `ErrorCatalog`) y `@DomainRule(Rule.X)`, que ejecuta la regla de forma escrita una vez en el dominio (caracteres de control) o en `FieldValues` (opciones y mes; T-A9 lo mueve desde `application/command/CommandValues`) y toma de su rechazo el código y el mensaje. Cada restricción ignora el valor ausente o en blanco, así que cada campo tiene a lo sumo una violación. `ApiExceptionHandler.fieldProblems` arma `errors[]` con un elemento por campo: si la violación trae el código de `@DomainRule`, usa ese código y su mensaje; si no, `ErrorCatalog.FIELD_CODES` (`<Dto>.<campo>.<Restricción>` → código) y `FIELD_MESSAGES`.
2. **Dominio.** `WorkExperience.create(WorkExperienceDraft, DateBounds)` y `Education.create(EducationDraft, DateBounds)` aplican todas las reglas (forma como invariante y reglas del registro) con `InvalidFieldsException.Collector` y lanzan una sola excepción con un error por campo. `rebuild(...)` reconstruye sin reglas.
3. **Agregado.** `ProfessionalProfile.addWorkExperience`/`addEducation` comprueban el máximo (409); `removeEducation` comprueba pertenencia (404) y mínimo (409).

Los mensajes que emiten las dos fases para el mismo código son idénticos: `FieldMessagesConsistencyTest` provoca cada código en el dominio y compara su mensaje con `ErrorCatalog.FIELD_MESSAGES`.

### 2.2 Bloqueo (D1, D2)

`ProfessionalProfileJpaRepository.findLockedById` con `@Lock(PESSIMISTIC_WRITE)`; el adaptador fija antes `set local lock_timeout = '2s'` (constante), captura `55P03` con el `isLockNotAvailable` existente y lanza `ProfileUpdateInProgressException` (409), y luego vuelve el `lock_timeout` a su valor por defecto, como hace `lockCreationFor`. El puerto expone `findByIdForUpdate(ProfileId)` con `Propagation.MANDATORY`. `ProfileAppService.loadForUserForUpdate` lo usa en todas las escrituras.

### 2.3 Reloj

`ClockConfig` (lo crea CM-279 P1) publica `Clock.systemUTC()`; esta tarea no crea otro reloj. `ProfileAppService` lo recibe por constructor y arma `DateBounds(YearMonth.now(clock), null)`; en el bloque F, el mes de nacimiento sale del puerto `BirthDateReplica`.

### 2.4 Persistencia

`WorkExperienceEntity.startDate` admite `null`; `ProfessionalProfileMapping` convierte `null` ↔ `null` y reconstruye con `rebuild`. `ProfileResponse` formatea `null` como `null`.

## 3. Archivos por PR

| PR | Crear | Modificar | No tocar |
|---|---|---|---|
| 1 | `domain/model/SingleLineText`, `domain/model/FieldValues` (movido), `domain/exception/ProfileUpdateInProgressException`, `presentation/dto/validation/{CodePointSize, CodePointSizeValidator, DomainRule, DomainRuleValidator, package-info}`, `domain/exception/ProfileCreationInProgressException`, `docs/adr/0005-bloqueo-del-perfil-en-escrituras.md`; pruebas `SingleLineTextTest`, `InvalidFieldsExceptionTest`, `CodePointSizeValidatorTest`, `DomainRuleValidatorTest`, `ProfileWriteLockIT`, `ErrorCodeDocumentationTest` | `InvalidFieldsException` (acumulador), `ErrorCode`, `ErrorCatalog`, `ErrorCatalogTest`, `ApiExceptionHandler`, `WorkExperience` y `Education` (solo las constantes del rechazo por caracteres), `ProfessionalProfileRepository`, `ProfessionalProfileJpaRepository`, `ProfessionalProfileRepositoryAdapter`, `ProfileAppService`, `ProfileAppServiceTest`, `docs/errores.md` | `.github/**`, `pom.xml`, migraciones existentes |
| 2 | `domain/model/WorkExperienceDraft`, `domain/model/DateBounds`, `domain/exception/WorkExperienceLimitReachedException`, `db/migration/V6__alinear_experiencia_laboral.sql`; pruebas `WorkHistoryEndToEndIT`, `WorkHistorySchemaIT`, `ColumnLengthConsistencyIT`, `FieldMessagesConsistencyTest` | `WorkExperience`, `ProfessionalProfile`, `AddWorkExperienceRequest`, `AddWorkExperienceCommand`, `ProfileAppService`, `WorkExperienceEntity`, `ProfessionalProfileMapping`, `ProfileResponse`, `ProfileController` (OpenAPI), `ErrorCode`, `ErrorCatalog`, `docs/errores.md`, pruebas existentes de experiencia | lo de formación |
| 3 | `domain/model/EducationDraft`, `domain/exception/{EducationLimitReachedException, EducationNotAllowedException}`, `db/migration/V7__alinear_educacion.sql`; prueba `EducationTest` | `Education`, `ProfessionalProfile`, `AddEducationRequest`, `AddEducationCommand`, `FieldValues` (sin `yearOnly`), `DomainRule`, `ProfileAppService`, `EducationEntity`, `ProfessionalProfileMapping`, `ProfileResponse`, `ProfileController`, `ErrorCode`, `ErrorCatalog`, `docs/errores.md`, `ProfileWriteLockIT`, `WorkHistoryEndToEndIT`, `WorkHistorySchemaIT`, `ColumnLengthConsistencyIT` | — |
| 4 | — | colección de Postman (carpeta HU-2.4, experiencia) | código |
| 5 | `docs/perfil-local.postman_environment.json` | colección (formación), `README.md` (cómo correr Newman) | código |
| 6 | — (puerto `BirthDateReplica`, `BirthDateUnavailableException`, tabla y doble `InMemoryBirthDateReplica` los crea CM-279 bloque P1) | `DateBounds`, `WorkExperience`, `Education`, `ProfileAppService`, `docs/errores.md`, colección | lo de CM-279 |

## 4. Matriz de pruebas (resumen; el detalle con valores está en `tasks.md`)

| PR | Dominio | Borde | Servicio | Web | Integración |
|---|---|---|---|---|---|
| 1 | `SingleLineText`, acumulador | `@CodePointSize` y `@DomainRule` | cada escritura usa `findByIdForUpdate`; reloj | — | bloqueo serializa; 409 a los 2 s; creación alineada; `ErrorCodeDocumentationTest` |
| 2 | reglas de la experiencia, 4/5 | campos de la experiencia | `save` no se llama ante error | 422 por campo, 409, 201 | E2E, migración V5, largos de columna, carrera de altas |
| 3 | reglas de la formación, 5/6, mínimo | campos de la formación | — | 422, 409, 204 | E2E, V6, carrera de bajas |
| 4–5 | — | — | — | — | Newman completo contra la aplicación empaquetada |
| 6 | 15 años, nacimiento | — | réplica leída solo con fechas; 503 | 503 | E2E con réplica |

## 5. Riesgos

| Riesgo | Mitigación |
|---|---|
| La migración falla en staging por datos que no cumplen un `CHECK` | Consultas previas por DevOps (spec 7.3) antes de desplegar el PR 2 y el PR 3 |
| El bloqueo alarga la espera de una petición | Espera máxima de 2 s con 409; el bloqueo dura solo la transacción |
| Cambiar el constructor de `WorkExperience`/`Education` rompe pruebas existentes | Se actualizan en la misma tarjeta (listadas) |
| El número de migración choca con otra tarea | Números fijos por el orden de fusión de Perfil: V5 CM-279 P1, V6 y V7 esta tarea, V8 CM-66, V9 CM-67. T-B5 y T-C5 comprueban la base antes de crear el archivo; si el orden de fusión cambia, se renumera antes del push (ninguna está aplicada en un ambiente) |
| Dos `@Bean Clock` o dos ADR 0004 | El reloj es `ClockConfig` de CM-279 P1 (T-A4 lo reutiliza); el ADR del bloqueo es el 0005 (el 0004 es el de la réplica de CM-279 P2) |
| `cameia-web` espera 422 donde ahora hay 409 | Aviso a Frontend (documento único de Frontend) antes de fusionar el PR 2 |

## 6. Orden y dependencias

CM-279 P1 (fusionado) → PR 0 → PR 1 → 2 → 3 → 4 → 5 → PR 6. CM-54, CM-66 y CM-67 salen de `develop` con el PR 1 fusionado (usan `SingleLineText`, `@CodePointSize`, `@DomainRule`, el acumulador y el bloqueo). Ninguna tarjeta tiene preguntas abiertas que la bloqueen.
