# Tareas — CM-271 (cameia-perfil)

Estado: sin ejecutar; spec y plan pendientes de aprobación de Paula. Se marca `[x]` en el momento en que cada tarjeta termina, con la salida real de las pruebas (regla del repositorio).

## Reglas para todas las tarjetas (el modelo que ejecuta no lee la spec ni el plan)

- Paquete base: `src/main/java/co/edu/unicauca/cameia/perfil/`; pruebas: `src/test/java/co/edu/unicauca/cameia/perfil/`. Código en inglés; Javadoc, comentarios, mensajes y logs en español; **sin** `CM-NNN` ni rutas a otros archivos en comentarios; sin `TODO`.
- Límites del repositorio: métodos ≤ 20 líneas, ≤ 3 parámetros, ≤ 2 niveles de anidamiento, clase ≤ 200 líneas; `@Transactional` solo en `application.service` (y en el adaptador de persistencia, como ya está); `domain` no importa Spring, JPA ni nada de otras capas (`ArquitecturaTest`).
- Pruebas: clase `<Clase>Test` (unidad) o `<Clase>IT` (integración); método `<metodo>_should<Resultado>_when<Condicion>`; `@DisplayName` en español. Las clases existentes conservan su estilo de nombres.
- Prohibido: agregar dependencias (salvo la tarjeta T-0.1), tocar migraciones, registrar nombres, resúmenes o correos, cambiar un texto que la tarjeta no nombre, refactorizar fuera de la tarjeta.
- Todo código de error nuevo, o cuyo estado o texto cambia, se agrega o actualiza en `docs/errores.md` en el mismo PR, con las columnas `Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba` (una fila por código; «Endpoints» lista cada método y ruta que lo emite). Si `docs/errores.md` aún no existe en `develop`, el PR lo dice y la pieza B de CM-283 lo recoge (su verificación V-12 compara el catálogo con el código).
- Comandos: `./mvnw.cmd -q -B -Dtest=<Clase> test`; suite: `./mvnw.cmd -B test`; al cerrar: `./mvnw.cmd -B clean verify` (JaCoCo en `target/site/jacoco/`). Las `*IT` necesitan PostgreSQL: `docker compose run --rm verify` o Testcontainers si existe T-0.1.
- **Detenerse y reportar** si la tarjeta contradice el código real, falta un dato, una prueba existente se rompe sin causa clara o hace falta algo no listado.
- Terminado: pruebas nuevas en verde, suite completa en verde, `ArquitecturaTest` en verde, diff dentro de lo estimado.
- Commit: `CM-271 | <tipo>(perfil): <resultado> [IA-ASISTIDO]`.

---

## PR 0 (aprobado por Paula el 6-oct-2026, pregunta 1) — Testcontainers en Perfil

Rama `CM-271-testcontainers-perfil` desde `develop`.

## [ ] T-0.1 · PostgreSQL de prueba con Testcontainers — ≤ 30 min, ≈ 120 líneas

- **Modificar:** `pom.xml` (dependencias de prueba `org.springframework.boot:spring-boot-testcontainers` y `org.testcontainers:postgresql`, **sin versión**: las gestiona `spring-boot-starter-parent`; si el parent no las gestiona, detenerse). **Crear:** `src/test/java/co/edu/unicauca/cameia/perfil/infrastructure/persistence/PostgresTestConfiguration.java`:
  ```java
  /** Base de datos PostgreSQL real para las pruebas de integración, levantada por Testcontainers. */
  @TestConfiguration(proxyBeanMethods = false)
  public class PostgresTestConfiguration {
      /** @return contenedor de PostgreSQL con la misma versión mayor que usa docker-compose */
      @Bean
      @ServiceConnection
      PostgreSQLContainer<?> postgres() {
          return new PostgreSQLContainer<>("postgres:<versión de docker-compose.yml>");
      }
  }
  ```
  Leer la imagen exacta de `docker-compose.yml` (servicio de base de datos) y usarla. **Modificar:** `ProfessionalProfileRepositoryAdapterIT` → `@Import(PostgresTestConfiguration.class)` y quitar del Javadoc la frase «No usa H2 ni Testcontainers».
- **Verificación:** con Docker, `./mvnw.cmd -B -Dtest=ProfessionalProfileRepositoryAdapterIT test` en verde **sin** `docker compose up` (si Surefire no ejecuta `*IT`, ejecutarla con `-Dtest` explícito, que sí funciona). PR: `CM-271 | test(perfil): PostgreSQL real con Testcontainers en las pruebas de integración [IA-ASISTIDO]`.

---

## PR A — formato de error con código

Rama `CM-271-formato-error-perfil` desde `develop`.

## [ ] T-A.1 · Catálogo y excepción base — ≤ 30 min, ≈ 150 líneas

- **Cubre:** REQ-PE-01, 08. **Crear:** `domain/exception/ErrorCode.java`, `domain/exception/BusinessException.java`. **Modificar:** las 11 excepciones de `domain/exception/`.
- **`ErrorCode`** (un valor por fila, cada uno con Javadoc de una línea que diga cuándo se usa): `VALIDATION_FAILED`, `REQUEST_BODY_INVALID_FORMAT`, `REQUEST_INVALID_VALUE`, `IDENTITY_REQUIRED`, `PROFILE_NOT_FOUND`, `PROFILE_NOT_ALLOWED`, `PROFILE_LIMIT_REACHED`, `PROFILE_ALREADY_COMPLETED`, `PROFILE_INCOMPLETE`, `PROFESSIONAL_ROLE_NOT_FOUND`, `TARGET_ROLE_LIMIT_REACHED`, `TARGET_ROLE_ALREADY_EXISTS`, `TARGET_ROLE_NOT_ALLOWED`, `SKILL_ALREADY_EXISTS`, `INTERNAL_ERROR`, y los de campo `COMPANY_REQUIRED`, `POSITION_REQUIRED`, `START_DATE_REQUIRED`, `EMPLOYMENT_STATUS_REQUIRED`, `PROVENANCE_REQUIRED`, `SKILL_NAME_REQUIRED`, `SKILL_LEVEL_REQUIRED`, `PROFESSIONAL_ROLE_ID_REQUIRED`.
- **`BusinessException`:**
  ```java
  /**
   * Raíz de las excepciones de negocio de Perfil.
   *
   * <p>Cada una lleva un código estable que el cliente puede usar para decidir qué mostrar.
   * El estado HTTP lo decide la capa de presentación a partir del código, para que el dominio
   * no dependa de la web.</p>
   */
  public abstract class BusinessException extends RuntimeException {
      private final ErrorCode code;
      /** @param code código estable del error @param message mensaje para la persona, en español */
      protected BusinessException(ErrorCode code, String message) { super(message); this.code = code; }
      /** @return código estable del error */
      public ErrorCode getCode() { return code; }
  }
  ```
- **Excepciones:** cambiar `extends RuntimeException` por `extends BusinessException` y `super(mensaje)` por `super(ErrorCode.X, mensaje)` con: `ProfileNotFoundException`→`PROFILE_NOT_FOUND`, `ProfileAccessDeniedException`→`PROFILE_NOT_ALLOWED`, `ProfileAlreadyExistsException`→`PROFILE_LIMIT_REACHED` (texto sin cambiar todavía), `ProfileAlreadyCompletedException`→`PROFILE_ALREADY_COMPLETED`, `IncompleteProfileException`→`PROFILE_INCOMPLETE`, `ProfessionalRoleNotFoundException`→`PROFESSIONAL_ROLE_NOT_FOUND`, `MaxTargetRolesExceededException`→`TARGET_ROLE_LIMIT_REACHED`, `DuplicateTargetRoleException`→`TARGET_ROLE_ALREADY_EXISTS`, `LastTargetRoleException`→`TARGET_ROLE_NOT_ALLOWED`, `DuplicateSkillException`→`SKILL_ALREADY_EXISTS`, `IdentityRequiredException`→`IDENTITY_REQUIRED`. Ningún texto cambia.
- **Prueba** (`domain/exception/ErrorCodeTest`, sin Spring): todo valor cumple `^[A-Z]+(_[A-Z]+)+$`; no hay dos valores iguales (trivial, pero fija la forma).
- **Verificación:** `./mvnw.cmd -B test` en verde (ninguna prueba existente debería cambiar).

## [ ] T-A.2 · Manejador: negocio, validación, cuerpo ilegible y 500 — ≤ 30 min, ≈ 150 líneas

- **Cubre:** REQ-PE-01, 03 a 07, 09. **Modificar:** `presentation/advice/ApiExceptionHandler.java`.
- **Estructura:**
  - `private static final Map<ErrorCode, HttpStatus> STATUS` con: `PROFILE_NOT_FOUND`→404, `PROFILE_NOT_ALLOWED`→403, `PROFILE_LIMIT_REACHED`→409, `PROFILE_ALREADY_COMPLETED`→409, `PROFILE_INCOMPLETE`→422, `PROFESSIONAL_ROLE_NOT_FOUND`→404, `TARGET_ROLE_LIMIT_REACHED`→422, `TARGET_ROLE_ALREADY_EXISTS`→409, `TARGET_ROLE_NOT_ALLOWED`→422, `SKILL_ALREADY_EXISTS`→409, `IDENTITY_REQUIRED`→401.
  - `private static final Map<String, ErrorCode> FIELD_CODES` con las ocho claves de la tabla «Códigos de campo» de la spec (`AddWorkExperienceRequest.company.NotBlank` → `COMPANY_REQUIRED`, …) y `private static final Map<ErrorCode, String> FIELD_MESSAGES` con su texto literal («Ingresa la empresa.», «Ingresa el cargo.», «Ingresa la fecha de inicio.», «Selecciona una opción.», «Ingresa una habilidad.», «Elige un nivel.»).
  - `@ExceptionHandler(BusinessException.class)` → `ProblemDetail` con el estado del mapa, `title` = el de hoy para esa excepción (copiar los títulos actuales a un `Map<ErrorCode, String> TITLES`), `detail` = `ex.getMessage()`, propiedad `code`. **Excepción:** `IncompleteProfileException` conserva su `ResponseEntity<CompletionErrorResponse>` actual (método propio, más específico, se queda) y agrega el encabezado `X-Request-Id`; su cuerpo lo cambia CM-67.
  - `handleMethodArgumentNotValid` → 422, `title` «Datos no válidos», `detail` «Revisa los campos marcados.», `code` `VALIDATION_FAILED`, `errors` = un elemento por campo (si un campo trae dos errores, solo el primero) con `field`, `code` y `message` de los mapas. La clave se arma como `fieldError.getObjectName()` en formato de clase: usar `ex.getBindingResult().getTarget().getClass().getSimpleName() + "." + fe.getField() + "." + fe.getCode()`.
  - `handleHttpMessageNotReadable` (sobrescribir el de `ResponseEntityExceptionHandler`) → 422, `REQUEST_BODY_INVALID_FORMAT`, «Revisa el formato de los datos enviados.».
  - `handleMissingRequestHeader` (sobrescribir `handleServletRequestBindingException` o `handleMissingRequestHeaderException`, el que exista en la versión) → 400, `IDENTITY_REQUIRED`, «Identidad del usuario requerida».
  - `@ExceptionHandler(IllegalArgumentException.class)` → 422, `REQUEST_INVALID_VALUE`, «Revisa los datos enviados.»; **nunca** `ex.getMessage()`. Log `WARN` «Valor rechazado sin campo: code=REQUEST_INVALID_VALUE requestId={} origen={}» con `clase.método` de `ex.getStackTrace()[0]`.
  - `@ExceptionHandler(Exception.class)` → 500, `INTERNAL_ERROR`, «Ocurrió un error. Inténtalo de nuevo.»; log `ERROR` con la traza.
- **Trampa:** los métodos sobrescritos de `ResponseEntityExceptionHandler` devuelven `ResponseEntity<Object>`; agregar `code` y `requestId` con `problem.setProperty(...)`. El `requestId` lo agrega T-A.3: en esta tarjeta dejar un método `private ProblemDetail withCode(ProblemDetail p, ErrorCode code)`.
- **Log:** negocio y validación `WARN` «Petición rechazada: code={} requestId={}», sin traza; 500 `ERROR` «Fallo no controlado: requestId={}» con la excepción.
- **Verificación:** `./mvnw.cmd -q -B -Dtest=ProfileControllerTest test`. Fallarán las afirmaciones de 400 por validación: es lo esperado; T-A.4 las ajusta.

## [ ] T-A.3 · `requestId` en el cuerpo y en el encabezado — ≤ 30 min, ≈ 60 líneas

- **Cubre:** REQ-PE-02. **Modificar:** `ApiExceptionHandler`.
- **Código:** cada método del manejador recibe `HttpServletRequest` (o `WebRequest` en los sobrescritos) y llama a:
  ```java
  /** Devuelve el identificador de la petición: el recibido si es válido, o uno nuevo. */
  private static String requestId(String recibido) {
      return recibido != null && recibido.matches("^[A-Za-z0-9._-]{1,64}$")
              ? recibido : UUID.randomUUID().toString();
  }
  ```
  Se pone en la propiedad `requestId` del `ProblemDetail` y en el encabezado `X-Request-Id` de la respuesta (los métodos que hoy devuelven `ProblemDetail` pasan a `ResponseEntity<ProblemDetail>` para poder fijar el encabezado). La constante del patrón va como `private static final Pattern`.
- **Verificación:** `./mvnw.cmd -q -B -Dtest=ProfileControllerTest test`.

## [ ] T-A.4 · Pruebas del manejador y ajuste del controlador — ≤ 30 min, ≈ 150 líneas

- **Crear** `presentation/advice/ApiExceptionHandlerTest.java`: `MockMvc` `standaloneSetup` con un `@RestController` de prueba interno que lanza lo que cada prueba necesita, y el manejador real.
  1. `businessException_shouldReturnItsStatusAndCode_whenThrown` (`@ParameterizedTest` sobre `ErrorCode` presentes en `STATUS`): cada una de las 11 excepciones → su estado y su `$.code`.
  2. `everyErrorCode_shouldHaveStatusOrBeHandledElsewhere`: recorre `ErrorCode.values()`; los que no están en `STATUS` deben ser exactamente `VALIDATION_FAILED`, `REQUEST_BODY_INVALID_FORMAT`, `REQUEST_INVALID_VALUE`, `INTERNAL_ERROR` y los ocho de campo.
  3. `requestId_shouldEchoValidHeader_whenPresent`: `X-Request-Id: abc-123` → `$.requestId` y encabezado `abc-123`.
  4. `requestId_shouldBeGenerated_whenHeaderInvalidOrMissing` (`<script>`, `"a".repeat(65)`, ausente) → UUID v4 (`[0-9a-f-]{36}`) distinto del enviado.
  5. `illegalArgument_shouldHideMessage_whenThrown`: mensaje `co.edu.unicauca.cameia.perfil.domain.model.EmploymentStatus.FREELANCE` → 422, `$.code` `REQUEST_INVALID_VALUE`, `$.detail` sin `co.edu`; el log capturado contiene `origen=` y el `requestId` de la respuesta.
  6. `unexpectedException_shouldReturnGeneric500_whenThrown`: `RuntimeException("SELECT * FROM secreto")` → 500, `INTERNAL_ERROR`, `$.detail` = «Ocurrió un error. Inténtalo de nuevo.».
  7. `everyFieldConstraint_shouldHaveCode`: recorre los DTO del paquete `presentation.dto` con reflexión, encuentra cada anotación `@NotBlank`/`@NotNull`/`@Size` y comprueba que su clave está en `FIELD_CODES` (exponer el mapa con visibilidad de paquete para la prueba). REQ-PE-03.
- **Modificar** `ProfileControllerTest`: las pruebas que hoy esperan 400 por validación pasan a esperar 422, `$.code` `VALIDATION_FAILED` y `$.errors[0].field`/`code`/`message`; agregar `patchProfile_shouldReturn422_whenBodyIsMalformed` (cuerpo `{"name":`) → `REQUEST_BODY_INVALID_FORMAT`. Las afirmaciones de `title` siguen igual.
- **Verificación:** `./mvnw.cmd -B test` completo en verde.

## [ ] T-A.5 · OpenAPI, cobertura y entrega del PR A — ≤ 30 min

- OpenAPI de `POST /api/v1/profiles`: `@ApiResponse` 400 (`IDENTITY_REQUIRED`), 409 (`PROFILE_LIMIT_REACHED`, ejemplo con `code` y `requestId`) y 500. En las demás rutas, agregar la respuesta 422 donde hoy dice 400 de validación.
- `./mvnw.cmd -B clean verify`; cobertura de `ApiExceptionHandler`, `ErrorCode`, `BusinessException` ≥ 90 % de líneas y ramas; cada rama sin cubrir con su razón. `/code-review high`, `/security-review` (no se filtran mensajes). Autochequeo: `git grep -n "getMessage()" src/main/java/.../presentation` solo en el manejador de negocio.
- Si existen `docs/errores.md` o el ADR del `code` (los crea CM-283 Parte 1): agregar los códigos y quitar «Perfil aún no emite `code`»; si no existen, decirlo en el PR.
- PR: `CM-271 | feat(perfil): código estable, requestId y 422 en todas las respuestas de error [IA-ASISTIDO]`. Atributos: compatibilidad de contrato (aditiva; 400→422 en validación), seguridad (sin mensajes de librería), observabilidad. Qué revisar: el manejador; mecánico: las excepciones y los mapas. Aviso a Frontend (documento existente): `code`, `requestId`, 422 en validación y cuerpo ilegible.

---

## PR B — cupo del Plan Free

Rama `CM-271-cupo-perfil-plan-free` desde `develop` **con el PR A fusionado**.

## [ ] T-B.1 · Excepción y puerto — ≤ 30 min, ≈ 80 líneas

- **Cubre:** REQ-PE-20, 24. **Renombrar** `domain/exception/ProfileAlreadyExistsException.java` → `ProfileLimitReachedException.java` (`git mv`):
  ```java
  /** El Usuario alcanzó el máximo de Perfiles Profesionales de su plan. */
  public class ProfileLimitReachedException extends BusinessException {
      /** Crea la excepción con el mensaje del Plan Free. */
      public ProfileLimitReachedException() {
          super(ErrorCode.PROFILE_LIMIT_REACHED, "Tu Plan Free permite 1 Perfil Profesional.");
      }
  }
  ```
  Actualizar sus usos (`ProfileAppService`, `ApiExceptionHandler` si la nombra, pruebas).
- **Puerto** `domain/port/ProfessionalProfileRepository.java`: quitar `existsByFirebaseUid`; agregar
  ```java
  /**
   * Cuenta los Perfiles Profesionales del Usuario.
   * @param firebaseUid dueño
   * @return cantidad de perfiles, en cualquier estado
   */
  long countByFirebaseUid(FirebaseUid firebaseUid);

  /**
   * Impide que otra transacción cree un perfil para el mismo Usuario hasta que termine la actual.
   * <p>Debe llamarse dentro de una transacción y antes de contar los perfiles.</p>
   * @param firebaseUid dueño
   */
  void lockCreationFor(FirebaseUid firebaseUid);
  ```
- **Verificación:** compila tras T-B.2.

## [ ] T-B.2 · Adaptador y servicio — ≤ 30 min, ≈ 90 líneas

- **Cubre:** REQ-PE-20, 21, 23. **Modificar:** `ProfessionalProfileJpaRepository` (quitar `existsByFirebaseUid`; agregar `long countByFirebaseUid(String firebaseUid);` y
  ```java
  @Query(value = "select pg_advisory_xact_lock(hashtextextended(:firebaseUid, 0))", nativeQuery = true)
  void lockCreation(@Param("firebaseUid") String firebaseUid);
  ```
  ), `ProfessionalProfileRepositoryAdapter` (implementa los dos métodos; `lockCreationFor` **sin** `@Transactional` propio: hereda la del servicio; `countByFirebaseUid` con `@Transactional(readOnly = true)` como los demás), `ProfileAppService.createProfile`:
  ```java
  /** Cantidad máxima de Perfiles Profesionales del Plan Free. */
  static final int FREE_PLAN_MAX_PROFILES = 1;

  @Transactional
  public ProfessionalProfile createProfile(CreateProfileCommand command) {
      var uid = new FirebaseUid(command.firebaseUid());
      // El bloqueo serializa las creaciones del mismo Usuario: el conteo y el guardado quedan juntos.
      repository.lockCreationFor(uid);
      if (repository.countByFirebaseUid(uid) >= FREE_PLAN_MAX_PROFILES) throw new ProfileLimitReachedException();
      var profile = ProfessionalProfile.create(uid);
      repository.save(profile);
      log.info("perfil creado id={}", profile.getId().value());
      return profile;
  }
  ```
- **Trampas:** (1) un `@Query` nativo que devuelve `void` en Spring Data puede exigir `@Modifying`; si falla con «query did not return a unique result» o similar, declarar el retorno como `Object` e ignorarlo, o usar `@Modifying` según el error real y anotarlo en el PR. (2) `pg_advisory_xact_lock` necesita una transacción abierta: la da el `@Transactional` del servicio.
- **Pruebas (`ProfileAppServiceTest`):** `createProfile_shouldLockBeforeCounting_whenCreating` (`InOrder`: `lockCreationFor` y luego `countByFirebaseUid`); `createProfile_shouldThrowLimitReached_whenUserAlreadyHasOne` (`count` = 1 → `ProfileLimitReachedException`, `save` nunca); `createProfile_shouldCreate_whenAnotherUserHasProfile` (`count` del uid B = 0). Ajustar las pruebas existentes que usaban `existsByFirebaseUid`.
- **Verificación:** `./mvnw.cmd -q -B -Dtest=ProfileAppServiceTest test`.

## [ ] T-B.3 · Concurrencia con base real — ≤ 30 min, ≈ 90 líneas — **la ejecución automática depende de la pregunta 1**

- **Cubre:** REQ-PE-21. **Crear** `infrastructure/persistence/ProfileCreationConcurrencyIT.java` (`@SpringBootTest`, `@Import(PostgresTestConfiguration.class)` si existe T-0.1; si no, la base de `docker-compose` como la `*IT` existente).
- **Prueba** `createProfile_shouldCreateOnlyOne_whenFiveRequestsArriveTogether`: `ExecutorService` de 5 hilos y un `CountDownLatch` de salida; se calcula **una vez** `String uid = "uid-concurrencia-" + UUID.randomUUID();` y los 5 hilos llaman a `profileAppService.createProfile(new CreateProfileCommand(uid))`; contar éxitos y `ProfileLimitReachedException`; esperar 1 éxito y 4 rechazos; `countByFirebaseUid` = 1. **Sin** `@Transactional` en la clase de prueba (cada hilo necesita su propia transacción); limpiar al final con `jdbcTemplate.update("delete from perfil_profesional where firebase_uid = ?", uid)`.
- **Verificación:** con Docker, `./mvnw.cmd -B -Dtest=ProfileCreationConcurrencyIT test` tres veces seguidas en verde; sin T-0.1, ejecutar con `docker compose run --rm verify` y pegar la salida en el PR.

## [ ] T-B.4 · Controlador, perfil vacío y entrega — ≤ 30 min, ≈ 60 líneas

- **Cubre:** REQ-PE-20, 22, 24. **Modificar** `ProfileControllerTest`: `postProfiles_returns409WhenProfileAlreadyExists` afirma además `$.code` `PROFILE_LIMIT_REACHED` y `$.detail` = «Tu Plan Free permite 1 Perfil Profesional.» y que el `detail` no contiene `TODO`; nueva `getProfile_shouldReturnEmptyDraft_whenJustCreated`: `ProfessionalProfile.create(...)` simulado → `$.status` `IN_PROGRESS`, `$.name` nulo, `$.summary` nulo y las cuatro listas vacías.
- `./mvnw.cmd -B clean verify`, cobertura de lo tocado ≥ 90 %, `/code-review high`. PR: `CM-271 | fix(perfil): cupo del Plan Free con su mensaje y sin perfiles duplicados por doble envío [IA-ASISTIDO]`. Atributos: fiabilidad (concurrencia), compatibilidad (texto del 409). Qué revisar: el servicio y la consulta del bloqueo. Tarjeta de Jira a «En revisión» al abrir el PR.
