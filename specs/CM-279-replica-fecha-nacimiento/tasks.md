# Tarjetas · CM-279 · Réplica de la fecha de nacimiento en Perfil

Repositorio `cameia-perfil`, worktree `C:\Users\paanm\Documents\cameia-worktrees\perfil-CM-279-replica`, rama
`CM-279-replica-fecha-nacimiento`, base `origin/develop` `2a54ca7`. Bloques P1 → P2 (un PR cada uno, apilados). El bloque 5 de
CM-274 se apila sobre P1. Las decisiones de diseño están respondidas (sección 14 de `spec.md`): `usuarioId` es el `firebaseUid`, cada
mensaje se anota en un Inbox (`evento_procesado`) y los rechazos de negocio con estado 5xx (el 503 de esta CM) se registran en `ERROR` sin traza, mientras los 4xx siguen en `WARN`.

## 0. Reglas para todas las tarjetas (léelas antes de cualquiera)

1. **Idioma (R1).** En inglés solo los identificadores (clases, métodos, variables, constantes) y los nombres de método de prueba
   (`method_shouldX_whenY`). En español todo lo demás: Javadoc, comentarios de línea y de bloque (también los de SQL y de `.yml`),
   `@DisplayName`, mensajes de log, mensajes de excepción, `@Schema` y documentos. Los nombres de tabla y columna y los de campo del JSON
   del contrato (`usuarioId`, `fechaNacimiento`) son los del contrato. El mensaje para la persona es el literal «Ocurrió un error.
   Inténtalo de nuevo.» (CA-2.4.36 y 2.4.52) y el título del catálogo, «Servicio no disponible». Los esqueletos de abajo ya traen el
   Javadoc y los mensajes en español: se copian tal cual.
2. **Pruebas.** Unitarias `XTest` (Surefire); con base de datos o broker `XIT` (Failsafe, corre en `verify`). Método
   `method_shouldX_whenY` en inglés y `@DisplayName` en español. Arrange-Act-Assert, valores literales, reloj fijo
   `Clock.fixed(Instant.parse("2026-10-09T15:04:05Z"), ZoneOffset.UTC)`, sin `Thread.sleep` (para esperar al consumidor se usa
   `Awaitility`, que ya está en el classpath de pruebas: `org.awaitility:awaitility:4.3.0`, traído por `spring-boot-starter-test`,
   comprobado con `.\mvnw.cmd dependency:tree -Dincludes=org.awaitility`; `await().atMost(Duration.ofSeconds(10))`). Toda prueba debe poder fallar.
3. **PostgreSQL en pruebas:** `@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");`
   como `PerfilApplicationIT`.
4. **RabbitMQ en pruebas** (sin dependencia nueva):
   ```java
   @Container
   static GenericContainer<?> rabbit = new GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
           .withExposedPorts(5672)
           .waitingFor(Wait.forLogMessage(".*Server startup complete.*", 1));

   @DynamicPropertySource
   static void rabbitProperties(DynamicPropertyRegistry registry) {
       registry.add("spring.rabbitmq.host", rabbit::getHost);
       registry.add("spring.rabbitmq.port", () -> rabbit.getMappedPort(5672));
       registry.add("spring.rabbitmq.username", () -> "guest");
       registry.add("spring.rabbitmq.password", () -> "guest");
   }
   ```
   Para publicar en la prueba: `rabbitTemplate.send("cuentas.events", "cuenta.creada", message)` con un `Message` construido a mano
   (`MessageBuilder.withBody(json.getBytes(UTF_8)).setContentType("application/json").setMessageId(...)`), nunca con
   `convertAndSend` de un objeto (probaría el convertidor de la prueba, no el contrato).
5. **Prohibido:** dependencias nuevas; tocar migraciones existentes, `.github/`, `SubscriptionUpdatedListener`,
   `ConsumptionRecordedListener`, `ProfileEventPublisher`; comentarios con `CM-279` o con rutas a otros archivos; registrar la fecha de
   nacimiento, el correo o el cuerpo de un mensaje; `System.out`; `catch` vacío.
6. **Arquitectura.** `domain` no importa Spring, JPA, Jackson ni AMQP (`ArquitecturaTest`). Servicios de aplicación terminan en
   `AppService`; controladores en `Controller`. `@Transactional` en el servicio de aplicación, que abre la transacción; los
   adaptadores de persistencia también llevan `@Transactional` en cada método (escritura) o `@Transactional(readOnly = true)` (lectura),
   como `ProfessionalProfileRepositoryAdapter`, y se unen a la del servicio.
7. **Javadoc en español** en toda clase, record, interfaz y método, con el porqué, `@param`, `@return`, `@throws`; `package-info.java` si el paquete
   es nuevo (ninguno lo es en esta CM: todos existen con `.gitkeep`).
8. **Comandos** (PowerShell, raíz del worktree): una clase `.\mvnw.cmd -q test -Dtest=XTest`; una IT
   `.\mvnw.cmd -q verify -Dit.test=XIT -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false`; cierre `.\mvnw.cmd clean verify`.
   Docker encendido.
9. **Detente y reporta** con la salida real si la tarjeta contradice el código, una clase o método de Spring no existe en la versión del
   repo, una prueba existente se rompe sin causa clara o falta algo.
10. **Terminado** = pruebas nuevas en verde, `clean verify` en verde, `ArquitecturaTest` en verde, salida pegada.

---

## Bloque P1 — réplica, Inbox, puertos y 503

### T-P1.1 · Migración y prueba de esquema

- **Antes:** `git fetch; git ls-tree --name-only origin/develop src/main/resources/db/migration/`. Se espera que la última sea
  `V4__i18n_catalogo_roles.sql`, así que esta migración es `V5`. Si `V5` ya existe en `develop`, usa el siguiente número libre y
  repórtalo (lo anota el PR).
- **Crear** `src/main/resources/db/migration/V5__replica_fecha_nacimiento.sql`:
  ```sql
  -- Réplica local de la fecha de nacimiento de cada Usuario, alimentada por el evento cuenta.creada del servicio de cuentas
  -- y borrada por cuenta.eliminada. Solo se guardan la identidad y la fecha: lo mínimo que necesitan las reglas de fechas de la
  -- experiencia laboral y la formación. Sin clave foránea: los datos son de otro servicio.
  CREATE TABLE fecha_nacimiento_usuario (
      firebase_uid     VARCHAR(128) NOT NULL,
      fecha_nacimiento DATE         NOT NULL,
      CONSTRAINT pk_fecha_nacimiento_usuario PRIMARY KEY (firebase_uid),
      CONSTRAINT ck_fecha_nacimiento_usuario_firebase_uid CHECK (btrim(firebase_uid) <> '')
  );
  COMMENT ON TABLE fecha_nacimiento_usuario IS 'Replica de la fecha de nacimiento por Usuario (evento cuenta.creada de Cuentas).';

  -- Inbox: cada evento de cuenta ya procesado, por identificador de mensaje, para que una reentrega no tenga efecto. No guarda
  -- datos personales y las filas se conservan sin plazo: unas dos por Usuario en toda la vida de la cuenta.
  CREATE TABLE evento_procesado (
      message_id   UUID        NOT NULL,
      tipo         VARCHAR(32) NOT NULL,
      procesado_en TIMESTAMPTZ NOT NULL,
      CONSTRAINT pk_evento_procesado PRIMARY KEY (message_id),
      CONSTRAINT ck_evento_procesado_tipo CHECK (tipo IN ('cuenta.creada', 'cuenta.eliminada'))
  );
  COMMENT ON TABLE evento_procesado IS 'Inbox de eventos de cuenta ya procesados (identificador del mensaje, sin datos personales).';
  ```
  (Comentarios y `COMMENT ON` en español, sin tildes en el SQL como en las migraciones existentes si estas no las usan.)
- **Crear** `src/test/java/co/edu/unicauca/cameia/perfil/infrastructure/persistence/BirthDateReplicaSchemaIT.java`
  (`@SpringBootTest`, PostgreSQL, `JdbcTemplate`):

  | Método | Acción | Espera |
  |---|---|---|
  | `insert_shouldSucceed_whenRowIsValid` | `('uid-1', DATE '2008-03-15')` | 1 fila |
  | `insert_shouldFail_whenUserRepeats` | dos veces `uid-1` | SQLState 23505, `pk_fecha_nacimiento_usuario` |
  | `insert_shouldFail_whenUserIsBlank` | `'   '` | `ck_fecha_nacimiento_usuario_firebase_uid` |
  | `insert_shouldFail_whenBirthDateIsNull` | `('uid-1', NULL)` | SQLState 23502 |
  | `insert_shouldFail_whenUserHas129Characters` | 129 `a` | SQLState 22001 |
  | `table_shouldHaveOnlyIdentityAndBirthDate` | `information_schema.columns` | exactamente `firebase_uid`, `fecha_nacimiento` |
  | `columnLength_shouldMatchFirebaseUidLimit` | `character_maximum_length` de `firebase_uid` | igual a `FirebaseUid.MAX_LENGTH` (128) |
  | `primaryKey_shouldBeTheOnlyIndex` | `pg_indexes` de la tabla | solo `pk_fecha_nacimiento_usuario` |
  | `inbox_shouldFail_whenMessageIdRepeats` | dos veces `('11111111-1111-4111-8111-111111111111', 'cuenta.creada', now())` | SQLState 23505, `pk_evento_procesado` |
  | `inbox_shouldFail_whenTypeIsNotAnAccountEvent` | `tipo` = `'perfil.actualizado'` | `ck_evento_procesado_tipo` |
  | `inbox_shouldFail_whenProcessedAtIsNull` | `procesado_en` `NULL` | SQLState 23502 |
  | `inbox_shouldHaveNoPersonalDataColumns` | `information_schema.columns` de `evento_procesado` | exactamente `message_id`, `tipo`, `procesado_en` |
  | `migration_shouldKeepProfiles_whenProfilesExist` | inserta un `perfil_profesional` con SQL antes de las consultas | sigue existiendo |

**Resultado T-P1.1 (9-oct-2026):** `V5` libre en `origin/develop` (última `V4`). Rojo sin la migración: 13 pruebas, 8 fallas y 5 errores; verde con ella: `BirthDateReplicaSchemaIT` 13/13 (`./mvnw -B -q verify -Dit.test=BirthDateReplicaSchemaIT -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false`, EXIT=0). Comentarios del SQL con tildes, como `V4`.

### T-P1.2 · Puertos, excepciones y código

- **Crear** `domain/port/BirthDateReplica.java`:
  ```java
  /**
   * Copia local de la fecha de nacimiento de cada Usuario, alimentada por los eventos de cuenta del servicio de cuentas.
   *
   * <p>Las reglas de fechas de la experiencia laboral y la formación la leen en lugar de llamar a otro servicio. La falta de la
   * fila significa que el evento de cuenta creada aún no se procesó, no que el Usuario no tenga fecha de nacimiento.</p>
   */
  public interface BirthDateReplica {
      /** @return la fecha de nacimiento replicada, o vacío si aún no llegó */
      Optional<LocalDate> findBirthDate(FirebaseUid userId);
      /** Guarda la fecha de nacimiento si el Usuario aún no tiene una; @return true si se insertó una fila */
      boolean saveIfAbsent(FirebaseUid userId, LocalDate birthDate);
      /** @return true si se borró una fila */
      boolean delete(FirebaseUid userId);
  }
  ```
- **Crear** `domain/port/ProcessedMessageInbox.java`:
  ```java
  /**
   * Registro de los mensajes de eventos de cuenta ya procesados, para que una reentrega no tenga efecto.
   *
   * <p>Los mensajes llegan al menos una vez. Anotar el identificador del mensaje en la misma transacción que su efecto hace que
   * el efecto ocurra una sola vez aunque el broker entregue el mensaje otra vez después del commit.</p>
   */
  public interface ProcessedMessageInbox {
      /**
       * @param messageId   identificador del mensaje, que pone el productor
       * @param eventType   {@code cuenta.creada} o {@code cuenta.eliminada}
       * @param processedAt instante del procesamiento, en UTC
       * @return true si el mensaje no estaba registrado y ahora lo está; false si ya se había procesado
       */
      boolean registerIfAbsent(UUID messageId, String eventType, Instant processedAt);
  }
  ```
- **Modificar** `domain/exception/ErrorCode.java`: agrega `BIRTH_DATE_UNAVAILABLE` junto a los demás códigos de negocio (mira cómo están
  agrupados antes de insertarlo).
- **Crear** `domain/exception/BirthDateUnavailableException.java`:
  ```java
  /**
   * La fecha de nacimiento del Usuario aún no se replicó, así que no se puede validar una experiencia laboral o una formación con fechas.
   * Se responde como un fallo temporal que la persona puede reintentar; no se guarda nada.
   */
  public class BirthDateUnavailableException extends BusinessException {
      /** Crea la excepción con el mensaje de reintento para la persona. */
      public BirthDateUnavailableException() {
          super(ErrorCode.BIRTH_DATE_UNAVAILABLE, "Ocurrió un error. Inténtalo de nuevo.");
      }
  }
  ```
  Comprueba la firma real del constructor de `BusinessException` con `git grep -n "BusinessException(" src/main` antes de escribirlo.
- **Crear** `domain/exception/InvalidAccountEventException.java`:
  ```java
  /**
   * Evento de cuenta cuyo contenido incumple el contrato publicado. Nunca llega a HTTP: el consumidor rechaza el mensaje hacia la
   * cola de fallidos. La razón es un código estable para el log; el mensaje nunca incluye datos del evento.
   */
  public class InvalidAccountEventException extends RuntimeException {
      /** Razones estables, que se registran junto con el identificador del mensaje. */
      public enum Reason { MESSAGE_ID_INVALID, USER_ID_INVALID, BIRTH_DATE_REQUIRED, BIRTH_DATE_IN_THE_FUTURE }
      private final Reason reason;
      /** @param reason por qué se rechazó el evento */
      public InvalidAccountEventException(Reason reason) { super("Evento de cuenta inválido: " + reason); this.reason = reason; }
      /** @return por qué se rechazó el evento */
      public Reason getReason() { return reason; }
  }
  ```
- **Modificar** `presentation/advice/ErrorCatalog.java`: `entry(BIRTH_DATE_UNAVAILABLE, SERVICE_UNAVAILABLE, "Servicio no disponible", null),`
  en la sección «Errores de negocio» (comprueba la forma real de las filas existentes, por ejemplo la de `PROFILE_CREATION_TIMEOUT`).
- **Modificar** `docs/errores.md`: fila
  `| \`BIRTH_DATE_UNAVAILABLE\` | 503 | \`POST /api/v1/profiles/{id}/work-experiences\` y \`/educations\` (desde CM-274) | — | «Ocurrió un error. Inténtalo de nuevo.» | \`BirthDateUnavailableException\`: la réplica local aún no tiene la fecha de nacimiento del Usuario (evento \`cuenta.creada\` sin procesar); no se guarda nada | \`ApiExceptionHandlerTest.birthDateUnavailable_shouldReturn503_whenThrown\` |`
  después de la de `PROFILE_CREATION_TIMEOUT`, y cambia el nombre del libro citado en la introducción a `09102026_01_Backlog_v6.xlsx`.
- **Modificar** `presentation/advice/ApiExceptionHandler.reject(...)` (el método que hoy registra `log.warn("Petición rechazada: code={} requestId={} firebaseUid={}", ...)`; búscalo con `git grep -n "Petición rechazada" src/main`): si el estado del `ProblemDetail` es `>= 500`, registra `log.error("Rechazo de negocio con estado de servidor: code={} requestId={} firebaseUid={}", ...)` **sin** pasar la excepción (no hay causa técnica que trazar); si no, deja el `warn` existente tal cual (no cambies su texto: es código previo). Comentario de una línea sobre el `if`: «Un 5xx significa que alguien debe actuar (por ejemplo, una réplica que nunca llegó); los 4xx son resultados esperables del cliente.» Si `PROFILE_CREATION_TIMEOUT` sigue existiendo en la rama base, también pasa a `ERROR`: es lo que pide el estándar; repórtalo.
- **Pruebas** en `ApiExceptionHandlerTest`:
  - agrega la fila de `BirthDateUnavailableException` → 503 / `BIRTH_DATE_UNAVAILABLE` al origen de datos de
    `businessException_shouldReturnItsStatusAndCode_whenThrown`;
  - `birthDateUnavailable_shouldReturn503_whenThrown` (`@DisplayName("La falta de la fecha replicada responde 503 con el mensaje de reintento")`):
    estado 503; `Content-Type` `application/problem+json;charset=UTF-8`; `$.code` `BIRTH_DATE_UNAVAILABLE`; `$.detail`
    «Ocurrió un error. Inténtalo de nuevo.»; `$.title` «Servicio no disponible»; `$.requestId` presente; `$.errors` ausente; el cuerpo no
    contiene `Exception` ni `co.edu`.
  - `rejection_shouldLogError_whenBusinessStatusIs5xx`: la clase ya captura el log con un `ListAppender<ILoggingEvent>` (`logs`), no
    uses `OutputCaptureExtension`. Lanza `BirthDateUnavailableException` → un evento de nivel `ERROR` cuyo mensaje contiene
    `code=BIRTH_DATE_UNAVAILABLE` y el `requestId` enviado, con `getThrowableProxy()` nulo (sin traza) y ningún evento `WARN` para ese rechazo.
  - `rejection_shouldLogWarn_whenBusinessStatusIs4xx`: lanza `ProfileNotFoundException` → un evento `WARN` con `code=PROFILE_NOT_FOUND` y
    ningún evento `ERROR`. La prueba existente `rejection_shouldLogCodeRequestIdAndUid_whenIdentityPresent` debe seguir en verde.
- `ErrorCatalogTest.everyErrorCode_shouldBeResponseOrField_whenCatalogLoaded` debe seguir en verde (detecta un código sin fila).

**Resultado T-P1.2 (9-oct-2026):** rojo antes del código: `ApiExceptionHandlerTest` no compilaba (faltaban `BirthDateUnavailableException` y `ErrorCode.BIRTH_DATE_UNAVAILABLE`). Verde después: `ApiExceptionHandlerTest` 48/48, `ErrorCatalogTest` 6/6, `ArquitecturaTest` 8/8 (`./mvnw -B test -Dtest=ApiExceptionHandlerTest,ErrorCatalogTest,ErrorCodeDocumentationTest,ArquitecturaTest`, BUILD SUCCESS; `ErrorCodeDocumentationTest` aún no existe en la cadena, lo crea CM-274 PR 1). `PROFILE_CREATION_TIMEOUT` también pasa a `ERROR` por ser 503.

### T-P1.3 · Inbox: entidad, repositorio y adaptador

- **Crear** `infrastructure/persistence/entity/ProcessedMessageEntity.java` (`@Entity @Table(name = "evento_procesado")`;
  `@Id @Column(name = "message_id") UUID messageId`, `@Column(name = "tipo", nullable = false, length = 32) String eventType`,
  `@Column(name = "procesado_en", nullable = false) Instant processedAt`; constructor protegido y getters). La entidad existe para que
  `ddl-auto=validate` compruebe la tabla; la escritura es la consulta nativa.
- **Crear** `infrastructure/persistence/repository/ProcessedMessageJpaRepository.java` (`JpaRepository<ProcessedMessageEntity, UUID>`):
  ```java
  /** Inserta el identificador del mensaje si no está; @return 1 si se insertó, 0 si ya estaba */
  @Modifying
  @Query(value = """
          INSERT INTO evento_procesado (message_id, tipo, procesado_en)
          VALUES (:messageId, :eventType, :processedAt)
          ON CONFLICT (message_id) DO NOTHING
          """, nativeQuery = true)
  int insertIfAbsent(@Param("messageId") UUID messageId, @Param("eventType") String eventType,
                     @Param("processedAt") Instant processedAt);
  ```
- **Crear** `infrastructure/persistence/repository/ProcessedMessageInboxAdapter.java` (`@Repository`, package-private como los demás
  adaptadores si el repo lo hace así; implementa `ProcessedMessageInbox`; el método lleva `@Transactional` y devuelve
  `insertIfAbsent(...) == 1`).
- **Crear** el doble `src/test/java/co/edu/unicauca/cameia/perfil/infrastructure/persistence/InMemoryProcessedMessageInbox.java`
  (`ConcurrentHashMap<UUID, String>`; `putIfAbsent(...) == null`; ayuda `contents()`).
- **Pruebas** `infrastructure/persistence/ProcessedMessageInboxAdapterIT.java` (`@SpringBootTest`, PostgreSQL):
  - `registerIfAbsent_shouldReturnTrue_whenMessageIsNew` → `true`; una fila con `tipo` `cuenta.creada` y `procesado_en`
    `2026-10-09T15:04:05Z`.
  - `registerIfAbsent_shouldReturnFalse_whenMessageWasRegistered` → la segunda llamada devuelve `false`; una sola fila; `procesado_en` sin
    cambios.
  - `registerIfAbsent_shouldReturnTrueOnce_whenCalledConcurrently`: dos hilos con `CountDownLatch` y `ExecutorService` (2 hilos), cada uno
    con su `TransactionTemplate` → exactamente un `true`, una fila, ninguna excepción.

**Resultado T-P1.3 (9-oct-2026):** rojo sin el adaptador: `ProcessedMessageInboxAdapterIT` 3 errores (`NoSuchBeanDefinitionException` de `ProcessedMessageInbox`). Verde con él: 3/3 (`./mvnw -B -q verify -Dit.test=ProcessedMessageInboxAdapterIT -Dtest=NoUnitTests -Dsurefire.failIfNoSpecifiedTests=false`, EXIT=0, reporte Failsafe tests=3 errors=0 failures=0).

### T-P1.4 · `ClockConfig`, comandos y `BirthDateReplicaAppService`

- **Crear** `infrastructure/config/ClockConfig.java`: `@Configuration` con `@Bean Clock clock() { return Clock.systemUTC(); }`
  y Javadoc («las fechas se calculan en UTC; las pruebas reemplazan el reloj»). Si al ejecutar ya existe un bean `Clock`, no lo dupliques:
  reutilízalo y repórtalo.
- **Crear** `application/command/ReplicateBirthDateCommand.java`:
  `record ReplicateBirthDateCommand(String messageId, String userId, LocalDate birthDate)` y
  `application/command/RemoveBirthDateCommand.java`: `record RemoveBirthDateCommand(String messageId, String userId)`, con Javadoc
  (los valores llegan crudos del mensaje; el servicio los valida).
- **Crear** `application/service/BirthDateReplicaAppService.java`:
  ```java
  /**
   * Mantiene la réplica de la fecha de nacimiento al día con los eventos de cuenta.
   *
   * <p>Los eventos llegan al menos una vez y en cualquier orden. Cada mensaje se valida primero y luego se anota en el Inbox y se
   * aplica en la misma transacción, así que una reentrega no tiene efecto. Un segundo evento de creación del mismo Usuario conserva
   * la primera fecha guardada y borrar una fila inexistente no falla. El contenido que incumple el contrato se rechaza con una
   * razón estable antes de tocar el Inbox, de modo que el mensaje se puede reenviar una vez corregido. Ni la fecha de nacimiento
   * ni ningún otro dato del evento se escribe en el log.</p>
   */
  @Service
  public class BirthDateReplicaAppService {

      static final String ACCOUNT_CREATED = "cuenta.creada";
      static final String ACCOUNT_DELETED = "cuenta.eliminada";

      /**
       * @param command identificador del mensaje, identidad y fecha de nacimiento tomados de un evento de cuenta creada
       * @throws InvalidAccountEventException si el identificador del mensaje falta o no es un UUID, la identidad falta, está en
       *         blanco o mide más de 128 caracteres, o la fecha de nacimiento falta o es posterior a hoy en UTC
       */
      @Transactional
      public void recordAccountCreated(ReplicateBirthDateCommand command) {
          UUID messageId = messageId(command.messageId());
          FirebaseUid userId = userId(command.userId());
          LocalDate birthDate = birthDate(command.birthDate());
          if (!inbox.registerIfAbsent(messageId, ACCOUNT_CREATED, clock.instant())) {
              log.info("Mensaje ya procesado; se ignora [messageId={}]", messageId);
              return;
          }
          if (replica.saveIfAbsent(userId, birthDate)) {
              log.info("Fecha de nacimiento replicada [firebaseUid={}, messageId={}]", userId.value(), messageId);
          } else {
              log.info("Fecha ya replicada; el evento se ignora [firebaseUid={}, messageId={}]", userId.value(), messageId);
          }
      }

      /**
       * @param command identificador del mensaje e identidad tomados de un evento de cuenta eliminada
       * @throws InvalidAccountEventException si el identificador del mensaje o la identidad no son válidos
       */
      @Transactional
      public void recordAccountDeleted(RemoveBirthDateCommand command) { /* same order: validate, inbox, delete, log */ }

      /** Convierte el identificador de mensaje del productor; si falta o está mal formado, incumple el contrato. */
      private static UUID messageId(String raw) {
          if (raw == null) {
              throw new InvalidAccountEventException(Reason.MESSAGE_ID_INVALID);
          }
          try {
              return UUID.fromString(raw);
          } catch (IllegalArgumentException malformed) {
              throw new InvalidAccountEventException(Reason.MESSAGE_ID_INVALID);
          }
      }

      /** Convierte la identidad y traduce el rechazo del objeto de valor a una violación del contrato. */
      private static FirebaseUid userId(String raw) {
          if (raw == null || raw.isBlank() || raw.length() > FirebaseUid.MAX_LENGTH) {
              throw new InvalidAccountEventException(Reason.USER_ID_INVALID);
          }
          return new FirebaseUid(raw);
      }
  }
  ```
  **Trampa:** `UUID.fromString` acepta formas no canónicas (`"1-1-1-1-1"`). Después de convertir, compara
  `uuid.toString().equals(raw.toLowerCase(Locale.ROOT))` y, si no coincide, lanza `MESSAGE_ID_INVALID`. Prueba con `"1-1-1-1-1"`.
  Constructor con `BirthDateReplica replica`, `ProcessedMessageInbox inbox` y `Clock clock` (3 parámetros).
- **Crear** el doble `src/test/java/co/edu/unicauca/cameia/perfil/infrastructure/persistence/InMemoryBirthDateReplica.java`
  (`ConcurrentHashMap<String, LocalDate>`; `putIfAbsent`; método de ayuda `contents()`), con Javadoc que diga que lo usan también las
  pruebas de las reglas de fechas.
- **Pruebas** `application/service/BirthDateReplicaAppServiceTest.java` (sin Spring, los dos dobles y reloj fijo). `MESSAGE_ID` =
  `"11111111-1111-4111-8111-111111111111"`, `OTHER_MESSAGE_ID` = `"22222222-2222-4222-8222-222222222222"`, uid
  `"6f1d2c3b4a5e4f60718293a4b5c6d7e8"`:
  - `recordAccountCreated_shouldStoreBirthDateAndRegisterMessage_whenEventIsValid` (`2008-03-15`) → réplica con la fecha; Inbox con
    `MESSAGE_ID`, `cuenta.creada`, `2026-10-09T15:04:05Z`.
  - `recordAccountCreated_shouldHaveNoEffect_whenMessageWasProcessed`: el mismo comando 20 veces → una entrada en la réplica y una en el
    Inbox.
  - `recordAccountCreated_shouldKeepFirstDate_whenOtherMessageHasOtherDate` (`MESSAGE_ID` con `2008-03-15`, luego `OTHER_MESSAGE_ID` con
    `2000-01-01`) → `2008-03-15`; Inbox con los dos ids.
  - `recordAccountCreated_shouldReject_whenMessageIdIsInvalid` — `@ParameterizedTest` con `@NullSource` y
    `@ValueSource(strings = {"", "abc", "1-1-1-1-1", "11111111-1111-4111-8111-11111111111Z"})` → `MESSAGE_ID_INVALID`; los dos dobles
    vacíos.
  - `recordAccountCreated_shouldAccept_whenUserIdHas128Characters` (128 `a`).
  - `recordAccountCreated_shouldReject_whenUserIdIsInvalid` — `@NullSource` y `@ValueSource(strings = {"", "   ", "\t", "\n"})` más 129
    `a` → `USER_ID_INVALID`; los dos dobles vacíos (el Inbox no se toca).
  - `recordAccountCreated_shouldReject_whenBirthDateIsNull` → `BIRTH_DATE_REQUIRED`; Inbox vacío.
  - `recordAccountCreated_shouldAccept_whenBirthDateIsToday` (`2026-10-09`).
  - `recordAccountCreated_shouldReject_whenBirthDateIsTomorrow` (`2026-10-10`) → `BIRTH_DATE_IN_THE_FUTURE`; Inbox vacío.
  - `recordAccountCreated_shouldStoreLeapDay_whenBornOnFebruary29` (`2008-02-29`).
  - `recordAccountDeleted_shouldRemoveBirthDate_whenPresent`; `recordAccountDeleted_shouldNotFail_whenAbsent` (Inbox con el id);
    `recordAccountDeleted_shouldHaveNoEffect_whenMessageWasProcessed` (fecha guardada, el mismo `cuenta.eliminada` ya en el Inbox → la
    fecha sigue); `recordAccountDeleted_shouldReject_whenUserIdIsBlank`; `recordAccountDeleted_shouldReject_whenMessageIdIsNotUuid`.
  - `recordAccountCreated_shouldNotLogBirthDate_whenStored` (`OutputCaptureExtension`): la salida contiene el uid y `MESSAGE_ID` y no
    contiene `2008-03-15`.

### T-P1.5 · Réplica: entidad, repositorio, adaptador y transacción

- **Crear** `infrastructure/persistence/entity/BirthDateReplicaEntity.java` (`@Entity @Table(name = "fecha_nacimiento_usuario")`,
  `@Id @Column(name = "firebase_uid", length = 128) String firebaseUid`, `@Column(name = "fecha_nacimiento", nullable = false) LocalDate
  fechaNacimiento`; constructor protegido y getters).
- **Crear** `infrastructure/persistence/repository/BirthDateReplicaJpaRepository.java`:
  ```java
  @Modifying
  @Query(value = """
          INSERT INTO fecha_nacimiento_usuario (firebase_uid, fecha_nacimiento)
          VALUES (:firebaseUid, :birthDate)
          ON CONFLICT (firebase_uid) DO NOTHING
          """, nativeQuery = true)
  int insertIfAbsent(@Param("firebaseUid") String firebaseUid, @Param("birthDate") LocalDate birthDate);

  @Modifying
  @Query("delete from BirthDateReplicaEntity e where e.firebaseUid = :firebaseUid")
  int deleteByFirebaseUid(@Param("firebaseUid") String firebaseUid);
  ```
- **Crear** `BirthDateReplicaRepositoryAdapter.java` (`@Repository`, implementa `BirthDateReplica`): `saveIfAbsent` y `delete` con
  `@Transactional`; `findBirthDate` con `@Transactional(readOnly = true)` (regla 6).
- **Pruebas** `infrastructure/persistence/BirthDateReplicaRepositoryAdapterIT.java` (`@SpringBootTest`, PostgreSQL):
  - `saveIfAbsent_shouldInsert_whenAbsent` → `true`; `findBirthDate` → `2008-03-15`.
  - `saveIfAbsent_shouldReturnFalse_whenPresent` → `false`; fecha sin cambios.
  - `saveIfAbsent_shouldInsertOnce_whenCalledConcurrently`: dos hilos con `CountDownLatch` y `ExecutorService` (2 hilos), cada uno en su
    propia transacción → exactamente un `true`, una fila, ninguna excepción.
  - `findBirthDate_shouldReturnStoredDate_whenPresent`; `findBirthDate_shouldBeEmpty_whenAbsent`.
  - `delete_shouldReturnTrue_whenPresent` y `delete_shouldReturnFalse_whenAbsent`.
- **Pruebas** `application/service/BirthDateReplicaAppServiceIT.java` (`@SpringBootTest`, PostgreSQL, `@MockitoSpyBean BirthDateReplica`):
  `recordAccountCreated_shouldRollBackInbox_whenReplicaFails`: el espía lanza `DataAccessResourceFailureException` en `saveIfAbsent` →
  la excepción sale del servicio; `evento_procesado` y `fecha_nacimiento_usuario` quedan sin filas (el reintento podrá procesar el
  mensaje). Si `@MockitoSpyBean` no envuelve el adaptador package-private, usa `@MockitoBean` del puerto y repórtalo.

### T-P1.6 · Cierre de P1

- `.\mvnw.cmd clean verify` con salida real; cobertura por clase (`BirthDateReplicaAppService`, `BirthDateReplicaRepositoryAdapter`,
  `ProcessedMessageInboxAdapter`, `BirthDateUnavailableException`, `InvalidAccountEventException`, `ErrorCatalog`, `ApiExceptionHandler`,
  `ClockConfig`);
  `git diff --stat` ≤ 1000 (meta 800). Newman de la colección existente contra el jar: las 77 peticiones siguen en verde.

---

## Bloque P2 — consumo desde RabbitMQ

### T-P2.1 · Variables de RabbitMQ y comprobación del `docker-compose.yml`

- **No hay nada que corregir en el usuario de RabbitMQ.** La imagen `rabbitmq:3.13-management-alpine` que usa el compose trae
  `loopback_users.guest = false` (comprobado en `/etc/rabbitmq/conf.d/10-defaults.conf` de la imagen), así que `guest/guest` sí se
  conecta desde el contenedor de la aplicación. Antes se creía que daba `ACCESS_REFUSED`; no es así. Por eso `docker-compose.yml` **no
  se modifica** en esta CM. Comprobación: `docker compose up -d` y `docker logs cameia-perfil 2>&1 | Select-String "ACCESS_REFUSED"` →
  sin líneas (pega la salida).
- **Modificar** `.env.example`: el archivo declara que RabbitMQ queda fuera; ahora la aplicación sí lo consume. Agrega un bloque con
  `SPRING_RABBITMQ_HOST=localhost`, `SPRING_RABBITMQ_PORT=5672`, `SPRING_RABBITMQ_USERNAME=`, `SPRING_RABBITMQ_PASSWORD=` (vacías,
  como toda variable sensible), `SPRING_RABBITMQ_VIRTUAL_HOST=/`, `SPRING_RABBITMQ_SSL_ENABLED=false` (comentario: «true en staging y
  producción: AMQPS») y `SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP=true` (comentario: «false en una instancia que solo atiende la
  API»), cada una con su comentario de una línea, y corrige la frase de la cabecera que dice que RabbitMQ queda fuera.
- **Después:** la aplicación del compose arranca y declara las colas (`rabbitmqctl list_queues` o el API de administración). Pega la salida.

### T-P2.2 · Topología y convertidor

- **Comprobado el 9-oct-2026 en `spring-amqp-4.1.1.jar`:** `org.springframework.amqp.support.converter.JacksonJsonMessageConverter`
  existe (ya la usa `RabbitConfig`) y `setAlwaysConvertToInferredType(boolean)` está en su clase base `AbstractJacksonMessageConverter`.
  Repite `javap` si el repo cambió de versión; si el método no existe, detente y reporta.
- **Modificar** `RabbitConfig`: quita `QUEUE_CUENTA_ELIMINADA`, `queueCuentaEliminada()` y `bindingCuentaEliminada(...)`; en
  `messageConverter()` llama `converter.setAlwaysConvertToInferredType(true)` con el comentario «Se usa el tipo del parámetro del consumidor y se ignora el
  encabezado __TypeId__: un mensaje no debe elegir qué clase se instancia.» Actualiza su Javadoc para no listar `cuenta-eliminada`.
- **Crear** `infrastructure/messaging/config/AccountEventsRabbitConfig.java`:
  ```java
  /**
   * Topología de los eventos de cuenta que consume este servicio.
   *
   * <p>Las dos colas envían sus mensajes fallidos a {@code perfil.dlx} con su propio nombre como clave de enrutamiento, así que cada
   * mensaje fallido llega a la cola de fallidos de su origen. El exchange de cuentas también se declara aquí, con los mismos
   * atributos que usa el productor, para que el orden de arranque no importe.</p>
   */
  @Configuration
  public class AccountEventsRabbitConfig {
      public static final String ACCOUNTS_EXCHANGE = "cuentas.events";
      public static final String DEAD_LETTER_EXCHANGE = "perfil.dlx";
      public static final String ACCOUNT_CREATED_QUEUE = "perfil.cuenta-creada";
      public static final String ACCOUNT_DELETED_QUEUE = "perfil.cuenta-eliminada";
      static final String ACCOUNT_CREATED_KEY = "cuenta.creada";
      static final String ACCOUNT_DELETED_KEY = "cuenta.eliminada";
      static final String DEAD_LETTER_SUFFIX = ".dlq";

      @Bean TopicExchange accountsExchange() { return new TopicExchange(ACCOUNTS_EXCHANGE, true, false); }
      @Bean DirectExchange deadLetterExchange() { return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false); }
      @Bean Queue accountCreatedQueue() { return withDeadLetter(ACCOUNT_CREATED_QUEUE); }
      @Bean Queue accountDeletedQueue() { return withDeadLetter(ACCOUNT_DELETED_QUEUE); }
      @Bean Queue accountCreatedDeadLetterQueue() { return QueueBuilder.durable(ACCOUNT_CREATED_QUEUE + DEAD_LETTER_SUFFIX).build(); }
      @Bean Queue accountDeletedDeadLetterQueue() { return QueueBuilder.durable(ACCOUNT_DELETED_QUEUE + DEAD_LETTER_SUFFIX).build(); }
      // cuatro enlaces: dos al exchange de cuentas con las claves de los eventos y dos de las colas de fallidos a perfil.dlx con el nombre de la cola de origen

      private static Queue withDeadLetter(String name) {
          return QueueBuilder.durable(name).deadLetterExchange(DEAD_LETTER_EXCHANGE).deadLetterRoutingKey(name).build();
      }
  }
  ```
  Cada `@Bean` con su Javadoc de una línea. Con dos beans `Queue` y `Exchange` del mismo tipo, los métodos de binding reciben los beans por
  nombre de parámetro igual al nombre del método (`Queue accountCreatedQueue`), como hace `RabbitConfig`.
- **Modificar** `src/main/resources/application.yml`, bajo `spring:`:
  ```yaml
  rabbitmq:
    listener:
      simple:
        # El ack se envía cuando el consumidor termina, es decir, después del commit de la base de datos.
        acknowledge-mode: auto
        # Un mensaje fallido nunca vuelve a su propia cola: agotados los reintentos va a la cola de fallidos.
        default-requeue-rejected: false
        prefetch: 10
        concurrency: 1
        retry:
          enabled: true
          # Reintentos DESPUÉS del primer intento: 2 reintentos = 3 intentos en total (esperas de 1 s y 2 s).
          max-retries: 2
          initial-interval: 1s
          multiplier: 2
          max-interval: 2s
  ```
  **Trampa comprobada en `spring-boot-amqp-4.1.1.jar`:** la propiedad se llama `max-retries`, no `max-attempts` (con `max-attempts` Boot
  no falla: ignora la clave y aplica el valor por defecto). La prueba `AccountEventsRetryIT.retry_shouldAttemptThreeTimes_whenDatabaseFails`
  de T-P2.4 cuenta las invocaciones y debe dar exactamente 3.
- **Crear** `infrastructure/messaging/config/AccountEventsRetryConfig.java`: sin esto, Spring reintenta **todo**, también los mensajes que
  incumplen el contrato (3 intentos y 3 registros por mensaje inválido, contra REQ-RF-10 y REQ-RF-12). El contrato de Spring Boot 4 es
  un bean `RabbitListenerRetrySettingsCustomizer` cuyo método recibe `org.springframework.boot.retry.RetryPolicySettings`:
  ```java
  /** Excluye del reintento los rechazos por contrato: un dato inválido no mejora al repetirse. */
  @Configuration
  public class AccountEventsRetryConfig {
      /** @return el personalizador que deja los mensajes inválidos fuera del reintento */
      @Bean
      RabbitListenerRetrySettingsCustomizer accountEventsRetryCustomizer() {
          return settings -> settings.setExceptionExcludes(List.of(AmqpRejectAndDontRequeueException.class));
      }
  }
  ```
  La política de reintento revisa también la cadena de causas (`ExceptionTypeFilter.match(throwable, true)` en `DefaultRetryPolicy`), así
  que sigue valiendo aunque Spring envuelva la excepción en `ListenerExecutionFailedException`. Pruebas: `AccountEventsRetryIT`
  (T-P2.4): un mensaje inválido invoca el consumidor **una** vez y llega a la cola de fallidos; un fallo técnico lo invoca **tres** veces.
  Si la clase `RabbitListenerRetrySettingsCustomizer` o `setExceptionExcludes` no existen en el jar del repo, detente y reporta.
- **Pruebas** `infrastructure/messaging/AccountEventsTopologyIT.java` (RabbitMQ y PostgreSQL de la regla 0, `RabbitAdmin`):
  `topology_shouldDeclareContractNames_whenStarted`: `getQueueInfo("perfil.cuenta-creada")`, `"perfil.cuenta-eliminada"`,
  `"perfil.cuenta-creada.dlq"`, `"perfil.cuenta-eliminada.dlq"` no nulos; `getQueueProperties` de las dos colas principales con
  `x-dead-letter-exchange` = `perfil.dlx` (consulta los argumentos con el API de administración o declarando la misma cola con
  argumentos distintos y esperando `PRECONDITION_FAILED`, lo que sea posible con `RabbitAdmin`; documenta cuál usaste);
  `topology_shouldNotDeclareLegacyAccountDeletedQueue_whenStarted` (`getQueueInfo("cuenta-eliminada")` nulo).

### T-P2.3 · Cargas y consumidores

- **Crear** `infrastructure/messaging/payload/AccountCreatedPayloadV1.java`:
  ```java
  /**
   * Campos que este servicio lee de {@code cuenta.creada} versión 1. El correo y el instante de creación no se declaran a propósito:
   * aquí nunca se enlazan, ni se guardan, ni se registran.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record AccountCreatedPayloadV1(
          @JsonProperty("usuarioId") String userId,
          @JsonProperty("fechaNacimiento") LocalDate birthDate) { }
  ```
  y `AccountDeletedPayloadV1(@JsonProperty("usuarioId") String userId)` igual. Comprueba el paquete de las anotaciones en Jackson 3
  (`com.fasterxml.jackson.annotation`) con `git grep -n "JsonProperty\|JsonIgnoreProperties" src/main`.
- **Crear** `infrastructure/messaging/consumer/AccountCreatedListener.java`:
  ```java
  /**
   * Consume {@code cuenta.creada} y guarda la fecha de nacimiento del Usuario en la réplica local.
   *
   * <p>El contenido que incumple el contrato se rechaza sin reencolar y termina en la cola de fallidos; los fallos técnicos se
   * propagan para que el contenedor los reintente. El cuerpo del mensaje nunca se escribe en el log.</p>
   */
  @Component
  public class AccountCreatedListener {
      @RabbitListener(queues = AccountEventsRabbitConfig.ACCOUNT_CREATED_QUEUE, errorHandler = "accountEventsErrorHandler")
      public void onAccountCreated(AccountCreatedPayloadV1 payload,
                                   @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
          try {
              service.recordAccountCreated(new ReplicateBirthDateCommand(messageId, payload.userId(), payload.birthDate()));
          } catch (InvalidAccountEventException invalid) {
              log.warn("Evento de cuenta rechazado hacia la cola de fallidos [cola={}, messageId={}, razón={}]",
                      AccountEventsRabbitConfig.ACCOUNT_CREATED_QUEUE, messageId == null ? "-" : messageId, invalid.getReason());
              throw new AmqpRejectAndDontRequeueException(invalid.getReason().name());
          }
      }
  }
  ```
  El `messageId` puede faltar: llega `null` al servicio, que lo rechaza con `MESSAGE_ID_INVALID`; el log escribe `-`. Un `messageId` que
  no es UUID se registra tal cual solo si mide ≤ 64 caracteres y cumple `[A-Za-z0-9._-]+`; si no, se registra `invalid` (evita inyectar
  saltos de línea en el log).
  Un cuerpo ilegible o con fecha mal formada nunca llega al método (falla el convertidor). Para que también quede registrado una vez y
  con código, crea `infrastructure/messaging/consumer/AccountEventsErrorHandler.java`, un `RabbitListenerErrorHandler` (firma comprobada en `spring-rabbit-4.1.1.jar`:
  `handleError(org.springframework.amqp.core.Message, com.rabbitmq.client.Channel, org.springframework.messaging.Message<?>,
  org.springframework.amqp.listener.ListenerExecutionFailedException)`; ojo con el paquete `org.springframework.amqp.listener`), registrado como
  bean `accountEventsErrorHandler` y referenciado en ambos `@RabbitListener(errorHandler = "accountEventsErrorHandler")`: si la causa es
  `MessageConversionException`, registra `WARN` con `reason=PAYLOAD_INVALID_FORMAT` y lanza `AmqpRejectAndDontRequeueException`; si es
  `AmqpRejectAndDontRequeueException`, la relanza; cualquier otra, registra `ERROR` con traza («Falló el procesamiento del evento de cuenta;
  lo reintenta el contenedor [cola={}, messageId={}]») y la relanza. Como el contenedor reintenta tres veces, esa línea sale por cada
  intento técnico: el `ERROR` de «mensaje agotado» lo registra el recuperador al enviarlo a fallidos (una vez por mensaje). Si `RabbitListenerErrorHandler` no recibe los errores de conversión
  en la versión del repo, detente y reporta (alternativa: `ConditionalRejectingErrorHandler` en la fábrica del contenedor).
- **Reescribir** `AccountDeletedListener.java` con la misma forma, cola `ACCOUNT_DELETED_QUEUE`, `AccountDeletedPayloadV1` y
  `service.recordAccountDeleted(new RemoveBirthDateCommand(messageId, payload.userId()))`. Elimina el `log.warn(... payload={})`
  (escribía el cuerpo del mensaje) y los `TODO`.
- **Crear** `src/test/resources/contracts/cuenta-creada-v1.schema.json`: copia literal del bloque «JSON Schema» de la sección 5 de
  `cameia-cuentas/specs/CM-279-PublicarCuentaCreada/spec.md` (en el worktree `cuentas-CM-279-replica`; si ya está en `develop` de
  Cuentas, cópialo de `docs/eventos/cuenta-creada-v1.schema.json`). No le agregues comentarios (JSON no los admite): el origen lo dice el
  `$id` del esquema.
- **Pruebas** `infrastructure/messaging/payload/AccountCreatedPayloadV1Test.java` (Jackson del contexto o un `JsonMapper` igual al del
  convertidor): `schemaExample_shouldDeserialize_whenReadWithPayloadRecord` lee `examples[0]` del esquema copiado y lo convierte a
  `AccountCreatedPayloadV1` → `userId` `6f1d2c3b4a5e4f60718293a4b5c6d7e8`, `birthDate` `2008-03-15`;
  `schema_shouldRequireTheFieldsThisServiceReads` → `required` del esquema contiene `usuarioId` y `fechaNacimiento`.
- **Pruebas** `infrastructure/messaging/consumer/AccountCreatedListenerTest.java` y `AccountDeletedListenerTest.java` (Mockito, sin
  Spring): `onAccountCreated_shouldCallService_whenPayloadIsValid` (captura el comando: `messageId`
  `11111111-1111-4111-8111-111111111111`, uid y `2008-03-15`); `onAccountCreated_shouldPassNullMessageId_whenHeaderIsMissing`;
  `onAccountCreated_shouldRejectWithoutRequeue_whenEventIsInvalid` (servicio lanza `USER_ID_INVALID` → `AmqpRejectAndDontRequeueException`);
  `onMessage_shouldPropagateTechnicalFailure_whenDatabaseFails` (servicio lanza `DataAccessResourceFailureException` → la misma
  excepción sale); `onAccountCreated_shouldNotLogPayload_whenRejected` (captura de salida sin `2008-03-15`).
  `AccountEventsErrorHandlerTest`: conversión → rechazo y `WARN` con `PAYLOAD_INVALID_FORMAT`; técnica → relanza y `ERROR`.

### T-P2.4 · Prueba de punta a punta con RabbitMQ y PostgreSQL

- **Reproducir antes:** escribe primero `accountCreated_shouldStoreBirthDate_whenEventIsValid` sobre P1 y córrela: debe fallar (no hay
  cola). Pega la salida.
- **Crear** `infrastructure/messaging/BirthDateReplicationIT.java` (`@SpringBootTest`, PostgreSQL y RabbitMQ de la regla 0,
  `@ExtendWith(OutputCaptureExtension.class)`; en `@BeforeEach` vacía la tabla y purga las cuatro colas con `RabbitAdmin.purgeQueue`).
  Mensaje base (literal del contrato):
  `{"usuarioId":"6f1d2c3b4a5e4f60718293a4b5c6d7e8","email":"ana.perez@ejemplo.test","fechaNacimiento":"2008-03-15","creadaEn":"2026-10-09T15:04:05.123Z"}`
  con `contentType` `application/json`, clave `cuenta.creada` y un `messageId` UUID nuevo por prueba (`UUID.randomUUID()`), salvo donde se
  diga. En `@BeforeEach` vacía también `evento_procesado`.
  - `accountCreated_shouldStoreBirthDate_whenEventIsValid` → fila (`6f1d…`, `2008-03-15`) en ≤ 10 s; una fila en `evento_procesado` con
    ese `message_id` y `tipo` `cuenta.creada`; DLQ vacía.
  - `accountCreated_shouldKeepOneRowAndOneInboxEntry_whenSameMessageArrives20Times` (FIA-02) → el mismo mensaje con el mismo `messageId`
    20 veces → 1 fila en la réplica, 1 en el Inbox; espera a que la cola principal quede vacía; DLQ vacía; la salida tiene 19 líneas
    «Mensaje ya procesado; se ignora».
  - `accountCreated_shouldHaveNoEffect_whenRedeliveredAfterCommit` (FIA-03): procesa el mensaje; borra la fila de la réplica con SQL
    para que un segundo efecto sea visible; publica otra vez el mismo mensaje con el mismo `messageId` (el cliente no puede marcar una
    reentrega del broker, y para el consumidor ambos casos son iguales) → la réplica sigue sin fila porque el Inbox lo reconoce.
  - `accountCreated_shouldGoToDeadLetterQueue_whenMessageIdIsMissing` (sin `setMessageId`) y `..._whenMessageIdIsNotUuid` (`"abc"`) →
    en `perfil.cuenta-creada.dlq`; réplica e Inbox vacíos.
  - `accountCreated_shouldIgnoreExtraFields_whenPresent` (agrega `"rol":"ADMIN"`) → fila creada; ninguna columna nueva.
  - `accountCreated_shouldIgnoreTypeIdHeader_whenPresent` (encabezado `__TypeId__` = `java.util.HashMap`) → fila creada.
  - `accountCreated_shouldStoreLeapDay_whenBornOnFebruary29`.
  - `AccountEventsRetryIT` (clase aparte, mismo entorno): `retry_shouldInvokeOnce_whenMessageBreaksContract` (cuerpo `no es json`: el
    consumidor se invoca 1 vez y el mensaje queda en la cola de fallidos) y `retry_shouldAttemptThreeTimes_whenDatabaseFails`
    (`@MockitoSpyBean` del servicio que lanza `DataAccessResourceFailureException` siempre: 3 invocaciones y luego la cola de fallidos).
  - `accountCreated_shouldGoToDeadLetterQueue_whenPayloadIsInvalid` — `@ParameterizedTest` con los cuerpos literales de la sección 10
    de la spec: `no es json`, `[]`, cuerpo vacío, sin `usuarioId`, `usuarioId` nulo, `"   "`, 129 `a`, 1 048 576 `a`, `{"a":1}` como
    `usuarioId`, sin `fechaNacimiento`, `"15/03/2008"`, `"2008-3-15"`, `"2008-03-15T00:00:00Z"`, `"2008-02-30"`, `"2007-02-29"`,
    `20080315`, fecha de mañana según el reloj real (`LocalDate.now(ZoneOffset.UTC).plusDays(1)`) → el mensaje aparece en
    `perfil.cuenta-creada.dlq` en ≤ 15 s (incluye reintentos); la tabla sigue vacía; la cola principal vacía. Reporta cuántas
    invocaciones hizo el consumidor por mensaje (con un `@MockitoSpyBean` del servicio).
  - `accountCreated_shouldAcceptNumericUserId_whenSent` (`"usuarioId":12345`) → documenta el resultado real (fila `12345` si Jackson
    convierte el número; DLQ si no). La prueba afirma lo que ocurra y el reporte lo dice; si va a la DLQ, cambia la fila de la sección 10
    de la spec en el mismo PR.
  - `accountDeleted_shouldRemoveBirthDate_whenPresent` (inserta con SQL `('6f1d…', '2008-03-15')`; publica
    `{"usuarioId":"6f1d2c3b4a5e4f60718293a4b5c6d7e8","eliminadaEn":"2026-10-20T03:00:00.000Z"}` con clave `cuenta.eliminada`) → 0 filas.
  - `accountDeleted_shouldAck_whenAbsent` → sin fila, DLQ de eliminada vacía; el `message_id` en el Inbox con `tipo` `cuenta.eliminada`.
  - `accountDeleted_shouldHaveNoEffect_whenMessageWasProcessed`: publica el `cuenta.eliminada`; vuelve a insertar la fila con SQL; publica
    el mismo mensaje con el mismo `messageId` → la fila sigue.
  - `accountDeleted_shouldGoToDeadLetterQueue_whenUserIdIsBlank` → en `perfil.cuenta-eliminada.dlq`.
  - `accountCreated_shouldNotLogBirthDateNorEmail_whenProcessed` → la salida capturada no contiene `2008-03-15`, `ana.perez@ejemplo.test`
    ni el cuerpo; contiene `6f1d2c3b4a5e4f60718293a4b5c6d7e8`.
- **Verificar:** `.\mvnw.cmd clean verify` (corre todas las IT).

### T-P2.5 · Documentación

- **Crear** `docs/adr/0004-replica-de-fecha-de-nacimiento-por-evento.md` (el `0005` es del bloqueo de escritura de CM-274): contexto (consulta síncrona descartada por el PO el 8-oct),
  decisión (réplica de dos columnas, Inbox `evento_procesado` sin plazo de borrado en la misma transacción, `ack` tras el commit, colas,
  una cola de fallidos por cola, 3 intentos), por qué (IOP-04 y FIA-02 del anexo de atributos de calidad), alternativas descartadas
  (solo clave natural, Inbox con borrado a 30 días, Job de limpieza, una sola `perfil.dlq`), consecuencias (riesgo de orden invertido y
  cómo se reenvía a mano la cola de fallidos: comprobar antes en Cuentas que la cuenta sigue existiendo), conexión AMQPS en staging
  (`SPRING_RABBITMQ_SSL_ENABLED=true`), decisión humana (Paula, 9-oct-2026).
- **Modificar** `CLAUDE.md`: tabla de componentes (consumidores reales de `cuenta.creada` y `cuenta.eliminada`, `AccountEventsRabbitConfig`),
  sección de datos (tablas `fecha_nacimiento_usuario` y `evento_procesado`), variables `SPRING_RABBITMQ_*` y `SPRING_RABBITMQ_LISTENER_SIMPLE_AUTO_STARTUP`, y la
  fila de pendientes «Consumidores y publicador de RabbitMQ son esqueletos» reducida a lo que sigue pendiente (suscripción, consumo,
  publicador del perfil).

### T-P2.6 · Postman y Newman

- **Modificar** `docs/CAMEIA_Perfil_Sprint1.postman_collection.json`: carpeta «Réplica de fecha de nacimiento (local)», con autenticación
  básica `{{rabbitUser}}`/`{{rabbitPassword}}` contra `{{rabbitManagementUrl}}` (`http://localhost:15672`). Variables nuevas en el
  entorno que use la colección (no existe archivo de entorno: créalo como `docs/perfil-local.postman_environment.json`, el nombre que
  espera la colección de CM-274 y la que corre Newman, con `base_url` = `http://localhost:8082` —la variable que ya usan las 154
  referencias de la colección—, `rabbitManagementUrl`, `rabbitUser` = `guest` y `rabbitPassword` = `guest`, que son los del broker local
  del compose y no son secretos; sin otras variables).

  | # | Petición | Pruebas |
  |---|---|---|
  | R-01 | `GET /api/queues/%2F/perfil.cuenta-creada` | 200; `arguments["x-dead-letter-exchange"]` = `perfil.dlx` |
  | R-02 | `POST /api/exchanges/%2F/cuentas.events/publish` con `routing_key` `cuenta.creada`, `properties` `{"content_type":"application/json","message_id":"{{replicaMessageId}}"}` y la carga literal del contrato con `usuarioId` `pm-{{$timestamp}}`. Pre-request: `pm.collectionVariables.set("replicaMessageId", pm.variables.replaceIn("{{$guid}}"))` | 200; `routed` = `true` |
  | R-03 | La misma carga y el mismo `{{replicaMessageId}}` (sin pre-request) | 200; `routed` = `true` (el duplicado no produce error ni va a fallidos; se comprueba en R-05) |
  | R-04 | Publicar `no es json` con la misma clave y `message_id` `{{$guid}}` | `routed` = `true` |
  | R-05 | `GET /api/queues/%2F/perfil.cuenta-creada.dlq` (con `setTimeout` de 5 s en el pre-request para dar tiempo a los reintentos) | `messages` = 1 (solo el inválido) |
  | R-06 | Publicar `cuenta.eliminada` con el mismo `usuarioId` y `GET` de `perfil.cuenta-eliminada.dlq` | `routed` = `true`; DLQ de eliminada con 0 |
  | R-07 | `DELETE /api/queues/%2F/perfil.cuenta-creada.dlq/contents` | 204 (limpieza) |

  La fila de la réplica no se ve por HTTP (no hay endpoint): la prueba automática la cubre y la prueba manual de punta a punta (tarjetas de
  Cuentas, sección 7) la consulta en la base. Lo dice la descripción de la carpeta.
- **Ejecutar** contra el jar con PostgreSQL y RabbitMQ de compose:
  `npx newman run docs\CAMEIA_Perfil_Sprint1.postman_collection.json -e docs\perfil-local.postman_environment.json --reporters cli,junit`
  (las 77 peticiones existentes y las nuevas) y pega la salida.

### T-P2.7 · Cierre de P2 y de la mitad Perfil

- `.\mvnw.cmd clean verify`; cobertura por clase (P1 y P2: además `AccountCreatedListener`, `AccountDeletedListener`,
  `AccountEventsErrorHandler`, `AccountEventsRabbitConfig`, `RabbitConfig`, cargas, `ProcessedMessageInboxAdapter`); Newman completo;
  `git diff --stat` por bloque.
  Reporte con cada línea o rama sin cubrir y su razón.
