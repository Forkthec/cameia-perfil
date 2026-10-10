# Tareas — CM-54 (cameia-perfil)

Estado: sin ejecutar; spec y plan pendientes de aprobación de Paula. Una tarjeta a la vez, con la salida real de las pruebas antes de marcar `[x]`.

## Reglas para todas las tarjetas

Las once reglas comunes de `specs/CM-274-experiencia-formacion-v4/tasks.md` («Reglas para todas las tarjetas») aplican igual y se repiten aquí en lo esencial:

1. Rutas `src/main/java/co/edu/unicauca/cameia/perfil/` y `src/test/java/co/edu/unicauca/cameia/perfil/`.
2. **Idioma (R1).** En inglés: identificadores, nombres de métodos de prueba y códigos de error. En español: Javadoc, comentarios, `@DisplayName`, descripciones de OpenAPI, logs y mensajes para la persona (los de « » se copian exactos). Los esqueletos ya traen el Javadoc en español.
3. Borde con Bean Validation y `@Valid`; dominio con objetos de valor. Cada campo se prueba con ausente, `null`, `""`, espacios, `\t\n`, U+00A0, n−1, n, n+1 en puntos de código (`ñ`, emoji, NFD), control y valores hostiles. Todos los errores de forma a la vez.
4. Nunca 500 por una entrada. `application/problem+json;charset=UTF-8` con `code`, `requestId`, `errors[]` y `detail` «Revisa los campos marcados.» en 422. Sin traza, SQL ni nombres de clase. Log en `WARN` con `code`, `requestId`, `firebaseUid`; nunca el nombre ni el resumen.
5. Sin migraciones en esta tarea. Métodos ≈ 20 líneas, ≤ 3 parámetros, `domain` sin Spring. Sin dependencias nuevas.
6. Javadoc en todo lo nuevo o modificado; `docs/errores.md` al día en el mismo PR.
7. Pruebas `method_shouldResult_whenCondition`, Arrange-Act-Assert, datos literales, sin `Thread.sleep`; las `*IT` con Testcontainers y Failsafe.
8. Comandos: `./mvnw.cmd -q -B "-Dtest=<Clase>" test`; `./mvnw.cmd -B "-Dit.test=<Clase>" verify`; cierre `./mvnw.cmd -B clean verify`.
9. Detente y reporta si algo no coincide con el código real (en especial, si los PR 1 a 6 de CM-274 no están en la base de la rama).
10. Commit `CM-54 | <tipo>(perfil): <resultado>`, sin `[IA-ASISTIDO]`, con `Co-Authored-By`. Push y PR solo con el sí de Paula.
11. Escribe los caracteres invisibles con escapes `\uXXXX` y verifica con `git diff` que quedaron como escape.

---

# PR 1 (`CM-54-informacion-general-resumen`, desde `origin/develop` con CM-274 completo fusionado)

## [ ] T-0 · Comprobar la base y reproducir — ≤ 15 min

- `git grep -n "class SingleLineText\|hasControlCharacter\|@interface CodePointSize\|@interface DomainRule\|findByIdForUpdate\|class FieldValues" origin/develop -- src/main` debe encontrar las seis piezas, y `git ls-tree --name-only origin/develop src/test/java/co/edu/unicauca/cameia/perfil/infrastructure/persistence/` debe listar `ColumnLengthConsistencyIT.java` y `ProfileWriteLockIT.java`. Si falta alguna, detente.
- **Línea base:** corre la sección 20 de la spec (`./mvnw.cmd -B clean verify` y Newman de la colección completa) y pega la salida antes de editar.
- Con `docker compose up -d --build`: `PATCH` con `{"name":"","summary":"<2001 a>"}` → anotar que hoy sale un solo error; `{"name":"Analista\u0000"}` → 500; `{"provenance":"AI_SUGGESTED"}` → la procedencia cambia. Pegar en `specs/CM-54-informacion-general-resumen/evidencia-antes.md`.

## [ ] T-1 · Petición normalizada y validada en el borde — ≤ 30 min, ≈ 130 líneas

- **Modificar** `presentation/dto/UpdateProfileInfoRequest.java`:
  ```java
  /**
   * Cuerpo de PATCH /api/v1/profiles/{id}. Un campo ausente o null significa «no cambiar»; un resumen
   * en blanco significa «borrarlo» (el dominio lo rechaza en un perfil Activo).
   */
  @Schema(description = "Información general del perfil; un campo ausente o null no cambia")
  public record UpdateProfileInfoRequest(
          @Schema(description = "Nombre del perfil", example = "Analista de datos", minLength = 1, maxLength = 255, nullable = true)
          // @Size(min = 1) rechaza el nombre que queda vacío tras recortar y deja que null signifique «sin cambio».
          @Size(min = 1) @CodePointSize(max = 255) @DomainRule(Rule.PROFILE_NAME) String name,
          @Schema(description = "Resumen profesional; vacío lo borra salvo en un perfil Activo", example = "Analista con tres años en banca.", maxLength = 2000, nullable = true)
          @CodePointSize(max = 2000) @DomainRule(Rule.SUMMARY) String summary,
          @Schema(description = "Modalidad de trabajo preferida", allowableValues = {"REMOTE", "HYBRID", "ON_SITE"}, nullable = true)
          // @Size(min = 1): una modalidad vacía no es una opción, así que se rechaza en lugar de ignorarse.
          @Size(min = 1) @DomainRule(Rule.PREFERRED_MODALITY) String preferredModality) {

      /** Normaliza antes de Bean Validation, para que los límites se midan sobre lo que se guardará. */
      public UpdateProfileInfoRequest {
          name = SingleLineText.normalize(name);
          summary = SingleLineText.normalize(summary);
          preferredModality = SingleLineText.normalize(preferredModality);
      }
  }
  ```
  `allowableValues`: son los tres valores de `WorkModality` en `2a54ca7`; si el `enum` cambió, detente. **Trampa:** `@DomainRule` acepta `""` por diseño (lo deja a otra restricción); aquí `""` debe dar `PREFERRED_MODALITY_INVALID_VALUE` (decisión de Paula), por eso `@Size(min = 1)` con la clave `UpdateProfileInfoRequest.preferredModality.Size` → `PREFERRED_MODALITY_INVALID_VALUE`.
- **Modificar** `presentation/dto/validation/DomainRule.java`: agregar al final del `enum Rule` (punto de extensión que dejó CM-274):
  ```java
  PROFILE_NAME(text -> rejectControl(text, false, ProfileName.CHARACTERS), Set.of(PROFILE_NAME_INVALID_CHARACTERS)),
  SUMMARY(text -> rejectControl(text, true, ProfessionalSummary.CHARACTERS), Set.of(SUMMARY_INVALID_CHARACTERS)),
  PREFERRED_MODALITY(text -> FieldValues.option(WorkModality.class, text, PREFERRED_MODALITY_INVALID_VALUE, "preferredModality"),
          Set.of(PREFERRED_MODALITY_INVALID_VALUE))
  ```
  (CM-66 va después y agrega las suyas a continuación; cuida la coma y el `;` final).
- **Modificar** `UpdateProfileInfoCommand`: quitar `provenance`. `ProfileController.updateProfileInfo`: dejar de pasar `r.provenance()`.
- **Modificar** `ErrorCatalog.FIELD_CODES`: `UpdateProfileInfoRequest.name.Size`→`PROFILE_NAME_REQUIRED`, `…name.CodePointSize`→`PROFILE_NAME_TOO_LONG`, `…summary.CodePointSize`→`SUMMARY_TOO_LONG`, `…preferredModality.Size`→`PREFERRED_MODALITY_INVALID_VALUE`. `FIELD_MESSAGES`: «Ingresa un nombre para el perfil.», «El nombre no puede superar los 255 caracteres.», «El resumen no puede superar los 2000 caracteres.», «Selecciona una opción.». Los campos con `@DomainRule` no llevan clave (el código viaja en la violación). Mover a `FIELD_MESSAGES` solo los códigos que ahora emite el borde por tabla y sacarlos de `ErrorCatalogTest.DOMAIN_FIELD_CODES`. Agregar al final de `ErrorCode` `PROFILE_NAME_INVALID_CHARACTERS` y `SUMMARY_INVALID_CHARACTERS` con Javadoc en español, y a `DOMAIN_FIELD_CODES` (los emite el dominio).
- **Pruebas** `ProfileControllerTest`:
  - `updateProfileInfo_shouldReturnNameRequired_whenNameIsBlank` (parametrizada: `""`, `"   "`, `"\t\n"`, `" "`, `"﻿"`) → 422, `errors[0]` `name`/`PROFILE_NAME_REQUIRED`/«Ingresa un nombre para el perfil.».
  - `updateProfileInfo_shouldReturnTooLong_whenTextExceedsLimit`: `name` 256 `a`; `name` 255 `a` + `"😀"`; `name` 256 `ñ`; `summary` 2001 `a`; `summary` 1999 `a` + `"😀😀"`.
  - `updateProfileInfo_shouldAccept_whenTextIsAtLimit`: `name` 255 `a`, 254 `a` + `"😀"`, `"é"` × 255; `summary` 2000 `a`, 2000 `"😀"`.
  - `updateProfileInfo_shouldReturnBothFields_whenNameAndSummaryAreInvalid`: `{"name":"","summary":"<2001 a>"}` → dos elementos (`containsExactlyInAnyOrder("name", "summary")`).
  - `updateProfileInfo_shouldReturnModalityInvalid_whenValueIsUnknown`: `"hybrid"`, `"PRESENCIAL"`, `""`.
  - `updateProfileInfo_shouldRejectControlCharacter_whenNameHasNul`: `"Analista\u0000"` → `PROFILE_NAME_INVALID_CHARACTERS`, «El nombre tiene caracteres no permitidos.»; `"Hola\u0000"` en `summary` → `SUMMARY_INVALID_CHARACTERS`, «El resumen tiene caracteres no permitidos.»; `"Línea 1\nLínea 2"` en `summary` se acepta.
  - `updateProfileInfo_shouldReturn403_whenProfileBelongsToAnotherUser` (`code` `PROFILE_NOT_ALLOWED`, `detail` «No encontramos lo que buscabas.»).
  - Cada una comprueba `Content-Type` y la ausencia de `trace`, `exception`, `co.edu`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfileControllerTest,ErrorCatalogTest" test`.

## [ ] T-2 · Objetos de valor con fábrica — ≤ 25 min, ≈ 110 líneas

- **Modificar** `domain/model/ProfileName.java`:
  ```java
  /**
   * Nombre del Perfil Profesional: de 1 a 255 caracteres tras recortar, contados en puntos de código en NFC.
   *
   * <p>{@link #of(String)} aplica las reglas al texto recibido. El constructor canónico solo rechaza
   * {@code null}: reconstruye un nombre guardado sin volver a validar, para que una regla agregada
   * después nunca rompa la lectura de un perfil existente.</p>
   *
   * @param value el nombre tal como está guardado
   */
  public record ProfileName(String value) {
      public static final int MAX_LENGTH = 255;
      public ProfileName { Objects.requireNonNull(value, "value"); }
      /**
       * Crea el nombre a partir del texto recibido, aplicando todas sus reglas.
       *
       * @param raw nombre recibido; no {@code null}
       * @return el nombre normalizado
       * @throws InvalidFieldsException con PROFILE_NAME_REQUIRED, PROFILE_NAME_INVALID_CHARACTERS o PROFILE_NAME_TOO_LONG
       */
      public static ProfileName of(String raw) { ... }
  }
  ```
  Igual `ProfessionalSummary` (`MAX_LENGTH = 2000`; `of(raw)` lanza `SUMMARY_INVALID_CHARACTERS` o `SUMMARY_TOO_LONG`; con texto vacío tras normalizar lanza `IllegalArgumentException`, porque el servicio nunca lo llama así: el vacío se convierte en `null` antes — documentarlo en `@throws`). Mensajes literales de la spec §6. Caracteres de control con `SingleLineText.hasControlCharacter(texto, false)` en el nombre y `(texto, true)` en el resumen; cada `record` declara su constante de rechazo, que también usa `@DomainRule`:
  ```java
  /** Rechazo de un nombre con un carácter de control. */
  public static final FieldError CHARACTERS =
          new FieldError("name", ErrorCode.PROFILE_NAME_INVALID_CHARACTERS, "El nombre tiene caracteres no permitidos.");
  ```
  y en `ProfessionalSummary`: `new FieldError("summary", ErrorCode.SUMMARY_INVALID_CHARACTERS, "El resumen tiene caracteres no permitidos.")`.
- **Modificar** `ProfessionalProfileMapping`: sigue usando el constructor canónico (sin validar) para leer.
- **Pruebas** `ProfileValueRulesTest` (o `ProfileNameTest` y `ProfessionalSummaryTest` nuevas): `of_shouldTrimAndCompose_whenNameHasSpacesAndDecomposedLetters`; `of_shouldRejectRequired_whenNameIsBlank`; límites 254/255/256 con `a`, `ñ` y emoji; `of_shouldRejectControlCharacter_whenNameHasBell`; `constructor_shouldNotValidate_whenRebuildingStoredValue` (256 `a` → sin excepción); para el resumen: 1999/2000/2001, saltos internos conservados, `"\n\nTexto\n"` → `"Texto"`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfileValueRulesTest" test`.

## [ ] T-3 · Servicio: bloqueo, errores a la vez y sin `provenance` — ≤ 25 min, ≈ 90 líneas

- **Modificar** `ProfileAppService.updateProfileInfo`:
  ```java
  @Transactional
  public ProfessionalProfile updateProfileInfo(UpdateProfileInfoCommand cmd) {
      var profile = loadForUserForUpdate(cmd.profileId(), cmd.uid());
      var errors = new InvalidFieldsException.Collector();
      var name = cmd.name() == null ? null : collect(errors, () -> ProfileName.of(cmd.name()));
      var summary = readSummary(cmd.summary(), errors); // en blanco → borrar; null → sin cambio
      var modality = cmd.preferredModality() == null ? null : collect(errors, () -> FieldValues.option(
              WorkModality.class, cmd.preferredModality(), PREFERRED_MODALITY_INVALID_VALUE, "preferredModality"));
      errors.throwIfAny();
      if (name != null) profile.updateName(name);
      if (cmd.summary() != null) profile.updateSummary(summary); // null borra; un perfil COMPLETED lo rechaza
      if (modality != null) profile.updatePreferredModality(modality);
      repository.save(profile);
      return profile;
  }

  /**
   * Lee el resumen recibido.
   *
   * @return {@code null} si no llegó o llegó en blanco (quien llama distingue los dos casos con {@code cmd.summary()}),
   *         o el resumen validado; si es inválido, anota el error y devuelve {@code null}
   */
  private static ProfessionalSummary readSummary(String raw, InvalidFieldsException.Collector errors) {
      if (raw == null || raw.isBlank()) {
          return null;
      }
      return collect(errors, () -> ProfessionalSummary.of(raw));
  }

  /** Ejecuta una fábrica del dominio y, si rechaza el valor, pasa sus errores al acumulador en lugar de cortar. */
  private static <T> T collect(InvalidFieldsException.Collector errors, Supplier<T> factory) {
      try {
          return factory.get();
      } catch (InvalidFieldsException rejection) {
          rejection.getErrors().forEach(errors::add);
          return null;
      }
  }
  ```
  `Collector.add(FieldError)` existe desde CM-274 PR 1. Comentario de bloque sobre el método: «Primero se juntan las reglas de cada campo, para informar todos los rechazados a la vez; la regla de estado del resumen corre solo si la forma pasó.» Quitar `updateProvenance` y el privado `provenance(String)` si queda sin uso (`PROVENANCE_INVALID_VALUE` sigue vivo: lo usa `POST …/target-roles`); si `ProfessionalProfile.updateProvenance` queda sin llamadas (`git grep`), bórralo.
- **Pruebas** `ProfileAppServiceTest`: `updateProfileInfo_shouldIgnoreProvenance_whenCommandHasNone` (perfil `MANUAL` sigue `MANUAL`); `updateProfileInfo_shouldReportNameAndSummary_whenBothAreInvalid` (comando con `""` y 2001 → dos errores, `save` nunca); `updateProfileInfo_shouldNotSave_whenSummaryClearIsRejected` (perfil `COMPLETED`, `summary` `""`); `updateProfileInfo_shouldLoadForUpdate_whenCalled`.
- El texto se guarda literal (RT-01-CA08; la nota técnica de HU-2.3 «Sanitización XSS en el backend» cita ese mismo CA). No se agrega ningún saneamiento de HTML.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfileAppServiceTest,ProfessionalProfileTest" test`.

## [ ] T-4 · `summaryProvenance` en la respuesta — ≤ 15 min, ≈ 40 líneas

- **Modificar** `ProfileResponse`: componente `String summaryProvenance` después de `summary`, con `@Schema(description = "Procedencia del resumen; MANUAL en el MVP, null sin resumen", allowableValues = {"MANUAL"}, nullable = true)`; en `from`: `p.getSummary() != null ? DataProvenance.MANUAL.name() : null`. Comentario de línea: «Derivado mientras no haya resúmenes escritos por la IA; entonces saldrá de una columna.» No agregar ningún campo de «origen de ejecución de IA» (decisión de Paula: lo define HU-2.8).
- **Pruebas** `ProfileControllerTest.getProfile_shouldReturnManualSummaryProvenance_whenSummaryExists` y `…_shouldReturnNullSummaryProvenance_whenSummaryIsAbsent`.

## [ ] T-5 · Integración — ≤ 30 min, ≈ 180 líneas

- **Crear** `presentation/GeneralInfoEndToEndIT.java` con la misma forma que `presentation/WorkHistoryEndToEndIT.java` de CM-274 (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@Testcontainers`, `postgres:16-alpine` con `@ServiceConnection`, `TestRestTemplate`, Usuario `uid-e2e-<UUID>`):
  - `updateProfileInfo_shouldSaveNameAndSummary_whenDraft`: «Analista de datos» y 300 `a` → 200, `status` `IN_PROGRESS`, `summaryProvenance` `MANUAL`.
  - `updateProfileInfo_shouldClearSummary_whenDraftAndSummaryIsBlank` (`""` y `"   "`): 200, `summary` y `summaryProvenance` `null`; `GET` posterior igual.
  - `updateProfileInfo_shouldRenameAndKeepActive_whenProfileIsCompleted`: perfil completo y finalizado, nombre «Analista de datos senior» → 200 y `COMPLETED`.
  - `updateProfileInfo_shouldKeepSummary_whenActiveProfileClearsIt`: 422 `SUMMARY_NOT_ALLOWED`; `GET` muestra el resumen anterior y `COMPLETED`.
  - `updateProfileInfo_shouldNotChangeOtherProfile_whenUserIsNotOwner`: 403 y el perfil de B igual.
  - `updateProfileInfo_shouldIgnoreUnexpectedFields_whenBodyHasThem`: `{"name":"X","status":"COMPLETED","provenance":"AI_SUGGESTED","firebaseUid":"otro","id":"…"}` → 200; `status` `IN_PROGRESS`, `provenance` `MANUAL`, dueño el del encabezado.
  - `updateProfileInfo_shouldReturnTextLiterally_whenSummaryHasMarkup`: `"<script>alert(1)</script>"` igual en respuesta y base.
  - `updateProfileInfo_shouldReturn422_whenSummaryIsOneMebibyte`.
  - `updateProfileInfo_shouldRejectControlCharacter_whenNameHasNul`: 422, no 500.
  - `updateProfileInfo_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes` (FIA-02): el mismo `PATCH` (`{"name":"Analista","summary":"Resumen"}`) 20 veces → veinte 200 con el mismo cuerpo salvo `updatedAt`; el `GET` final igual al primero y ninguna otra lista cambió.
- **Agregar** a `ProfileWriteLockIT`: `updateProfileInfo_shouldNeverLeaveActiveProfileWithoutSummary_whenCompletionRunsAtTheSameTime`: perfil listo para finalizar; hilo A en `TransactionTemplate` vacía el resumen y espera un latch; hilo B llama `completeProfile`; soltar A → B termina con `IncompleteProfileException` y el perfil queda `IN_PROGRESS` sin resumen. Repetir con el orden inverso (A finaliza y espera; B vacía → `SUMMARY_NOT_ALLOWED`; perfil `COMPLETED` con resumen).
- **Agregar** a `ColumnLengthConsistencyIT` (la creó CM-274 PR 2): `perfil_profesional.nombre` = `ProfileName.MAX_LENGTH`, `resumen` = `ProfessionalSummary.MAX_LENGTH`. A `OpenApiDocumentIT`: `maxLength` 255 y 2000 en `UpdateProfileInfoRequest`, y que `provenance` ya no está.
- **Comandos:** `./mvnw.cmd -B "-Dit.test=GeneralInfoEndToEndIT,ProfileWriteLockIT,ColumnLengthConsistencyIT,OpenApiDocumentIT" verify`.

## [ ] T-6 · OpenAPI, catálogo y cierre — ≤ 25 min

- `ProfileController.updateProfileInfo`: `@ApiResponse` 200, 401, 403, 404, 415, 422 (códigos: `PROFILE_NAME_REQUIRED`, `PROFILE_NAME_INVALID_CHARACTERS`, `PROFILE_NAME_TOO_LONG`, `SUMMARY_NOT_ALLOWED`, `SUMMARY_INVALID_CHARACTERS`, `SUMMARY_TOO_LONG`, `PREFERRED_MODALITY_INVALID_VALUE`, `REQUEST_BODY_INVALID_FORMAT`, `PROFILE_ID_INVALID_FORMAT`), 409 `PROFILE_UPDATE_IN_PROGRESS`, con ejemplos con `code`; quitar `PROVENANCE_INVALID_VALUE` de la descripción del `PATCH`.
- `docs/errores.md`: filas de los códigos de §8 de la spec (endpoints, campo, mensaje, origen, prueba); `PROVENANCE_INVALID_VALUE` ya no lista el `PATCH`.
- **Cierre:** `./mvnw.cmd -B clean verify`; cobertura (≥ 90 % líneas y ramas) de `ProfileName`, `ProfessionalSummary`, `UpdateProfileInfoRequest`, `UpdateProfileInfoCommand`, `ProfileAppService`, `ProfessionalProfile`, `ProfileResponse`, `ErrorCatalog`, y global sin bajar; diff ≤ 1000.

# PR 2 (`CM-54-postman-informacion-general`)

## [ ] T-7 · Postman y Newman — ≤ 30 min, ≈ 450 líneas

- Carpeta `HU-2.3 — Información general` / `PATCH profiles/{id}`; `pre-request` con Usuario `uid-pm-{{$guid}}` y creación del perfil; utilidades `expectProblem`/`expectFieldError` del script de la colección, creadas por CM-274 PR 4).
- Peticiones: las de la columna «Postman» de la spec §4 y una por causa de §6 (`name` `""`, espacios, U+00A0, 254, 255, 256, 255 con emoji, control; `summary` 1999, 2000, 2001, 2000 emojis, vacío en Borrador con `GET` posterior, vacío en Activo, `<script>`; `preferredModality` `hybrid` y `""`; dos campos a la vez; `{}` → 200 sin cambios; `{"name":` → 422 `REQUEST_BODY_INVALID_FORMAT`; `text/plain` → 415; sin `X-User-Id` → 401; ajeno → 403; inexistente → 404; `provenance` ignorado). Cada una con estado, `Content-Type`, `code`, `errors[]`, `requestId` y sin traza.
- **Comandos:** `docker compose down -v`; `docker compose up -d --build`; `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json -e docs/perfil-local.postman_environment.json --folder "HU-2.3 — Información general" --reporters cli,junit --reporter-junit-export target/newman/hu23.xml`, y después la colección completa sin `--folder` para comprobar que nada anterior se rompió. Pegar las dos salidas.
- **SEG-04:** `docker compose logs app | Select-String -Pattern 'Analista|<script>'` → 0 líneas.
- **DES-02:** carpeta `DES-02 — HU-2.3` (crear perfil con `X-User-Id: uid-des-{{$guid}}` y `PATCH` válido), `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json -e docs/perfil-local.postman_environment.json --folder "DES-02 — HU-2.3" -n 105 --reporters cli,json --reporter-json-export target/newman/des02-hu23.json` y el script de p95 de CM-274 T-E3 (cambiando el nombre del archivo); p95 ≤ 2000 ms con `n=100`.
