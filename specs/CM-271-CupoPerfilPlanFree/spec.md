# Spec — CM-271: Perfil Profesional sin duplicados, cupo del Plan Free y formato de error común en Perfil

- **Tarea:** CM-271 · Subtarea «Ajustes v4 – Backend Perfil (HU-2.2)» · padre CM-16 «HU-2.2 Selección del Método de Configuración» · Sprint 2 · responsable: Paula Andrea Muñoz Delgado
- **Repositorio:** `cameia-perfil`, ramas desde `origin/develop`
- **Backlog vigente:** `05102026_01_Backlog.xlsx`, hoja `HE-02`, HU-2.2 (CA-2.2.1 a 2.2.7) y reglas transversales que cita la HU: RT-02, RT-04, RT-05 y RT-06. El formato de error común (incluida la validación en 422 de RT-01-CA05) lo pide el estándar de Backend (`docs/estandar-backend.md` §6) y se hace en esta tarea por decisión de Paula (D6)
- **Estado:** **aprobada por Paula Andrea Muñoz Delgado el 7-oct-2026**. Sin preguntas abiertas
- **Atributos de calidad que toca:** fiabilidad (un solo perfil ante peticiones simultáneas), compatibilidad de contrato (sección 6), seguridad (sin mensajes internos al cliente; identidad solo del Gateway), observabilidad (código estable y `requestId` en cada error), mantenibilidad y testabilidad (catálogo único de errores; pruebas de integración con PostgreSQL real)

## 1. Contexto y objetivo

HU-2.2 crea un Perfil Profesional vacío, sin nombre y en Borrador (`IN_PROGRESS`) con `POST /api/v1/profiles`, que no recibe cuerpo. El Plan Free permite 1 Perfil Profesional no archivado (RN-021); en el MVP no existen perfiles archivados, así que cuentan todos. En el Sprint 2 el cupo agotado se responde con el mensaje provisional de CA-2.2.3, sin Paywall. El Plan Premium (CA-2.2.5 y 2.2.6) es del Sprint 3.

Medido con la aplicación real (`docker compose up -d --build app`, 7-oct-2026):

1. Ocho peticiones simultáneas del mismo Usuario crean perfiles duplicados en 15 de 15 rondas (hasta 8 perfiles). Incumple CA-2.2.7 y RT-06-CA02.
2. El 409 del segundo perfil devuelve al cliente un texto con `TODO CM-TBD`. Incumple CA-2.2.3 y RT-05.
3. Una fecha mal escrita (`31/02/2020`) responde 500, porque `DateTimeParseException` no es `IllegalArgumentException`.
4. Los errores no llevan un código estable ni `requestId`; los del framework (ruta inexistente, método no permitido, tipo de contenido no soportado, UUID mal escrito en la ruta) devuelven textos de Spring que repiten la entrada.

**Objetivo:**

1. Que cada creación de perfil termine en exactamente un perfil por Usuario del Plan Free, aunque lleguen varias peticiones a la vez.
2. Que el segundo perfil responda 409 `PROFILE_LIMIT_REACHED` con «Tu Plan Free permite 1 Perfil Profesional.».
3. Que todo error de Perfil tenga la misma forma (`code`, `detail`, `requestId` y, en validación, `errors[]`) y nunca filtre texto de librerías, trazas ni nombres de clases.

## 2. Alcance y PR

Un solo alcance, en tres PR de menos de 1.000 líneas cada uno, en este orden (pueden ir apilados con `gh stack`):

| PR | Qué entrega | Tamaño | Requisitos |
|---|---|---|---|
| 0 | PostgreSQL real con Testcontainers en las pruebas de integración, que corren en `clean verify` | ≈ 60 líneas | REQ-PE-30 a 32 |
| A | Formato de error común de Perfil | ≈ 900 líneas (824 ya escritas y probadas en local, más REQ-PE-11 a 15) | REQ-PE-01 a 15 |
| B | Cupo del Plan Free y creación sin duplicados | ≈ 300 líneas | REQ-PE-20 a 28 |

Si el PR A supera 1.000 líneas al terminar, se parte en dos: A1 (catálogo, excepción base y manejador) y A2 (errores del framework, `charset` y documentación).

Fuera de alcance: sección 11.

## 3. Trazabilidad de los criterios

| CA o regla | Dueño | Estado en `develop` | Qué hace esta tarea | PR |
|---|---|---|---|---|
| CA-2.2.1 Crear con «Llenado Manual» | B | Cumple: 201, `IN_PROGRESS`, `name` nulo | Conserva el comportamiento y su prueba | B |
| CA-2.2.2 «Autocompletar con IA» deshabilitado | F | Cumple en `cameia-web` | Nada (sin llamada al backend) | — |
| CA-2.2.3 Cupo del Plan Free agotado | F y B | 409 con texto interno (`TODO CM-TBD`) | 409 `PROFILE_LIMIT_REACHED` con el texto literal; nada se guarda. Frontend debe mostrar el mismo texto (sección 6) | B |
| CA-2.2.4 Perfil vacío y sin método guardado | B | Cumple, sin prueba | Prueba del perfil recién creado y de que no existe dato de método | B |
| CA-2.2.5 y 2.2.6 Plan Premium | B | — | Sprint 3 (requiere HU-8.2) | — |
| CA-2.2.7 Selección repetida | F y B | **Defecto:** duplica perfiles | Un único perfil; todas las peticiones simultáneas reciben 201 con ese perfil, sin mensaje de cupo ni Paywall | B |
| RT-02 Rutas autenticadas | Gateway y B | Perfil responde 400 sin `X-User-Id` en `POST /profiles` | 401 `IDENTITY_REQUIRED` en todas las rutas | A |
| RT-05-CA01 y CA03 Error técnico | B | Sin manejador genérico; textos de librerías en 400 y 422 | 500 «Ocurrió un error. Inténtalo de nuevo.»; ningún `detail` con texto interno | A |
| RT-06-CA02 Doble clic | B | Defecto (igual que CA-2.2.7) | Igual que CA-2.2.7 | B |
| RT-01-CA05 Validación en 422 (por el estándar) | B | 400 | 422 `VALIDATION_FAILED` con `errors[]` | A |

## 4. Requisitos (EARS)

### PR 0 — pruebas de integración con base real

- **REQ-PE-30.** Las pruebas de integración (`*IT`) deben ejecutarse contra PostgreSQL 16 levantado por Testcontainers (`org.testcontainers:postgresql` y `testcontainers-junit-jupiter`, con `@Testcontainers` y `@ServiceConnection` en cada clase), sin depender de la base de `docker compose`.
- **REQ-PE-31.** `./mvnw -B clean verify` debe ejecutar las `*IT` con Failsafe y el reporte debe mostrar `Skipped: 0` cuando Docker está disponible.
- **REQ-PE-32.** El servicio `verify` de `docker-compose.yml` debe seguir funcionando; si no puede levantar contenedores, debe quedar documentado en el `README` cómo ejecutar las `*IT`.

### PR A — formato de error común

- **REQ-PE-01.** Cuando Perfil responda cualquier error (4xx o 5xx), el cuerpo debe ser `application/problem+json;charset=UTF-8` e incluir `code` (valor del catálogo de la sección 5), `detail` y `requestId`, además de `title`, `status` e `instance`.
- **REQ-PE-02.** El `requestId` debe ser el valor del encabezado `X-Request-Id` recibido si cumple `^[A-Za-z0-9._-]{1,64}$`; si no lo cumple o falta, un UUID v4 nuevo. El mismo valor debe volver en el encabezado `X-Request-Id` de la respuesta.
- **REQ-PE-03.** Cuando falle la validación de un cuerpo, Perfil debe responder 422 `VALIDATION_FAILED` con `detail` «Revisa los campos marcados.» y `errors[]` con un elemento por campo rechazado (`field` = nombre del campo JSON, `code`, `message`). Si una restricción de un DTO no tiene código asignado, una prueba debe fallar.
- **REQ-PE-04.** Cuando el cuerpo no se pueda leer (JSON mal formado o tipo incorrecto), Perfil debe responder 422 `REQUEST_BODY_INVALID_FORMAT` con `detail` «Revisa el formato de los datos enviados.», sin el texto de Jackson.
- **REQ-PE-05.** Cuando el dominio rechace un dato (obligatorio, largo, signo, fecha mal escrita, opción que no existe o fechas que no encajan), Perfil debe responder 422 `VALIDATION_FAILED` con `errors[]`: en cada elemento, el campo, el código de la regla y un mensaje que diga qué corregir, sin repetir el valor recibido (D18). Una `IllegalArgumentException` deja de ser una respuesta al cliente: es un fallo del servidor y responde como REQ-PE-06.
- **REQ-PE-06.** Cuando ocurra un fallo no previsto, Perfil debe responder 500 `INTERNAL_ERROR` con `detail` «Ocurrió un error. Inténtalo de nuevo.», sin traza, SQL ni nombres de clases, y registrarlo en `ERROR` con la traza.
- **REQ-PE-07.** Cada error de negocio debe registrarse una sola vez en `WARN`, sin traza, con `code`, `requestId` y `firebaseUid` cuando se conoce, sin nombre, resumen ni correo.
- **REQ-PE-08.** Cada excepción de negocio de Perfil debe extender `BusinessException` y llevar su `ErrorCode`; el manejador toma el código de la excepción y el estado de su tabla `ErrorCode → HttpStatus`, no los inventa. Una prueba recorre `ErrorCode.values()` y falla si un código no tiene estado.
- **REQ-PE-09.** Los estados de los errores de negocio se conservan (sección 5). Cambian tres: validación del cuerpo (400 → 422), cuerpo ilegible (400 → 422) y `X-User-Id` ausente en `POST /api/v1/profiles` (400 → 401). Los dos primeros difieren del mapa base del estándar (§6, regla 4: 400 para cuerpo ilegible) y se anotan en `docs/errores.md`, igual que en Cuentas.
- **REQ-PE-10.** Cuando una petición a una ruta con identidad llegue sin `X-User-Id`, con él vacío, solo con espacios o con más de 128 caracteres, Perfil debe responder 401 `IDENTITY_REQUIRED` con `detail` «Identidad del usuario requerida» y no ejecutar la operación. Un `X-User-Id` de exactamente 128 caracteres es válido.
- **REQ-PE-11.** Cuando una fecha `YYYY-MM` del cuerpo no exista (por ejemplo, `2020-13`) o tenga otro formato (`31/02/2020`), Perfil debe responder 422 `VALIDATION_FAILED` con `START_DATE_INVALID_FORMAT` o `END_DATE_INVALID_FORMAT` en su campo (como REQ-PE-05), nunca 500.
- **REQ-PE-12.** Cuando la ruta no exista, el método no esté permitido, el tipo de contenido no sea soportado o un identificador de la ruta no sea un UUID, Perfil debe responder con la forma de REQ-PE-01, con el código, el estado y el mensaje de la sección 5 (un código por causa, D10) y sin repetir el valor recibido.
- **REQ-PE-13.** Toda respuesta JSON de Perfil, de éxito o de error, debe declarar `charset=UTF-8` en `Content-Type`.
- **REQ-PE-14.** El esquema de error del OpenAPI debe mostrar `code`, `requestId` y `errors[]` con un ejemplo; cada endpoint documenta sus respuestas de error con su código.
- **REQ-PE-15.** Las excepciones de negocio y las clases que esta tarea toca deben tener Javadoc y no contener claves de Jira (`CM-NNN`) ni `TODO` en comentarios o mensajes.
- **REQ-PE-16.** Cuando se intente finalizar un perfil que no cumple los requisitos, Perfil debe responder 422 con la forma de REQ-PE-01, `code` `PROFILE_INCOMPLETE`, `detail` «Todavía no cumples estos requisitos:» y, como miembro adicional, la lista `missingRequirements` con los requisitos que faltan. El `detail` no incluye la lista. Los códigos de cada requisito y la lista `errors[]` que lee `cameia-web` los agrega CM-67.
- **REQ-PE-17.** Cuando `GET /api/v1/profiles/professional-roles` reciba un `lang` distinto de `es` o `en` (comparación exacta, por ejemplo `fr`, `ES` o `es-CO`), Perfil debe responder 422 con la forma de REQ-PE-01, `code` `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE` y `detail` «Elige un idioma disponible: español o inglés.», sin repetir el valor recibido. Sin `lang`, o con `lang` vacío, se usa `es`.

### PR B — cupo del Plan Free y creación sin duplicados

- **REQ-PE-20 (CA-2.2.3).** Cuando un Usuario que ya tenía 1 Perfil Profesional (en cualquier estado) antes de su petición llame a `POST /api/v1/profiles`, Perfil debe responder 409 `PROFILE_LIMIT_REACHED` con `detail` «Tu Plan Free permite 1 Perfil Profesional.» y no crear ningún perfil.
- **REQ-PE-21 (CA-2.2.7, RT-06-CA02).** Cuando lleguen dos o más `POST /api/v1/profiles` del mismo Usuario mientras una creación suya está en proceso, Perfil debe crear exactamente un perfil y responder **201 con ese mismo perfil a todas**, sin 409. Una petición que llega cuando la creación ya terminó no está «en proceso»: cumple REQ-PE-20 y recibe 409.
- **REQ-PE-22 (CA-2.2.1 y 2.2.4).** El perfil creado debe tener `status` `IN_PROGRESS`, `name` y `summary` nulos y `workExperiences`, `educations`, `profileSkills` y `targetRoles` vacías, tanto en la respuesta 201 como en `GET /api/v1/profiles/{id}`.
- **REQ-PE-23 (CA-2.2.4).** Perfil no debe recibir ni guardar el método de configuración elegido. El campo `provenance` del perfil indica el origen del nombre y el resumen (`MANUAL`, `AI_SUGGESTED`, `AI_EDITED`), nace en `MANUAL` y no representa el método.
- **REQ-PE-24 (RT-03-CA04).** El cupo se cuenta por el `firebase_uid` de `X-User-Id`. Si la petición trae cuerpo, se ignora: no cambia el dueño ni el estado.
- **REQ-PE-25.** El `detail` del 409 no debe contener `TODO`, claves de Jira ni nombres internos.
- **REQ-PE-26.** La decisión de crear, devolver el perfil recién creado o rechazar sigue esta regla, dentro de una sola transacción:
  1. contar los perfiles del Usuario al llegar (`antes`);
  2. tomar el bloqueo de creación del Usuario (espera si otra creación suya está en proceso);
  3. contar de nuevo (`después`);
  4. si `después` es 0 → crear y responder 201;
  5. si `antes` es 0 y `después` es 1 → otra petición simultánea lo acaba de crear: responder 201 con el perfil más reciente del Usuario, sin crear otro;
  6. si `antes` es 1 → responder 409 `PROFILE_LIMIT_REACHED`.
- **REQ-PE-27.** El bloqueo de creación solo debe afectar a las creaciones del mismo Usuario; creaciones de Usuarios distintos no se esperan entre sí.
- **REQ-PE-28.** Si la creación en proceso falla y se deshace, la petición que esperaba debe crear el perfil normalmente (paso 4).

## 5. Catálogo de errores de Perfil

`ErrorCode` vive en `domain/exception`. Un código ↔ un estado ↔ un mensaje ↔ una excepción ↔ al menos una prueba.

| Código | HTTP | Origen | `detail` |
|---|---|---|---|
| `VALIDATION_FAILED` | 422 | Bean Validation del cuerpo | «Revisa los campos marcados.» |
| `REQUEST_BODY_INVALID_FORMAT` | 422 | `HttpMessageNotReadableException` | «Revisa el formato de los datos enviados.» |
| `REQUEST_INVALID_VALUE` | 422 | `MissingRequestHeaderException` de otro encabezado y respaldo de los rechazos del framework (D13, D14) | «Revisa los datos enviados.» |
| `IDENTITY_REQUIRED` | 401 | `IdentityRequiredException`; `MissingRequestHeaderException` de `X-User-Id` | «Identidad del usuario requerida» |
| `PROFILE_NOT_FOUND` | 404 | `ProfileNotFoundException` | mensaje vigente |
| `PROFILE_NOT_ALLOWED` | 403 | `ProfileAccessDeniedException` | «No tienes permiso para acceder a este perfil» |
| `PROFILE_LIMIT_REACHED` | 409 | `ProfileLimitReachedException` | «Tu Plan Free permite 1 Perfil Profesional.» |
| `PROFILE_ALREADY_COMPLETED` | 409 | `ProfileAlreadyCompletedException` | mensaje vigente (causa `ALREADY_COMPLETED`, D9) |
| `PROFILE_INCOMPLETE` | 422 | `IncompleteProfileException`; agrega `missingRequirements` (REQ-PE-16) | «Todavía no cumples estos requisitos:» (causa `INCOMPLETE`, D9) |
| `PROFESSIONAL_ROLE_NOT_FOUND` | 404 | `ProfessionalRoleNotFoundException` | mensaje vigente |
| `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE` | 422 | `UnsupportedLanguageException` (nueva), lanzada por el catálogo de roles | «Elige un idioma disponible: español o inglés.» |
| `TARGET_ROLE_LIMIT_REACHED` | 422 | `MaxTargetRolesExceededException` | mensaje vigente |
| `TARGET_ROLE_ALREADY_EXISTS` | 409 | `DuplicateTargetRoleException` | mensaje vigente |
| `TARGET_ROLE_NOT_ALLOWED` | 422 | `LastTargetRoleException` | mensaje vigente |
| `SKILL_ALREADY_EXISTS` | 409 | `DuplicateSkillException` | mensaje vigente (CM-66 lo cambia) |
| `ROUTE_NOT_FOUND` | 404 | La ruta no existe (`NoResourceFoundException`) | «La ruta solicitada no existe.» |
| `METHOD_NOT_ALLOWED` | 405 | Método HTTP no permitido en la ruta | «La operación no está permitida en esta ruta.» |
| `CONTENT_TYPE_NOT_ALLOWED` | 415 | `Content-Type` distinto de `application/json` en una ruta con cuerpo | «Envía los datos en formato JSON.» |
| `PROFILE_ID_INVALID_FORMAT` | 422 | `{id}` de la ruta no es un UUID | «El identificador del perfil no es válido.» |
| `WORK_EXPERIENCE_ID_INVALID_FORMAT` | 422 | `{expId}` no es un UUID | «El identificador de la experiencia no es válido.» |
| `EDUCATION_ID_INVALID_FORMAT` | 422 | `{eduId}` no es un UUID | «El identificador de la formación no es válido.» |
| `SKILL_ID_INVALID_FORMAT` | 422 | `{skillId}` no es un UUID | «El identificador de la habilidad no es válido.» |
| `TARGET_ROLE_ID_INVALID_FORMAT` | 422 | `{roleId}` no es un UUID | «El identificador del rol objetivo no es válido.» |
| `INTERNAL_ERROR` | 500 | Cualquier otro | «Ocurrió un error. Inténtalo de nuevo.» |

**Códigos de campo** (`errors[].code`). La tabla del manejador usa la clave `ClaseDelDto.campo.Restricción`, porque `level` y `provenance` se repiten en varios DTO:

| Clave | `code` | `message` |
|---|---|---|
| `AddWorkExperienceRequest.company.NotBlank` | `COMPANY_REQUIRED` | «Ingresa la empresa.» (CA-2.4.3) |
| `AddWorkExperienceRequest.position.NotBlank` | `POSITION_REQUIRED` | «Ingresa el cargo.» (CA-2.4.3) |
| `AddWorkExperienceRequest.startDate.NotBlank` | `START_DATE_REQUIRED` | «Ingresa la fecha de inicio.» |
| `AddWorkExperienceRequest.employmentStatus.NotNull` | `EMPLOYMENT_STATUS_REQUIRED` | «Selecciona una opción.» (RT-01) |
| `AddWorkExperienceRequest.provenance.NotNull`, `AddSkillRequest.provenance.NotBlank`, `AddTargetRoleRequest.provenance.NotNull` | `PROVENANCE_REQUIRED` | «Selecciona una opción.» (RT-01) |
| `AddSkillRequest.skillName.NotBlank` | `SKILL_NAME_REQUIRED` | «Ingresa una habilidad.» (CA-2.5.7) |
| `AddSkillRequest.level.NotBlank` | `SKILL_LEVEL_REQUIRED` | «Elige un nivel.» (CA-2.5.7) |
| `AddTargetRoleRequest.professionalRoleId.NotNull` | `PROFESSIONAL_ROLE_ID_REQUIRED` | «Selecciona una opción.» (RT-01) |

Cada código de campo tiene al menos una prueba que lo emite, con valor ausente, `null`, vacío, solo espacios y tabulador cuando la restricción es `NotBlank`.

## 6. Contrato

- **Aditivo:** `code` y `requestId` en todo error; `errors[]` con `field`, `code` y `message`; encabezado `X-Request-Id` en las respuestas de error; `charset=UTF-8` en todas las respuestas JSON.
- **Cambia el estado:** validación del cuerpo 400 → 422 y cuerpo ilegible 400 → 422. `cameia-web` espera hoy 400 en «agregar experiencia» y «agregar educación». Comunicado a Frontend el 6-oct-2026 (documento de cambios de contrato, fila 12).
- **Cambia el estado de un caso que no ocurre a través del Gateway:** `POST /api/v1/profiles` sin `X-User-Id` pasa de 400 a 401.
- **Cambia un texto:** el `detail` del 409 de creación (CA-2.2.3). `cameia-web` muestra su propio texto ante este 409 («Ya tienes un perfil creado.»); para cumplir CA-2.2.3 debe mostrar «Tu Plan Free permite 1 Perfil Profesional.». Se solicita a Frontend en el canal `arquitectura`.
- **Doble clic (CA-2.2.7):** las creaciones que llegan mientras otra está en proceso reciben 201 con el mismo perfil (REQ-PE-21). La creación tarda pocos milisegundos y dos clics de una persona llegan con más separación, así que el segundo clic suele llegar con la creación ya terminada y recibe 409 (REQ-PE-20). Backend garantiza que nunca se crea un segundo perfil; para que el Usuario no vea el mensaje de cupo, `cameia-web` deshabilita «Llenado Manual» mientras la creación está en curso (D15). Se solicita a Frontend.
- **Código nuevo:** 406 `ACCEPT_TYPE_NOT_ALLOWED` cuando el cliente pide una respuesta que no es JSON. Los demás errores del framework, que antes no tenían `code`, responden 422 `REQUEST_INVALID_VALUE` o 500 `INTERNAL_ERROR` (D13).
- **OpenAPI de `POST /api/v1/profiles`:** 201 (perfil vacío, con ejemplo), 401 `IDENTITY_REQUIRED`, 409 `PROFILE_LIMIT_REACHED` (con ejemplo) y 500 `INTERNAL_ERROR`. La descripción del 201 aclara que una petición repetida mientras la creación está en proceso devuelve el mismo perfil.

## 7. Datos

- Sin migración. Ya existe `idx_perfil_firebase_uid` sobre `perfil_profesional (firebase_uid)`, que sirve al conteo y a la búsqueda del perfil más reciente.
- **No** se agrega `UNIQUE (firebase_uid)`: el Plan Premium permitirá hasta 5 perfiles (RN-092).
- **Bloqueo de creación:** `SELECT pg_advisory_xact_lock(hashtextextended(:firebaseUid, 0))` dentro de la transacción de la creación. Se libera solo al confirmar o deshacer. Una colisión del hash (64 bits) solo hace esperar a dos Usuarios, nunca da un resultado incorrecto.
- **Aislamiento:** `READ COMMITTED` (el de PostgreSQL por defecto). Es necesario para que el segundo conteo vea el perfil que confirmó la otra petición; con `REPEATABLE READ` la regla de REQ-PE-26 no funciona. Una prueba de integración lo comprueba.
- **Puerto `ProfessionalProfileRepository`:** se quita `existsByFirebaseUid`; se agregan `long countByFirebaseUid(FirebaseUid)`, `void lockCreationFor(FirebaseUid)` y `Optional<ProfessionalProfile> findLatestByFirebaseUid(FirebaseUid)` (el de `created_at` más reciente).

## 8. Seguridad (OWASP API Top 10 y ASVS nivel 1)

- **API1 y RT-03-CA04:** el dueño sale solo de `X-User-Id`, que pone el Gateway. El perfil devuelto en REQ-PE-21 es del mismo Usuario (se busca por su `firebase_uid`).
- **API3 y RT-01-CA06:** la creación no lee cuerpo; nada del cuerpo cambia el dueño ni el estado (caso 13 de la sección 9).
- **API4:** el bloqueo es por Usuario y dura lo que la transacción (milisegundos); no frena a otros Usuarios.
- **API8 y RT-05-CA03:** ningún `detail` lleva texto de librerías, trazas, SQL ni nombres de clases; los errores del framework no repiten la entrada.
- **Registro:** `code`, `requestId` y `firebaseUid`; nunca nombre, resumen, correo ni el valor rechazado.

## 9. Casos de prueba

### Creación del perfil (PR B)

| # | Caso y datos | Esperado | Capa |
|---|---|---|---|
| 1 | `X-User-Id: uid-ana-001`, sin perfiles | 201, `status` `IN_PROGRESS`, `name` y `summary` nulos, cuatro listas vacías | servicio, controlador, Postman |
| 2 | `GET /api/v1/profiles/{id}` del perfil del caso 1 | 200 con los mismos valores vacíos; la respuesta no tiene ningún campo de método | controlador, Postman |
| 3 | `uid-ana-001` con un perfil `IN_PROGRESS` | 409 `PROFILE_LIMIT_REACHED`, `detail` literal; la base sigue con 1 perfil | servicio, controlador, Postman |
| 4 | `uid-ana-001` con un perfil `ACTIVE` | 409 igual que el caso 3 | servicio |
| 5 | `detail` del 409 | No contiene `TODO`, `CM-`, `Exception` ni `co.edu` | controlador |
| 6 | `uid-ana-001` con perfil; `uid-luis-002` sin perfil | `uid-luis-002` recibe 201 | servicio |
| 7 | 5 hilos con `uid-concurrencia-<UUID>` a la vez, sin perfiles | 5 respuestas exitosas con el **mismo** `id`; 1 fila en la base; ningún 409 | integración (Testcontainers) |
| 8 | 5 hilos, cada uno con un Usuario distinto, a la vez | 5 perfiles, uno por Usuario | integración |
| 9 | Orden de las llamadas | `countByFirebaseUid`, `lockCreationFor`, `countByFirebaseUid` (`InOrder`) | servicio |
| 10 | `antes` 0 y `después` 1 (simulado) | Devuelve el perfil de `findLatestByFirebaseUid`; `save` nunca se llama | servicio |
| 11 | La primera creación falla y se deshace; la segunda esperaba | La segunda crea el perfil: 1 fila | integración |
| 12 | Cuerpo `{"status":"ACTIVE","firebaseUid":"otro"}` | 201 `IN_PROGRESS` con dueño `uid-ana-001` | controlador |
| 13 | La base falla al guardar | 500 `INTERNAL_ERROR`, sin SQL ni traza | controlador |
| 14 | Script de la carrera: 15 rondas × 8 peticiones con la app real | En cada ronda: 1 perfil en la base; cada respuesta es 201 con el `id` de ese perfil o 409 `PROFILE_LIMIT_REACHED` (la que llega cuando la creación ya terminó, REQ-PE-20) | manual, salida adjunta al PR |

### Identidad y formato de error (PR A)

| # | Caso y datos | Esperado | Capa |
|---|---|---|---|
| 15 | `POST /api/v1/profiles` sin `X-User-Id` | 401 `IDENTITY_REQUIRED` | controlador, Postman |
| 16 | `X-User-Id` `""`, `"   "` y tabulador | 401 `IDENTITY_REQUIRED` | controlador |
| 17 | `X-User-Id` de 128 y de 129 caracteres | 128 → 201; 129 → 401 `IDENTITY_REQUIRED`, sin repetir el valor | controlador |
| 18 | `X-Request-Id: abc-123` | Cuerpo y encabezado con `abc-123` | manejador |
| 19 | `X-Request-Id` `<script>`, de 65 caracteres y ausente | UUID v4 nuevo en cuerpo y encabezado | manejador |
| 20 | `POST /work-experiences` sin `company` | 422 `VALIDATION_FAILED`, `errors[0]` = `company`, `COMPANY_REQUIRED`, «Ingresa la empresa.» | controlador |
| 21 | Cada código de campo de la sección 5 con ausente, `null`, vacío, espacios y tabulador | Su código y su mensaje | controlador (parametrizada) |
| 22 | JSON mal formado en `PATCH /api/v1/profiles/{id}` | 422 `REQUEST_BODY_INVALID_FORMAT`, sin «JSON» ni «Jackson» | controlador |
| 23 | Enumerado inválido (`"preferredModality":"X"`) | 422 `VALIDATION_FAILED`, `errors[0]` = `preferredModality`, `PREFERRED_MODALITY_INVALID_VALUE`, «Selecciona una opción.», sin `co.edu` ni `No enum constant` | servicio y `CommandValues` |
| 24 | `startDate` `2020-13` y `31/02/2020` | 422 `VALIDATION_FAILED`, `errors[0]` = `startDate`, `START_DATE_INVALID_FORMAT` (no 500) | `CommandValues` y controlador |
| 24b | Cada regla del dominio (obligatorio, largo, signo, fechas) y su valor límite | Su campo, su código y su mensaje; el límite se acepta | dominio (parametrizada) |
| 24c | `IllegalArgumentException` lanzada por cualquier capa | 500 `INTERNAL_ERROR`, con la traza en el registro | manejador |
| 25 | `RuntimeException("SQL secreto")` | 500 `INTERNAL_ERROR`, sin «SQL» | manejador |
| 26 | Ruta inexistente, `DELETE` en `/api/v1/profiles`, `Content-Type: text/plain`, `/api/v1/profiles/no-es-uuid` | 404 `ROUTE_NOT_FOUND`, 405 `METHOD_NOT_ALLOWED`, 415 `CONTENT_TYPE_NOT_ALLOWED` y 422 `PROFILE_ID_INVALID_FORMAT`; ningún `detail` repite la entrada | controlador |
| 27 | `Content-Type` de 200, 201 y de un error | Declaran `charset=UTF-8` | controlador |
| 28 | Cada `ErrorCode` | Tiene estado en la tabla y cumple `^[A-Z]+(_[A-Z]+)+$` | unidad |
| 29 | Finalizar un perfil sin resumen ni habilidades | 422 `PROFILE_INCOMPLETE` con `requestId`, `detail` «Todavía no cumples estos requisitos:» y `missingRequirements` con los dos faltantes; el estado no cambia | controlador |
| 30 | `?lang=fr`, `?lang=ES`, `?lang=es-CO` | 422 `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE`; el `detail` no contiene el valor | controlador |
| 31 | Sin `lang`, `?lang=`, `?lang=es`, `?lang=en` | 200 con el catálogo en el idioma que corresponde (`es` en los dos primeros) | controlador |

Cobertura de lo nuevo o modificado: ≥ 90 % de líneas y de ramas con JaCoCo; cada línea o rama sin cubrir se reporta con su razón. La colección de Postman de Perfil se actualiza con los casos 1, 2, 3 y 15.

## 10. Decisiones

| # | Decisión | Porqué | Alternativas descartadas | Decisión humana |
|---|---|---|---|---|
| D1 | Formato de error igual al de Cuentas: `ErrorCode` en `domain/exception`, `BusinessException` base, tabla `campo + restricción → código` en el manejador | Un solo formato en los servicios Spring (estándar §6) | Formato propio de Perfil | Paula, 6-oct-2026 |
| D2 | Bloqueo de transacción de PostgreSQL por `firebase_uid` para la creación | Serializa solo las creaciones del mismo Usuario, sin columna nueva y compatible con el cupo Premium | `UNIQUE (firebase_uid)` (rompe Premium); `SERIALIZABLE` (reintentos); confiar en el botón deshabilitado (RT-06-CA02 pide que el backend no duplique) | Paula, 6-oct-2026 |
| D3 | `requestId` en el cuerpo desde esta tarea, sin filtro ni `MDC` (los agrega CM-283) | Igual que en Cuentas | Esperar a CM-283 | Paula, 6-oct-2026 |
| D4 | `ProfileAlreadyExistsException` pasa a `ProfileLimitReachedException` | El nombre dice la regla real (cupo del plan) | Conservar el nombre | Paula, 6-oct-2026 |
| D5 | ~~Respaldo `REQUEST_INVALID_VALUE` para `IllegalArgumentException` y `DateTimeException`~~ Reemplazada por D18 | Cortaba la fuga de mensajes sin reescribir los objetos de valor, pero también tapaba los mensajes de las reglas de negocio | Reescribirlos aquí | Paula, 6-oct-2026 |
| D6 | Todo en CM-271: Testcontainers, formato de error común y cupo | Las tareas de Perfil siguientes se apoyan en el formato; el formato ya está escrito y probado | Sacar el formato a otra tarea; solo `code` en el 409 | Paula, 7-oct-2026 |
| D7 | Petición repetida durante la creación → 201 con el mismo perfil (REQ-PE-21 y 26) | Cumple CA-2.2.7 («crear un único perfil y no mostrar el Paywall») sin cambiar `cameia-web`, y no choca con CA-2.2.3: quien ya tenía perfil antes de pedir recibe 409 | 409 a la segunda petición (mostraría el mensaje de cupo, y en el Sprint 3 el Paywall); ventana de tiempo (valor arbitrario); `Idempotency-Key` (exige cambiar `cameia-web`) | Paula, 7-oct-2026 |
| D8 | Identidad ausente, vacía o de más de 128 caracteres → 401 `IDENTITY_REQUIRED` | Un código, un estado (estándar §6); 401 es «sin identidad» en el mapa base | 400 en `POST /profiles`; código propio para el exceso de longitud | Paula, 7-oct-2026 |
| D9 | `PROFILE_ALREADY_COMPLETED` y `PROFILE_INCOMPLETE` conservan su nombre; las causas `ALREADY_COMPLETED` e `INCOMPLETE` se agregan al vocabulario cerrado del estándar con esta spec | Los nombres dicen exactamente qué pasa; el estándar permite agregar causas con su spec; la spec de CM-67 no cambia | Renombrar a `PROFILE_CONFLICT` (genérico, ambiguo cuando aparezca otro conflicto del perfil) | Paula, 7-oct-2026 («entre más claro, mejor») |
| D10 | Un código por causa también en los errores del framework; un identificador mal escrito en la ruta tiene su propio código por parámetro y responde 422 | Distingue «mal escrito» de «no existe» en el registro y para Frontend; el mensaje dice cuál identificador falló | UUID inválido → 404 del recurso (oculta la causa real); un solo `REQUEST_INVALID_VALUE` (no dice qué parámetro) | Paula, 7-oct-2026 («entre más claro, mejor») |
| D11 | La finalización incompleta y el idioma no soportado del catálogo de roles adoptan la forma común en esta tarea | Al fusionar CM-271 todos los errores de Perfil tienen la misma forma. La finalización solo cambia de forma (los requisitos y `errors[]` siguen en CM-67, que depende del enumerado de requisitos) | Dejarlas en CM-67 y en CM-283 Parte 2: dos respuestas distintas hasta entonces | Paula, 7-oct-2026 |

| D12 | El PR A se publica en tres PR apilados: T-A.1 a T-A.6, T-A.7 a T-A.12 y T-A.13 y T-A.14 con el respaldo del framework | Medido al terminar: 2.173 líneas, y el corte en dos del plan deja 1.145 en el primero. Es el único corte contiguo con cada PR bajo 1.000 | Un solo PR de más de 1.000 líneas; el corte A1/A2 del plan | Paula, 7-oct-2026 («soluciona usando los estándares») |
| D13 | Toda respuesta de error lleva `code`: 406 `ACCEPT_TYPE_NOT_ALLOWED` propio; las demás excepciones del framework sin fila, 422 `REQUEST_INVALID_VALUE` (o 500 `INTERNAL_ERROR` si son del servidor), con el origen en el registro | El estándar pide `code` en toda respuesta de error y nunca el texto de Spring; un respaldo con origen permite darles código propio si aparecen | Conservar el estado y el texto de Spring sin `code`; un código por cada excepción de Spring que ningún endpoint produce | Paula, 7-oct-2026 |
| D14 | Un encabezado obligatorio ausente distinto de `X-User-Id` responde 422 `REQUEST_INVALID_VALUE` | Un código tiene un solo estado | 400 con el mismo código | Paula, 7-oct-2026 |
| D15 | Ante el doble clic, Backend garantiza un solo perfil y mantiene el 409 de la petición que llega con la creación terminada; `cameia-web` deshabilita el botón mientras la creación está en curso | Medido con la app real: en 30 rondas × 8 nunca hubo dos perfiles, pero 9 rondas tuvieron 409 tardíos. Distinguir en el servidor un segundo clic de un segundo intento exige una ventana de tiempo arbitraria o `Idempotency-Key`, ya descartados en D7; deshabilitar el botón es la práctica habitual y no cambia CA-2.2.3 | Ventana de tiempo; `Idempotency-Key`; devolver 201 con el borrador vacío existente (contradice CA-2.2.3) | Paula, 7-oct-2026 |
| D16 | La creación vive en `ProfileCreationAppService` y la identidad del Gateway la valida `FirebaseUid.required` | Cada clase queda bajo 200 líneas y la regla de identidad está en un solo lugar | Dejar la creación en `ProfileAppService` (211 líneas) | Paula, 7-oct-2026 |
| D17 | Los 404 de experiencia, formación, habilidad y rol objetivo inexistentes no se hacen en esta tarea | CM-274 (REQ-EF-02) y CM-66 (REQ-HB-07) ya los especifican con sus códigos y textos; hacerlos aquí duplicaría trabajo con mensajes distintos | Hacerlos en CM-271 | Paula, 7-oct-2026 |
| D18 | Las reglas del dominio rechazan con `InvalidFieldsException`: 422 `VALIDATION_FAILED` con `errors[]`, un código por regla y un mensaje que dice qué corregir. Las opciones y las fechas se leen con `CommandValues`, sin `Enum.valueOf` ni `YearMonth.parse` sueltos. `IllegalArgumentException` pasa a 500. Editar un rol objetivo que no está en el perfil responde 404 `TARGET_ROLE_NOT_FOUND`. Nombres, textos y forma son los de las specs de CM-54, CM-66 y CM-274 (T-1.1 y REQ-EF-07 de CM-274); los límites y reglas nuevas (100, 150, 60 caracteres, fechas futuras, recorte NFC, fecha solo con año) siguen en esas historias | En la revisión final se vio que el respaldo de D5 respondía «Revisa los datos enviados.» a reglas que tenían mensaje propio (fechas de la experiencia, largos, rol objetivo inexistente), en contra de «entre más claro, mejor» y de CLAUDE.md §4. Usar los nombres ya aprobados evita publicar códigos que esas historias tendrían que renombrar | Un código propio por regla en la raíz de la respuesta (`WORK_EXPERIENCE_END_DATE_BEFORE_START`, con `errors[]` de un elemento): choca con los nombres y la forma aprobados en CM-274; devolver el mensaje de la `IllegalArgumentException` tal cual (vuelve a filtrar textos de Java) | Paula, 7-oct-2026 («no quiero mensajes de negocio reemplazados por el genérico; que los errores sean lo más explícitos posible») |

## 11. Fuera de alcance y hallazgos con destino

| Tema | Destino |
|---|---|
| CA-2.2.5 y 2.2.6 (Plan Premium), Paywall (HU-2.12), lectura del plan | Sprint 3 |
| Swagger UI abierto en producción | CM-283, Parte 2 (P2-07) |
| Códigos de cada requisito de la finalización, `errors[]` de la finalización y su texto por requisito | CM-67 (su spec deja de cubrir la forma común del 422) |
| `TODO` y `CM-NNN` en archivos que esta tarea no toca | CM-283, Parte 2 (P2-12) |
| `package-info.java` por paquete y `@Schema` en cada campo de todos los DTO | CM-283, Parte 2 |
| Filtro de `requestId` y `MDC` | CM-283, Parte 2 (P2-05) |
| Textos de las demás excepciones | Cada tarea de Perfil (CM-54, CM-274, CM-66, CM-67) |
| `DELETE` de experiencia y formación inexistentes (hoy 200) y `DELETE` de rol objetivo inexistente (hoy 200). El `PATCH` de rol objetivo inexistente ya responde 404 `TARGET_ROLE_NOT_FOUND` (D18) | CM-274, bloque 1 (REQ-EF-02) |
| Regla ArchUnit que impida `Enum.valueOf` y `YearMonth.parse` en `application`, y mover `CommandValues` al dominio (`DataProvenance.parse` y fábricas `create`) | CM-274, bloque 1 (REQ-EF-08) |
| Varios campos rechazados a la vez por el dominio: hoy el primero que falla corta la validación | CM-274, bloque 2 (fábricas que acumulan errores) |
| `SUMMARY_REQUIRED`: el resumen en blanco se rechaza hasta que CM-54 lo convierta en «borrar el resumen» (CM-247) | CM-54 |
| Formación sin `inProgress`: el campo es `boolean` y, sin él, Jackson responde `REQUEST_BODY_INVALID_FORMAT` en lugar de un error de campo | CM-274, bloque 2 |
| De la revisión final: el bloqueo de creación espera sin límite de tiempo (`lock_timeout`); el respaldo del framework responde 422 a rechazos que no son del cliente (413, 404, 409); el registro de un fallo no controlado puede llevar valores de columnas en el mensaje de una excepción de persistencia; varios `detail` de negocio repiten el valor recibido (habilidad o rol duplicados) | Decisión de Paula, cada uno con más de una solución posible |
| `DELETE` de habilidad inexistente (hoy 200) | CM-66 (REQ-HB-07, `SKILL_NOT_FOUND`) |
| Deshabilitar «Llenado Manual» mientras la creación está en curso (D15) | Frontend, por el documento de aclaraciones de Backend |
| Lista de perfiles del Usuario (`GET /api/v1/profiles`, HU-4.2): sin ella el Usuario no vuelve a su perfil si el navegador no guardó su `id` | Product Owner: tarea nueva en Jira, por comunicación a Vela |
| `ProfessionalProfile` (201 líneas) pasa del límite de 200 desde antes de esta tarea | Se parte en la primera tarea que cambie su estructura (CM-274, bloque 1) |

**Entregables de documentación en esta tarea:** `docs/errores.md` (catálogo completo y los estados que difieren del mapa base), un ADR para el cambio a 422 y otro para el bloqueo de creación con la regla de REQ-PE-26, `README` (Docker necesario para las pruebas de integración) y `CLAUDE.md` del repositorio si lista estos puntos como pendientes. La carpeta de esta spec se renombra a `specs/CM-271-CupoPerfilPlanFree` (convención del estándar) en el PR B.

## 12. Preguntas abiertas

Sin preguntas abiertas.

**Respondidas antes:** forma común de la finalización y del idioma del catálogo (D11), nombres de `PROFILE_ALREADY_COMPLETED` y `PROFILE_INCOMPLETE` (D9) y errores del framework (D10), 7-oct; Testcontainers en un PR propio antes del PR B (6-oct); 422 y aviso a Frontend (6-oct); `requestId` desde esta tarea (6-oct); «Selecciona una opción.» para listas (RT-01). Alcance, doble envío e identidad: D6 a D8 (7-oct).
