# Tareas — CM-67: evento `perfil.profesional-actualizado` y CA-2.5.18 (cameia-perfil)

Estado: T-0 a T-8 ejecutadas el 15-oct-2026; T-9 hecha. Spec, plan y tarjetas aprobados por Paula el 13-oct-2026 (D-16 A, D-17 B, D-18 sin cambio en Jira por ahora, D-19 A). Una tarjeta a la vez; se marca `[x]` solo
con la salida real de sus comandos pegada debajo.

## Reglas para todas las tarjetas

1. Rutas: `src/main/java/co/edu/unicauca/cameia/perfil/` (abreviado `main/`) y `src/test/java/co/edu/unicauca/cameia/perfil/` (`test/`).
2. **Idioma (R1).** En inglés: identificadores, nombres de métodos de prueba. En español: Javadoc, comentarios, `@DisplayName`, registros y
   documentos. Los nombres JSON del contrato van en español con `@JsonProperty` (los fija HT-03).
3. `domain` sin Spring, JPA, Rabbit ni Jackson. `application.service` solo clases que terminan en `AppService` (`ArquitecturaTest`).
   Métodos ≈ 20 líneas, ≤ 3 parámetros. Sin dependencias nuevas (`pom.xml` intacto). Sin Lombok.
4. Javadoc en toda clase, record y método nuevos o modificados, con `@param`, `@return` y `@throws`. Comentarios sin `CM-NNN` ni rutas a
   otros archivos o repos.
5. Ningún registro escribe la carga, el nombre del perfil, el resumen, experiencias, formaciones, habilidades ni roles.
6. Pruebas `method_shouldResult_whenCondition`, `@DisplayName` en español, AAA, valores literales, sin `Thread.sleep` (usar Awaitility,
   ya en el repo). `*IT` con Testcontainers; los corre Failsafe en `verify`.
7. Comandos (PowerShell, desde la raíz del repo): unitarias `./mvnw.cmd -q -B "-Dtest=<Clase>" test`; integración
   `./mvnw.cmd -B "-Dit.test=<Clase>" "-Dtest=NoSuchTest" "-Dsurefire.failIfNoSpecifiedTests=false" verify`; cierre `./mvnw.cmd -B clean verify`.
   Docker encendido para los `*IT`.
8. Commit al terminar la tarjeta: `CM-67 | <tipo>(perfil): <resultado>` (mensaje exacto en cada tarjeta), sin `[IA-ASISTIDO]`, con el
   trailer `Co-Authored-By`. Push y PR solo con el sí de Paula.
9. **Detente y reporta** si una firma, clase o línea citada no coincide con el código real, si una prueba existente se rompe sin causa
   clara o si hace falta algo no listado. No improvises el diseño.

## Contraste con `origin/develop` `504f838` (hecho al escribir las tarjetas, 13-oct-2026)

| Lo que citan las tarjetas | Existe | Dónde |
|---|---|---|
| `OutboundEvent(UUID id, String type, int version, UUID aggregateId, String payloadJson, String correlationId, Instant createdAt)` | Sí | `main/domain/event/OutboundEvent.java:18` |
| `OutboxRepository` con `append`, `lockPending`, `markPublished`, `addAttempt`, `countPending` | Sí | `main/domain/port/OutboxRepository.java` |
| `OutboxRepositoryAdapter.append` con `@Transactional(propagation = MANDATORY)` y `repository.insert(...)` | Sí | `main/infrastructure/persistence/repository/OutboxRepositoryAdapter.java:30-36` |
| `OutboxRelayAppService.relayAfterCommit(OutboundEvent)` y `relayPending()` | Sí | `main/application/service/OutboxRelayAppService.java:61,83` |
| `RabbitConfig.EXCHANGE_PERFIL` = `perfil.events`, bean `perfilExchange` | Sí | `main/infrastructure/messaging/config/RabbitConfig.java:39,43` |
| `ProfileUpdatedPayloadV1` (esqueleto con `profileId`, `firebaseUid`, `name`, `status`), sin usos | Sí | `git grep ProfileUpdatedPayloadV1 origin/develop -- src` → solo su archivo |
| `ProfileAppService(ProfessionalProfileRepository, ProfessionalRoleRepository, Clock, BirthDateReplica)` y `repository.save` en las líneas 101, 138, 178, 188, 200, 207, 226, 232, 253, 271, 276, 283 | Sí | `main/application/service/ProfileAppService.java` |
| `new ProfileAppService(` en pruebas | Sí, dos | `test/application/service/ProfileAppServiceTest.java:88,158` |
| `ProfileAppServiceTest.completeProfile()`, `freshProfile()`, `writeUseCases()`, `SVC_UID`, `COMPLETE_UID` | Sí | mismo archivo, líneas 73-74, 558, 1022-1036 |
| `MessagingIntegrationSupport` (abstracta, paquete `infrastructure.messaging`, `rabbitTemplate`, `rabbitAdmin`, `jdbcTemplate`) | Sí | `test/infrastructure/messaging/MessagingIntegrationSupport.java` |
| `ReplicatedBirthDates.insertAdult(JdbcTemplate, String)` y `deleteByPrefix` | Sí | `test/support/ReplicatedBirthDates.java:37,47` |
| `ApiTestClient.send`, `json`, `createProfile` | Sí | `test/presentation/ApiTestClient.java:37,53,65` |
| `ColumnLengthConsistencyIT` con `postgres` estático y Flyway manual | Sí | `test/infrastructure/persistence/ColumnLengthConsistencyIT.java` |
| `CreateProfileCommand(String firebaseUid)`, `ProfileCreationAppService.createProfile` | Sí | `main/application/command/CreateProfileCommand.java:9`; `ProfileCreationAppService.java:46` |
| Rol del catálogo `a0000001-0000-0000-0000-000000000002` («Desarrollador Backend») | Sí | `V3__catalogo_roles_profesionales.sql:15` |
| `infrastructure/web` | No: se crea (D-19 = A) | — |
| `CompletionEndToEndIT.completeProfile_shouldListSummary_whenStoredSummaryHas2001Characters` | No (H11) y no se crea (D-17 = B) | — |

---

## [x] T-0 · Base, Jira y evidencia antes — ≤ 20 min

- `git fetch`; `git status` limpio (los `.classpath`, `.project` y `.settings/` sin seguimiento no cuentan; no se agregan).
- `git rev-parse origin/develop^{tree}` → debe ser `1699baf6d979d27ffe292a8e3ec3c096c41b5614` (spec §20). Si no lo es:
  `./mvnw.cmd -B clean verify` y Newman de la colección completa; si algo falla, **detente**.
- `git switch -c CM-67-evento-perfil-actualizado origin/develop`. Los archivos de esta carpeta de spec entran en el commit de T-8.
- Jira: **no se cambia el estado sin preguntar a Paula en ese momento** (D-18, 13-oct: «todavía no»). Si ella lo autoriza: CM-67 a «En curso» y comentario «Acción: empieza la publicación del evento del perfil (HT-03) y la prueba de punta a
  punta de CA-2.5.18 · Repositorio: cameia-perfil, rama CM-67-evento-perfil-actualizado · Resultado: — · Evidencia: — · Decisión o bloqueo: lo
  ya entregado de la finalización no cambia y se puede seguir probando · Siguiente paso: implementación · Responsable: Paula Andrea Muñoz Delgado».
- **Evidencia antes** (`docker compose up -d --build` sobre la rama recién creada, sin cambios): un perfil completo por la API con
  `X-User-Id: uid-ev-antes-1` (la carpeta `HU-2.5 — Finalización` de Postman lo arma) y `POST …/completion` → 200; luego
  - `docker compose exec db psql -U cameia_perfil -d cameia_perfil -c "select count(*) from evento_saliente"` → anotar `0`;
  - `curl -s -u guest:guest http://localhost:15672/api/exchanges/%2F/perfil.events/bindings/source` → anotar `[]` (nadie ligado);
  - `docker compose exec db psql -U cameia_perfil -d cameia_perfil -c "update perfil_profesional set resumen = repeat('a', 2001) where firebase_uid = 'uid-ev-antes-1'"` → anotar el error `value too long for type character varying(2000)`.
  Pegar las tres salidas en `specs/CM-67-EventoPerfilActualizado/evidencia-antes.md` (tabla: petición, resultado antes, resultado esperado después).

## [x] T-1 · Contrato v1 — ≤ 30 min, ≈ 330 líneas

**Requisitos:** REQ-EV-02, REQ-EV-08, REQ-EV-09; IOP-01. **Commit:** `CM-67 | feat(perfil): contrato v1 de perfil.profesional-actualizado sin embedding`.

1. **Crear** `docs/eventos/perfil-profesional-actualizado-v1.schema.json` (JSON Schema 2020-12) con el contenido de la spec §5.2:
   - Raíz: `"$id": "urn:cameia:eventos:perfil-profesional-actualizado:v1"`, `"type": "object"`, `"additionalProperties": false`,
     `"required": ["usuarioId", "contextoProfesional", "rolesAspirados", "actualizadoEn"]`.
   - `usuarioId`: `{"type":"string","minLength":1,"maxLength":128}`.
   - `contextoProfesional`: objeto con `additionalProperties: false`, `required` = los ocho campos (`id`, `nombre`, `estado`,
     `fecha_archivado`, `resumen`, `experiencias`, `formaciones`, `habilidades`); `id` `{"type":"string","format":"uuid"}`; `nombre`
     `minLength 1, maxLength 255`; `estado` `{"enum":["PENDING","IN_PROGRESS","IN_REVIEW","COMPLETED"]}`; `fecha_archivado`
     `{"type":["string","null"],"format":"date-time"}`; `resumen` `minLength 1, maxLength 2000`; `experiencias` `{"type":"array","maxItems":4,"items":{…}}`
     con `empresa` y `cargo` (1–100), `descripcion` (`["string","null"]`, máx. 500), `inicio` y `fin` (`["string","null"]`,
     `"pattern":"^[0-9]{4}-(0[1-9]|1[0-2])$"`), `estadoEmpleo` (`enum` `CURRENT`, `UNKNOWN_END`, `ENDED`); `formaciones` (`minItems 1`,
     `maxItems 5`) con `institucion` y `titulo` (1–150), `nivel` (`TECHNICAL`, `UNDERGRADUATE`, `POSTGRADUATE`), `inicio`, `fin` como arriba,
     `enCurso` `boolean`; `habilidades` (`minItems 1`, `maxItems 20`) con `nombre` (1–60) y `nivel` (`BASIC`, `INTERMEDIATE`, `ADVANCED`).
     Cada objeto de lista con `additionalProperties: false` y todos sus campos en `required`.
   - `rolesAspirados`: `minItems 1`, `maxItems 5`, objetos con `codigo` (`format uuid`) y `nombre` (1–255).
   - `actualizadoEn`: `"pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z$"`.
   - `"examples": [ … ]` con el ejemplo literal de la spec §5.2. **No** hay propiedad `embedding`.
2. **Crear** `docs/eventos/perfil-profesional-actualizado-v1.md` con el mismo orden que `cuenta-creada-v1.md` de Cuentas: destino (exchange,
   clave, cola y **sus argumentos**: `x-dead-letter-exchange` `entrevista.dlx`, `x-dead-letter-routing-key`
   `entrevista.perfil-profesional-actualizado`; con D-16 = B: «la declara Entrevista»), cuerpo con el ejemplo, tabla de campos (spec §5.2),
   propiedades AMQP (spec §5.1), garantías (al menos una vez; sin orden; descartar `actualizadoEn` anterior; dos escrituras del mismo perfil
   pueden compartir milisegundo solo en teoría y la carga siempre es el estado completo), cuándo se publica (las once escrituras de REQ-EV-01 con
   el perfil `COMPLETED`; nunca en Borrador, en un rechazo ni en una reversión) y la línea «`embedding` no viaja: lo calcula Entrevista».
3. **Crear** `main/domain/event/CorrelationId.java`: copia literal de `cameia-cuentas/src/main/java/tech/cameia/cuentas/domain/event/CorrelationId.java`
   cambiando solo el `package` a `co.edu.unicauca.cameia.perfil.domain.event` (constante `MAX_LENGTH = 64`, patrón `^[A-Za-z0-9._-]{1,64}$`,
   `fromRequestIdOrElse(String requestId, UUID fallback)`).
4. **Crear** `main/domain/event/ProfileUpdated.java`:
   ```java
   /**
    * Hecho de negocio «cambió un Perfil Profesional activo»: lo que Entrevista necesita para mantener su copia del perfil.
    *
    * <p>Lleva el agregado ya modificado y se convierte en la carga dentro de la misma transacción del cambio, así que el
    * agregado no vuelve a cambiar antes de leerse.</p>
    *
    * @param eventId       identificador del evento, también el {@code message_id} del mensaje
    * @param profile       perfil activo tal como queda después del cambio
    * @param updatedAt     instante del cambio en UTC, con milisegundos
    * @param correlationId correlación con la petición que originó el cambio
    */
   public record ProfileUpdated(UUID eventId, ProfessionalProfile profile, Instant updatedAt, CorrelationId correlationId) {

       /** Tipo del evento; también es la clave de enrutamiento. */
       public static final String TYPE = "perfil.profesional-actualizado";
       /** Versión del contrato de la carga. */
       public static final int VERSION = 1;

       /**
        * Comprueba los componentes.
        *
        * @throws NullPointerException     si falta algún componente
        * @throws IllegalArgumentException si el perfil no está activo: solo un perfil activo se anuncia
        */
       public ProfileUpdated {
           Objects.requireNonNull(eventId, "eventId");
           Objects.requireNonNull(profile, "profile");
           Objects.requireNonNull(updatedAt, "updatedAt");
           Objects.requireNonNull(correlationId, "correlationId");
           if (profile.getStatus() != ProfileStatus.COMPLETED) {
               throw new IllegalArgumentException("Solo se anuncia un perfil activo");
           }
       }
   }
   ```
5. **Reemplazar** `main/infrastructure/messaging/payload/ProfileUpdatedPayloadV1.java` (borra el esqueleto y sus `TODO`):
   ```java
   /**
    * Cuerpo JSON de {@code perfil.profesional-actualizado} versión 1. Los nombres de campo los fija el contrato publicado.
    * Todo valor es texto, salvo {@code enCurso} y las listas, para que el formato en el cable no dependa del serializador;
    * los valores opcionales viajan como {@code null}. No lleva {@code embedding}: lo calcula Entrevista.
    *
    * @param userId              {@code firebaseUid} del dueño del perfil
    * @param professionalContext copia del perfil
    * @param targetRoles         roles objetivo del perfil, en su orden
    * @param updatedAt           instante del cambio en UTC con milisegundos y sufijo {@code Z}
    */
   public record ProfileUpdatedPayloadV1(
           @JsonProperty("usuarioId") String userId,
           @JsonProperty("contextoProfesional") ProfessionalContext professionalContext,
           @JsonProperty("rolesAspirados") List<TargetRoleItem> targetRoles,
           @JsonProperty("actualizadoEn") String updatedAt) {

       private static final DateTimeFormatter INSTANT_FORMAT =
               DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

       /**
        * Construye la carga a partir del evento de dominio.
        *
        * @param event evento con el perfil activo ya modificado
        * @return la carga del contrato v1
        */
       public static ProfileUpdatedPayloadV1 from(ProfileUpdated event) { /* arma los registros anidados; ≤ 20 líneas */ }

       /** Datos del perfil (JSON {@code contextoProfesional}); {@code fecha_archivado} siempre es nulo mientras no exista el archivado. */
       public record ProfessionalContext(@JsonProperty("id") String id, @JsonProperty("nombre") String name,
               @JsonProperty("estado") String status, @JsonProperty("fecha_archivado") String archivedAt,
               @JsonProperty("resumen") String summary, @JsonProperty("experiencias") List<WorkExperienceItem> workExperiences,
               @JsonProperty("formaciones") List<EducationItem> educations, @JsonProperty("habilidades") List<SkillItem> skills) { }

       /** Una experiencia laboral (JSON {@code experiencias[]}). */
       public record WorkExperienceItem(@JsonProperty("empresa") String company, @JsonProperty("cargo") String position,
               @JsonProperty("descripcion") String description, @JsonProperty("inicio") String start,
               @JsonProperty("fin") String end, @JsonProperty("estadoEmpleo") String employmentStatus) { }

       /** Una formación académica (JSON {@code formaciones[]}). */
       public record EducationItem(@JsonProperty("institucion") String institution, @JsonProperty("titulo") String degree,
               @JsonProperty("nivel") String level, @JsonProperty("inicio") String start, @JsonProperty("fin") String end,
               @JsonProperty("enCurso") boolean inProgress) { }

       /** Una habilidad (JSON {@code habilidades[]}). */
       public record SkillItem(@JsonProperty("nombre") String name, @JsonProperty("nivel") String level) { }

       /** Un rol objetivo (JSON {@code rolesAspirados[]}); el código es el id del rol en el catálogo de Perfil. */
       public record TargetRoleItem(@JsonProperty("codigo") String code, @JsonProperty("nombre") String name) { }
   }
   ```
   Reglas de `from`: `usuarioId` = `profile.getFirebaseUid().value()`; nombre y resumen con ayudantes nulo-seguros (`null` → `null`; un perfil
   activo siempre los tiene, pero una fila antigua sin ellos no debe convertirse en un 500); meses con `YearMonth.toString()` o `null`;
   enumerados con `name()`; listas en el orden de los getters del agregado; `actualizadoEn` con `INSTANT_FORMAT`. Imports:
   `com.fasterxml.jackson.annotation.JsonProperty` (como `AccountCreatedPayloadV1`).
6. **Pruebas.**
   - `test/domain/event/CorrelationIdTest.java`: `fromRequestIdOrElse_shouldKeepRequestId_whenItHas64AllowedCharacters` (`"a".repeat(64)` →
     el mismo); `fromRequestIdOrElse_shouldUseFallback_whenRequestIdIsInvalid` (`@ParameterizedTest @NullAndEmptySource @ValueSource` con
     `"a".repeat(65)` vía `@MethodSource`, `"req 1"`, `"<script>alert(1)</script>"`, `"abc\ndef"`, `"petición-1"` → valor =
     `UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7").toString()`); `constructor_shouldReject_whenValueIsInvalid` (`"a b"` →
     `IllegalArgumentException`).
   - `test/domain/event/ProfileUpdatedTest.java`: `constructor_shouldRejectNulls_whenAnyComponentIsMissing` (cuatro filas);
     `constructor_shouldReject_whenProfileIsNotCompleted` (perfil de `ProfessionalProfile.create(new FirebaseUid("uid-1"))` →
     `IllegalArgumentException`); `constants_shouldMatchTheContract`.
   - `test/infrastructure/messaging/payload/ProfileUpdatedPayloadV1Test.java` (arma un perfil activo con `ProfessionalProfile.reconstitute(…)`
     en `ProfileStatus.COMPLETED`; `updatedAt` = `Instant.parse("2026-10-15T12:00:00.123Z")`):
     `from_shouldTakeOwnerFromProfile_whenBuilt` (`usuarioId` = `"6f1d2c3b4a5e4f60718293a4b5c6d7e8"`);
     `from_shouldWriteNulls_whenOptionalValuesAreMissing` (experiencia sin descripción ni `fin`, formación sin fechas; nombre y resumen nulos
     en una fila antigua → el JSON tiene `"descripcion":null`, `"fin":null`, `"inicio":null`, `"nombre":null`, `"resumen":null` y
     `"fecha_archivado":null`, presentes);
     `from_shouldKeepTextLiteral_whenValuesAreAtTheirLimits` (nombre 255 `a`; resumen 2000 `"😀"`; habilidad 60 `a`; descripción 500 `a`; y
     una segunda carga con resumen `<script>alert(1)</script> "comillas" ñandú 😀 e\u0301` → al leer el JSON con `JsonMapper` cada texto es
     idéntico; `inicio` de `YearMonth.of(2021, 12)` → `"2021-12"`);
     `from_shouldKeepEveryItemInOrder_whenCollectionsAreFull` (4 experiencias, 5 formaciones, 20 habilidades `h01`…`h20`, 5 roles → mismas
     cantidades y orden);
     `from_shouldFormatInstantWithMillis_whenMillisAreZero` (`2026-10-15T12:00:00Z` → `"2026-10-15T12:00:00.000Z"`).
   - `test/infrastructure/messaging/payload/ProfileUpdatedEventContractTest.java` (lee el esquema con
     `Files.readString(Path.of("docs/eventos/perfil-profesional-actualizado-v1.schema.json"))` y `tools.jackson.databind.json.JsonMapper`,
     como `AccountCreatedPayloadV1Test`):
     `serializedPayload_shouldHaveExactlyTheSchemaProperties_whenProfileIsCompleted` (los nombres de cada objeto serializado = las claves de
     `properties` del nivel correspondiente, y también = su `required`);
     `serializedPayload_shouldNotContainEmbedding_whenSerialized` (el texto JSON no contiene `embedding`);
     `schema_shouldMatchDomainLimits_whenLengthsAndItemsAreCompared` (`maxLength` de `nombre` = `ProfileName.MAX_LENGTH`, `resumen` =
     `ProfessionalSummary.MAX_LENGTH`, `empresa`/`cargo` = `WorkExperience.MAX_COMPANY_LENGTH`/`MAX_POSITION_LENGTH`, `descripcion` =
     `MAX_DESCRIPTION_LENGTH`, `institucion`/`titulo` = `Education.MAX_INSTITUTION_LENGTH`/`MAX_DEGREE_LENGTH`, `habilidades[].nombre` =
     `SkillName.MAX_LENGTH`, `usuarioId` = `FirebaseUid.MAX_LENGTH`; `maxItems` = `ProfessionalProfile.MAX_WORK_EXPERIENCES`,
     `MAX_EDUCATIONS`, `MAX_SKILLS`, `MAX_TARGET_ROLES`; `rolesAspirados[].nombre` = 255);
     `schema_shouldListEveryDomainValue_whenEnumsAreCompared` (`enum` de `estado`, `estadoEmpleo`, `formaciones[].nivel` y
     `habilidades[].nivel` = `values()` de `ProfileStatus`, `EmploymentStatus`, `EducationLevel`, `SkillLevel`);
     `schemaExample_shouldDeserialize_whenReadWithPayloadRecord` (`examples[0]` → `ProfileUpdatedPayloadV1` sin error y vuelve a serializarse
     igual).
7. **Trampas.** (a) El título del rol objetivo no tiene constante pública (`TargetRole.MAX_TITLE_LENGTH` es privada): la prueba compara con
   el literal `255`; no la hagas pública. (b) `"😀"` son 2 unidades UTF-16: 2000 emoji = 4000 `char`; mide con `codePointCount`.
   (c) Jackson 3 escribe los `null` por omisión; si no aparecen, detente (no agregues `@JsonInclude` sin decirlo).
8. **Comandos:** `./mvnw.cmd -q -B "-Dtest=CorrelationIdTest,ProfileUpdatedTest,ProfileUpdatedPayloadV1Test,ProfileUpdatedEventContractTest" test`.
   **Ensayo en seco:** antes de crear el esquema, `ProfileUpdatedEventContractTest` falla con `NoSuchFileException`.

## [x] T-2 · La tabla de salida guarda el evento — ≤ 20 min, ≈ 90 líneas

**Requisitos:** REQ-EV-01, REQ-EV-03. **Commit:** `CM-67 | feat(perfil): la tabla de salida guarda el evento del perfil en la transacción del cambio`.

1. **Modificar** `main/domain/port/OutboxRepository.java`, agregar:
   ```java
   /**
    * Guarda el evento de perfil actualizado como pendiente, con su carga del contrato v1, en la transacción del cambio.
    *
    * @param event evento con el perfil activo ya modificado
    * @return el evento tal como quedó guardado, listo para el intento posterior al commit
    */
   OutboundEvent appendProfileUpdated(ProfileUpdated event);
   ```
2. **Modificar** `OutboxRepositoryAdapter`: campo `private static final JsonMapper JSON = JsonMapper.builder().build();`
   (`tools.jackson.databind.json.JsonMapper`) y
   ```java
   /** {@inheritDoc} */
   @Override
   @Transactional(propagation = Propagation.MANDATORY)
   public OutboundEvent appendProfileUpdated(ProfileUpdated event) {
       var stored = new OutboundEvent(event.eventId(), ProfileUpdated.TYPE, ProfileUpdated.VERSION, event.profile().getId().value(),
               JSON.writeValueAsString(ProfileUpdatedPayloadV1.from(event)), event.correlationId().value(), event.updatedAt());
       append(stored);
       return stored;
   }
   ```
   Actualizar el Javadoc de la clase: «la carga del evento de perfil se arma aquí, porque el contrato JSON es asunto de la infraestructura».
3. **Pruebas** en `test/infrastructure/persistence/OutboxRepositoryAdapterIT.java` (seguir su forma actual):
   `appendProfileUpdated_shouldStorePendingRowWithContractPayload_whenCalledInTransaction` (dentro de `TransactionTemplate`; fila con
   `tipo` `perfil.profesional-actualizado`, `version` 1, `agregado_id` = id del perfil, `fecha_publicacion` nula, `intentos` 0,
   `carga->>'usuarioId'` = el uid, `carga->'contextoProfesional'->>'estado'` = `COMPLETED`, `carga ? 'embedding'` = `false`);
   `appendProfileUpdated_shouldFail_whenNoTransactionIsActive` (sin transacción → `IllegalTransactionStateException`, 0 filas).
4. **Comandos:** `./mvnw.cmd -B "-Dit.test=OutboxRepositoryAdapterIT" "-Dtest=NoSuchTest" "-Dsurefire.failIfNoSpecifiedTests=false" verify`.
   **Ensayo en seco:** sin el método, la prueba no compila (rojo).
5. **Trampa:** el adaptador se llama a sí mismo (`append(stored)`): el proxy no aplica su `@Transactional`, pero ya estamos en la
   transacción obligatoria del método exterior; no lo cambies.

## [x] T-3 · Correlación con `X-Request-Id` — ≤ 15 min, ≈ 90 líneas

**Requisitos:** REQ-EV-09. **Commit:** `CM-67 | feat(perfil): correlación del evento con el X-Request-Id de la petición`.

1. **Crear** `main/domain/port/RequestCorrelation.java`:
   ```java
   /**
    * Da el identificador de la petición que está en curso, para unir un evento con la petición que lo originó.
    *
    * <p>El dominio no sabe de HTTP: fuera de una petición (por ejemplo en el Job de relevo) no hay identificador.</p>
    */
   public interface RequestCorrelation {
       /** @return el {@code X-Request-Id} tal como llegó, o vacío si no hay petición o no trae el encabezado */
       Optional<String> currentRequestId();
   }
   ```
2. **Crear** `main/infrastructure/web/package-info.java` («Adaptadores que leen la petición HTTP en curso.») y
   `main/infrastructure/web/HttpRequestCorrelation.java` (`@Component`, package-private, `static final String REQUEST_ID_HEADER = "X-Request-Id"`):
   si `RequestContextHolder.getRequestAttributes()` es `ServletRequestAttributes`, devuelve `Optional.ofNullable(request.getHeader(…))`; si no,
   `Optional.empty()`. No valida: la regla la aplica `CorrelationId`. **Si D-19 = B:** misma clase en `main/infrastructure/config/`, sin
   `package-info.java` nuevo.
3. **Pruebas** `test/infrastructure/web/HttpRequestCorrelationTest.java` (con `MockHttpServletRequest` y
   `RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request))`; `@AfterEach` `RequestContextHolder.resetRequestAttributes()`):
   `currentRequestId_shouldReturnHeader_whenRequestHasIt` (`"req-123"`); `currentRequestId_shouldBeEmpty_whenHeaderIsMissing`;
   `currentRequestId_shouldBeEmpty_whenNoRequestIsBound`.
4. **Comandos:** `./mvnw.cmd -q -B "-Dtest=HttpRequestCorrelationTest,ArquitecturaTest" test`.

## [x] T-4 · Toda escritura de un perfil activo anuncia el evento — ≤ 30 min, ≈ 260 líneas

**Requisitos:** REQ-EV-01, 03, 04, 05, 06, 08, 09, 11. **Commit:** `CM-67 | feat(perfil): toda escritura de un perfil activo anuncia el evento`.

1. **Crear** `main/application/service/ProfileEventsAppService.java`:
   ```java
   /**
    * Anuncia los cambios de un Perfil Profesional activo para que Entrevista mantenga su copia.
    *
    * <p>El evento se guarda en la tabla de salida dentro de la misma transacción que el cambio: si la escritura se revierte,
    * el evento desaparece con ella, y si el broker está caído la escritura no se pierde. La publicación se intenta una vez
    * después del commit; lo que falle lo recoge el Job de relevo.</p>
    */
   @Service
   public class ProfileEventsAppService {
       private final OutboxRepository outbox;
       private final OutboxRelayAppService relay;
       private final RequestCorrelation requestCorrelation;
       private final Clock clock;

       ProfileEventsAppService(OutboxRepository outbox, OutboxRelayAppService relay, RequestCorrelation requestCorrelation, Clock clock) { … }

       /**
        * Guarda el evento del perfil si quedó activo y deja programado su envío para después del commit.
        *
        * @param profile perfil ya modificado y guardado en la transacción en curso
        * @throws org.springframework.transaction.IllegalTransactionStateException si no hay transacción en curso
        */
       @Transactional(propagation = Propagation.MANDATORY)
       public void announceIfCompleted(ProfessionalProfile profile) {
           // Un borrador no le interesa a Entrevista: solo un perfil activo se puede usar en una entrevista.
           if (profile.getStatus() != ProfileStatus.COMPLETED) {
               return;
           }
           var eventId = UUID.randomUUID();
           var correlation = CorrelationId.fromRequestIdOrElse(requestCorrelation.currentRequestId().orElse(null), eventId);
           var event = new ProfileUpdated(eventId, profile, clock.instant().truncatedTo(ChronoUnit.MILLIS), correlation);
           relay.relayAfterCommit(outbox.appendProfileUpdated(event));
       }
   }
   ```
2. **Modificar** `ProfileAppService`: quinto parámetro del constructor `ProfileEventsAppService events` (campo `private final`, Javadoc
   «Anuncia los cambios de un perfil activo; se llama en la misma transacción que el cambio.») y
   ```java
   /** Guarda el perfil y, si está activo, anuncia el cambio en la misma transacción; toda escritura que Entrevista debe ver pasa por aquí. */
   private void saveAndAnnounce(ProfessionalProfile profile) {
       repository.save(profile);
       events.announceIfCompleted(profile);
   }
   ```
   Reemplazar `repository.save(...)` por `saveAndAnnounce(...)` en: `updateProfileInfo` (l. 101), `addWorkExperience` (138),
   `removeWorkExperience` (178), `addEducation` (188), `removeEducation` (200), `addSkill` (226), `removeSkill` (232), `addTargetRole` (253),
   `updateTargetRole` (271), `removeTargetRole` (276), `completeProfile` (283). **No** en `updateSalaryExpectation` (207): agregar el comentario
   «La expectativa salarial no viaja en el evento del perfil: guardar basta.»
3. **Pruebas** `test/application/service/ProfileEventsAppServiceTest.java` (Mockito: `OutboxRepository`, `OutboxRelayAppService`,
   `RequestCorrelation`; reloj fijo `Clock.fixed(Instant.parse("2026-10-15T12:00:00.123456Z"), ZoneOffset.UTC)`; perfil activo como
   `ProfileAppServiceTest.completeProfile()` más `complete()`):
   - `announceIfCompleted_shouldAppendAndRelay_whenProfileIsCompleted`: `appendProfileUpdated` devuelve un `OutboundEvent` fijo →
     `verify(relay).relayAfterCommit(eseEvento)`; el `ProfileUpdated` capturado tiene el mismo perfil.
   - `announceIfCompleted_shouldDoNothing_whenProfileIsNotCompleted` (`@EnumSource(value = ProfileStatus.class, names = "COMPLETED", mode = EXCLUDE)`,
     perfil reconstituido en ese estado) → `verifyNoInteractions(outbox, relay)`.
   - `announceIfCompleted_shouldUseRequestId_whenItIsValid` (`Optional.of("req-123")` → correlación `req-123`).
   - `announceIfCompleted_shouldUseEventId_whenRequestIdIsMissing` (`Optional.empty()` → correlación = `eventId().toString()`).
   - `announceIfCompleted_shouldTruncateToMilliseconds_whenClockHasMicros` → `updatedAt` = `2026-10-15T12:00:00.123Z`.
   - `announceIfCompleted_shouldPropagate_whenOutboxFails` (`appendProfileUpdated` lanza `new IllegalStateException("x")` → se propaga;
     `relay` sin llamadas).
   En `ProfileAppServiceTest`: agregar `@Mock ProfileEventsAppService events;` y pasarlo en las dos construcciones (l. 88 y 158); luego
   - `writes_shouldAnnounceAfterSave_whenWriteSucceeds` (`@ParameterizedTest(name = "{0}")` con `@MethodSource("successfulWrites")`): un
     perfil `IN_PROGRESS` de `SVC_UID` con nombre «Analista de datos», resumen «Analista con tres años de experiencia en SQL y Python.», una
     experiencia (`new WorkExperienceDraft("ACME", "Analista", null, YearMonth.of(2022, 1), null, EmploymentStatus.CURRENT)` con
     `new DateBounds(YearMonth.of(2026, 10), YearMonth.of(1990, 1))`), una formación (como `completeProfile()`), una habilidad «Java»
     `ADVANCED` y un rol (`professionalRoleId` `a0000001-0000-0000-0000-000000000002`); `roleRepository.findById` devuelve
     `new ProfessionalRole(UUID.fromString("a0000001-0000-0000-0000-000000000003"), "Data Engineer", "Datos", true)`. Las once llamadas (los
     ids de los `remove…` se leen del perfil): `updateProfileInfo` (nombre «Analista senior»), `addWorkExperience` («Globex», «QA», sin
     descripción, `"2023-01"`, sin fin, `"CURRENT"`), `removeWorkExperience`, `addEducation` («Unicauca», «Maestría», `"POSTGRADUATE"`,
     `"2024-01"`, sin fin, `true`), `removeEducation`, `addSkill` («SQL», `"BASIC"`), `removeSkill`, `addTargetRole`, `updateTargetRole`,
     `removeTargetRole`, `completeProfile`. Cada una: `InOrder` → `repository.save(perfil)` y luego `events.announceIfCompleted(perfil)`, una vez.
   - `updateSalaryExpectation_shouldNotAnnounce_whenSaved` (`new BigDecimal("3500000")` → `verify(events, never()).announceIfCompleted(any())`).
   - `writes_shouldNotAnnounce_whenWriteIsRejected` (parametrizada: `addSkill` con `skillName` `"   "`; `completeProfile` de `freshProfile()`;
     `addSkill` sobre un perfil de `"otro-uid"`) → lanza y `verify(events, never())…`.
4. **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfileEventsAppServiceTest,ProfileAppServiceTest,ArquitecturaTest" test`.
   **Ensayo en seco:** con `saveAndAnnounce` aún sin llamar a `events`, `writes_shouldAnnounceAfterSave_whenWriteSucceeds` falla en las once filas.
5. **Trampas.** (a) `WorkExperienceDraft` recibe `(company, position, description, startDate, endDate, employmentStatus)` —el orden de
   `ProfileAppService.java:133`—. (b) `addWorkExperience` con fechas lee la réplica: `SVC_UID` ya tiene 1990 en `setUp`. (c) Si alguna de las
   once falla por una regla de dominio que no aparece aquí (por ejemplo un mínimo en Borrador), **detente** y reporta la regla.

## [x] T-5 · Topología y prueba con RabbitMQ real — ≤ 30 min, ≈ 300 líneas

**Requisitos:** REQ-EV-01 a 08, REQ-EV-07 (D-16). **Commit:** `CM-67 | feat(perfil): cola de Entrevista ligada a perfil.events y prueba con RabbitMQ real`.

1. **Modificar** `RabbitConfig` (solo si D-16 = A):
   ```java
   /** Clave de enrutamiento de los cambios del Perfil Profesional activo. */
   public static final String PROFILE_UPDATED_KEY = "perfil.profesional-actualizado";
   /** Cola donde Entrevista recibe los cambios del perfil. */
   public static final String INTERVIEW_PROFILE_QUEUE = "entrevista.perfil-profesional-actualizado";
   /** Exchange de fallidos de Entrevista; lo declara Entrevista junto con su cola de fallidos. */
   public static final String INTERVIEW_DEAD_LETTER_EXCHANGE = "entrevista.dlx";

   /**
    * Cola de Entrevista para los cambios del perfil, declarada también aquí con los mismos argumentos que usa Entrevista.
    *
    * <p>Así ningún cambio se pierde ni queda devuelto mientras Entrevista no esté desplegada: los mensajes esperan en la
    * cola. Si los argumentos no coinciden, el broker rechaza la declaración de quien llegue segundo.</p>
    *
    * @return cola durable cuyos rechazos van a {@value #INTERVIEW_DEAD_LETTER_EXCHANGE}
    */
   @Bean
   Queue interviewProfileQueue() {
       return QueueBuilder.durable(INTERVIEW_PROFILE_QUEUE).deadLetterExchange(INTERVIEW_DEAD_LETTER_EXCHANGE)
               .deadLetterRoutingKey(INTERVIEW_PROFILE_QUEUE).build();
   }

   /** @return enlace de los cambios del perfil a la cola de Entrevista */
   @Bean
   Binding interviewProfileBinding(TopicExchange perfilExchange, Queue interviewProfileQueue) {
       return BindingBuilder.bind(interviewProfileQueue).to(perfilExchange).with(PROFILE_UPDATED_KEY);
   }
   ```
   Cambiar en el Javadoc de la clase «la cola que los recibe la declara cada consumidor, no Perfil» por «la cola de Entrevista se declara
   también aquí, con los argumentos de Entrevista, para que ningún cambio quede sin destino». No tocar las colas de suscripción y consumo.
2. **Crear** `test/infrastructure/messaging/ProfileEventsEndToEndIT.java` (`extends MessagingIntegrationSupport`; `@Autowired`
   `ProfileCreationAppService`, `ProfileAppService`, `OutboxRelayAppService`, `Queue interviewProfileQueue`; usuarios `"uid-ev-" + UUID`):
   - `@BeforeEach`: `resetState()`, `jdbcTemplate.update("delete from evento_saliente")`, `rabbitAdmin.purgeQueue(INTERVIEW_PROFILE_QUEUE, false)`.
   - `@AfterEach`: `rabbitAdmin.initialize()` (vuelve a declarar el exchange si una prueba lo borró), borrar los perfiles `uid-ev-%`,
     `ReplicatedBirthDates.deleteByPrefix(jdbcTemplate, "uid-ev-")`, borrar disparadores creados.
   - Ayudante `String activeProfile(String uid)`: `ReplicatedBirthDates.insertAdult`; `createProfile(new CreateProfileCommand(uid))`;
     `updateProfileInfo` (nombre «Analista de datos», resumen «Analista con tres años de experiencia en SQL y Python.»); `addEducation`
     («Universidad del Cauca», «Ingeniería de Sistemas», `"UNDERGRADUATE"`, `"2017-02"`, `"2021-12"`, `false`); `addSkill` («Python»,
     `"INTERMEDIATE"`); `addTargetRole` (`a0000001-0000-0000-0000-000000000002`); no finaliza. Ayudante `Message next()` =
     `rabbitTemplate.receive(INTERVIEW_PROFILE_QUEUE, 5000)`; `int outboxRows(String profileId)`.
   - Pruebas (nombres de la spec §11): `topology_shouldRouteToInterviewQueue_whenServiceStarts` (argumentos del bean =
     `{x-dead-letter-exchange=entrevista.dlx, x-dead-letter-routing-key=entrevista.perfil-profesional-actualizado}` y un mensaje enviado a
     `perfil.events` con la clave llega a la cola); `completeProfile_shouldPublishSchemaPayload_whenProfileIsActivated` (un mensaje;
     propiedades `type`, `app_id` `cameia-perfil`, `x-event-version` 1, `content_type` `application/json`, `messageId` = `id` de la fila;
     cuerpo con `usuarioId` = uid, `contextoProfesional.id` = perfil, `estado` `COMPLETED`, `fecha_archivado` nulo, `experiencias` `[]`,
     `habilidades[0]` = `{"nombre":"Python","nivel":"INTERMEDIATE"}`, `rolesAspirados[0].codigo` = `a0000001-0000-0000-0000-000000000002`,
     sin `embedding`; la fila queda con `fecha_publicacion` y `carga` nula); `completeProfile_shouldPublishNothing_whenRequirementsAreMissing`
     (perfil recién creado → `IncompleteProfileException`, 0 filas, `next()` nulo con `receive(…, 1000)`);
     `completeProfile_shouldPublishOnlyOnce_whenRepeatedTwentyTimes` (1 éxito, 19 `ProfileAlreadyCompletedException`, 1 fila, 1 mensaje);
     `completeProfile_shouldPublishNothing_whenDatabaseFailsDuringCompletion` (disparador de `CompletionEndToEndIT` con sufijo aleatorio → la
     llamada lanza, 0 filas, ningún mensaje, estado `IN_PROGRESS`); `addSkill_shouldPublishOneEvent_whenProfileIsCompleted` (tras finalizar y
     vaciar la cola: «SQL» `BASIC` → un mensaje con dos habilidades y `actualizadoEn` no anterior al de la finalización);
     `addSkill_shouldPublishNothing_whenProfileIsDraft`; `addSkill_shouldPublishNothing_whenProfileBelongsToAnotherUser` (B sobre el activo de A →
     `ProfileAccessDeniedException`, 0 filas nuevas); `updateProfileInfo_shouldPublishTwoEvents_whenSameChangeIsSentTwice` (dos mensajes con
     el mismo `contextoProfesional` y `actualizadoEn` no decreciente); `completeAndRemoveSkill_shouldPublishOneEventWithSkills_whenBothRunAtOnce`
     (`@RepeatedTest(10)`, arranque simultáneo con `CountDownLatch` como `ProfileWriteLockIT`; si la finalización ganó: 1 fila y el mensaje
     con una habilidad; si ganó la eliminación: 0 filas; nunca un mensaje con `habilidades` vacío);
     `completeProfile_shouldKeepEventPending_whenBrokerRejectsAndRelayLater` (`rabbitAdmin.deleteExchange(RabbitConfig.EXCHANGE_PERFIL)` →
     `completeProfile` no lanza; fila con `intentos` 1 y `fecha_publicacion` nula; `rabbitAdmin.initialize()`; `relayPending()` →
     `published` = 1; un mensaje con `messageId` = id de la fila).
   - **Si D-16 = B:** la prueba declara su propia cola temporal ligada a `perfil.events` en `@BeforeEach` y la borra en `@AfterEach`;
     `topology_…` se reemplaza por `publish_shouldBeReturned_whenNoQueueIsBound` (sin la cola temporal → fila pendiente con `intentos` 1).
3. **Comandos:** `./mvnw.cmd -B "-Dit.test=ProfileEventsEndToEndIT,AccountEventsTopologyIT,BirthDateReplicationIT" "-Dtest=NoSuchTest" "-Dsurefire.failIfNoSpecifiedTests=false" verify`
   (las dos últimas comparten contexto y no deben romperse).
4. **Trampas.** (a) Borrar el exchange cierra el canal del publicador: el `@AfterEach` con `rabbitAdmin.initialize()` es obligatorio o las
   clases siguientes del mismo contexto fallan. (b) `receive` sin plazo devuelve `null` al instante: usa siempre el plazo. (c) Hay varios beans
   `TopicExchange` y `Queue`: el nombre del parámetro (`perfilExchange`, `interviewProfileQueue`) elige el bean, como en los enlaces existentes.

## [x] T-6 · CA-2.5.18: la base rechaza un resumen de 2001 — ≤ 15 min, ≈ 30 líneas

**Requisitos:** REQ-FN-18 (D-17 = B). **Commit:** `CM-67 | test(perfil): la base rechaza un resumen de 2001 caracteres`.

1. **Modificar** `test/infrastructure/persistence/ColumnLengthConsistencyIT.java`, agregar
   `summaryColumn_shouldReject2001Characters_whenWritten` (`@DisplayName` «La base acepta un resumen de 2000 caracteres y rechaza uno de 2001»):
   migra como `assertColumnLengths` y, con `DriverManager`,
   `INSERT INTO perfil_profesional (id, firebase_uid, resumen, creado_en, actualizado_en) VALUES (gen_random_uuid(), 'uid-col-2000', repeat('a', 2000), now(), now())`
   → 1 fila; la misma con `'uid-col-2001'` y `repeat('a', 2001)` → `SQLException` con `getSQLState()` = `"22001"`.
2. No se crea ninguna otra prueba: la regla de finalización ya la cubre `ProfessionalProfileTest.complete_shouldListSummary_whenStoredSummaryHas2001CodePoints`.
3. **Comandos:** `./mvnw.cmd -B "-Dit.test=ColumnLengthConsistencyIT" "-Dtest=NoSuchTest" "-Dsurefire.failIfNoSpecifiedTests=false" verify`.
4. **Trampa:** si el `INSERT` falla por otra columna `NOT NULL` que no está en la lista, agrégala con un valor válido y anótalo; el único
   SQLState esperado es `22001`.

## [x] T-7 · Carpeta de Postman del evento — ≤ 30 min, ≈ 150 líneas de colección

**Requisitos:** R11 (prueba por petición real). **Commit:** `CM-67 | test(perfil): carpeta de Postman del evento del perfil`.

- Agregar al final de `docs/CAMEIA_Perfil_Sprint1.postman_collection.json` la carpeta `HT-03 — Evento del perfil (local)` (v2.1, UTF-8). Editar
  con un script de Python escrito con la herramienta de escritura en `C:\Users\paanm\Documents\cameia\ejecucion\herramientas\ht03_postman.py`
  (idempotente: borra la carpeta si existe y la vuelve a crear), no a mano ni con heredoc. Variable de colección nueva `event_user`, `event_profile`,
  `event_updated_at`, `event_request_id`. Peticiones a RabbitMQ con `auth` básica `{{rabbitUser}}`/`{{rabbitPassword}}`, como `CM-279 — Réplica…`.
- Peticiones, en orden (cada una con `pm.test` de estado y de lo indicado):
  - `E-00 Vaciar la cola de Entrevista` — `DELETE {{rabbitManagementUrl}}/api/queues/%2F/entrevista.perfil-profesional-actualizado/contents` → 204.
  - `E-01 la cola de Entrevista existe y está ligada` — `GET …/api/queues/%2F/entrevista.perfil-profesional-actualizado` → 200,
    `arguments['x-dead-letter-exchange']` = `entrevista.dlx`; y `GET …/api/exchanges/%2F/perfil.events/bindings/source` → un enlace con
    `routing_key` `perfil.profesional-actualizado` (dos peticiones: `E-01a`, `E-01b`).
  - `E-02 Publicar cuenta.creada de la identidad del evento` (pre-request: `event_user` = `pm-ev-{{$timestamp}}`; copia la petición de
    `Preparación` cambiando el `usuarioId`) → `routed` `true`.
  - `E-03 201 crear perfil`, `E-04 200 nombre y resumen` (`{"name":"Analista de datos","summary":"Analista con tres años de experiencia en SQL y Python."}`),
    `E-05 201 formación`, `E-06 201 habilidad Python INTERMEDIATE` (luego `E-06b 201 rol a0000001-0000-0000-0000-000000000002`).
  - `E-07 ningún evento con el perfil en Borrador` — `POST …/api/queues/%2F/entrevista.perfil-profesional-actualizado/get` con
    `{"count":10,"ackmode":"ack_requeue_false","encoding":"auto"}` → `[]`.
  - `E-08 200 finalizar` con `X-Request-Id: pm-ev-{{$timestamp}}` (guardar en `event_request_id`).
  - `E-09 un evento con la carga del contrato` (misma petición `get`) → exactamente 1; `properties.type` `perfil.profesional-actualizado`;
    `properties.app_id` `cameia-perfil`; `properties.headers['x-event-version']` 1; `properties.correlation_id` = `event_request_id`;
    `properties.delivery_mode` 2; `JSON.parse(payload)`: `usuarioId` = `event_user`, `contextoProfesional.id` = `event_profile`, `estado`
    `COMPLETED`, `fecha_archivado` `null`, `habilidades` = `[{"nombre":"Python","nivel":"INTERMEDIATE"}]`, `rolesAspirados[0].codigo`
    `a0000001-0000-0000-0000-000000000002`, sin la clave `embedding`, `actualizadoEn` con `/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/`
    (guardar en `event_updated_at`).
  - `E-10 201 agregar SQL a un perfil activo` con `X-Request-Id: <script>alert(1)</script>` y cuerpo
    `{"skillName":"SQL","level":"BASIC","usuarioId":"otro","embedding":[1]}`.
  - `E-11 un evento con dos habilidades` → 1 mensaje; `correlation_id` = `message_id`; `usuarioId` = `event_user`; 2 habilidades; sin
    `embedding`; `actualizadoEn` ≥ `event_updated_at`.
  - `E-12 409 java repetida` (`{"skillName":"python","level":"BASIC"}` → 409 `SKILL_ALREADY_EXISTS`) y `E-13 ningún evento tras el 409` → `[]`.
  - `E-14 409 finalizar otra vez` → 409 `PROFILE_ALREADY_COMPLETED`; `E-15 ningún evento tras el 409` → `[]`.
  - `E-16 200 expectativa salarial` (`PATCH …/salary-expectation` con `{"amount":3500000}`; si el contrato del DTO pide otro nombre, usa el
    de `UpdateSalaryExpectationRequest`) y `E-17 la expectativa salarial no publica` → `[]`.
- Agregar a `README.md` (sección «Pruebas de API con Newman») una línea: la carpeta `HT-03 — Evento del perfil (local)` necesita RabbitMQ con
  la API de administración (compose) y el entorno local.
- **Comandos:** `docker compose up -d --build`; `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json -e docs/perfil-local.postman_environment.json --folder "HT-03 — Evento del perfil (local)" --reporters cli`.
- **Si D-16 = B:** la carpeta no se crea (no hay cola que leer); se deja escrito en el README.

## [x] T-8 · Documentación — ≤ 25 min, ≈ 120 líneas

**Requisitos:** R8, A-6, A-7, A-8, A-10. **Commit:** `CM-67 | docs(perfil): ADR del evento, catálogo, CLAUDE.md y bitácora`.

- **Crear** `docs/adr/0006-evento-perfil-profesional-actualizado.md` (formato de los ADR 0004 y 0005): contexto (HT-03; Entrevista no llama a
  Perfil), decisión (tabla de salida en la misma transacción, intento tras el commit, Job; carga v1 sin `embedding`; Perfil declara la cola de
  Entrevista si D-16 = A; correlación con `X-Request-Id`), consecuencias (al menos una vez; Entrevista declara la cola con los mismos
  argumentos; cambiar la carga exige v2) y alternativas descartadas (spec §6 y D-16).
- **`docs/errores.md`:** la cita `09102026_01_Backlog_v6.xlsx` pasa a `12102026_01_Backlog_v7.xlsx`. Sin filas nuevas.
- **`CLAUDE.md`** del repo: §2, fila de mensajería → `RabbitConfig`, `RabbitEventPublisher`, `OutboxRelayAppService`,
  `ProfileEventsAppService`, `ProfileEventsRelayJobRunner`: «Publican `perfil.profesional-actualizado` con tabla de salida»; y fila aparte para
  `SubscriptionUpdatedListener` y `ConsumptionRecordedListener` (esqueletos); §4, quitar la fila de `review-requests` (la ruta ya no existe);
  §5, filas de V6 a V9 (`V6__alinear_experiencia_laboral.sql`, `V7__alinear_educacion.sql`, `V8__alinear_habilidad_perfil.sql`,
  `V9__evento_saliente.sql`, una frase cada una tomada del comentario de cabecera del archivo); §9, «no pasa de 1000 líneas…» → «Un PR por tarea
  de Jira, sin tope de líneas, con un commit por capacidad»; §10, quitar «el publicador de eventos del perfil» de la fila de esqueletos y agregar
  la fila «El Job `profile-events-relay` no está programado en staging | DevOps, por una tarea de Vela | Reintentar en staging lo que falle tras el commit».
- **Bitácora:** copiar `C:\Users\paanm\Documents\cameia\prompts\entradas\13_provenance-ignorado-en-el-patch-de-informacion-general_prompt.md` a
  `docs/bitacora-ia/02_provenance-ignorado-en-el-patch-de-informacion-general_prompt.md` (sin cambios de contenido salvo quitar rutas a carpetas
  fuera del repo, si las hay) y agregar su fila al `README.md` de la bitácora: «Ignorar `provenance` en el `PATCH` de información general» →
  salida `../../specs/CM-54-informacion-general-resumen/spec.md` (decisión D5). Si Paula marca D-16 o D-17 como decisión con peso, su entrada
  es la `03_…`; si no, no se agrega.
- **`specs/CM-67-finalizacion-perfil-v4/tasks.md`:** reemplazar la línea «**BLOQUEADO** hasta dos respuestas…» del PR 3 por «Se ejecuta con la
  spec `specs/CM-67-EventoPerfilActualizado/`.» (sin tocar lo demás).
- Agregar esta carpeta de spec (`spec.md`, `plan.md`, `tasks.md`, `evidencia-antes.md`) al commit.

## [x] T-9 · Cierre sobre la punta — ≤ 40 min

1. `./mvnw.cmd -B clean verify` → pegar el resumen de Surefire y Failsafe (pruebas, fallos, errores) y `BUILD SUCCESS`.
2. Cobertura con el comando de la spec §18: cada clase listada ≥ 90 % de líneas y de ramas; global ≥ 97,4 % y ≥ 98,3 %. Cada línea o rama
   sin cubrir, con su razón.
3. `docker compose up -d --build`; Newman completo (spec §18) → 0 fallos; pegar el resumen.
4. DES-02: `--folder "DES-02 — HU-2.5 finalización" -n 105` y el script del README → p95 de cada petición ≤ 2000 ms.
5. V-17 (registros), V-18 (`pom.xml`), V-19 (`CM-NNN` en comentarios) y V-20 (Job local) de la spec §21.
6. Revisión: `/simplify`, `/code-review high`, `pr-review-toolkit` (`silent-failure-hunter`, `pr-test-analyzer`) si está; cada hallazgo
   corregido o descartado con razón.
7. Llenar «Resultado real» de la tabla de atributos (spec §13) y la lista de verificación (spec §21) con la salida real.
8. Detenerse: el push y el PR se piden a Paula (con rama, build, pruebas, cobertura y archivos a revisar). PR en Draft con `[IA-ASISTIDO]` al
   final del título y el cuerpo validado con `.github/scripts/pr_policy.py` antes de crearlo.

**Definición de terminado:** todas las tarjetas con su salida real; `clean verify` en verde; cobertura cumplida; Newman y DES-02 en verde;
`ArquitecturaTest` en verde; lista de verificación completa.

## Resultado real de la ejecución (15-oct-2026)

| Tarjeta | Commit | Salida real |
|---|---|---|
| T-0 | — | Árbol de `develop` distinto al de la spec (cambió un archivo de CI): `clean verify` de base con 846 unitarias y 270 de integración, 0 fallos, y Newman de base con 605 peticiones y 2801 aserciones, 0 fallos. Evidencia previa en `evidencia-antes.md` |
| T-1 | `6d44229` | Rojo por compilación y luego verde en `CorrelationIdTest`, `ProfileUpdatedTest`, `ProfileUpdatedPayloadV1Test` y `ProfileUpdatedEventContractTest`. La prueba de nulos usa una formación con inicio y sin fin, porque el dominio exige el inicio |
| T-2 | `ad81c8c` | `OutboxRepositoryAdapterIT`: 17 pruebas, 0 fallos |
| T-3 | `9d125b4` | `HttpRequestCorrelationTest` y `ArquitecturaTest` en verde |
| T-4 | `d521a41` | Ensayo en seco: fallan las 11 filas de `writes_shouldAnnounceAfterSave_whenWriteSucceeds`; con la línea real, verde |
| T-5 | `3fbca5c` | `ProfileEventsEndToEndIT` (20, luego 22), `AccountEventsTopologyIT` y `BirthDateReplicationIT`: 54 pruebas, 0 fallos |
| T-6 | `3f29df8` | `ColumnLengthConsistencyIT`: 6 pruebas, SQLState `22001` con 2001 |
| T-7 | `9274577` | Carpeta `HT-03 — Evento del perfil (local)`: 57 aserciones, 0 fallos |
| T-8 | `88e29ca` | ADR 0006, catálogo, `CLAUDE.md`, bitácora y carpeta de la spec |
| T-9 | `f9ab13f`, `2163a31`, `ee03fee` | `clean verify` sobre la punta en verde (902 unitarias y 295 de integración); cobertura de las clases tocadas ≥ 90 %; Newman completo de 625 peticiones y 2858 aserciones, 0 fallos; DES-02 con p95 ≤ 42 ms; V-17 a V-20 cumplidos; revisiones ejecutadas y registradas en el resumen de evidencia |
