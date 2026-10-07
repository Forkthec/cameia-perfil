# Spec — CM-271: cupo del Plan Free y formato de error con código en Perfil

- **Tarea:** CM-271 · Subtarea «Ajustes v4 – Backend Perfil (HU-2.2)» · padre CM-16 «HU-2.2 Selección del Método de Configuración» · Sprint 2 · responsable: Paula Andrea Muñoz Delgado
- **Repositorio:** `cameia-perfil`, rama `CM-271-cupo-perfil-plan-free`, creada desde `origin/develop` (`42320d9`)
- **Backlog vigente:** `05102026_01_Backlog.xlsx`, hoja `HE-02`, HU-2.2 (CA-2.2.1 a 2.2.7) y reglas transversales RT-01, RT-03, RT-05 y RT-06
- **Estado:** spec, plan y tarjetas escritos; **pendiente de aprobación de Paula** (preguntas en la sección 12)
- **Atributos de calidad que toca:** compatibilidad de contrato (aditiva salvo dos estados, sección 6), mantenibilidad y observabilidad (código estable por error), fiabilidad (sin perfiles duplicados por doble envío), seguridad (sin mensajes de librería al cliente)

## 1. Contexto y objetivo

HU-2.2 crea un Perfil Profesional vacío en Borrador con `POST /api/v1/profiles` (sin cuerpo). El Plan Free permite 1 perfil no archivado (RN-021). En el Sprint 2 el cupo agotado se prueba con el mensaje provisional de CA-2.2.3, sin Paywall; el Plan Premium (CA-2.2.5 y 2.2.6) es del Sprint 3 de Jira.

Esta tarea es además **la primera tarea de código de Perfil**, y el estándar de Backend (CM-283, hallazgo H-14) le asigna adoptar el formato de error común: `code` estable en toda respuesta de error, `requestId`, `errors[]` con `field`, `code` y `message`, 422 para la validación (RT-01-CA05) y 500 genérico sin detalle (RT-05-CA03). Las tareas siguientes de Perfil (CM-54, CM-274, CM-66 y CM-67) se apoyan en esta base.

**Objetivo de Backend:** (1) que todo error de Perfil lleve un `code` estable y nunca filtre texto de librerías ni trazas; (2) que el segundo perfil de un Usuario con Plan Free responda 409 `PROFILE_LIMIT_REACHED` con «Tu Plan Free permite 1 Perfil Profesional.»; (3) que dos creaciones simultáneas del mismo Usuario creen un solo perfil.

## 2. Alcance y bloques de PR

| Bloque | Qué entrega | CA o regla |
|---|---|---|
| A | Formato de error de Perfil: `ErrorCode`, excepción base `BusinessException`, `code` y `requestId` en toda respuesta de error, validación en 422 con `errors[]`, 500 genérico | RT-01-CA05, RT-05-CA01 y CA03, estándar §A |
| B | Cupo del Plan Free: texto y código del 409, creación sin duplicados ante doble envío, pruebas del perfil vacío | CA-2.2.1, 2.2.3, 2.2.4, 2.2.7 |

Fuera de alcance: sección 11.

## 3. Trazabilidad de los CA

| CA | Dueño | Estado en `42320d9` | Brecha | Bloque |
|---|---|---|---|---|
| 2.2.1 creación con «Llenado Manual» | B | Cumple: 201, `IN_PROGRESS`, sin nombre (`ProfileControllerTest.postProfiles_returns201WithProfileBody`) | Ninguna; se conserva la prueba | — |
| 2.2.2 «Autocompletar con IA» deshabilitado | F | — | — | — |
| 2.2.3 cupo del Plan Free agotado | F y B | 409 con «El usuario ya tiene un perfil profesional creado. El plan gratuito permite solo uno. TODO CM-TBD: …» (`ProfileAlreadyExistsException`) | Texto literal del CA, sin `TODO`; `code` `PROFILE_LIMIT_REACHED` | B |
| 2.2.4 perfil creado vacío y sin método | B | Cumple (no existe columna de método) | Prueba de `GET` del perfil recién creado: `name` nulo, colecciones vacías | B |
| 2.2.5 / 2.2.6 Plan Premium | B | — | **Sprint 3 de Jira** (requiere HU-8.2) | — |
| 2.2.7 selección repetida | F y B | **Defecto:** `existsByFirebaseUid` y `save` no son atómicos; dos peticiones simultáneas crean dos perfiles | Una sola creación ante peticiones simultáneas | B |

## 4. Requisitos funcionales (EARS)

### Bloque A — formato de error

- **REQ-PE-01.** Cuando Perfil responda cualquier error (4xx o 5xx), el cuerpo debe ser `application/problem+json` e incluir el miembro `code` de nivel superior con un valor del catálogo de la sección 5, sin quitar `type`, `title`, `status`, `detail` ni `instance`.
- **REQ-PE-02.** Cuando Perfil responda un error, debe incluir el miembro `requestId` con el valor del encabezado `X-Request-Id` recibido si cumple `^[A-Za-z0-9._-]{1,64}$`, o con un UUID v4 generado en caso contrario, y devolver el mismo valor en el encabezado `X-Request-Id` de la respuesta.
- **REQ-PE-03.** Cuando falle la validación de Bean Validation de un cuerpo, Perfil debe responder **422** (hoy 400) con `code` `VALIDATION_FAILED`, `detail` «Revisa los campos marcados.» y una lista `errors` con un elemento por campo rechazado (`field` = nombre del campo JSON, `code`, `message`). Si una restricción no tiene `code` asignado en la tabla del manejador, una prueba debe fallar.
- **REQ-PE-04.** Cuando el cuerpo no se pueda leer (JSON mal formado, tipo incorrecto), Perfil debe responder 422 con `code` `REQUEST_BODY_INVALID_FORMAT` y `detail` «Revisa el formato de los datos enviados.», sin el texto de Jackson.
- **REQ-PE-05.** Cuando un objeto de valor o un enumerado rechace un dato (`IllegalArgumentException`, incluido `Enum.valueOf` y `YearMonth.parse` envuelto), Perfil debe responder 422 con `code` `REQUEST_INVALID_VALUE` y `detail` «Revisa los datos enviados.», **sin** el mensaje de la excepción (hoy filtra el nombre completo de clases, hallazgo 2 del estándar). Las tareas CM-54, CM-274 y CM-66 reemplazan esta respuesta genérica por errores con `field` en sus campos. Mientras exista, el respaldo registra en `WARN` el origen (`clase.método` del primer elemento de la traza) con `code` y `requestId`, sin el mensaje: cada caso que caiga aquí queda identificado en el log.
- **REQ-PE-06.** Cuando ocurra un fallo no previsto, Perfil debe responder 500 con `code` `INTERNAL_ERROR` y `detail` «Ocurrió un error. Inténtalo de nuevo.» (RT-05-CA01), sin traza, SQL ni nombre de clase, y registrar el error en `ERROR` con la traza y el `requestId`.
- **REQ-PE-07.** Cada error de negocio debe registrarse una vez en `WARN`, sin traza, con `code` y `requestId`, y sin datos personales.
- **REQ-PE-08.** Mientras exista, cada excepción de negocio de Perfil debe extender `BusinessException` y llevar su `ErrorCode`; el manejador toma el estado y el código de la excepción, no los inventa.
- **REQ-PE-09.** Los estados vigentes de los errores de negocio no cambian (sección 5): solo cambian la validación del cuerpo (400 → 422) y el cuerpo ilegible (400 → 422).

### Bloque B — cupo del Plan Free

- **REQ-PE-20 (CA-2.2.3).** Cuando un Usuario que ya tiene 1 Perfil Profesional (en cualquier estado) llame a `POST /api/v1/profiles`, Perfil debe responder 409 con `code` `PROFILE_LIMIT_REACHED` y `detail` «Tu Plan Free permite 1 Perfil Profesional.», y no debe crear ningún perfil.
- **REQ-PE-21 (CA-2.2.7, RT-06-CA02).** Cuando lleguen dos o más `POST /api/v1/profiles` simultáneos del mismo Usuario sin perfiles, Perfil debe crear **exactamente un** perfil: una petición responde 201 y las demás 409 `PROFILE_LIMIT_REACHED`.
- **REQ-PE-22 (CA-2.2.4).** Cuando se consulte con `GET /api/v1/profiles/{id}` un perfil recién creado, la respuesta debe tener `status` `IN_PROGRESS`, `name` y `summary` nulos y `workExperiences`, `educations`, `profileSkills` y `targetRoles` vacías.
- **REQ-PE-23.** El cupo se cuenta por `firebase_uid` tomado de `X-User-Id` (RT-03-CA04); el cuerpo de la petición se ignora (la creación no recibe cuerpo).
- **REQ-PE-24.** El `detail` del 409 no debe contener `TODO`, claves de Jira ni nombres internos.

## 5. Catálogo de errores de Perfil (primera versión)

`ErrorCode` vive en `domain/exception`. Un código ↔ un estado ↔ un mensaje ↔ una excepción ↔ una prueba. Los textos vigentes de las excepciones no se cambian en esta tarea, salvo los indicados.

| Código | HTTP | Excepción u origen | `detail` |
|---|---|---|---|
| `VALIDATION_FAILED` | 422 | Bean Validation del cuerpo | «Revisa los campos marcados.» |
| `REQUEST_BODY_INVALID_FORMAT` | 422 | `HttpMessageNotReadableException` | «Revisa el formato de los datos enviados.» |
| `REQUEST_INVALID_VALUE` | 422 | `IllegalArgumentException` (respaldo hasta que cada campo tenga su código) | «Revisa los datos enviados.» |
| `IDENTITY_REQUIRED` | 401 (400 en `POST /api/v1/profiles`, por el `@RequestHeader` obligatorio) | `IdentityRequiredException`; `MissingRequestHeaderException` | «Identidad del usuario requerida» (vigente) |
| `PROFILE_NOT_FOUND` | 404 | `ProfileNotFoundException` | vigente |
| `PROFILE_NOT_ALLOWED` | 403 | `ProfileAccessDeniedException` | vigente («No tienes permiso para acceder a este perfil») |
| `PROFILE_LIMIT_REACHED` | 409 | `ProfileAlreadyExistsException` (se renombra a `ProfileLimitReachedException`, ver D4) | «Tu Plan Free permite 1 Perfil Profesional.» |
| `PROFILE_ALREADY_COMPLETED` | 409 | `ProfileAlreadyCompletedException` | vigente (CM-67 lo cambia a «Este perfil ya está activo.») |
| `PROFILE_INCOMPLETE` | 422 | `IncompleteProfileException` (cuerpo propio `missingRequirements`, que CM-67 lleva a `ProblemDetail`) | vigente |
| `PROFESSIONAL_ROLE_NOT_FOUND` | 404 | `ProfessionalRoleNotFoundException` | vigente |
| `TARGET_ROLE_LIMIT_REACHED` | 422 | `MaxTargetRolesExceededException` | vigente |
| `TARGET_ROLE_ALREADY_EXISTS` | 409 | `DuplicateTargetRoleException` | vigente |
| `TARGET_ROLE_NOT_ALLOWED` | 422 | `LastTargetRoleException` | vigente |
| `SKILL_ALREADY_EXISTS` | 409 | `DuplicateSkillException` | vigente (CM-66 lo cambia) |
| `INTERNAL_ERROR` | 500 | cualquier otro | «Ocurrió un error. Inténtalo de nuevo.» |

**Códigos de campo** (elementos de `errors[]`) para las restricciones que existen hoy en los DTO; la tabla del manejador usa la clave `ClaseDelDto.campo.Restricción`, porque `level` y `provenance` se repiten en varios DTO con mensajes distintos:

| Clave | `code` | `message` (vigente: el de Bean Validation se reemplaza por este) |
|---|---|---|
| `AddWorkExperienceRequest.company.NotBlank` | `COMPANY_REQUIRED` | «Ingresa la empresa.» (CA-2.4.3) |
| `AddWorkExperienceRequest.position.NotBlank` | `POSITION_REQUIRED` | «Ingresa el cargo.» (CA-2.4.3) |
| `AddWorkExperienceRequest.startDate.NotBlank` | `START_DATE_REQUIRED` | «Ingresa la fecha de inicio.» (CM-274 la vuelve opcional en la experiencia, C-14) |
| `AddWorkExperienceRequest.employmentStatus.NotNull` | `EMPLOYMENT_STATUS_REQUIRED` | «Selecciona una opción.» |
| `AddWorkExperienceRequest.provenance.NotNull`, `AddSkillRequest.provenance.NotBlank`, `AddTargetRoleRequest.provenance.NotNull` | `PROVENANCE_REQUIRED` | «Selecciona una opción.» |
| `AddSkillRequest.skillName.NotBlank` | `SKILL_NAME_REQUIRED` | «Ingresa una habilidad.» (CA-2.5.7) |
| `AddSkillRequest.level.NotBlank` | `SKILL_LEVEL_REQUIRED` | «Elige un nivel.» (CA-2.5.7) |
| `AddTargetRoleRequest.professionalRoleId.NotNull` | `PROFESSIONAL_ROLE_ID_REQUIRED` | «Selecciona una opción.» |

Los textos de esta tabla son los literales de los CA cuando existen; los demás («Selecciona una opción.») siguen RT-01. «Selecciona una opción.» lo fija RT-01 para listas (pregunta 5, cerrada sin consulta).

`PROFILE_NOT_ALLOWED` y `TARGET_ROLE_NOT_ALLOWED` usan la causa `NOT_ALLOWED` del vocabulario cerrado. `ProfessionalRoleController` arma hoy su propio `ProblemDetail` (hallazgo 5): queda igual en esta tarea y se registra como pendiente con destino (pregunta 4).

## 6. Contrato

- **Aditivo:** `code` y `requestId` en todo error; `errors[]` con `field`, `code`, `message`; encabezado `X-Request-Id` en las respuestas de error.
- **Cambia el estado** de dos casos que `cameia-web` puede ver: validación del cuerpo 400 → 422 y cuerpo ilegible 400 → 422 (lo exige RT-01-CA05). `cameia-web` hoy espera 400 en «agregar experiencia» y «agregar educación» (`SPEC.md` de `professional-profile`, filas de la API): se avisa a Frontend en el documento ya existente (pregunta 2).
- **Cambia un texto:** el `detail` del 409 de creación (CA-2.2.3). `cameia-web` muestra hoy «Ya tienes un perfil creado.» con su propio catálogo; no depende del texto del servidor.
- OpenAPI: `POST /api/v1/profiles` documenta 201, 400 (`IDENTITY_REQUIRED`), 409 (`PROFILE_LIMIT_REACHED`, con ejemplo) y 500; el `@Schema` del error común muestra `code` y `requestId`.

## 7. Datos

- Sin columnas nuevas. **No** se agrega `UNIQUE (firebase_uid)`: el Plan Premium permitirá hasta 5 perfiles (RN-092).
- Exclusión mutua de la creación (REQ-PE-21): bloqueo asesor de transacción de PostgreSQL por Usuario, `SELECT pg_advisory_xact_lock(hashtextextended(:firebaseUid, 0))`, tomado dentro de la transacción de `createProfile` **antes** de contar sus perfiles. El bloqueo se libera solo al confirmar o deshacer. Decisión D2.
- El conteo pasa de `existsByFirebaseUid` a `countByFirebaseUid` para que el Sprint 3 compare con el cupo del plan (1 o 5) sin otra consulta.

## 8. Seguridad y calidad

- **API3 / RT-01-CA06:** la creación no lee cuerpo; nada del cuerpo cambia el dueño ni el estado.
- **API1 / RT-03-CA04:** el dueño sale de `X-User-Id`; el cupo se cuenta por ese valor.
- **API4:** el bloqueo asesor es por Usuario y dura lo que la transacción (milisegundos); no bloquea a otros Usuarios.
- **API8 / RT-05-CA03:** ningún `detail` lleva mensajes de librería, trazas ni clases (REQ-PE-05 y 06).
- **Observabilidad:** `code` y `requestId` en el log de cada error (REQ-PE-07).
- **Datos sensibles en logs:** solo `firebase_uid` cuando haga falta; nunca nombre, resumen ni correo.

## 9. Casos borde (estándar §C)

| Grupo | Aplica | Cómo se cubre |
|---|---|---|
| Presencia | `X-User-Id` ausente, vacío, solo espacios | Pruebas de controlador: ausente → 400 `IDENTITY_REQUIRED` en `POST`; vacío y espacios → 401 `IDENTITY_REQUIRED` (servicio) |
| Duplicados y concurrencia | Sí | Dos y cinco hilos creando a la vez para el mismo Usuario → un solo perfil (prueba con base real, pregunta 1) |
| Colecciones | Cupo: 0 perfiles (201), 1 perfil (409) | Servicio y controlador |
| Propiedad | Otro Usuario con su propio cupo | Usuario B crea aunque A ya tenga un perfil |
| `X-Request-Id` | Válido (`abc-123`), inválido (`<script>`, 65 caracteres), ausente | Se conserva el válido; se genera UUID en los otros |
| Carga | JSON mal formado, campo de tipo erróneo | 422 `REQUEST_BODY_INVALID_FORMAT` (con una ruta que reciba cuerpo, por ejemplo `PATCH /api/v1/profiles/{id}`) |
| Estado, fechas, Unicode | No | La creación no recibe datos |

## 10. Decisiones

| # | Decisión | Porqué | Alternativas descartadas | Decisión humana |
|---|---|---|---|---|
| D1 | Formato de error igual al de Cuentas (CM-36, PR 1A): `ErrorCode` en `domain/exception`, `BusinessException` base, tabla `campo + restricción → código` en el manejador | Un solo formato en los servicios Spring (estándar §A); el modelo menor copia el patrón | Formato propio de Perfil | PENDIENTE (Paula) |
| D2 | Bloqueo asesor de transacción por `firebase_uid` para la creación | Serializa solo las creaciones del mismo Usuario, sin columna nueva y compatible con el cupo Premium; el `UNIQUE` no sirve porque Premium admite 5 | `UNIQUE (firebase_uid)` (rompe Premium); aislamiento `SERIALIZABLE` (reintentos en toda la transacción); confiar en el botón deshabilitado (RT-06-CA02 pide que el backend no duplique) | PENDIENTE (Paula) |
| D3 | `requestId` desde esta tarea, sin filtro ni `MDC` (los agrega CM-283, P2-05) | Misma decisión que PD-08 en Cuentas (Paula, 6-oct-2026) | Esperar a P2-05 | Confirmada por Paula (6-oct-2026), pregunta 3 |
| D4 | `ProfileAlreadyExistsException` pasa a `ProfileLimitReachedException` | El nombre dice la regla real (cupo del plan), no «ya existe» | Conservar el nombre | PENDIENTE (Paula) |
| D5 | El respaldo `REQUEST_INVALID_VALUE` para `IllegalArgumentException` | Corta hoy la fuga de mensajes de librería sin reescribir todos los objetos de valor en esta tarea | Reescribir todos los objetos de valor aquí (es el trabajo de CM-274 y CM-66) | PENDIENTE (Paula) |

## 11. Fuera de alcance

CA-2.2.5 y 2.2.6 (Plan Premium, Sprint 3 de Jira) · Paywall (HU-2.12) · lectura del plan (`X-User-Plan`) · `ProfessionalRoleController` y su `ProblemDetail` propio (pregunta 4) · textos de las demás excepciones (cada tarea de Perfil cambia los suyos) · filtro de `requestId` y `MDC` (CM-283, P2-05) · Testcontainers (pregunta 1).

## 12. Preguntas abiertas

| # | Pregunta | A quién | Recomendación | Bloquea |
|---|---|---|---|---|
| 1 | Perfil no tiene Testcontainers (CM-283 lo agrega en P2-09, después del 13-oct) y sus `*IT` no corren en el CI. Las pruebas de concurrencia de esta tarea y de CM-274, CM-66 y CM-67 necesitan PostgreSQL real. ¿Se adelanta P2-09 a un PR propio antes de CM-271 B (≈ 1 h: dependencia de prueba `org.testcontainers:postgresql` y `@ServiceConnection`, como Cuentas)? | **Respondida (Paula, 6-oct-2026): sí, PR propio de Testcontainers antes del PR B** | Sí: el estándar ya lo aprobó (CM-283) y sin él la concurrencia solo se probaría a mano | Tarjeta T-B.3 |
| 2 | La validación pasa de 400 a 422 (RT-01-CA05) y `cameia-web` espera 400 en dos rutas. ¿Se cambia en esta tarea y se avisa a Frontend? | **Respondida (Paula, 6-oct-2026): sí, 422 y aviso a Frontend** | Sí: lo pide el CA; se avisa en el documento a Frontend | Bloque A |
| 3 | ¿Se confirma D3 (`requestId` en el cuerpo desde esta tarea, como en Cuentas)? | **Respondida (Paula, 6-oct-2026): sí, requestId desde esta tarea** | Sí | T-A.3 |
| 4 | `ProfessionalRoleController` arma su `ProblemDetail` y refleja el parámetro `lang` en el `detail` (hallazgo 5). ¿Se corrige en CM-271 o va a otra tarea? | **PENDIENTE de Paula** | A una pieza de CM-283 Parte 2 (es de HU-2.11, no de HU-2.2) | Nada de CM-271 |
| 5 | Texto para `employmentStatus`, `provenance` y `professionalRoleId` vacíos (los CA no lo definen): «Selecciona una opción.» | **Cerrada sin consulta (cambio sin alternativa):** RT-01 fija el texto para listas: «Selecciona una opción.» | «Selecciona una opción.» (RT-01) | Texto de tres filas de la tabla |
