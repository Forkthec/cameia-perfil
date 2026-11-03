# Tareas — CM-67 (cameia-perfil)

Estado: PR 1 (T-0 a T-6, código y pruebas) hecho y verificado en local; spec y plan aprobados por Paula el 9-oct-2026. Tres PR: finalización (T-0 a T-6), tabla de salida (T-7 a T-9) y evento (T-10 a T-12). Una tarjeta a la vez, con la salida real antes de marcar `[x]`.

## Reglas para todas las tarjetas

1. Rutas `src/main/java/co/edu/unicauca/cameia/perfil/` y `src/test/java/co/edu/unicauca/cameia/perfil/`.
2. **Idioma (R1).** En inglés: identificadores, nombres de métodos de prueba y códigos de error. En español: Javadoc, comentarios (también los de SQL y de `.properties`/`.yml`), `@DisplayName`, OpenAPI, logs y mensajes para la persona (los de « » se copian exactos). Los esqueletos ya traen el Javadoc en español.
3. Nunca 500 por una entrada del cliente; el único 500 de esta tarea es el de CA-2.5.14 (falla real de la base). `application/problem+json;charset=UTF-8` con `code`, `requestId`; sin traza, SQL ni nombres de clase. Log de rechazo en `WARN` con `code`, `requestId`, `firebaseUid`; el 500 en `ERROR` con traza.
4. `domain` sin Spring; métodos ≈ 20 líneas, ≤ 3 parámetros; sin dependencias nuevas. El PR 1 no lleva migración; el PR 2 lleva `V9__evento_saliente.sql` (número fijo del orden de Perfil: V5 CM-279, V6 y V7 CM-274, V8 CM-66).
5. Javadoc en lo nuevo o modificado; `docs/errores.md` al día en el mismo PR.
6. Pruebas `method_shouldResult_whenCondition`, AAA, literales, sin `Thread.sleep`; `*IT` con Testcontainers (`postgres:16-alpine`) y Failsafe.
7. Comandos: `./mvnw.cmd -q -B "-Dtest=<Clase>" test`; `./mvnw.cmd -B "-Dit.test=<Clase>" verify`; cierre `./mvnw.cmd -B clean verify`.
8. Detente y reporta si algo no coincide con el código real.
9. Commit `CM-67 | <tipo>(perfil): <resultado>`, sin `[IA-ASISTIDO]`, con `Co-Authored-By`. Push y PR solo con el sí de Paula.

---

## [x] T-0 · Base y reproducción — ≤ 15 min

- Comprobar en `origin/develop`: `findByIdForUpdate` (CM-274 PR 1), `ProfessionalSummary.MAX_LENGTH` público y constructor canónico sin validar (CM-54 PR 1), `SKILL_NOT_ALLOWED` 409 en `removeSkill` y `V8__alinear_habilidad_perfil.sql` (CM-66). Si falta alguno, detente y reporta cuál.
- **Línea base:** corre la sección 20 de la spec (`./mvnw.cmd -B clean verify` y Newman de la colección completa) y pega la salida antes de editar.
- Reproducir con `docker compose up -d --build`: (a) perfil recién creado → `POST …/completion` → anotar que no hay `errors`; (b) `docker compose exec db psql -U cameia_perfil -d cameia_perfil -c "update perfil_profesional set resumen = repeat('a', 2001) where id = '<id>'"` y `POST …/completion` → anotar el 422 `VALIDATION_FAILED`; (c) `POST …/completion` con cuerpo `{"x":` (V1) → anotar el resultado. Pegar en `specs/CM-67-finalizacion-perfil-v4/evidencia-antes.md`.

## [x] T-1 · `CompletionRequirement` y requisitos del dominio — ≤ 25 min, ≈ 90 líneas

- **Crear** `domain/model/CompletionRequirement.java`:
  ```java
  /**
   * Requisito que un perfil debe cumplir para pasar a Activo, declarado en el orden en que lo listan los
   * criterios de aceptación; los que faltan se informan en ese mismo orden.
   */
  public enum CompletionRequirement {
      /** El perfil tiene un nombre que no está en blanco. */
      NAME,
      /** El resumen existe, no está en blanco y no supera el máximo del resumen. */
      SUMMARY,
      /** Al menos una formación académica. */
      EDUCATION,
      /** Al menos una habilidad. */
      SKILLS,
      /** Al menos un rol objetivo. */
      TARGET_ROLES
  }
  ```
- **Modificar** `ProfessionalProfile.getMissingRequirements()` → `List<CompletionRequirement>` con las reglas del plan §1 (largo del resumen con `SingleLineText.length(summary.value()) > ProfessionalSummary.MAX_LENGTH`); `isComplete()` igual; `complete()`: 409 si `COMPLETED`, luego 422 con la lista. `IncompleteProfileException` recibe y expone `List<CompletionRequirement>`; Javadoc en español en lo tocado.
- **Pruebas** `ProfessionalProfileTest`:
  - `complete_shouldListFiveInOrder_whenProfileIsNew` → `[NAME, SUMMARY, EDUCATION, SKILLS, TARGET_ROLES]`.
  - `complete_shouldListSummaryAndSkills_whenBothAreMissing` (nombre, 1 formación, 1 rol) → `[SUMMARY, SKILLS]`, estado `IN_PROGRESS`.
  - `complete_shouldListOnlyMissing_whenSomeCollectionsAreEmpty` (parametrizada: las 8 combinaciones de formación/habilidad/rol con nombre y resumen presentes).
  - `complete_shouldSucceed_whenSummaryHasExactly2000CodePoints` (2000 `a` y 2000 `"😀"`) y `complete_shouldListSummary_whenStoredSummaryHas2001CodePoints` (perfil reconstituido con `new ProfessionalSummary("a".repeat(2001))`).
  - `complete_shouldListName_whenStoredNameIsBlank` (reconstituido con `new ProfileName("   ")`).
  - `complete_shouldSucceed_whenProfileHasNoWorkExperience`.
  - `complete_shouldRejectAlreadyCompleted_beforeCheckingRequirements` (perfil `COMPLETED` sin habilidades → `ProfileAlreadyCompletedException`, no `IncompleteProfileException`).
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfessionalProfileTest" test`.

## [x] T-2 · `errors[]` del 422 — ≤ 25 min, ≈ 90 líneas

- **Modificar** `ErrorCode`: `COMPLETION_NAME_REQUIRED`, `COMPLETION_SUMMARY_REQUIRED`, `COMPLETION_EDUCATION_REQUIRED`, `COMPLETION_SKILLS_REQUIRED`, `COMPLETION_TARGET_ROLES_REQUIRED` (Javadoc: «El perfil no puede pasar a Activo: falta {el requisito}.»). (Códigos propios: decisión de Paula del 9-oct-2026.)
- **Modificar** `ErrorCatalog`:
  ```java
  /** Requisito de la finalización → elemento de errors[] que el cliente lee para listarlo. */
  static final Map<CompletionRequirement, CompletionItem> COMPLETION_REQUIREMENTS = Map.of(
          NAME, new CompletionItem("name", COMPLETION_NAME_REQUIRED, "Nombre del perfil"),
          SUMMARY, new CompletionItem("summary", COMPLETION_SUMMARY_REQUIRED, "Resumen profesional"),
          EDUCATION, new CompletionItem("education", COMPLETION_EDUCATION_REQUIRED, "Formación académica"),
          SKILLS, new CompletionItem("skills", COMPLETION_SKILLS_REQUIRED, "Habilidades"),
          TARGET_ROLES, new CompletionItem("targetRoles", COMPLETION_TARGET_ROLES_REQUIRED, "Roles objetivo"));
  ```
  con `record CompletionItem(String field, ErrorCode code, String message)` anidado.
- **Modificar** `ApiExceptionHandler.handleBusiness`: para `IncompleteProfileException`, `missingRequirements` = nombres del `enum` en orden y `errors` = `COMPLETION_REQUIREMENTS.get(r)` en el mismo orden, como `FieldProblem(field, code, message)`. Comentario de línea: «El cliente lista los requisitos que faltan desde errors[].field; missingRequirements conserva los códigos que nombran los criterios.»
- **Modificar** `ErrorCatalogTest.everyErrorCode_shouldBeResponseOrField_whenCatalogLoaded`: agregar el grupo de los códigos de `COMPLETION_REQUIREMENTS` (cada código en un solo grupo) y una prueba `completionRequirements_shouldCoverEveryRequirement_whenCatalogLoaded` (las cinco claves).
- **Pruebas** `ProfileControllerTest`:
  - `postCompletion_shouldListFiveRequirementsInOrder_whenProfileIsNew`: 422, `$.code` `PROFILE_INCOMPLETE`, `$.detail` «Todavía no cumples estos requisitos:», `$.missingRequirements` `["NAME","SUMMARY","EDUCATION","SKILLS","TARGET_ROLES"]`, `$.errors[*].field` `["name","summary","education","skills","targetRoles"]` en ese orden, `$.errors[1].code` `COMPLETION_SUMMARY_REQUIRED`, `$.errors[1].message` «Resumen profesional», `Content-Type` con `charset=UTF-8`.
  - `postCompletion_shouldReturn422WithCommonShape_whenSummaryAndSkillsAreMissing` (existente): agregar las aserciones de `errors`.
  - `postCompletion_shouldReturn409_whenProfileIsAlreadyActive`, `postCompletion_shouldReturn403_whenProfileBelongsToAnotherUser`, `postCompletion_shouldReturn200_whenAllRequirementsAreMet` (`$.status` `COMPLETED`).
  - `postCompletion_shouldIgnoreBody_whenBodyIsSent` (V1): con `{"status":"COMPLETED"}` y con `{"x":` → el resultado depende solo del perfil.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfileControllerTest,ErrorCatalogTest,ApiExceptionHandlerTest" test`.

## [x] T-3 · Integración: éxito, faltantes, 2001 guardado y falla real — ≤ 30 min, ≈ 180 líneas

- **Crear** `presentation/CompletionEndToEndIT.java` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Testcontainers`, `postgres:16-alpine` con `@ServiceConnection`, `TestRestTemplate`, `JdbcTemplate`, Usuario `uid-e2e-<UUID>`; un ayudante privado arma un perfil completo por la API: nombre, resumen de 300, una formación, una habilidad, un rol del catálogo).
  - `completeProfile_shouldReturn200AndCompleted_whenAllRequirementsAreMet`: 200 y `GET` con `status` `COMPLETED`.
  - `completeProfile_shouldActivate_whenNoWorkExperience`.
  - `completeProfile_shouldListSummaryAndSkills_whenBothAreMissing`.
  - `completeProfile_shouldListSummary_whenStoredSummaryHas2001Characters`: perfil completo; `jdbcTemplate.update("update perfil_profesional set resumen = repeat('a', 2001) where id = ?", id)`; `POST` → 422 `PROFILE_INCOMPLETE` con `missingRequirements` `["SUMMARY"]`; `GET` → 200 (leer no valida); estado `IN_PROGRESS`.
  - `completeProfile_shouldRollBack_whenDatabaseFailsDuringCompletion`: perfil completo; crear por JDBC
    ```sql
    create function fail_completion_<sufijo>() returns trigger language plpgsql as
      $$ begin raise exception 'forced failure'; end $$;
    create trigger fail_completion_<sufijo> before update of estado on perfil_profesional
      for each row when (new.firebase_uid = '<uid>') execute function fail_completion_<sufijo>();
    ```
    `POST` → 500, `$.code` `INTERNAL_ERROR`, `$.detail` «Ocurrió un error. Inténtalo de nuevo.», sin `forced failure` en el cuerpo; `GET` → `IN_PROGRESS` y el perfil igual que antes. `@AfterEach` borra el disparador y la función.
  - `completeProfile_shouldReturn403_whenProfileBelongsToAnotherUser` (el perfil de B no cambia).
  - `completeProfile_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes` (FIA-02): perfil completo; 20 `POST …/completion` seguidos → el primero 200 y los diecinueve siguientes 409 `PROFILE_ALREADY_COMPLETED`; el `GET` final igual al de después del primero. (En el PR 3, `ProfileEventsEndToEndIT` agrega que solo hay una fila en `evento_saliente`.)
- **Comandos:** `./mvnw.cmd -B "-Dit.test=CompletionEndToEndIT" verify`.
- **Detente si** el 500 trae `forced failure` o el SQL en el cuerpo, o si el perfil queda `COMPLETED`.

## [x] T-4 · Carreras de CA-2.5.22 — ≤ 25 min, ≈ 110 líneas

- **Agregar** a `infrastructure/persistence/ProfileWriteLockIT.java` (perfil completo con una sola habilidad S1):
  - `completeAndRemoveSkill_shouldRejectRemoval_whenCompletionWinsTheLock`: hilo A en `TransactionTemplate` llama `completeProfile` y espera un latch antes de confirmar; hilo B llama `removeSkill(S1)`; soltar A → A termina, B lanza la excepción de `SKILL_NOT_ALLOWED` (CM-66); la base: `COMPLETED` con S1.
  - `completeAndRemoveSkill_shouldRejectCompletion_whenRemovalWinsTheLock`: A elimina S1 y espera; B finaliza; soltar A → B lanza `IncompleteProfileException` con `[SKILLS]`; la base: `IN_PROGRESS` sin habilidades.
  - `completeProfile_shouldActivateOnce_whenTwoTabsCompleteTogether`: dos finalizaciones → una bien, la otra `ProfileAlreadyCompletedException`.
- **Comandos:** `./mvnw.cmd -B "-Dit.test=ProfileWriteLockIT" verify`.

## [x] T-5 · `review-requests` — ≤ 20 min

- Borrar `ProfileController.requestReview` y su `@PostMapping("/{id}/review-requests")`, `ProfileAppService.requestReview`, `ProfessionalProfile.requestReview`, y `markAsReviewed`/`returnForRevision` si quedan sin llamadas (`git grep`); borrar sus pruebas. **No** borrar el valor `IN_REVIEW` del estado ni la regla que permite finalizar desde `IN_REVIEW` (HU-2.8 lo usará); agregar `ProfessionalProfileTest.complete_shouldSucceed_whenProfileIsInReview` armando el perfil con el constructor de reconstrucción; `docs/errores.md` deja de citar `review-requests`; prueba `ProfileControllerTest.postReviewRequests_shouldReturn404_whenRouteDoesNotExist` (404 `ROUTE_NOT_FOUND`). Reportar las líneas borradas.

## [x] T-6 · OpenAPI, catálogo, Postman y cierre — ≤ 30 min, ≈ 280 líneas

- `ProfileController.completeProfile`: `@Operation` con los cinco requisitos en orden; `@ApiResponse` 200, 401, 403, 404, 409, 422 (ejemplo completo de la spec §5), 500, 409 `PROFILE_UPDATE_IN_PROGRESS`. `ApiErrorResponse`: `missingRequirements` opcional con `@Schema(allowableValues = {"NAME","SUMMARY","EDUCATION","SKILLS","TARGET_ROLES"})`.
- `docs/errores.md`: fila de `PROFILE_INCOMPLETE` con `errors[]`; filas de los cinco `COMPLETION_*_REQUIRED` (endpoint `POST …/completion`, campo, rótulo, origen `ErrorCatalog.COMPLETION_REQUIREMENTS`, prueba).
- Postman: carpeta `HU-2.5 — Finalización` / `POST profiles/{id}/completion` con las peticiones de la spec §13 (cada una con estado, `Content-Type`, `code`, `missingRequirements`, `errors[*].field` en orden, `requestId`, sin traza). Usuario `uid-pm-{{$guid}}` por carpeta.
- **Cierre:** `./mvnw.cmd -B clean verify`; cobertura (≥ 90 % líneas y ramas) de `ProfessionalProfile`, `CompletionRequirement`, `IncompleteProfileException`, `ApiExceptionHandler`, `ErrorCatalog`, `ProfileAppService`; global sin bajar; `docker compose down -v && docker compose up -d --build` y `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json -e docs/perfil-local.postman_environment.json --folder "HU-2.5 — Finalización" --reporters cli,junit`; pegar las salidas; diff ≤ 1000.
- **DES-02:** carpeta `DES-02 — HU-2.5 finalización` (por iteración: Usuario `uid-des-{{$guid}}`, crear perfil, nombre y resumen, una formación, una habilidad, un rol del catálogo y `POST …/completion`); `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json -e docs/perfil-local.postman_environment.json --folder "DES-02 — HU-2.5 finalización" -n 105 --reporters cli,json --reporter-json-export target/newman/des02-hu25.json` y el script de p95 de CM-274 T-E3 (cambiando el archivo); se informa el p95 de la petición de finalización (≤ 2000 ms con `n=100`).
- **Colección completa:** después de la carpeta, Newman de toda la colección sin `--folder` para comprobar que nada anterior se rompió.

---

# PR 2 — Tabla de salida y relevo (`CM-67-tabla-salida-eventos`, sobre el PR 1)

Reglas 1 a 9 de arriba, con dos cambios: esta parte **sí** lleva una migración, `V9__evento_saliente.sql` (número fijo; `git ls-tree --name-only HEAD src/main/resources/db/migration/` debe terminar en `V8__alinear_habilidad_perfil.sql`; si no, detente), y el `ERROR` de REQ-EV-06 es el único log nuevo en ese nivel. Decisiones comunes con Cuentas (CM-279, 9-oct-2026): metadatos en propiedades AMQP, intento tras el commit con 500 ms, Job cada 5 min, carga borrada al publicar y `@Transactional` en los métodos del adaptador.

## [ ] T-7 · Migración y adaptador de la tabla — ≤ 30 min, ≈ 220 líneas

- **Crear** `V9__evento_saliente.sql` con el SQL exacto de la spec §18.4 y un comentario de cabecera en español («Tabla de salida de eventos de Perfil: el evento se guarda en la misma transacción que el cambio y se publica después.»).
- **Crear** `domain/event/OutboundEvent.java` (`record OutboundEvent(UUID id, String type, int version, UUID aggregateId, String payloadJson, String correlationId, Instant createdAt)`), `domain/port/OutboxRepository.java`:
  ```java
  /** Eventos de salida, guardados en la misma transacción que el cambio que los produjo. */
  public interface OutboxRepository {
      /** @param event evento que se guarda; debe correr dentro de la transacción del cambio */
      void append(OutboundEvent event);
      /** @param limit máximo de filas @return eventos pendientes, el más antiguo primero, bloqueados para que otra instancia los salte */
      List<OutboundEvent> lockPending(int limit);
      /** @param id identificador del evento @param publishedAt instante de la confirmación; borra la carga (datos mínimos) */
      void markPublished(UUID id, Instant publishedAt);
      /** @param id identificador del evento @return intentos después de sumar este */
      int addAttempt(UUID id);
  }
  ```
- **Crear** (con `@Transactional` en cada método del adaptador que escribe, como `ProfessionalProfileRepositoryAdapter`) `infrastructure/persistence/entity/OutboxEventEntity.java` (`@Table(name = "evento_saliente")`, `carga` con `@JdbcTypeCode(SqlTypes.JSON)`), `…/repository/OutboxEventJpaRepository.java` (`lockPending` con `@Query(value = "select * from evento_saliente where fecha_publicacion is null order by fecha_creacion limit :limit for update skip locked", nativeQuery = true)`) y `OutboxRepositoryAdapter.java`.
- **Pruebas** `infrastructure/persistence/OutboxRepositoryAdapterIT.java` (Testcontainers): `append_shouldStorePendingRow_whenCalled`; `lockPending_shouldReturnOldestFirst_whenSeveralArePending`; `lockPending_shouldSkipRowsLockedByAnotherTransaction` (dos `TransactionTemplate`); `markPublished_shouldClearPayload_whenConfirmed`; `addAttempt_shouldIncrement_whenCalledTwice`; `constraint_shouldReject_whenRowBreaksIt` (parametrizada: `tipo` desconocido, `version` 0, `id_correlacion` con espacio, `intentos` −1, publicada con carga) con el nombre de cada `ck_`.
- **Trampa:** `@JdbcTypeCode(SqlTypes.JSON)` con `ddl-auto=validate` sobre `jsonb`: si el arranque falla, detente y reporta el mensaje completo.
- **Comandos:** `./mvnw.cmd -B "-Dit.test=OutboxRepositoryAdapterIT" verify`.

## [ ] T-8 · Publicador con confirmación y topología — ≤ 30 min, ≈ 180 líneas

- **Crear** `domain/port/EventPublisher.java` (`boolean publish(OutboundEvent event)`: `true` solo si el broker confirmó y no devolvió el mensaje).
- **Reemplazar** `ProfileEventPublisher` por `infrastructure/messaging/publisher/RabbitEventPublisher.java`: `RabbitTemplate` con `setMandatory(true)`, `CorrelationData(event.id().toString())`, espera de la confirmación con un plazo constante de 500 ms (`correlationData.getFuture().get(500, MILLISECONDS)`); propiedades AMQP de la spec §18.2. Una excepción del broker devuelve `false` y se registra en `WARN` con el id y el tipo, sin la carga.
- **Modificar** `RabbitConfig`: `EXCHANGE = "perfil.events"` (`TopicExchange(EXCHANGE, true, false)`); quitar `QUEUE_PERFIL_ACTUALIZADO`, su `Queue` y su `Binding` (la cola es del consumidor). No tocar las colas que consume Perfil (`cuenta-eliminada`, `suscripcion-actualizada`, `consumo-registrado`): las alinea su propia tarea. Quitar los `TODO` que esta tarjeta resuelve.
- **Modificar** `application.yml`: `spring.rabbitmq.publisher-confirm-type: correlated` y `spring.rabbitmq.publisher-returns: true`.
- **Pruebas** `RabbitEventPublisherTest` (Mockito): confirmado → `true`; `nack` → `false`; devuelto → `false`; `publish_shouldReturnFalse_whenConfirmationTakesLongerThanLimit` (confirmación a los 600 ms → `false` en ≤ 550 ms); propiedades AMQP exactas.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=RabbitEventPublisherTest" test`.

## [ ] T-9 · Relevo — ≤ 30 min, ≈ 160 líneas

- **Crear** `application/service/OutboxRelayService.java`:
  - `relayAfterCommit(UUID eventId)`: registra una `TransactionSynchronization` cuyo `afterCommit` publica esa fila una vez (confirmación en ≤ 500 ms, sin transacción abierta durante la llamada al broker) y marca o suma intento en una transacción corta. Un fallo aquí nunca cambia la respuesta HTTP: se registra en `WARN` y la fila queda para el Job.
  - `relayPending()`: en una transacción corta toma `lockPending(50)`, publica cada una y marca o suma intento; al llegar a 10 intentos registra `ERROR` una vez (REQ-EV-06).
- **Crear** `src/main/resources/application-profile-events-relay.yml`:
  ```yaml
  # Tarea de una sola ejecución (Cloud Run Job): no abre puerto HTTP ni consume colas de Perfil.
  spring:
    main:
      web-application-type: none
    rabbitmq:
      listener:
        simple:
          auto-startup: false
  ```
  **Trampa:** sin `auto-startup: false`, la tarea arrancaría los consumidores de Perfil (`AccountDeletedListener` y los de CM-279) y consumiría mensajes de la cola del servicio mientras publica.
- **Crear** `infrastructure/config/ProfileEventsRelayJobRunner.java` (`@Component`, `@Profile("profile-events-relay")`, `ApplicationRunner`): llama `relayPending()` hasta vaciar los pendientes o 10 lotes y registra en `INFO` «Relevo de eventos de Perfil terminado [publicados={}, fallidos={}, pendientes={}]». Comentario de bloque: «Solo existe con el perfil profile-events-relay, que usa el Cloud Run Job programado cada 5 minutos; un evento sin publicar no es una falla: espera la siguiente corrida.»
- **Modificar** `PerfilApplication.main` como en Cuentas: guardar el contexto de `SpringApplication.run` y, si el perfil `profile-events-relay` está activo, `System.exit(SpringApplication.exit(context))` (comentario: «Las tareas de una sola ejecución terminan con el código del contexto; los hilos de RabbitMQ mantendrían viva la JVM.»).
- **Pruebas** `infrastructure/config/ProfileEventsRelayJobRunnerTest.java` (`@SpringBootTest`, `@ActiveProfiles("profile-events-relay")`, Testcontainers PostgreSQL, `@MockitoBean OutboxRelayService`): `run_shouldRelayPending_whenProfileIsActive`; `context_shouldHaveNoWebServer_whenProfileIsActive`; `listeners_shouldNotStart_whenProfileIsActive` (cada `MessageListenerContainer` de `RabbitListenerEndpointRegistry` con `isRunning()` en `false`).
- **Manual (en el PR):** `java -jar target\cameia-perfil-0.0.1-SNAPSHOT.jar --spring.profiles.active=profile-events-relay` (PowerShell no expande comodines para `java`) con base y broker de compose → termina solo con código 0 (`echo $LASTEXITCODE`).
- **Pruebas** `OutboxRelayServiceTest`: `relayAfterCommit_shouldKeepRowPending_whenBrokerFails` (sin excepción hacia el llamador); publicado → marca; no confirmado → suma intento; ya publicado → nada; décimo intento → un solo `ERROR`; lote con un fallo → sigue con las demás filas.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=OutboxRelayServiceTest" test`; cierre del PR 2 con `./mvnw.cmd -B clean verify`, cobertura de las clases nuevas y diff ≤ 1000.

---

# PR 3 — Publicación de `perfil.profesional-actualizado` (`CM-67-evento-perfil-actualizado`, sobre el PR 2)

Se ejecuta con la spec `specs/CM-67-EventoPerfilActualizado/`.

## [ ] T-10 · Contrato v1 — ≤ 30 min, ≈ 150 líneas

- **Crear** `docs/eventos/perfil-profesional-actualizado-v1.schema.json` (JSON Schema 2020-12, `additionalProperties: false`, `required` con los campos de HT-03) y `docs/eventos/perfil-profesional-actualizado-v1.md` (destino, propiedades AMQP, garantías, ejemplo).
- **Reemplazar** `ProfileUpdatedPayloadV1` con los campos que fije el Product Owner (`@JsonProperty` en español; `actualizadoEn` como texto ISO con milisegundos y `Z`).
- **Crear** `domain/event/ProfileUpdated.java` y `domain/event/CorrelationId.java` (regla `^[A-Za-z0-9._-]{1,64}$`; si no cumple, el id del evento).
- **Pruebas** `ProfileUpdatedEventContractTest` (lee el esquema con Jackson y compara nombres, tipos y obligatorios con la carga serializada); `CorrelationIdTest` (64 válidos, 65 y espacio → respaldo).

## [ ] T-11 · `saveAndAnnounce` en toda escritura — ≤ 30 min, ≈ 160 líneas

- **Modificar** `ProfileAppService`: método privado `saveAndAnnounce(ProfessionalProfile profile, CorrelationId correlation)` que guarda y, si `profile.getStatus() == COMPLETED`, arma el evento con el reloj inyectado, llama `outbox.append` y `relay.relayAfterCommit(id)`. Reemplaza `repository.save` en `completeProfile`, `updateProfileInfo`, `addWorkExperience`, `removeWorkExperience`, `addEducation`, `removeEducation`, `addTargetRole`, `updateTargetRole`, `removeTargetRole`, `addSkill` y `removeSkill` (decisión de Paula: las habilidades también publican).
- **Pruebas** `ProfileAppServiceTest.writes_shouldAnnounce_whenProfileIsCompleted` (parametrizada por cada caso de uso: un `append`); `writes_shouldNotAnnounce_whenProfileIsDraft`; `completeProfile_shouldNotAnnounce_whenRequirementsAreMissing`.

## [ ] T-12 · Integración con RabbitMQ real y cierre — ≤ 30 min, ≈ 200 líneas

- **Crear** `ProfileEventsEndToEndIT` (Testcontainers `postgres:16-alpine` y `rabbitmq:3.13-management-alpine` con `@ServiceConnection`; cola de prueba ligada a `perfil.events` con la clave `perfil.profesional-actualizado`): finalizar → un mensaje con la carga del esquema; editar un perfil `COMPLETED` → otro mensaje con `actualizadoEn` mayor; editar un borrador → ninguno; finalización revertida por el disparador de T-3 → ninguno; broker detenido durante la escritura → 200 y la fila pendiente; broker de nuevo arriba y `relayPending()` → un mensaje con el mismo `message_id`.
- Postman: sin peticiones nuevas (el evento no se ve por HTTP); `docs/errores.md` sin códigos nuevos.
- **Cierre:** `./mvnw.cmd -B clean verify`; cobertura ≥ 90 % de las clases del PR; diff ≤ 1000.

## Resultados del PR 1 (10-oct-2026)

**T-0.** Línea base: la capa anterior (CM-66) tiene `clean verify` en verde (776 unitarias + 235 IT) y Newman de la colección completa sin fallos (574 peticiones). Las piezas citadas existen: `findByIdForUpdate`, `ProfessionalSummary.MAX_LENGTH`, `SKILL_NOT_ALLOWED` y `V8__alinear_habilidad_perfil.sql`. Reproducción en `evidencia-antes.md`: el punto (b) de la tarjeta no es posible porque la columna `resumen` es `VARCHAR(2000)` (Desvío 38 de la cola); el IT que la tarjeta pedía para 2001 guardado se reemplazó por uno con 2000 (`completeProfile_shouldActivate_whenStoredSummaryHasTheMaximumLength`) y el caso de 2001 queda en la prueba de dominio con un perfil reconstruido.

**T-1 y T-2.** `CompletionRequirement`, `IncompleteProfileException` con la lista tipada, `ProfessionalProfile.getMissingRequirements()` con el resumen vacío o de más de 2000 puntos de código como faltante, los cinco `COMPLETION_*_REQUIRED`, `ErrorCatalog.COMPLETION_REQUIREMENTS` y `errors[]` en el 422 en el orden de los criterios. Rojo: no capturado (código antes que las pruebas; Desvío 34). Verde: `ProfessionalProfileTest` (+ 9 pruebas de `complete_…`), `ProfileControllerTest.postCompletion_…` (5 pruebas), `ErrorCatalogTest.completionRequirements_shouldCoverEveryRequirement_whenCatalogLoaded` y `ApiExceptionHandlerTest`.

**T-3.** `CompletionEndToEndIT` 8 pruebas: 200 con los cinco requisitos, sin experiencia, resumen y habilidades faltantes, perfil nuevo con los cinco, resumen de 2000 guardado, 500 genérico con rollback ante un disparador que falla (sin `forced failure` en el cuerpo), 403 y veinte repeticiones (un 200 y diecinueve 409).

**T-4.** `ProfileWriteLockIT` (+3): gana la finalización y la baja de la única habilidad se rechaza con `SKILL_NOT_ALLOWED`; gana la baja y la finalización se rechaza con `[SKILLS]`; dos finalizaciones simultáneas activan una vez.

**T-5.** Se borraron `requestReview` (controlador, servicio y agregado), `markAsReviewed` y `returnForRevision` (sin llamadores) y sus siete pruebas (`ProfileControllerTest` 2, `ProfileAppServiceTest` 2, `ProfessionalProfileTest` 2 y la fila del caso de bloqueo de `writeUseCases`); `IN_REVIEW` y la finalización desde él se conservan (`complete_shouldSucceed_whenProfileIsInReview`). `postReviewRequests_shouldReturn404_whenRouteDoesNotExist` fija el 404 `ROUTE_NOT_FOUND`.

**T-6 (código).** OpenAPI de `POST …/completion` con los cinco requisitos, los estados 200, 401, 403, 404, 409, 422 (con ejemplo) y 500, y `missingRequirements` con sus valores permitidos (`OpenApiDocumentIT.completion_shouldDocumentRequirements_whenApiDocsAreGenerated`); `docs/errores.md` con `PROFILE_INCOMPLETE` y la tabla de los cinco códigos. Cierre: `clean verify` BUILD SUCCESS, 795 unitarias + 247 de integración, 0 fallos (`ejecucion/verify-CM-67-1.log`). Cobertura: `ProfessionalProfile` 131/131 y 66/66; `CompletionRequirement` 6/6; `IncompleteProfileException` 4/4; `ErrorCatalog` 93/93 y 2/2; `ApiExceptionHandler` 69/69 y 29/30 (previa); `ProfileAppService` 99/100 y 40/40; global 97,3 % líneas y 98,6 % ramas. La carpeta de Postman y DES-02 van en el PR siguiente (`CM-67-postman-finalizacion`, regla de 800 líneas).

**Resultado de Postman y DES-02 del PR 1 (carpeta `HU-2.5 — Finalización`; 10-oct-2026).** Colección generada con `ejecucion/herramientas/hu25f_postman.py` (idempotente, antes de la carpeta de HU-2.4). 19 peticiones: perfil recién creado con los cinco requisitos en orden (`missingRequirements` y `errors[]` con campo, código y rótulo), faltan resumen y habilidades (CA-2.5.4), 401, 403 sin cambios en el perfil ajeno, cuerpo mal formado ignorado, 404, 422 de identificador, 405 con `Allow: POST`, 200 con resumen de 2000 y sin experiencia, 409 al finalizar otra vez y 409 `SKILL_NOT_ALLOWED` al eliminar la única habilidad de un perfil activo. Newman contra la imagen empaquetada con la base limpia y el rol `a0000007-…06` en `INACTIVE`: carpeta 20 peticiones (19 más una publicación auxiliar), 0 fallidas, 89 aserciones (`newman-CM-67-1.log`); colección completa 601 peticiones, 0 fallidas, 2 774 aserciones (`newman-CM-67-1-completa.log`); DES-02 con n=100: p95 de la finalización 32 ms (las otras cinco peticiones entre 32 y 47 ms) (`newman-CM-67-1-des02.log`); con una copia mutada fallan 3 aserciones (`newman-CM-67-1-mutada.log`). `docker compose logs app`: dos líneas `ERROR`, ambas el 503 `BIRTH_DATE_UNAVAILABLE` esperado de HU-2.4.

