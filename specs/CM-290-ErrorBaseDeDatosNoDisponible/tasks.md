# Tarjetas · CM-290 503 `DATABASE_UNAVAILABLE` en Perfil

**Reglas para todas las tarjetas:** identificadores y nombres de prueba nuevos en inglés (`method_shouldX_whenY`) con `@DisplayName` en español; Javadoc en todo lo nuevo; sin `CM-290` en comentarios ni rutas a otros archivos; sin dependencias nuevas; sin tocar migraciones, `.github/`, `pom.xml`, `docker-compose.yml`, `.env.example` ni `RedactedException`; nunca devolver al cliente ni registrar el mensaje de una excepción de la librería. Escapes `\uXXXX` en Java: verificar con `git diff` que no se convirtieron en el carácter real. Docker Desktop encendido. Si algo no coincide con la tarjeta, detenerse y reportar a Paula con la salida real. Estado de la spec: **LISTA PARA EJECUTAR, aprobada por Paula el 19-oct-2026** (las tarjetas usan las opciones recomendadas P-1 a P-5). Paquete base: `co.edu.unicauca.cameia.perfil` (abreviado `…`).

---

## T0 · Preparación

1. `git fetch`; rama `CM-290-error-503-base-de-datos-no-disponible` desde `origin/develop`; anotar el SHA (la spec se escribió sobre `8ed2cf9`).
2. Línea base: `./mvnw.cmd -B -ntp clean verify` → `BUILD SUCCESS` con las `*IT` ejecutadas (no omitidas). Si falla, detenerse.
3. `ls docs/adr`: la spec dice 0009; si ya existe, usar el siguiente y avisar a Paula.

---

## T1 · Espera de 5 s por una conexión (REQ-05)

**Reproducción primero (falla antes del arreglo):** crear `src/test/java/…/infrastructure/config/DatabasePoolConfigurationIT.java` con la forma de las otras `*IT` con Testcontainers del repositorio (leer una antes de escribir):

```java
/** Comprueba la configuración efectiva del pool de conexiones. */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DatabasePoolConfigurationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("El pool espera como máximo 5 segundos por una conexión")
    void connectionTimeout_shouldBeFiveSeconds() {
        assertThat(((HikariDataSource) dataSource).getConnectionTimeout()).isEqualTo(5000L);
    }
}
```

Antes del arreglo falla con `expected: 5000L but was: 30000L`: pegar esa salida en el resultado de la tarjeta. (Si RabbitMQ impide arrancar el contexto en `@SpringBootTest`, copiar las propiedades que usan las demás `*IT` del repositorio; no inventar otras.)

**Arreglo:** en `src/main/resources/application.yml`, bajo `spring.datasource`, a continuación de `password`:

```yaml
    # Espera máxima por una conexión del pool. Con el valor por defecto (30 s), igual al tiempo del
    # Gateway, una base caída hacía esperar 30 s y el cliente nunca veía el 503.
    hikari:
      connection-timeout: 5000
```

**Trampas:** si el valor sigue en 30000, buscar un `application-*.yml` o la variable `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT` que lo pise. Respetar la sangría de dos espacios del archivo.

**Comando:** `./mvnw.cmd -B -ntp verify -Dit.test=DatabasePoolConfigurationIT -Dtest=NoExiste -Dsurefire.failIfNoSpecifiedTests=false`; confirmar en la salida que Failsafe ejecutó la clase (`Tests run: 1`).

**Commit:** `CM-290 | feat(perfil): esperar como máximo 5 s por una conexión de la base de datos`.

---

## T2 · Clasificador de indisponibilidad (REQ-01, REQ-02, REQ-03)

**Código nuevo** `src/main/java/…/presentation/advice/DatabaseUnavailability.java` (paquete-privado, mismo estilo que `BadParameterEncoding`; leerla primero). Es el mismo clasificador de la spec de Cuentas:

```java
/**
 * Reconoce que un fallo se debe a que la base de datos no está disponible: no responde, la
 * conexión se cortó, el pool de conexiones se agotó o una consulta pasó su tiempo máximo.
 *
 * <p>Recorre la excepción, sus causas y sus excepciones suprimidas, porque el corte de una
 * conexión puede llegar como una excepción de transacción sin código de estado y con la causa
 * verdadera solo entre las suprimidas. No reconoce los fallos del dato o de la consulta
 * (violaciones de restricción, SQL inválido, espera de bloqueo agotada).</p>
 */
final class DatabaseUnavailability {

    /** Máximo de excepciones que se visitan: una cadena con ciclos no debe dejar el hilo en un bucle. */
    private static final int MAX_NODES = 64;

    /** Estados de apagado o arranque del servidor y de demasiadas conexiones. */
    private static final Set<String> OUTAGE_STATES = Set.of("57P01", "57P02", "57P03", "53300");

    private DatabaseUnavailability() {
    }

    /** @return {@code true} si la excepción o alguna causa o suprimida es una indisponibilidad de la base */
    static boolean matches(Throwable error) { /* recorrido en anchura, ver abajo */ }

    /** @return el primer SQLState del árbol, o {@code "-"} si ninguna excepción lo trae */
    static String sqlState(Throwable error) { /* mismo recorrido */ }

    private static boolean isOutage(Throwable error) {
        if (error instanceof DataAccessResourceFailureException
                || error instanceof TransientDataAccessResourceException
                || error instanceof QueryTimeoutException
                || error instanceof JDBCConnectionException
                || error instanceof SQLTransientConnectionException
                || error instanceof SQLNonTransientConnectionException
                || error instanceof SQLTimeoutException) {
            return true;
        }
        // TransactionException de Hibernate: un corte solo si lo causó la conexión (un SQLException).
        if (error instanceof org.hibernate.TransactionException && error.getCause() instanceof SQLException) {
            return true;
        }
        if (error instanceof SQLException sql && sql.getSQLState() != null) {
            return sql.getSQLState().startsWith("08") || OUTAGE_STATES.contains(sql.getSQLState());
        }
        return false;
    }
}
```

Recorrido (un método privado que ambos métodos usan, ≤ 20 líneas): cola `ArrayDeque<Throwable>` con la excepción de entrada; conjunto de visitados `Collections.newSetFromMap(new IdentityHashMap<>())`; mientras la cola no esté vacía y los visitados sean menos de `MAX_NODES`: sacar, ignorar si ya se visitó, aplicar el predicado, y encolar `getCause()` (si no es nulo) y todas las de `getSuppressed()`. Un `null` de entrada devuelve falso y `"-"`. El `-` coincide con lo que ya registra `RedactedException.sqlState`.

**Importaciones:** `QueryTimeoutException` y las dos de acceso a recursos son de `org.springframework.dao`; `JDBCConnectionException` de `org.hibernate.exception`. No importar clases de PostgreSQL (`org.postgresql`): el SQLState se lee de `java.sql.SQLException`. Verificar que `ArquitecturaTest` permite estos imports en `presentation`; si no los permite, detenerse y reportar (la alternativa es comparar el nombre de la clase, como hace `BadParameterEncoding` si es el caso: leerla).

**Pruebas** `src/test/java/…/presentation/advice/DatabaseUnavailabilityTest.java` (sin Spring): una por fila de la spec §5 («Excepción»), con las excepciones construidas a mano:

| Prueba | Entrada | Resultado |
|---|---|---|
| `matches_shouldBeTrue_whenPoolIsExhausted` | `CannotCreateTransactionException("x", new JDBCConnectionException("x", new SQLException("x")))` con `addSuppressed(new SQLTransientConnectionException("pool"))` | `true` |
| `matches_shouldBeTrue_whenSqlStateIsAdminShutdown` | `DataAccessResourceFailureException("x", new SQLException("FATAL", "57P01"))` con `addSuppressed(new SQLException("Connection is closed"))` | `true` |
| `matches_shouldBeTrue_whenRollbackFailsOnClosedConnection` | `JpaSystemException(new org.hibernate.TransactionException("Unable to rollback", new SQLException("Connection is closed")))` | `true` |
| `matches_shouldBeTrue_whenOnlySuppressedIsConnectionError` | `RuntimeException("x")` con `addSuppressed(new SQLException("x", "08006"))` | `true` |
| `matches_shouldBeTrue_whenSqlStateIsConnectionClass` (`@ParameterizedTest`) | `"08001"`, `"08006"`, `"57P01"`, `"57P02"`, `"57P03"`, `"53300"` | `true` |
| `matches_shouldBeTrue_whenQueryTimesOut` | `QueryTimeoutException("x", new SQLException("canceling", "57014"))` | `true` |
| `matches_shouldBeFalse_whenNotAvailabilityProblem` (`@ParameterizedTest`) | `DataIntegrityViolationException` con `SQLException("x","23502")`; `BadSqlGrammarException` con `"42601"`; `CannotAcquireLockException` con `"55P03"`; `new IllegalStateException("x")`; `new RuntimeException()`; `new SQLException("x","22012")` | `false` |
| `matches_shouldBeFalse_whenNull` | `null` | `false` |
| `matches_shouldTerminate_whenCauseCycle` | `RuntimeException a = new RuntimeException("a"); RuntimeException b = new RuntimeException("b", a); a.initCause(b);` | termina; `false` |
| `matches_shouldStopAtLimit_whenChainIsTooDeep` | 100 `RuntimeException` encadenadas con `new SQLException("x","08001")` en el fondo | `false` |
| `matches_shouldFind_whenOutageIsAtNode64` | 63 `RuntimeException` encadenadas y `new SQLException("x","08001")` como nodo 64 | `true` |
| `sqlState_shouldReturnFirstState_whenPresent` / `sqlState_shouldReturnDash_whenAbsent` | `SQLException("x","57P01")` / `RuntimeException()` | `"57P01"` / `"-"` |

**Trampa:** `Throwable.initCause` lanza si la causa ya se fijó en el constructor, y `addSuppressed(this)` lanza `IllegalArgumentException`; para el ciclo, `a` debe crearse sin causa.

**Comando:** `./mvnw.cmd -B -ntp test -Dtest=DatabaseUnavailabilityTest`.

**Commit:** `CM-290 | feat(perfil): reconocer la indisponibilidad de la base de datos en una cadena de excepciones`.

---

## T3 · Código, catálogo, rama del manejador y sus pruebas (REQ-01, REQ-04, REQ-06, REQ-08, REQ-09)

1. `domain/exception/ErrorCode.java`: después de `BIRTH_DATE_UNAVAILABLE` agregar

```java
    /** La base de datos no está disponible; la persona puede reintentar. */
    DATABASE_UNAVAILABLE,
```

2. `presentation/advice/ErrorCatalog.java`: a continuación de la entrada de `BIRTH_DATE_UNAVAILABLE` (con el mismo `import static` de `SERVICE_UNAVAILABLE` ya presente):

```java
            entry(DATABASE_UNAVAILABLE, SERVICE_UNAVAILABLE, "Servicio no disponible", "Ocurrió un error. Inténtalo de nuevo."),
```

3. `presentation/advice/ApiExceptionHandler.java`, `handleUnexpected`: entre el `if` de `BadParameterEncoding` y la construcción del 500 (`var requestId = requestId(request);`), agregar

```java
        if (DatabaseUnavailability.matches(ex)) {
            // La base no responde: fallo pasajero del servicio que la persona puede reintentar. El mensaje de la
            // excepción (puede citar el servidor o el usuario de la base) no llega al cliente ni al registro.
            var requestId = requestId(request);
            markLogged(request);
            log.error("Base de datos no disponible: code={} requestId={} firebaseUid={} sqlState={}",
                    ErrorCode.DATABASE_UNAVAILABLE, requestId, firebaseUid(request),
                    DatabaseUnavailability.sqlState(ex), RedactedException.of(ex));
            return respond(problem(ErrorCode.DATABASE_UNAVAILABLE, null), requestId);
        }
```

   Si `requestId` ya está declarada antes en el método, mover la declaración antes de ambos `if` en lugar de duplicarla. Actualizar el Javadoc del método (si no lo tiene, agregarlo) para decir que también cubre la indisponibilidad de la base (`503`).

4. **Pruebas.** Leer primero las pruebas existentes de `ApiExceptionHandlerTest` y `ErrorCatalogTest`:

| Archivo | Prueba | Entrada | Resultado |
|---|---|---|---|
| `ApiExceptionHandlerTest` | `databaseUnavailable_shouldReturn503_whenAvailabilityProblem` | `DataAccessResourceFailureException("host=10.0.0.5 password=x")` | 503; `code` `DATABASE_UNAVAILABLE`; `title` «Servicio no disponible»; `detail` «Ocurrió un error. Inténtalo de nuevo.»; `requestId` presente; cuerpo sin `10.0.0.5` ni `password` |
| `ApiExceptionHandlerTest` | `databaseUnavailable_shouldLogOnce_whenAvailabilityProblem` | la misma, con la captura de salida que use la clase | una línea `ERROR` con `DATABASE_UNAVAILABLE`, `sqlState=` y el `requestId` de la respuesta; sin `10.0.0.5` ni `password` en el registro |
| `ApiExceptionHandlerTest` | `databaseUnavailable_shouldKeepGeneric500_whenNotAvailabilityProblem` | `IllegalStateException("x")` y `DataIntegrityViolationException` con `SQLException("x","23502")` | 500 `INTERNAL_ERROR` en ambas |
| `ApiExceptionHandlerTest` | `databaseUnavailable_shouldReturn503_whenCauseIsOnlySuppressed` | `RuntimeException("x")` con `addSuppressed(new SQLException("x","08006"))` | 503 `DATABASE_UNAVAILABLE` |
| `ErrorCatalogTest` | (existente) | — | debe pasar con el código nuevo; si hay una prueba que cuenta entradas, actualizar el número |
| `ProfileControllerTest` | cambiar `postProfiles_shouldReturnGeneric500_whenDatabaseFails` por `postProfiles_shouldReturn503_whenDatabaseFails` | `createProfile` lanza `DataAccessResourceFailureException("conexión")` | 503 `DATABASE_UNAVAILABLE`; cuerpo sin «conexión» |
| `ProfileControllerTest` | nueva `postProfiles_shouldReturnGeneric500_whenUnexpectedFailure` | `createProfile` lanza `IllegalStateException("x")` | 500 `INTERNAL_ERROR` (control: conserva el comportamiento que fijaba la prueba anterior) |
| `ProfileAppServiceTest` | `addWorkExperience_shouldPropagateDatabaseFailure_whenReplicaFails` | Con el `replica` (el `spy` de la clase) configurado con `doThrow(new DataAccessResourceFailureException("host=10.0.0.5 password=x")).when(replica).findBirthDate(any())`, `addWorkExperience` con fechas (`"2020-01"`) | Lanza `DataAccessResourceFailureException` tal cual (no `BirthDateUnavailableException`); `verify(repository, never()).save(any())`. Junto con la prueba de la rama del manejador (`databaseUnavailable_shouldReturn503_whenAvailabilityProblem`) cubre REQ-09: la causa es la base y gana `DATABASE_UNAVAILABLE` |
| `ApiExceptionHandlerDomainRuleTest` (existente) | — | — | debe seguir en verde: `BirthDateUnavailableException` conserva su 503 `BIRTH_DATE_UNAVAILABLE` |

**Trampa:** `ErrorCodeDocumentationTest` falla hasta que `docs/errores.md` liste el código (T6): ejecutar solo las pruebas de la tarjeta aquí.

**Comando:** `./mvnw.cmd -B -ntp test -Dtest=ApiExceptionHandlerTest,ApiExceptionHandlerDomainRuleTest,ErrorCatalogTest,ErrorCodeTest,ProfileControllerTest,ProfileAppServiceTest,DatabaseUnavailabilityTest`.

**Commit:** `CM-290 | feat(perfil): responder 503 DATABASE_UNAVAILABLE cuando la base de datos no está disponible`.

---

## T4 · Pruebas de punta a punta con PostgreSQL real (REQ-01, casos borde de §7)

Crear `src/test/java/…/presentation/DatabaseUnavailableEndToEndIT.java` con la forma de las `*IT` de punta a punta existentes (`@SpringBootTest(webEnvironment = RANDOM_PORT, properties = {"spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.connection-timeout=1500"})`, `@Testcontainers(disabledWithoutDocker = true)`, `@ServiceConnection`, `RawHttpClient`, captura de salida). Copiar de una `*IT` existente las propiedades que apagan RabbitMQ en pruebas. Cada caso lee `code` y `requestId` del cuerpo con `ObjectMapper`. Usar identidades `X-User-Id: uid-cm290-a` y `uid-cm290-b`.

| Prueba | Situación | Esperado |
|---|---|---|
| `listProfiles_shouldReturn503_whenPoolIsExhausted` | Tomar las 2 conexiones del `DataSource` y pedir `GET /api/v1/profiles`; soltar al terminar | 503 `DATABASE_UNAVAILABLE`, `requestId`, `Content-Type` con `charset=UTF-8`, en menos de 3 s |
| `listProfiles_shouldReturn503_whenServerTerminatesConnections` | Con una conexión admin aparte: `SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid()` y luego la petición | 503 |
| `listProfiles_shouldRecover_whenDatabaseComesBack` | Pausar el contenedor (`postgres.getDockerClient().pauseContainerCmd(postgres.getContainerId()).exec()`), pedir `GET /api/v1/profiles`, reanudarlo (`unpauseContainerCmd`) y pedir otra vez | 503 con el contenedor en pausa y 200 con lista vacía al reanudarlo |
| `allDatabaseRoutes_shouldReturn503_whenDatabaseIsDown` (`@ParameterizedTest`) | Con el contenedor en pausa: `GET /api/v1/profiles`, `POST /api/v1/profiles` (cuerpo válido de las pruebas existentes), `GET /api/v1/profiles/{uuid cualquiera}`, `PATCH /api/v1/profiles/{uuid cualquiera}` con `{"headline":"x"}`, `GET /api/v1/profiles/professional-roles` | 503 `DATABASE_UNAVAILABLE` en las cinco |
| `createProfile_shouldKeepLockConflict_whenLockTimesOut` | Es la prueba existente de bloqueo de creación: confirmar que sigue en verde | 409 `PROFILE_CREATION_IN_PROGRESS` |
| `listProfiles_shouldReturnSameAnswer_whenRepeated20Times` | 20 veces el primer caso | 20 × 503 con el mismo `code` |
| `listProfiles_shouldNotFail_whenManyRequestsRunInParallel` | 30 hilos con el pool agotado | 30 × 503; ninguna dura más de 3 s |
| `createProfile_shouldRollBack_whenConnectionsAreTerminatedBeforeCommit` | Creación con las conexiones cortadas antes del commit (si el arnés no permite cortar justo antes del commit, cortar antes de la petición) | 503 y, al volver la base, `count(*)` de `perfil_profesional` y de `evento_saliente` igual que antes. Si no se puede cortar en ese punto de forma estable, detenerse y reportar a Paula en lugar de inventar |
| `databaseUnavailable_shouldNotEchoDatabaseMessage` | Cuerpo y registro de cualquiera de las anteriores | Sin `HikariPool`, `jdbc`, `postgres` ni `org.` en el cuerpo; sin mensajes de excepción en el registro |

**Trampas:** Hikari no revalida las conexiones usadas en los últimos 500 ms (`aliveBypassWindowMs`): una conexión devuelta al pool justo antes de pausar la base se entrega sin comprobar y la primera consulta se cuelga en lugar de fallar rápido. Tras `pauseContainerCmd`, esperar unos 800 ms antes de la primera petición (en `listProfiles_shouldRecover_whenDatabaseComesBack` y en la prueba parametrizada de rutas). `docker pause` mantiene el puerto y deja las conexiones colgadas hasta `connection-timeout`; es la forma más estable de simular «base que no responde» sin cambiar el puerto. No compartir contexto con otras pruebas (`@DirtiesContext(classMode = AFTER_CLASS)` si hace falta). Cada prueba devuelve el contenedor a su estado normal en un `@AfterEach`. Si la prueba con `pause` no es estable en esta máquina, anotar la salida real, usar `pg_terminate_backend` más pool agotado (deterministas) y reportar la limitación.

**Comando:** `./mvnw.cmd -B -ntp verify -Dit.test=DatabaseUnavailableEndToEndIT -Dtest=NoExiste -Dsurefire.failIfNoSpecifiedTests=false`; confirmar en la salida el número de pruebas ejecutadas.

**Commit:** `CM-290 | test(perfil): comprobar con PostgreSQL real que cada forma de indisponibilidad responde 503`.

---

## T5 · OpenAPI (REQ-07, IOP-01)

En `infrastructure/config/OpenApiConfig.java`, dentro de `protocolErrorResponses()`, agregar el 503 sin pisar el que ya traigan las dos operaciones de experiencia laboral y educación (`BIRTH_DATE_UNAVAILABLE`):

- Si la operación **no** tiene respuesta `503`: agregar la de `protocolError(...)` con descripción `DATABASE_UNAVAILABLE: la base de datos no está disponible; la persona puede reintentar`, ejemplo `baseNoDisponible`, estado 503, título «Servicio no disponible», detalle «Ocurrió un error. Inténtalo de nuevo.», código `DATABASE_UNAVAILABLE`.
- Si **ya tiene** `503`: conservar su contenido y agregarle el ejemplo `baseNoDisponible` en `application/problem+json`, y anteponer a su descripción `DATABASE_UNAVAILABLE: …` separado por « · » del texto existente; ambos códigos quedan visibles.

Leer antes `protocolError` y cómo `OpenApiDocumentIT` recorre las operaciones; no tocar los controladores.

**Prueba** en `OpenApiDocumentIT`: `apiDocs_shouldDocumentDatabaseUnavailableInEveryOperation_whenRequested`: para cada operación de `/v3/api-docs` (las 15), `responses['503'].description` contiene `DATABASE_UNAVAILABLE` y el ejemplo `baseNoDisponible` existe; en `POST …/work-experiences` y `POST …/educations` la descripción conserva además `BIRTH_DATE_UNAVAILABLE`. Y `apiDocs_shouldKeepBirthDate503_whenRequested` si no cabe en la anterior.

**Comando:** `./mvnw.cmd -B -ntp verify -Dit.test=OpenApiDocumentIT -Dtest=NoExiste -Dsurefire.failIfNoSpecifiedTests=false`.

**Commit:** `CM-290 | docs(perfil): documentar en OpenAPI el 503 de la base de datos`.

---

## T6 · Postman, ADR y documentos (REQ-07)

1. **`docs/errores.md`:** agregar la fila `DATABASE_UNAVAILABLE` | 503 | «Ocurrió un error. Inténtalo de nuevo.» | todas las rutas de Perfil que usan la base | `DatabaseUnavailability` en la tabla de códigos, junto a `BIRTH_DATE_UNAVAILABLE`; en la sección «Respuestas que difieren del estándar» (cerca de la línea 195) **retirar** la fila del 500 por base que no responde, si existe, y agregar a las limitaciones: sin tiempo máximo de consulta (una consulta que no termina no produce error; destino: tarea de medición de consultas) y los consumidores de RabbitMQ y el Job de relevo no pasan por el manejador. Seguir la sección «Cómo se agrega un código» si existe.
2. **ADR** `docs/adr/0009-base-de-datos-no-disponible.md`, con la plantilla de `0008-errores-de-protocolo-previsibles.md` (leerla): Contexto (evidencia E-1 a E-6), Decisión (clasificador, lista de REQ-02, 5 s, mismo mensaje, sin `Retry-After`), Alternativas descartadas (spec §15), Consecuencias (cambio aditivo avisado a Frontend; sin tiempo de consulta) y Estado: `Propuesta` hasta que Paula diga que la acepta.
3. **`CLAUDE.md`, sección 4:** agregar una viñeta: «Una base de datos no disponible (caída, conexión cortada, pool agotado o consulta sin respuesta a tiempo) responde 503 `DATABASE_UNAVAILABLE` ([ADR 0009](docs/adr/0009-base-de-datos-no-disponible.md)).» No reescribir más.
4. **Postman:** archivo nuevo `docs/CAMEIA_Perfil_Sprint1_base-no-disponible.postman_collection.json` con D-01 y D-02 (spec §13), aparte de la colección principal para que esta se corra completa con la base encendida; cada petición comprueba `Content-Type` y `requestId`, y D-01 además `pm.response.responseTime < 8000`. Anotar el archivo y el comando en `README.md` y en la sección «Pruebas manuales» de `docs/errores.md`.

**Verificación:** `./mvnw.cmd -B -ntp test -Dtest=ErrorCodeDocumentationTest,ErrorCodeTest,ErrorCatalogTest` (ahora debe pasar) y `node -e "JSON.parse(require('fs').readFileSync('docs/CAMEIA_Perfil_Sprint1.postman_collection.json','utf8'))"`.

**Commit:** `CM-290 | docs(perfil): catálogo de errores, ADR y colección de Postman del 503 de la base de datos`.

---

## T7 · Verificación final (una sola vez, sobre la punta)

1. `./mvnw.cmd -B -ntp clean verify` → `BUILD SUCCESS`; suite completa (con las `*IT` ejecutadas, no omitidas) y `ArquitecturaTest` en verde.
2. Cobertura: `target/site/jacoco/jacoco.csv`; ≥ 90 % de líneas y ramas en `DatabaseUnavailability`, `ApiExceptionHandler` y `ErrorCatalog`; reportar el número real y cada línea o rama sin cubrir con su razón.
3. Newman manual: `docker compose up -d`, `docker compose stop` del servicio de base de datos (nombre en `docker-compose.yml`), `newman run docs/CAMEIA_Perfil_Sprint1_base-no-disponible.postman_collection.json` con el entorno `docs/perfil-local.postman_environment.json` → 2 peticiones, 0 fallos; volver a encender la base y repetir `GET /api/v1/profiles` → 200. Además la colección principal completa, con la base encendida, sin fallos.
4. Tiempo real: con la base detenida y la configuración empaquetada, medir el tiempo de la respuesta (esperado ≈ 5 s) y pegarlo; es la medición HTTP de Perfil que la spec deja como inferencia.
5. Revisión según `backend-estandar` §6 (`/simplify`, `/code-review high`, silent-failure-hunter) y entrega de la guía de revisión a Paula en el chat.

---
