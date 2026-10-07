# Tareas — CM-271 (cameia-perfil)

Estado: spec aprobada por Paula (7-oct-2026); plan y tarjetas **pendientes de su aprobación**. Se marca `[x]` en el momento en que cada tarjeta termina, con la salida real de las pruebas.

## Reglas para todas las tarjetas (quien ejecuta no lee la spec ni el plan)

- **Rutas.** Paquete base: `src/main/java/co/edu/unicauca/cameia/perfil/`. Pruebas: `src/test/java/co/edu/unicauca/cameia/perfil/`.
- **Idioma y comentarios.** Código en inglés; Javadoc, comentarios, mensajes y logs en español. Sin `CM-NNN`, sin `TODO` y sin rutas a otros archivos en comentarios.
- **Límites del repositorio:**
  - métodos de 20 líneas o menos, con 3 parámetros o menos y 2 niveles de anidamiento como máximo;
  - clases de 200 líneas o menos;
  - `@Transactional` solo en `application.service` y en el adaptador de persistencia;
  - `domain` no importa Spring, JPA ni otras capas (lo comprueba `ArquitecturaTest`).
- **Pruebas:**
  - clase `<Clase>Test` (unidad) o `<Clase>IT` (integración);
  - método `<metodo>_should<Resultado>_when<Condicion>`, con `@DisplayName` en español;
  - las clases existentes conservan su estilo de nombres.
- **Prohibido:**
  - agregar dependencias (salvo T-0.2) o tocar migraciones;
  - registrar nombres, resúmenes, correos o el valor rechazado;
  - cambiar un texto que la tarjeta no nombre;
  - refactorizar fuera de la tarjeta;
  - tocar `.github/**` o `docs/estandar-backend.md`.
- **Catálogo.** Cada código de error nuevo, o cuyo estado o texto cambia, se actualiza en `docs/errores.md` en el mismo PR. Columnas: `Código | HTTP | Endpoints | Campo | Mensaje | Origen | Prueba`.
- **Comandos (PowerShell, Windows):**
  - una clase: `./mvnw.cmd -q -B -Dtest=<Clase> test`;
  - suite: `./mvnw.cmd -B test`;
  - cierre: `./mvnw.cmd -B clean verify` (corre las `*IT` con Testcontainers; necesita Docker Desktop encendido). JaCoCo queda en `target/site/jacoco/`.
- **Commit:** `CM-271 | <tipo>(perfil): <resultado>`, en español, terminado con la línea `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. **Sin** `[IA-ASISTIDO]`: va solo al final del título del PR.
- **Nada se publica.** Ni `git push`, ni `gh pr create`, ni `gh stack`. Al terminar cada PR se detiene el trabajo y se reporta a Paula: qué cambió, la salida de `clean verify`, la cobertura y qué revisar.
- **Detenerse y reportar** si la tarjeta contradice el código real, falta un dato, una prueba existente se rompe sin causa clara o hace falta algo no listado.
- **Terminado** quiere decir:
  - pruebas nuevas en verde;
  - suite completa en verde, con `ArquitecturaTest` incluida;
  - diff dentro de lo estimado.

---

## PR 0 — Testcontainers en Perfil y spec

Rama `CM-271-testcontainers-perfil`, worktree `C:\Users\paanm\Documents\cameia-worktrees\perfil-CM-271-tc`. Base final: `origin/develop`.

## [x] T-0.1 · PostgreSQL de prueba con Testcontainers (hecho, commit `1693767`)

Dependencias `spring-boot-testcontainers` y `testcontainers-postgresql` en `pom.xml`, `PostgresTestConfiguration` y `@Import` en `ProfessionalProfileRepositoryAdapterIT`. Se verificó con `-Dtest=ProfessionalProfileRepositoryAdapterIT`.

## [x] T-0.2 · Rebase, Failsafe y contenedor por clase — ≤ 30 min, ≈ 60 líneas

> Hecha (`b1b337d`). Por decisión de Paula (7-oct), `PerfilApplicationTest` también levanta su contenedor: dependía de la base de compose y de una contraseña en el entorno. `clean verify` sin la base de compose: Surefire 75/0/0/0, Failsafe 5/0/0/0, `BUILD SUCCESS`.

- **Rebase.** `git fetch origin` y `git rebase origin/develop`. Después, cambiar el mensaje del commit con `git commit --amend` para quitar ` [IA-ASISTIDO]` (no usar `rebase -i`). La rama no está publicada, así que reescribirla no afecta a nadie.
- **`pom.xml`:**
  - agregar `org.testcontainers:testcontainers-junit-jupiter`, de prueba y sin versión (la gestiona `spring-boot-starter-parent` 4.1.1; si no la gestiona, detenerse);
  - agregar `maven-failsafe-plugin`, sin versión, con las metas `integration-test` y `verify`.
- **`ProfessionalProfileRepositoryAdapterIT`:**
  - quitar el `@Import(PostgresTestConfiguration.class)`;
  - anotar la clase con `@Testcontainers`;
  - declarar el contenedor así:
    ```java
    /** PostgreSQL real, de la misma versión mayor que docker-compose. */
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("<imagen del servicio db de docker-compose.yml>");
    ```
- **Borrar** `PostgresTestConfiguration.java`.
- **Verificación.** `./mvnw.cmd -B clean verify` debe mostrar en la salida de Failsafe `Tests run: N, Failures: 0, Errors: 0, Skipped: 0`, con N mayor que 0. Pegar esas líneas en el reporte.

## [x] T-0.3 · Servicio `verify` de compose y README — ≤ 20 min, ≈ 20 líneas

> Hecha (`50d9213`). Se agregó `extra_hosts: host.docker.internal:host-gateway` para Linux. `docker compose run --rm verify`: Surefire 75/0/0/0, Failsafe 5/0/0/0, `BUILD SUCCESS`.

- **`docker-compose.yml`, servicio `verify`:**
  - agregar el volumen `/var/run/docker.sock:/var/run/docker.sock`;
  - agregar la variable `TESTCONTAINERS_HOST_OVERRIDE: host.docker.internal`;
  - quitar de `environment` las tres variables `SPRING_DATASOURCE_*` (las pone `@ServiceConnection`);
  - mantener `depends_on: db` solo si alguna prueba la usa; si ninguna la usa, quitarlo.
- **Verificación.** `docker compose run --rm verify` termina en `BUILD SUCCESS` con las `*IT` ejecutadas (`Skipped: 0`). Si falla por la red de Testcontainers, **detenerse y reportar la salida**; no probar otras configuraciones a ciegas.
- **`README.md`.** Agregar una sección «Pruebas» con tres puntos:
  - `clean verify` necesita Docker encendido;
  - las `*IT` levantan PostgreSQL con Testcontainers;
  - `docker compose run --rm verify` hace lo mismo dentro de un contenedor.

## [x] T-0.4 · Spec en el repositorio — ≤ 10 min

- Traer con `git checkout CM-271-cupo-perfil-plan-free -- specs/CM-271-cupo-perfil-plan-free/{spec,plan,tasks}.md` los tres archivos, y moverlos con `git mv` a `specs/CM-271-CupoPerfilPlanFree/`.
- **No** traer `HANDOFF.md` ni `HALLAZGOS.md`: son notas de trabajo. Su copia queda en `C:\Users\paanm\Documents\cameia-respaldos\CM-271-2026-10-07\`.
- En los tres archivos, reemplazar la ruta vieja de la carpeta por la nueva.
- Commit `CM-271 | docs(spec): spec, plan y tareas de CM-271`.

## [x] T-0.5 · Cierre del PR 0 (parada)

> Hecha. PR Draft #57 hacia `develop`, CI en verde (Surefire 75, Failsafe 5).

- Medir con `git diff --shortstat origin/develop...HEAD` y correr `./mvnw.cmd -B clean verify`.
- Reportar a Paula con el formato de las reglas. Título previsto del PR: `CM-271 | test(perfil): pruebas de integración con PostgreSQL real en clean verify [IA-ASISTIDO]`.

---

## PR A — formato de error común

Rama `CM-271-formato-error-perfil`, worktree `C:\Users\paanm\Documents\cameia-worktrees\perfil-CM-271-a`. Base final: la rama del PR 0.

## [x] T-A.1 a T-A.4 (hechas, commits `ae70cc7`, `a434f2f`, `c81f763`, `2087ac2`, `3863d0e`)

Ya hechas:
- `ErrorCode` y `BusinessException`, con las 11 excepciones extendiéndola;
- manejador con `code`, `requestId` y `charset` en errores, validación en 422 con `errors[]`, cuerpo ilegible en 422, respaldo `REQUEST_INVALID_VALUE` y 500 genérico;
- `ApiExceptionHandlerTest`, ajuste de `ProfileControllerTest` y OpenAPI parcial;
- identidad en blanco → 401.

## [x] T-A.5 · Rebase y mensajes — ≤ 20 min

> Hecha. Los 5 commits quedan encima del PR 0; `clean verify` 102 + 5 en verde.

- `git rebase --onto CM-271-testcontainers-perfil 42320d9` (deja los 5 commits encima del PR 0).
- **Conflicto en `docs/errores.md`.** Conservar la estructura de `develop` (secciones «Formato», «Códigos que el servicio emite», «Respuestas sin código», «Cómo se agrega un código»). Dentro, poner la tabla de códigos de la rama. Dejar en «Respuestas sin código» solo lo que siga sin código.
- **Mensajes.** Quitar ` [IA-ASISTIDO]` de los 5 commits con `git filter-branch -f --msg-filter "sed 's/ \[IA-ASISTIDO\]//'" CM-271-testcontainers-perfil..HEAD`, y agregar la línea `Co-Authored-By` si falta.
- **Verificación.** `./mvnw.cmd -B clean verify` en verde **antes** de seguir. Si algo falla por cambios de `develop`, detenerse y reportar.

## [x] T-A.6 · Dividir el manejador — ≤ 30 min, ≈ 0 líneas netas

> Hecha (`807f349`). `ErrorCatalog` expone `of(ErrorCode)` (estado, título y mensaje en un `record Definition`), `fieldCode`, `fieldMessage` y `pathIdCode`. Suite 106/0/0/0.

- **Crear** `presentation/advice/ErrorCatalog.java`: clase `final` con constructor privado y Javadoc. Recibe del manejador, sin cambiar sus valores, las tablas `STATUS`, `TITLES`, `FIELD_CODES` y `FIELD_MESSAGES`, expuestas con métodos estáticos de paquete:
  - `HttpStatus statusOf(ErrorCode)` (lanza `IllegalStateException` si falta: es un error de programación que la prueba detecta);
  - `String titleOf(ErrorCode)`;
  - `Optional<ErrorCode> fieldCodeOf(String clave)`;
  - `String fieldMessageOf(ErrorCode)`.
- **Quitar** el `getOrDefault(..., UNPROCESSABLE_ENTITY)` de `handleBusiness`: ahora es `statusOf`.
- **Mover** a `ErrorCatalogTest` las pruebas que recorren los mapas.
- **Verificación.** Suite en verde; `ApiExceptionHandler` y `ErrorCatalog` con menos de 200 líneas cada una (`(Get-Content <archivo>).Count`).

## [x] T-A.7 · Identidad en 401 en todos los casos — ≤ 30 min, ≈ 60 líneas

> Hecha (`c7a2880`). El constructor de `ProfileAppService` es de paquete, así que el controlador prueba la traducción a 401 con el servicio simulado y los límites reales (128, 129, en blanco) están en `ProfileAppServiceTest`. Otro encabezado ausente responde 422 `REQUEST_INVALID_VALUE` (un código, un estado), no 400.

- **Cubre** REQ-PE-10 y D8.
- **`ProfileAppService`.** Un solo método privado que reemplaza las dos comprobaciones actuales:
  ```java
  /** Devuelve la identidad del Usuario, o rechaza la petición si falta o no es válida. */
  private static FirebaseUid requireIdentity(String raw) {
      if (raw == null || raw.isBlank() || raw.length() > FirebaseUid.MAX_LENGTH) throw new IdentityRequiredException();
      return new FirebaseUid(raw);
  }
  ```
  - Hacer pública la constante `MAX_LENGTH` de `FirebaseUid`; no cambiar nada más de esa clase.
  - En `createProfile` y en `loadForUser`, usar `requireIdentity`. `loadForUser` compara `p.getFirebaseUid().equals(uid)` con el `FirebaseUid` devuelto.
- **Manejador.** `X-User-Id` ausente (`MissingRequestHeaderException` con ese nombre) → **401** `IDENTITY_REQUIRED` (hoy 400). Otro encabezado ausente → 400 `REQUEST_INVALID_VALUE`.
- **Pruebas:**
  - controlador: `POST /api/v1/profiles` sin encabezado → 401;
  - controlador: `X-User-Id` `""`, `"   "` y `"\t"` → 401;
  - `"a".repeat(128)` → 201;
  - `"a".repeat(129)` → 401, y el `detail` no contiene `aaaa`;
  - servicio: `loadForUser` con 129 caracteres → `IdentityRequiredException` (no 403).
- **Verificación.** `-Dtest=ProfileControllerTest,ProfileAppServiceTest`.

## [x] T-A.8 · Fecha mal escrita en 422 — ≤ 15 min, ≈ 25 líneas

> Hecha (`2058e04`).

- **Cubre** REQ-PE-11.
- El `@ExceptionHandler` del respaldo pasa a atrapar `{IllegalArgumentException.class, DateTimeException.class}`, con el mismo código, el mismo texto y el mismo log de origen.
- **Pruebas** (controlador, `POST /api/v1/profiles/{id}/work-experiences` con cuerpo válido salvo la fecha):
  - `startDate` `"2020-13"` → 422 `REQUEST_INVALID_VALUE`;
  - `startDate` `"31/02/2020"` → 422 `REQUEST_INVALID_VALUE`;
  - en los dos, el `detail` no contiene `Text` ni `parse`.

## [x] T-A.9 · Errores del framework con código — ≤ 30 min, ≈ 120 líneas

> Hecha (`430d859`). Nombres de `@PathVariable` iguales a la tarjeta. También `NoHandlerFoundException` → 404 `ROUTE_NOT_FOUND`; los demás errores del framework conservan estado y texto y ganan `requestId` y charset. La construcción de la respuesta pasó a `ProblemResponses` para dejar el manejador bajo 200 líneas. Con la app real, la ruta inexistente responde `ROUTE_NOT_FOUND` (`ResponseCharsetIT`).

- **Cubre** REQ-PE-12 y D10.
- **`ErrorCode`.** Agregar `ROUTE_NOT_FOUND`, `METHOD_NOT_ALLOWED`, `CONTENT_TYPE_NOT_ALLOWED`, `PROFILE_ID_INVALID_FORMAT`, `WORK_EXPERIENCE_ID_INVALID_FORMAT`, `EDUCATION_ID_INVALID_FORMAT`, `SKILL_ID_INVALID_FORMAT` y `TARGET_ROLE_ID_INVALID_FORMAT`, cada uno con Javadoc de una línea.
- **`ErrorCatalog`.** Mapa `PATH_ID_CODES`: `id` → `PROFILE_ID_INVALID_FORMAT`, `expId` → `WORK_EXPERIENCE_ID_INVALID_FORMAT`, `eduId` → `EDUCATION_ID_INVALID_FORMAT`, `skillId` → `SKILL_ID_INVALID_FORMAT`, `roleId` → `TARGET_ROLE_ID_INVALID_FORMAT`. Comprobar los nombres exactos de las `@PathVariable` en `ProfileController`; si alguno es distinto, usar el real y anotarlo.
- **Manejador.** Sobrescribir estos métodos de `ResponseEntityExceptionHandler`. Todos devuelven la forma común y **ninguno usa el mensaje de Spring**:

  | Método | Estado | Código | `detail` |
  |---|---|---|---|
  | `handleNoResourceFoundException` | 404 | `ROUTE_NOT_FOUND` | «La ruta solicitada no existe.» |
  | `handleHttpRequestMethodNotSupported` | 405 | `METHOD_NOT_ALLOWED` | «La operación no está permitida en esta ruta.» (conservar el encabezado `Allow` que pone Spring) |
  | `handleHttpMediaTypeNotSupported` | 415 | `CONTENT_TYPE_NOT_ALLOWED` | «Envía los datos en formato JSON.» |
  | `handleTypeMismatch` | 422 | código de `PATH_ID_CODES` según `((MethodArgumentTypeMismatchException) ex).getName()` | mensaje de la tabla de la spec, §5 |

  En `handleTypeMismatch`, si el nombre no está en el mapa, responder 422 `REQUEST_INVALID_VALUE`.
- **Mensajes de los identificadores:** «El identificador del perfil no es válido.», «El identificador de la experiencia no es válido.», «El identificador de la formación no es válido.», «El identificador de la habilidad no es válido.», «El identificador del rol objetivo no es válido.».
- **Trampa.** Para que la ruta inexistente llegue al manejador en Spring Boot 4, revisar que `NoResourceFoundException` se lance (es lo predeterminado). Si la respuesta real es otra, detenerse.
- **Pruebas** (controlador):
  - `GET /api/v1/no-existe` → 404 `ROUTE_NOT_FOUND`;
  - `DELETE /api/v1/profiles` → 405 `METHOD_NOT_ALLOWED`;
  - `PATCH /api/v1/profiles/{id}` con `Content-Type: text/plain` → 415;
  - `GET /api/v1/profiles/no-es-uuid` → 422 `PROFILE_ID_INVALID_FORMAT`;
  - `DELETE /api/v1/profiles/{uuid}/skills/xyz` → 422 `SKILL_ID_INVALID_FORMAT`;
  - en todas, `$.requestId` presente y el `detail` sin `no-es-uuid`, `xyz`, `Failed` ni `convert`.

## [x] T-A.10 · Finalización incompleta e idioma del catálogo con la forma común — ≤ 30 min, ≈ 90 líneas

> Hecha (`f3114c1`).

- **Cubre** REQ-PE-16, REQ-PE-17 y D11.
- **Finalización:**
  - `IncompleteProfileException` pasa el mensaje fijo «Todavía no cumples estos requisitos:» a `super(...)` y conserva `getMissingRequirements()`.
  - Borrar el `@ExceptionHandler(IncompleteProfileException.class)` propio. La atrapa el de `BusinessException`, que agrega la propiedad `missingRequirements` cuando la excepción es `IncompleteProfileException`. Hacerlo con `instanceof` y patrón, en un método privado corto.
  - Borrar `CompletionErrorResponse.java` y cambiar sus dos usos en el OpenAPI de `ProfileController` por `ProblemDetail`.
- **Idioma:**
  - Crear `domain/exception/UnsupportedLanguageException` (extiende `BusinessException`, código `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE`, mensaje «Elige un idioma disponible: español o inglés.», con Javadoc).
  - Agregar el código a `ErrorCode` y a `ErrorCatalog` con 422.
  - En `ProfessionalRoleController`, reemplazar el `ProblemDetail` armado a mano por `throw new UnsupportedLanguageException();`. La comprobación sigue siendo exacta (`SUPPORTED_LANGS.contains(lang)`).
- **Pruebas:**
  - finalizar un perfil sin resumen ni habilidades → 422, `$.code` `PROFILE_INCOMPLETE`, `$.detail` literal, `$.missingRequirements` con 2 elementos y `$.requestId`;
  - `?lang=fr`, `?lang=ES` y `?lang=es-CO` → 422 `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE`, sin el valor en el `detail`;
  - sin `lang` y con `?lang=` → 200 en español;
  - `?lang=en` → 200 en inglés.
- Ajustar en `ProfessionalRoleControllerTest` la prueba que esperaba 400.

## [x] T-A.11 · `firebaseUid` en el registro — ≤ 15 min, ≈ 20 líneas

> Hecha (`f0e10e1`). En todos los registros del manejador; también se registra `-` si la identidad trae caracteres de control. Las pruebas usan el `ListAppender` que ya tenía `ApiExceptionHandlerTest`.

- **Cubre** REQ-PE-07.
- El método común que registra los rechazos agrega `firebaseUid={}` con el valor de `X-User-Id`, solo si existe, no está en blanco y mide 128 caracteres o menos. En otro caso escribe `firebaseUid=-`.
- **Prueba.** Con `OutputCaptureExtension`, un 409 con `X-User-Id: uid-ana-001` registra `code=`, `requestId=` y `firebaseUid=uid-ana-001`; una petición sin el encabezado registra `firebaseUid=-`.

## [x] T-A.12 · `charset` en las respuestas de éxito — ≤ 20 min, ≈ 40 líneas

> Hecha (`2007e3a`). En Spring Boot 4 la propiedad vigente es `spring.servlet.encoding.force-response`; `server.servlet.encoding.force-response` está retirada y no se aplica. Con la propiedad apagada, `ResponseCharsetIT` falla.

- **Cubre** REQ-PE-13.
- En `application.yml`, agregar `server.servlet.encoding.force-response: true` junto a la configuración existente de `server` (si no hay, crearla).
- **Crear** `presentation/ResponseCharsetIT` con `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Testcontainers` y su contenedor (como T-0.2). Usar `TestRestTemplate` o `RestClient`:
  - `POST /api/v1/profiles` con `X-User-Id: uid-charset-<UUID>` → `Content-Type` contiene `charset=UTF-8`;
  - el `GET` del mismo perfil, igual;
  - un 404 de ruta, igual.
- **Trampa.** Si el `Content-Type` del 201 no trae `charset`, **detenerse y reportar** con el encabezado real. No probar otras configuraciones.

## [x] T-A.13 · Pruebas de los códigos de campo, Javadoc y limpieza — ≤ 30 min, ≈ 120 líneas

> Hecha (`6f228a0`). 38 casos. Queda un `TODO` dentro del mensaje de `ProfileAlreadyExistsException`, que T-B.1 reemplaza por el texto aprobado.

- **Cubre** REQ-PE-15 y el caso 21.
- **Prueba parametrizada** en `ProfileControllerTest`, una fila por clave de `FIELD_CODES`. Cada campo con `NotBlank` se prueba ausente, `null`, `""`, `"   "` y `"\t"`; cada campo con `NotNull`, ausente y `null`. Se espera 422 y `$.errors[0]` con `field`, el `code` y el `message` de la tabla de la spec, §5 (por ejemplo `company` → `COMPANY_REQUIRED` → «Ingresa la empresa.»).
- **Javadoc** de clase y de constructor en cada excepción de `domain/exception`.
- **Limpieza** de `CM-NNN`, de los separadores `// ── CM-xx` y de los `TODO` en los archivos que esta rama toca: `ProfileAppService`, `CreateProfileCommand`, `ProfileController`, las excepciones y `ProfessionalProfileRepository` (este último en el PR B). El comando `git grep -n -E "CM-[0-9]+|TODO" -- <archivos tocados>` debe quedar vacío.

## [x] T-A.14 · OpenAPI, documentación y cierre del PR A (parada)

> Hecha (`a9939e8`). `clean verify`: Surefire 187/0/0/0, Failsafe 8/0/0/0. Cobertura de lo nuevo o modificado: 100 % de líneas y ramas; repositorio 71,1 % de líneas y 60,8 % de ramas (PR 0: 63,7 % y 50,0 %). Diff 2.173 líneas: ningún corte en dos queda bajo 1.000; el corte en tres (T-A.1 a T-A.6, T-A.7 a T-A.12, T-A.13 y T-A.14) espera la decisión de Paula.

- **OpenAPI.** Un `@Schema` del error común con `code`, `detail`, `requestId` y `errors[]`, con ejemplo. En `ProfileController`, cada `@ApiResponse` de error nombra su código; en `POST /api/v1/profiles` van 401 y 500 (el 409 lo agrega el PR B).
- **`docs/errores.md`:**
  - el catálogo completo de la spec, §5, con todas las columnas;
  - una sección «Estados que difieren del mapa base» con 422 para la validación y 422 para el cuerpo ilegible;
  - una nota con las dos causas nuevas del vocabulario (`ALREADY_COMPLETED`, `INCOMPLETE`).
- **`docs/adr/0002-validacion-en-422.md`.** Contexto, decisión (422 para validación y cuerpo ilegible, igual que Cuentas), consecuencias (Frontend ajusta dos formularios, ya comunicado) y alternativas (400 del mapa base).
- **Cierre:**
  - `./mvnw.cmd -B clean verify`;
  - cobertura de lo nuevo o modificado de 90 % o más en líneas y ramas, con cada línea o rama sin cubrir explicada;
  - `git diff --shortstat CM-271-testcontainers-perfil...HEAD`; si pasa de 1.000 líneas, proponer a Paula la división en A1 y A2 del plan.
  - Reportar a Paula. Título previsto: `CM-271 | feat(perfil): forma común en todas las respuestas de error [IA-ASISTIDO]`.

---

## PR B — cupo del Plan Free y creación sin duplicados

Rama `CM-271-cupo-perfil-plan-free`, que **se rehace** desde la rama del PR A. Los commits de la spec ya están en el PR 0 (T-0.4). Pasos:
- `git branch -m CM-271-cupo-perfil-plan-free CM-271-spec-trabajo` en el worktree `perfil-CM-271`;
- `git switch -c CM-271-cupo-perfil-plan-free CM-271-formato-error-perfil`.

## [ ] T-B.1 · Excepción del cupo — ≤ 15 min, ≈ 30 líneas

- **Cubre** REQ-PE-20 y REQ-PE-25.
- `git mv domain/exception/ProfileAlreadyExistsException.java domain/exception/ProfileLimitReachedException.java`:
  ```java
  /** El Usuario alcanzó el máximo de Perfiles Profesionales de su plan. */
  public class ProfileLimitReachedException extends BusinessException {
      /** Crea la excepción con el mensaje del Plan Free. */
      public ProfileLimitReachedException() {
          super(ErrorCode.PROFILE_LIMIT_REACHED, "Tu Plan Free permite 1 Perfil Profesional.");
      }
  }
  ```
- Actualizar sus usos y su título en `ErrorCatalog` («Cupo del plan alcanzado»).

## [ ] T-B.2 · Puerto y adaptador — ≤ 30 min, ≈ 70 líneas

- **Cubre** REQ-PE-26 y REQ-PE-27.
- **Puerto `ProfessionalProfileRepository`.** Quitar `existsByFirebaseUid` (con su Javadoc y su `TODO`) y agregar:
  ```java
  /**
   * Cuenta los Perfiles Profesionales del Usuario, en cualquier estado.
   * @param firebaseUid dueño
   * @return cantidad de perfiles
   */
  long countByFirebaseUid(FirebaseUid firebaseUid);

  /**
   * Hace esperar a cualquier otra creación de perfil del mismo Usuario hasta que termine la transacción actual.
   * <p>Debe llamarse dentro de una transacción.</p>
   * @param firebaseUid dueño
   */
  void lockCreationFor(FirebaseUid firebaseUid);

  /**
   * Busca el perfil creado más recientemente por el Usuario.
   * @param firebaseUid dueño
   * @return el perfil, o vacío si no tiene
   */
  Optional<ProfessionalProfile> findLatestByFirebaseUid(FirebaseUid firebaseUid);
  ```
- **`ProfessionalProfileJpaRepository`.** Quitar `existsByFirebaseUid` y agregar `long countByFirebaseUid(String firebaseUid);` y `Optional<ProfessionalProfileEntity> findFirstByFirebaseUidOrderByCreatedAtDesc(String firebaseUid);`.
- **Adaptador:**
  - `countByFirebaseUid` y `findLatestByFirebaseUid` con `@Transactional(readOnly = true)`, como los demás métodos de lectura del adaptador: se unen a la transacción abierta por el servicio. El mapeo reutiliza el de `findById`.
  - `lockCreationFor`, con un `@PersistenceContext EntityManager em` inyectado:
    ```java
    /** Bloqueo de transacción de PostgreSQL por Usuario; se libera solo al confirmar o deshacer. */
    @Override
    public void lockCreationFor(FirebaseUid firebaseUid) {
        em.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(:uid, 0))")
                .setParameter("uid", firebaseUid.value())
                .getSingleResult();
    }
    ```
- **Trampa.** `pg_advisory_xact_lock` sin transacción abierta se libera de inmediato. La transacción la abre `@Transactional` en `createProfile`. No agregar `@Transactional` al adaptador para este método.

## [ ] T-B.3 · Regla de creación en el servicio — ≤ 30 min, ≈ 40 líneas

- **Cubre** REQ-PE-20, 21, 24, 26 y 28.
- En `ProfileAppService`:
  ```java
  /** Cantidad máxima de Perfiles Profesionales del Plan Free. */
  static final int FREE_PLAN_MAX_PROFILES = 1;

  /**
   * Crea el Perfil Profesional vacío del Usuario.
   *
   * <p>Si llega otra petición del mismo Usuario mientras una creación está en proceso, espera a que
   * termine y devuelve el perfil que esa creó, en vez de crear otro o rechazarla. Si el Usuario ya
   * tenía el máximo de perfiles antes de pedir, rechaza la creación.</p>
   */
  @Transactional
  public ProfessionalProfile createProfile(CreateProfileCommand command) {
      var uid = requireIdentity(command.firebaseUid());
      long before = repository.countByFirebaseUid(uid);
      // Serializa las creaciones del mismo Usuario: el segundo conteo ve lo que confirmó la anterior.
      repository.lockCreationFor(uid);
      long after = repository.countByFirebaseUid(uid);
      if (after < FREE_PLAN_MAX_PROFILES) return createEmpty(uid);
      if (before < FREE_PLAN_MAX_PROFILES) return repository.findLatestByFirebaseUid(uid).orElseThrow();
      throw new ProfileLimitReachedException();
  }
  ```
  - `createEmpty(uid)` es un método privado con las líneas actuales: `ProfessionalProfile.create`, `save` y el `log.info`.
  - El `orElseThrow()` sin argumento solo falla si la base se contradice (cuenta 1 y no encuentra ninguno); cae en el 500 genérico. Documentarlo con un comentario de una línea.
  - Con `FREE_PLAN_MAX_PROFILES` = 1 la regla es la de la spec (REQ-PE-26).
- **Trampa.** El aislamiento debe ser `READ COMMITTED` (el de PostgreSQL por defecto). No poner `isolation` en `@Transactional`.

## [ ] T-B.4 · Pruebas del servicio — ≤ 30 min, ≈ 90 líneas

`ProfileAppServiceTest`, con dobles del puerto. Ajustar las pruebas que usaban `existsByFirebaseUid`.

| Prueba | Doble | Afirmación |
|---|---|---|
| `createProfile_shouldCreate_whenUserHasNoProfiles` (caso 1) | `count` 0 y 0 | `save` una vez; `IN_PROGRESS`; `name` nulo |
| `createProfile_shouldThrowLimitReached_whenUserAlreadyHadOne` (caso 3) | `count` 1 y 1 | `ProfileLimitReachedException` con el mensaje literal; `save` nunca |
| `createProfile_shouldReturnJustCreated_whenAnotherRequestCreatedItMeanwhile` (caso 10) | `count` 0 y luego 1; `findLatest` → perfil P | devuelve P; `save` nunca |
| `createProfile_shouldLockBetweenCounts_whenCreating` (caso 9) | — | `InOrder`: `countByFirebaseUid`, `lockCreationFor`, `countByFirebaseUid` |
| `createProfile_shouldCreate_whenAnotherUserHasProfile` (caso 6) | `uid-luis-002` con `count` 0 | crea con dueño `uid-luis-002` |
| `createProfile_shouldThrowLimitReached_whenExistingProfileIsActive` (caso 4) | `count` 1 y 1 | igual que el caso 3 (el estado no importa: el conteo no filtra por estado) |

## [ ] T-B.5 · Pruebas de integración con PostgreSQL real — ≤ 30 min, ≈ 120 líneas

- **Crear** `infrastructure/persistence/ProfileCreationConcurrencyIT`, con `@SpringBootTest`, `@Testcontainers` y contenedor propio (como T-0.2). **Sin** `@Transactional` en la clase: cada hilo necesita su propia transacción. Limpiar en `@AfterEach` con `jdbcTemplate.update("delete from perfil_profesional where firebase_uid like 'uid-it-%'")`.
- **Pruebas:**
  - `createProfile_shouldReturnSameProfileToAll_whenFiveRequestsArriveTogether` (caso 7). Usar `String uid = "uid-it-" + UUID.randomUUID();`, un `ExecutorService` de 5 hilos y un `CountDownLatch` de salida. Los 5 llaman a `profileAppService.createProfile(new CreateProfileCommand(uid))`. Afirmar: 5 resultados sin excepción, un solo `id` distinto entre ellos y `select count(*)` = 1.
  - `createProfile_shouldNotBlockOtherUsers_whenFiveUsersCreateTogether` (caso 8): 5 Usuarios distintos a la vez → 5 filas.
  - `createProfile_shouldCreate_whenPreviousCreationRolledBack` (caso 11). En un hilo, abrir una transacción con `TransactionTemplate`, tomar el bloqueo (`repository.lockCreationFor`), esperar a que el otro hilo esté esperando (`Thread.sleep(300)`, documentado) y lanzar una excepción para deshacerla. El otro hilo llama a `createProfile` y debe crear el perfil (1 fila).
- **Verificación.** Correr `./mvnw.cmd -B -Dit.test=ProfileCreationConcurrencyIT verify` **tres veces seguidas** en verde.

## [ ] T-B.6 · Controlador, OpenAPI y Postman — ≤ 30 min, ≈ 90 líneas

- **`ProfileControllerTest`:**
  - `postProfiles_shouldReturn409WithPlanMessage_whenUserAlreadyHasProfile` (casos 3 y 5): `$.code` `PROFILE_LIMIT_REACHED`, `$.detail` = «Tu Plan Free permite 1 Perfil Profesional.» y `$.detail` sin `TODO`, `CM-`, `Exception` ni `co.edu`;
  - `getProfile_shouldReturnEmptyDraft_whenJustCreated` (caso 2): `$.status` `IN_PROGRESS`, `$.name` y `$.summary` nulos, las cuatro listas vacías y `$.method` inexistente (`doesNotExist()`);
  - `postProfiles_shouldIgnoreBody_whenBodySent` (caso 12): cuerpo `{"status":"ACTIVE","firebaseUid":"otro"}` → 201 y el servicio recibe `uid-ana-001`;
  - `postProfiles_shouldReturnGeneric500_whenDatabaseFails` (caso 13): el servicio lanza `DataAccessResourceFailureException("conexión")` → 500 `INTERNAL_ERROR` sin `conexión`.
- **OpenAPI de `POST /api/v1/profiles`.** 201 con ejemplo del perfil vacío y la frase «Una petición repetida mientras la creación está en proceso devuelve el mismo perfil.»; 409 con ejemplo `PROFILE_LIMIT_REACHED`.
- **Postman** (`docs/CAMEIA_Perfil_Sprint1.postman_collection.json`, carpeta CM-16):
  - la petición del 409 afirma `code` y `detail`;
  - la de «400 sin header» pasa a «401 sin header» y afirma `code` `IDENTITY_REQUIRED`;
  - nueva petición `GET` del perfil recién creado (caso 2).

## [ ] T-B.7 · Documentación, carrera real y cierre del PR B (parada)

- **`docs/errores.md`.** Fila de `PROFILE_LIMIT_REACHED` con su texto y su prueba.
- **`docs/adr/0003-bloqueo-de-creacion-de-perfil.md`.** Contexto (duplicados medidos), decisión (bloqueo por Usuario y la regla de REQ-PE-26), consecuencias (las peticiones repetidas reciben el mismo perfil; Premium compara contra su cupo) y alternativas (`UNIQUE`, `SERIALIZABLE`, 409 a la repetida, `Idempotency-Key`).
- **Carrera con la app real** (caso 14). Levantar con `docker compose up -d --build app` y correr `python -I C:\Users\paanm\Documents\cameia-respaldos\CM-271-2026-10-07\prueba_carrera_doble_envio.py`. Antes, leer el script para confirmar qué afirma y ajustar lo que espera: 8 respuestas 201 con el mismo `id` y 1 perfil por ronda. Pegar la salida de las 15 rondas.
- **Postman contra la app real.** Correr la carpeta CM-16 y pegar el resultado.
- **Cierre:**
  - `./mvnw.cmd -B clean verify`;
  - cobertura de lo nuevo o modificado de 90 % o más;
  - `git diff --shortstat CM-271-formato-error-perfil...HEAD`.
  - Reportar a Paula. Título previsto: `CM-271 | fix(perfil): un solo perfil por doble envío y cupo del Plan Free con su mensaje [IA-ASISTIDO]`.
