# CM-290 · 503 `DATABASE_UNAVAILABLE` cuando la base de datos de Perfil no está disponible

| Dato | Valor |
|---|---|
| Tarea Jira | CM-290 (tarea, Sprint 3), responsable Paula Andrea Muñoz Delgado |
| Repositorio | `cameia-perfil`, base `origin/develop` = `8ed2cf9` (se vuelve a anotar el SHA al preparar la rama de código) |
| Depende de | Nada sin fusionar |
| Estado | **LISTA PARA EJECUTAR.** Aprobada por Paula Andrea Muñoz Delgado el 19-oct-2026, con las decisiones P-1 a P-5 en su opción recomendada (A; espera de 5 s confirmada tras medir 0,5 a 0,66 s en la primera conexión del entorno) |
| Estimación | 3 a 4 h de Backend (clasificador, manejador, configuración del pool, pruebas con base real, OpenAPI, Postman y documentos). Se informa a Vela por `comunicaciones/` |
| Servicio hermano | `cameia-cuentas` tiene su propia spec de la misma tarea; cada una se entiende sola |

## 1. Objetivo

Cuando Perfil no puede hablar con su base de datos PostgreSQL, hoy responde `500 INTERNAL_ERROR`, el mismo error genérico que un defecto de programación. Una prueba actual lo fija así (`ProfileControllerTest.postProfiles_shouldReturnGeneric500_whenDatabaseFails`). La persona (y Frontend) no pueden distinguir «reintenta en unos segundos» de «algo se rompió».

Esta tarea hace que, en cualquier ruta de Perfil que use la base (las 15 operaciones publicadas):

1. Una base **no disponible** (apagada, conexión cortada, pool de conexiones agotado, consulta que no termina) responda `503` con el código estable `DATABASE_UNAVAILABLE`, la forma común de error (`code`, `requestId`, `application/problem+json;charset=UTF-8`) y el mensaje genérico que ya usa `BIRTH_DATE_UNAVAILABLE`.
2. La espera máxima por una conexión sea corta (5 s), para que el 503 llegue antes de que el Gateway cierre la petición por tiempo (30 s).
3. Todo fallo de la base que **no** sea indisponibilidad (violación de restricción, SQL inválido, espera de bloqueo agotada que hoy es 409) conserve su respuesta de hoy.

## 2. Evidencia (laboratorio con PostgreSQL 16 real, 19-oct-2026)

Laboratorio desechable (no va a ningún PR) sobre `origin/develop` `8ed2cf9`, con repositorios reales de Perfil (Testcontainers, pool de 2 conexiones, espera de 1,5 s, tiempo máximo de consulta de 0,5 s). Registros: `analisis/evidencia/CM-290/` de la carpeta raíz de trabajo. El mismo laboratorio sobre Cuentas midió además la petición HTTP completa; Perfil usa la misma pila (Spring Boot 4.1.1, Hibernate, Hikari), y la medición HTTP de Perfil se repite en la verificación final (T7).

| # | Situación | Excepción que llega al manejador (medida) |
|---|---|---|
| E-1 | Pool agotado | `CannotCreateTransactionException` ← `JDBCConnectionException` ← `SQLTransientConnectionException` («Connection is not available, request timed out») |
| E-2 | Consulta lenta por bloqueo de tabla | `QueryTimeoutException` ← `QueryTimeoutException` (Hibernate) ← `PSQLException` SQLState `57014` |
| E-3 | Conexiones cortadas por el servidor antes de la consulta (`pg_terminate_backend`) | `JpaSystemException` ← `org.hibernate.TransactionException` («Unable to rollback») ← `SQLException` «Connection is closed», **sin SQLState** |
| E-4 | Corte dentro de una transacción | igual que E-3 |
| E-5 | Corte justo antes del commit | `DataAccessResourceFailureException` ← `PSQLException` `57P01`; la conexión cerrada viaja en **`getSuppressed()`**, no en `getCause()` |
| E-6 | Base detenida con la configuración por defecto del pool (medida en Cuentas) | 500 tras 30026 ms y 30039 ms: `connection-timeout` por defecto de Hikari = 30 s |

Hallazgos:

- **H-1** La causa verdadera puede estar en `getSuppressed()` (E-5) o no tener SQLState (E-3). Un clasificador que solo mira `getCause()` y el SQLState no reconoce dos de los cinco cortes. `RedactedException.sqlState` de Perfil tampoco recorre las suprimidas.
- **H-2** El `connection-timeout` por defecto (30 s) es igual al `response-timeout` del Gateway (`GATEWAY_TIMEOUT_MS`, 30 s): el 503 casi nunca se vería.
- **H-3** Perfil no configura tiempo máximo de consulta: una consulta que no termina no produce error. Fuera de alcance (§16, D-6).
- **H-4** El adaptador del perfil ya traduce la espera de bloqueo agotada (SQLState `55P03`) a su propio 409 (`PROFILE_CREATION_IN_PROGRESS`, `PROFILE_UPDATE_IN_PROGRESS`). Ese camino no cambia: `55P03` no es indisponibilidad.
- **H-5** `/actuator/health/readiness` no consulta la base (la plataforma deja de enrutar tráfico solo si la aplicación misma se declara no lista): no cambia.

Controles negativos medidos (siguen sin ser indisponibilidad): sintaxis SQL inválida `42601` (`BadSqlGrammarException`) y espera de bloqueo agotada `55P03` (`CannotAcquireLockException`); en el laboratorio de Cuentas, además, `22012`, `23502` e `IllegalStateException`.

## 3. Requisitos (EARS)

- **REQ-01** Cuando una excepción que llegue al manejador general de Perfil (`ApiExceptionHandler.handleUnexpected`), o alguna de sus causas o excepciones suprimidas (`getCause()` y `getSuppressed()`, máximo 64 nodos, sin repetir nodos), sea una indisponibilidad de la base de datos según REQ-02, el sistema debe responder `503` con `code` `DATABASE_UNAVAILABLE`, `title` «Servicio no disponible», `detail` «Ocurrió un error. Inténtalo de nuevo.», `application/problem+json;charset=UTF-8`, `requestId` y el encabezado `X-Request-Id`.
- **REQ-02** Es indisponibilidad de la base cualquiera de estas excepciones en el árbol de REQ-01: `DataAccessResourceFailureException`, `TransientDataAccessResourceException`, `QueryTimeoutException` (de Spring), `org.hibernate.exception.JDBCConnectionException`, `org.hibernate.TransactionException` (con causa `SQLException`), `SQLTransientConnectionException`, `SQLNonTransientConnectionException`, `SQLTimeoutException`, o una `SQLException` cuyo SQLState empiece por `08` (error de conexión) o sea `57P01`, `57P02`, `57P03` (apagado o arranque del servidor) o `53300` (demasiadas conexiones). `org.hibernate.TransactionException` cubre E-3/E-4, pero solo cuenta cuando su causa directa es una `SQLException` (en una conexión cerrada llega sin SQLState): Hibernate también la lanza por un uso incorrecto de la transacción, que es un defecto de programación y debe seguir siendo 500 `INTERNAL_ERROR`. Sin causa SQL cuenta únicamente si el resto del árbol trae otra pieza de la lista.
- **REQ-03** No es indisponibilidad, y por tanto conserva su respuesta actual: `DataIntegrityViolationException` (22xxx, 23xxx), `BadSqlGrammarException` (42xxx), `CannotAcquireLockException` (55P03, que el adaptador ya traduce a 409), `IllegalStateException`, cualquier `BusinessException` y cualquier otra excepción que REQ-02 no nombre.
- **REQ-04** El sistema debe registrar la respuesta de REQ-01 en `ERROR` una sola vez por petición, y ningún otro registro `ERROR` o `WARN` de Hibernate ni de la transacción de Spring debe quedar en la salida de la petición, porque esos registros citan el SQL y el mensaje de PostgreSQL y no llevan el `requestId`; para eso `application.yml` baja `org.hibernate.orm.jdbc.error` a `ERROR` y apaga `org.springframework.transaction.interceptor.TransactionInterceptor`. Los avisos del pool de conexiones (`com.zaxxer.hikari`, texto fijo sin datos) se conservan: la base detenida produce «Failed to validate connection» por cada conexión caducada, y el mismo aviso señala conexiones cortadas con la base viva (`maxLifetime` mal ajustado). La línea del manejador lleva `code`, `requestId`, `firebaseUid`, `sqlState` (el primero que aparezca en el árbol, o `-`) y la traza **sin mensajes** (`RedactedException`, como el resto de fallos de Perfil: PostgreSQL y Hibernate ponen valores de columnas y SQL con parámetros en sus mensajes). La respuesta nunca contiene el mensaje de la excepción de la librería.
- **REQ-05** El sistema debe fijar `spring.datasource.hikari.connection-timeout: 5000` (5 s) en `application.yml`. Se puede sobrescribir en el despliegue con la variable estándar `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT` (sin variable nueva en `.env.example`).
- **REQ-06** `DATABASE_UNAVAILABLE` solo se decide en el manejador general (`Exception.class`) y nunca reemplaza un manejador más específico: una `BusinessException` (incluidos `BirthDateUnavailableException` y los 409 de bloqueo) conserva su respuesta.
- **REQ-07** El sistema debe documentar `DATABASE_UNAVAILABLE` en `ErrorCode`, en `ErrorCatalog`, en `docs/errores.md`, en OpenAPI de las 15 operaciones publicadas (mediante el `OperationCustomizer` existente, sin repetirlo en cada controlador) y en un ADR. En las dos operaciones que ya declaran el 503 `BIRTH_DATE_UNAVAILABLE` (`POST …/work-experiences` y `POST …/educations`) la respuesta 503 describe y muestra los dos códigos.
- **REQ-08** La prueba existente que fija el 500 ante una base caída (`postProfiles_shouldReturnGeneric500_whenDatabaseFails`) pasa a esperar 503 `DATABASE_UNAVAILABLE`, y se agrega una prueba que fija que un fallo imprevisto con la misma ruta sigue siendo 500.
- **REQ-09** El 503 de la base y el 503 de la fecha de nacimiento (`BIRTH_DATE_UNAVAILABLE`) deben ser distinguibles por `code` y compartir título, estado y mensaje. Si la base falla mientras se lee la réplica de la fecha de nacimiento, gana `DATABASE_UNAVAILABLE` (la causa es la base, no que la réplica aún no llegó).

## 4. Contrato

Cambio aditivo para Frontend: un código nuevo, `DATABASE_UNAVAILABLE` (503), y casos que pasan de 500 a 503.

| Petición | Antes | Después |
|---|---|---|
| Cualquier operación de `/api/v1/profiles/**` con la base caída o el pool agotado | 500 `INTERNAL_ERROR` tras hasta 30 s | 503 `DATABASE_UNAVAILABLE` tras 5 s como máximo |
| Las mismas rutas con una violación de restricción | 500 `INTERNAL_ERROR` | sin cambio |
| `POST /api/v1/profiles` y las escrituras con espera de bloqueo agotada | 409 `PROFILE_CREATION_IN_PROGRESS` / `PROFILE_UPDATE_IN_PROGRESS` | sin cambio |
| `POST …/work-experiences` y `…/educations` cuando la réplica de la fecha de nacimiento aún no llegó | 503 `BIRTH_DATE_UNAVAILABLE` | sin cambio |

Frontend consume Perfil a través del Gateway. Aviso a Frontend al abrir el PR: código nuevo `DATABASE_UNAVAILABLE` (503, «Ocurrió un error. Inténtalo de nuevo.»); la acción esperada es mostrar el mensaje y permitir reintentar, igual que con `BIRTH_DATE_UNAVAILABLE`.

## 5. Validación de entrada y casos de prueba con datos literales

La tarea no agrega campos de entrada. Casos de prueba:

| Entrada | Caso | Valor literal | Resultado | Prueba |
|---|---|---|---|---|
| Excepción | Cadena de pool agotado | `CannotCreateTransactionException` ← `JDBCConnectionException` ← `SQLTransientConnectionException` | `matches` verdadero | `DatabaseUnavailabilityTest.matches_shouldBeTrue_whenPoolIsExhausted` |
| | Causa en `getSuppressed()` | `DataAccessResourceFailureException` ← `SQLException("FATAL","57P01")` con suprimida `SQLException("Connection is closed")` | verdadero | `…matches_shouldBeTrue_whenSqlStateIsAdminShutdown` |
| | Corte sin SQLState | `JpaSystemException` ← `org.hibernate.TransactionException` ← `SQLException("Connection is closed")` | verdadero | `…matches_shouldBeTrue_whenRollbackFailsOnClosedConnection` |
| | Causa solo en suprimidas | `RuntimeException` con suprimida `SQLException("x","08006")` | verdadero | `…matches_shouldBeTrue_whenOnlySuppressedIsConnectionError` |
| | SQLState de conexión | `"08001"`, `"08006"`, `"57P01"`, `"57P02"`, `"57P03"`, `"53300"` | verdadero (6 casos) | `…matches_shouldBeTrue_whenSqlStateIsConnectionClass` |
| | Consulta lenta | `QueryTimeoutException` ← `SQLException("canceling","57014")` | verdadero | `…matches_shouldBeTrue_whenQueryTimesOut` |
| | Negativos | `DataIntegrityViolationException`(`23502`), `BadSqlGrammarException`(`42601`), `CannotAcquireLockException`(`55P03`), `IllegalStateException`, `RuntimeException` sin causa, `SQLException("x","22012")` | falso (6 casos) | `…matches_shouldBeFalse_whenNotAvailabilityProblem` |
| | Nulo | `null` | falso | `…matches_shouldBeFalse_whenNull` |
| | Ciclo | `a` con causa `b`, `b` con causa `a` | termina; falso | `…matches_shouldTerminate_whenCauseCycle` |
| | TransactionException sin causa SQL | `TransactionException("Unable to commit")`; `TransactionException` con causa `IllegalStateException` | falso (2 casos); verdadero si el árbol trae además `SQLException("x","08006")` suprimida | `…matches_shouldBeFalse_whenTransactionExceptionHasNoSqlCause`, `…matches_shouldBeTrue_whenTransactionExceptionHasConnectionPieceElsewhere` |
| | Tope | 100 `RuntimeException` encadenadas con `SQLException("x","08001")` al fondo; 63 más el SQLException como nodo 64 | falso; verdadero | `…matches_shouldStopAtLimit_whenChainIsTooDeep`, `…matches_shouldFind_whenOutageIsAtNode64` (63 más el SQLException) y `…matches_shouldNotFind_whenOutageIsAtNode65` (64 más el SQLException: falso) |
| Manejador | 503 con forma común | `DataAccessResourceFailureException("host=10.0.0.5 password=x")` | 503, `code`, `requestId`; cuerpo y registro sin `10.0.0.5` ni `password` | `ApiExceptionHandlerTest.databaseUnavailable_shouldReturn503_whenAvailabilityProblem` |
| | Registro único | la misma | un `ERROR` con `code`, `requestId`, `sqlState` | `…databaseUnavailable_shouldLogOnce_whenAvailabilityProblem` |
| Punta a punta | Registro único de la petición | pool agotado, y conexiones cortadas por el servidor | en toda la salida de la petición, sin filtrar por `requestId`, una sola línea `ERROR` o `WARN`: la de `ApiExceptionHandler` | `DatabaseUnavailableEndToEndIT.databaseUnavailable_shouldNotEchoDatabaseMessage` y `…shouldLogOnlyHandlerLine_whenServerTerminatesConnections` |
| | Imprevisto | `IllegalStateException("x")` | 500 `INTERNAL_ERROR` (sin cambio) | `ApiExceptionHandlerTest.unexpectedException_shouldReturnGeneric500_whenThrown` existente |
| | Negocio | `BirthDateUnavailableException` | 503 `BIRTH_DATE_UNAVAILABLE` (sin cambio) | prueba existente |
| Controlador | Base caída en la creación | `createProfile` lanza `DataAccessResourceFailureException("conexión")` | 503 `DATABASE_UNAVAILABLE`, cuerpo sin «conexión» | `ProfileControllerTest.postProfiles_shouldReturn503_whenDatabaseFails` (reemplaza la del 500) |
| | Fallo imprevisto en la creación | `createProfile` lanza `IllegalStateException("x")` | 500 `INTERNAL_ERROR` | `ProfileControllerTest.postProfiles_shouldReturnGeneric500_whenUnexpectedFailure` |
| Punta a punta | Pool agotado por HTTP | `maximum-pool-size=2`, `connection-timeout=1500`, dos conexiones tomadas, `GET /api/v1/profiles` con `X-User-Id: uid-cm290-a` | 503 `DATABASE_UNAVAILABLE` en menos de 3 s | `DatabaseUnavailableEndToEndIT.listProfiles_shouldReturn503_whenPoolIsExhausted` |
| | Corte de conexiones | `pg_terminate_backend` sobre las conexiones del pool y luego la misma petición | 503 | `…shouldReturn503_whenServerTerminatesConnections` |
| | Base en pausa y recuperación | contenedor en pausa → 503; reanudado → 200 con lista vacía | 503 y luego 200 | `…shouldRecover_whenDatabaseComesBack` |
| | Rutas | `GET /api/v1/profiles`, `POST /api/v1/profiles`, `GET /api/v1/profiles/{id}` (uuid cualquiera), `PATCH /api/v1/profiles/{id}` con `{"headline":"x"}`, `GET /api/v1/profiles/professional-roles` con la base en pausa | 503 `DATABASE_UNAVAILABLE` en las cinco | `…allDatabaseRoutes_shouldReturn503_whenDatabaseIsDown` (5 casos) |
| | Bloqueo no es caída | espera de bloqueo agotada en la creación | 409 `PROFILE_CREATION_IN_PROGRESS` (sin cambio) | prueba existente de bloqueo de creación |
| | Configuración | valor efectivo de `spring.datasource.hikari.connection-timeout` | `5000` | `DatabasePoolConfigurationIT.connectionTimeout_shouldBeFiveSeconds` |

## 6. Base de datos

Sin migración ni cambio de esquema. El cambio es de configuración del pool de conexiones (REQ-05).

## 7. Casos borde (estándar §C)

| Grupo | Caso | Tratamiento |
|---|---|---|
| Nulos y vacíos | Excepción sin causa, sin suprimidas, mensaje nulo | `matches` falso sin `NullPointerException` |
| Límites | Árbol de 64 nodos exactos; de 65 | El nodo 64 se visita y el 65 no |
| Unicode | Mensaje con tildes o secuencias de control | Nunca se copia a la respuesta ni al registro (`RedactedException` descarta los mensajes) |
| Fechas y zonas | — | No aplica |
| Duplicados e idempotencia | 20 peticiones iguales con la base caída | 20 × 503 con el mismo `code`; ninguna fila nueva al volver la base. Prueba `…shouldReturnSameAnswer_whenRepeated20Times` |
| Concurrencia y doble envío | 30 peticiones en paralelo con el pool agotado | Todas 503, ninguna 500; ninguna espera más de `connection-timeout` + 1 s |
| Falla a medias | Corte antes del commit de una creación de perfil (perfil y evento de salida en la misma transacción) | La transacción se deshace: ni perfil ni evento. Prueba: tras el 503 y la recuperación, `count(*)` de `perfil_profesional` y `evento_saliente` igual que antes |
| Dependencia caída o lenta | Base caída durante la lectura de la réplica de fecha de nacimiento | 503 `DATABASE_UNAVAILABLE`, no `BIRTH_DATE_UNAVAILABLE` (REQ-09); prueba unitaria de `ProfileAppService` con la réplica simulada lanzando el error de la base |
| Recurso ajeno / estado inválido | — | No aplica: el 503 ocurre antes de cualquier decisión de negocio; las comprobaciones de dueño siguen igual |
| Recuperación | La base vuelve | La primera petición posterior responde con normalidad (Hikari repone el pool) |
| Entradas de ataque | Mensaje de la base con SQL, host o usuario | No se refleja en la respuesta ni en el registro; prueba con `SQLException("host=10.0.0.5 password=x","08001")` |

## 8. Seguridad (ASVS nivel 1 y OWASP API Top 10)

| Control | Qué hace | Prueba |
|---|---|---|
| ASVS 4.1.1 | El 503 lleva `application/problem+json;charset=UTF-8` | Pruebas de punta a punta de §5 y `ResponseCharsetIT` existente |
| ASVS 15.3.1 / API3 | La respuesta es un DTO de error con campos fijos; no revela clases, SQL, host ni usuario de la base | `…databaseUnavailable_shouldReturn503_whenAvailabilityProblem` |
| ASVS 8.2.2 | La identidad sigue viniendo de `X-User-Id`; el 503 no cambia esa comprobación | Pruebas de identidad existentes |
| API4 (consumo de recursos) | La espera por una conexión pasa de 30 s a 5 s: una base caída no retiene hilos de Tomcat 30 s por petición | `DatabasePoolConfigurationIT`; prueba de concurrencia de §7 |
| API8 (configuración) | El mensaje de la excepción no llega al cliente ni al registro | Prueba de entradas de ataque de §7 |

La matriz ASVS 5.0.0 nivel 1 de `cameia-infra` no trae un requisito propio de «mensaje genérico ante un error inesperado»; se cubre con API8 y 15.3.1.

## 9. Atributos de calidad de CAMEIA

La tarea no pertenece a una épica con atributos declarados; se aplican los transversales (anexo `Anexo_Restricciones_Atributos_Calidad`):

| Atributo | Métrica | Límite | Cómo | Prueba |
|---|---|---|---|---|
| AC-0002 (disponibilidad) | DES-04 | 5xx < 1 % por causas previsibles | Una base caída es un 503 conocido y no un 500 imprevisto; la espera baja de 30 s a 5 s | Pruebas de punta a punta de §5 |
| AC-0003 (seguridad) | SEG-04 | Sin datos personales en logs ni respuestas | REQ-04 | Prueba de entradas de ataque de §7 |
| AC-0004 (fiabilidad) | FIA-01 | Ningún fallo deja estado a medias | La transacción se deshace en el corte | Falla a medias de §7 |
| AC-0004 (fiabilidad) | FIA-02 | La misma petición, la misma respuesta | 20 repeticiones iguales | `…shouldReturnSameAnswer_whenRepeated20Times` |
| AC-0006 (interoperabilidad) | INT | Error con `code`, mensaje claro y distinguible | REQ-01, REQ-09 | Pruebas de §5 |
| Transversal | MAN-01 | ≥ 90 % de líneas y ramas en lo nuevo o modificado | JaCoCo | `./mvnw.cmd clean verify` |
| Transversal | IOP-01 | OpenAPI completo | 503 con ejemplo en las 15 operaciones | `OpenApiDocumentIT.apiDocs_shouldDocumentDatabaseUnavailableInEveryOperation_whenRequested` |

## 10. Errores

| Código | HTTP | Mensaje | Caso |
|---|---|---|---|
| `DATABASE_UNAVAILABLE` | 503 | «Ocurrió un error. Inténtalo de nuevo.» | **Código nuevo.** La base no responde, la conexión se cortó, el pool se agotó o una consulta pasó su tiempo máximo. Vocabulario: sujeto `DATABASE` + causa `UNAVAILABLE` (la causa ya existe en el vocabulario cerrado, como en `BIRTH_DATE_UNAVAILABLE`) |
| `BIRTH_DATE_UNAVAILABLE` | 503 | igual | La réplica de la fecha de nacimiento aún no llegó (sin cambio) |
| `INTERNAL_ERROR` | 500 | «Ocurrió un error. Inténtalo de nuevo.» | Solo lo imprevisto |

**Excepción de negocio.** La regla «todo error previsible tiene excepción de negocio» se cumple con una salvedad documentada: la indisponibilidad de la base no nace en el dominio ni en un puerto, sino en Hibernate, Spring y el controlador JDBC, y puede aparecer al abrir, consultar, confirmar o deshacer cualquier transacción. Se reconoce en el manejador, igual que `BadParameterEncoding` reconoce el rechazo de Tomcat. La alternativa (envolver los dos adaptadores de persistencia en una excepción de dominio) no cubre el inicio y el cierre de la transacción de los servicios de aplicación, donde ocurren E-1, E-3 y E-5 (D-1).

## 11. Pruebas, cobertura y comandos

| Capa | Pruebas |
|---|---|
| Presentación (unitarias) | `DatabaseUnavailabilityTest` (nueva), `ApiExceptionHandlerTest` (se amplía), `ErrorCatalogTest` (existente, debe cubrir el código nuevo) |
| Controlador | `ProfileControllerTest` (una prueba cambia de 500 a 503 y se agrega la del 500 imprevisto) |
| Configuración | `DatabasePoolConfigurationIT` (nueva) |
| Punta a punta | `DatabaseUnavailableEndToEndIT` (nueva, `RANDOM_PORT`, Testcontainers, pool de 2 conexiones y `connection-timeout=1500` por propiedades de la prueba, `RawHttpClient` existente) |
| Contrato | `OpenApiDocumentIT` (una prueba nueva), `ErrorCodeDocumentationTest` y `ErrorCodeTest` (existentes) |
| Arquitectura | `ArquitecturaTest` (existente, debe seguir verde) |

Clases a medir: `DatabaseUnavailability`, `ApiExceptionHandler` (rama nueva), `OpenApiConfig` (rama de unión del 503) y `ErrorCatalog`. Comandos: Docker encendido; `./mvnw.cmd -B -ntp clean verify` (las `*IT` las corre Failsafe); `target/site/jacoco/jacoco.csv`; Newman (§13).

## 12. Lo que la aplicación no puede cambiar (se documenta)

- Si Cloud Run o Cloud SQL cortan el tráfico antes de que Perfil responda, o si el Gateway agota su propio tiempo, la respuesta no la da Perfil. Con `connection-timeout` de 5 s la respuesta de Perfil llega antes que el límite del Gateway (30 s).
- Una consulta que no termina no produce error mientras no haya tiempo máximo de consulta (H-3, D-6).
- Los consumidores de RabbitMQ y el Job de relevo no pasan por el manejador HTTP: su comportamiento ante una base caída no cambia.

## 13. Postman y Newman

Archivo nuevo `docs/CAMEIA_Perfil_Sprint1_base-no-disponible.postman_collection.json`, aparte de la colección vigente `docs/CAMEIA_Perfil_Sprint1.postman_collection.json`: la colección principal se corre completa con la base encendida (README), y estas dos peticiones esperan 503, así que dentro de ella fallarían. Usa `{{base_url}}` del entorno `docs/perfil-local.postman_environment.json`.

| # | Petición | Espera |
|---|---|---|
| D-01 | `GET {{base_url}}/api/v1/profiles` con `X-User-Id: uid-cm290-newman`, **con el servicio de base de datos de compose detenido** (`docker compose stop db`) | 503 `DATABASE_UNAVAILABLE`, `requestId`, `charset=UTF-8`, en menos de 8 s |
| D-02 | `GET {{base_url}}/api/v1/profiles/professional-roles` en las mismas condiciones | 503 `DATABASE_UNAVAILABLE` |

El archivo es manual porque exige detener la base: se corre con `docker compose stop db`, Newman sobre ese archivo (2 peticiones, las dos en 503) y `docker compose start db`. Con la base encendida fallan a propósito. El recorrido automático lo cubre `DatabaseUnavailableEndToEndIT`. El archivo y el comando se anotan en el `README.md` (sección de Newman) y en `docs/errores.md` (sección «Pruebas manuales»).

## 14. Línea base

Antes de editar: `git fetch`, rama `CM-290-error-503-base-de-datos-no-disponible` desde `origin/develop` y `./mvnw.cmd -B -ntp clean verify` en verde. Si falla, detenerse y reportar. Resultado de la línea base del 19-oct-2026: ver `analisis/revisiones/revision-spec_CM-290.md`.

## 15. Decisiones

| # | Decisión | Por qué | Descartado | Quién |
|---|---|---|---|---|
| D-1 | Un clasificador `DatabaseUnavailability` en `presentation.advice` y una rama en el manejador general | Cubre abrir, consultar, confirmar y deshacer la transacción con una sola pieza; sigue el precedente de `BadParameterEncoding`; mínimo código | Envolver los adaptadores de persistencia en una excepción de dominio (no cubre el inicio ni el cierre de la transacción, donde ocurre la mayoría de los cortes); un aspecto AOP (más maquinaria que una rama) | Paula, 19-oct-2026 (opción recomendada) |
| D-2 | Cuentan como indisponibilidad las excepciones de REQ-02, incluidas `QueryTimeoutException` y `org.hibernate.TransactionException`; no cuentan 22xxx, 23xxx, 42xxx ni 55P03 | Lo medido en §2: cada una es un corte de conexión o un tiempo agotado; las demás son fallos de la consulta o del dato, y 55P03 ya tiene su 409 | Incluir 55P03 (rompería los 409 vigentes) | Paula, 19-oct-2026 (opción recomendada) |
| D-3 | `connection-timeout` de Hikari en 5 s en `application.yml` de ambos servicios | H-2: con 30 s el 503 no llega antes del límite del Gateway; 5 s absorbe un arranque en frío de la conexión y deja margen | Dejar 30 s (el 503 sería invisible); 3 s (riesgo de falsos 503 en arranque en frío de Cloud Run); una variable nueva (DevOps, `.env.example`) | Paula, 19-oct-2026 (opción recomendada) |
| D-4 | Mismo título y mensaje que `BIRTH_DATE_UNAVAILABLE` | El usuario no puede actuar distinto; un mensaje específico solo ayudaría a un atacante a conocer la infraestructura | Mensaje propio | Paula, 19-oct-2026 (opción recomendada) |
| D-5 | Sin encabezado `Retry-After` | No se sabe cuándo vuelve la base | `Retry-After: 5` fijo | Paula, 19-oct-2026 (opción recomendada) |
| D-6 | No se configura tiempo máximo de consulta en esta tarea | Cambia el comportamiento de consultas lentas legítimas y pide medir | Fijar `jakarta.persistence.query.timeout` ahora | Paula, 19-oct-2026 (opción recomendada) |
| D-7 | Un solo código para pool agotado, base detenida, corte y consulta lenta | Para la persona y para Frontend la acción es la misma: reintentar. El registro distingue la causa (`sqlState`, clase) | Un código por causa | Backend; Paula confirma con D-1 |

## 16. Fuera de alcance

El tiempo máximo de consulta (D-6); el comportamiento de los consumidores de RabbitMQ y del Job de relevo ante una base caída; el servicio de Entrevista (su 503 queda para la tarea que lo implemente); cualquier cambio en el Gateway; un reintento automático dentro de Perfil; `Retry-After`; el readiness de la aplicación (H-5).

## 17. Preguntas

Todas llevan recomendación y su efecto (la respuesta es una letra):

- **P-1 (D-1) Mecanismo.** A) Clasificador en el manejador general (recomendado; ~1 clase y una rama). B) Excepción de dominio en cada adaptador (más clases y no cubre el inicio de la transacción).
- **P-2 (D-2) Qué cuenta.** A) La lista de REQ-02 (recomendado). B) La misma lista sin `QueryTimeoutException` (una consulta lenta seguiría en 500).
- **P-3 (D-3) Espera por una conexión.** A) 5 s (recomendado). B) 3 s. C) Dejar 30 s: el 503 casi nunca se vería.
- **P-4 (D-4) Mensaje.** A) Igual que `BIRTH_DATE_UNAVAILABLE` (recomendado). B) Propio: Frontend y Paula escriben el texto.
- **P-5 (D-5, D-6) `Retry-After` y tiempo máximo de consulta.** A) Ninguno de los dos en esta tarea (recomendado). B) Agregar `Retry-After: 5`. C) Fijar el tiempo máximo de consulta (necesita medir antes).

Sin respuesta, se avanza con la opción recomendada y se informa (acuerdo de solicitudes con recomendación).

## 18. Lista de verificación

| # | Qué | Cómo | Esperado |
|---|---|---|---|
| V-01 | REQ-01, REQ-02 | `DatabaseUnavailabilityTest` y los cortes por HTTP real | 503 `DATABASE_UNAVAILABLE` |
| V-02 | REQ-03, REQ-06 | Negativos, 409 de bloqueo y `BIRTH_DATE_UNAVAILABLE` | Sin cambio |
| V-03 | REQ-04 | Captura de salida con `SQLException("host=10.0.0.5 password=x","08001")` | Sin `10.0.0.5` ni `password` en cuerpo ni registro; un solo `ERROR` con `code`, `requestId`, `firebaseUid`, `sqlState` |
| V-04 | REQ-05 | `DatabasePoolConfigurationIT`; base detenida con la configuración real del jar | 503 en menos de 8 s (hoy 30 s) |
| V-05 | REQ-07 | `ErrorCodeDocumentationTest`, `ErrorCatalogTest`, `OpenApiDocumentIT`, lectura de `docs/errores.md` | Verde |
| V-06 | REQ-08 | `ProfileControllerTest` | La del 500 pasa a 503 y la del imprevisto sigue en 500 |
| V-07 | REQ-09 | `ProfileAppServiceTest` con la réplica simulada lanzando el error de la base, y la rama del manejador | El error de la base se propaga y responde `DATABASE_UNAVAILABLE` |
| V-08 | Cobertura | `jacoco.csv` | ≥ 90 % de líneas y ramas por clase tocada |
| V-09 | Newman manual | D-01 y D-02 con la base detenida | 503 en ambas |
| V-10 | Adversarial | 30 peticiones en paralelo con el pool agotado; 20 repeticiones; mensaje con SQL | Ninguna 500; mismas respuestas; sin filtración |
| V-11 | Regresión | Suite completa y `ArquitecturaTest` | Verde |
