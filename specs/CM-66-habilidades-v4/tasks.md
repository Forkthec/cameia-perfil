# Tareas — CM-66 (cameia-perfil)

Estado: PR 1a (T-1 a T-3), PR 1b (T-4 a T-6), PR 2a y PR 2b (T-7) hechos y verificados en local; spec y plan aprobados por Paula el 9-oct-2026. Una tarjeta a la vez, con la salida real antes de marcar `[x]`.

## Reglas para todas las tarjetas

1. Rutas `src/main/java/co/edu/unicauca/cameia/perfil/`, `src/test/java/co/edu/unicauca/cameia/perfil/`, `src/main/resources/db/migration/`.
2. **Idioma (R1).** En inglés: identificadores, nombres de métodos de prueba y códigos de error. En español: Javadoc, comentarios (también los de SQL), `@DisplayName`, OpenAPI, logs y mensajes para la persona (los de « » se copian exactos). Los esqueletos ya traen el Javadoc en español.
3. Borde con Bean Validation y `@Valid`; dominio con `SkillName` y el agregado. Cada campo se prueba con ausente, `null`, `""`, espacios, `\t\n`, U+00A0, n−1, n, n+1 en puntos de código (`ñ`, emoji, NFD), control y valores hostiles. Todos los errores de forma a la vez.
4. Nunca 500 por una entrada. `application/problem+json;charset=UTF-8` con `code`, `requestId`, `errors[]`; sin traza, SQL ni nombres de clase. Log de rechazo en `WARN` con `code`, `requestId`, `firebaseUid`; nunca el nombre de la habilidad.
5. Migración nueva con número fijo `V8__alinear_habilidad_perfil.sql` (V5 es de CM-279, V6 y V7 de CM-274, V9 de CM-67); nunca editar V1 a V7. `VARCHAR(60)` = `SkillName.MAX_LENGTH` = `@CodePointSize(max = 60)` = `@Schema(maxLength = 60)` = `@Column(length = 60)`. Cada restricción con una prueba que la viola.
6. Métodos ≈ 20 líneas, ≤ 3 parámetros, `domain` sin Spring, sin dependencias nuevas, sin `Utils`.
7. Javadoc en lo nuevo o modificado; `docs/errores.md` al día en el mismo PR.
8. Pruebas `method_shouldResult_whenCondition`, AAA, literales, sin `Thread.sleep`; `*IT` con Testcontainers (`postgres:16-alpine`) y Failsafe.
9. Comandos: `./mvnw.cmd -q -B "-Dtest=<Clase>" test`; `./mvnw.cmd -B "-Dit.test=<Clase>" verify`; cierre `./mvnw.cmd -B clean verify`.
10. Detente y reporta si algo no coincide con el código real.
11. Commit `CM-66 | <tipo>(perfil): <resultado>`, sin `[IA-ASISTIDO]`, con `Co-Authored-By`. Push y PR solo con el sí de Paula.
12. Caracteres invisibles con escapes `\uXXXX`; verificar con `git diff`.

---

# PR 1 (`CM-66-habilidades`, desde `origin/develop` con CM-274 y CM-54 fusionados)

## [ ] T-0 · Base y reproducción — ≤ 15 min

- Comprobar en `origin/develop` las piezas de CM-274 (`SingleLineText.hasControlCharacter`, `@CodePointSize`, `@DomainRule`, `FieldValues`, `findByIdForUpdate`, `InvalidFieldsException.Collector`, `ColumnLengthConsistencyIT`, `WorkHistoryEndToEndIT`), las reglas `PROFILE_NAME`, `SUMMARY` y `PREFERRED_MODALITY` de CM-54 en `DomainRule.Rule`, y que la última migración sea `V7__alinear_educacion.sql`. Si falta alguna, detente.
- **Línea base:** corre la sección 20 de la spec (`./mvnw.cmd -B clean verify` y Newman de la colección completa) y pega la salida antes de editar.
- Reproducir con `docker compose up -d --build` y pegar en `specs/CM-66-habilidades-v4/evidencia-antes.md`: habilidad de 61 → 201; «Programación» y luego «programacion» → 201; 21.ª → 201; perfil `COMPLETED` con una habilidad y `DELETE` → 204; `"Py\u0000"` → 500; `{"skillName":"","level":"EXPERT","provenance":"MANUAL"}` → un solo error.

## [x] T-1 · Petición de la habilidad — ≤ 20 min, ≈ 80 líneas

- **Modificar** `presentation/dto/AddSkillRequest.java`:
  ```java
  /** Cuerpo de POST /api/v1/profiles/{id}/skills; los textos llegan recortados y en NFC. */
  @Schema(description = "Habilidad que se agrega al perfil")
  public record AddSkillRequest(
          @Schema(description = "Nombre de la habilidad, texto libre", example = "Python", requiredMode = REQUIRED, minLength = 1, maxLength = 60)
          @NotBlank @CodePointSize(max = 60) @DomainRule(Rule.SKILL_NAME) String skillName,
          @Schema(description = "Nivel", example = "INTERMEDIATE", requiredMode = REQUIRED, allowableValues = {"BASIC", "INTERMEDIATE", "ADVANCED"})
          @NotBlank @DomainRule(Rule.SKILL_LEVEL) String level) {
      // Sin procedencia: todo lo que se agrega por esta ruta es MANUAL; lo que escribe la IA usa sus propias rutas.

      /** Normaliza antes de que corra Bean Validation, para que el límite se mida sobre lo que se guardará. */
      public AddSkillRequest {
          skillName = SingleLineText.normalize(skillName);
          level = SingleLineText.normalize(level);
      }
  }
  ```
- **Modificar** `presentation/dto/validation/DomainRule.java`: agregar al final del `enum Rule`, después de `PREFERRED_MODALITY` de CM-54 (cuidando la coma y el `;`):
  ```java
  SKILL_NAME(text -> rejectControl(text, false, SkillName.CHARACTERS), Set.of(SKILL_NAME_INVALID_CHARACTERS)),
  SKILL_LEVEL(text -> FieldValues.option(SkillLevel.class, text, SKILL_LEVEL_INVALID_VALUE, "level"), Set.of(SKILL_LEVEL_INVALID_VALUE))
  ```
  `SkillName.CHARACTERS` lo crea T-2; ejecuta T-1 y T-2 juntas antes de compilar, o crea primero la constante.
- **Modificar** `ErrorCatalog`: claves `AddSkillRequest.skillName.CodePointSize`→`SKILL_NAME_TOO_LONG`; mensaje «La habilidad no puede superar los 60 caracteres.». Los campos con `@DomainRule` no llevan clave. Ajustar `ErrorCatalogTest.DOMAIN_FIELD_CODES`. Agregar a `ErrorCode` `SKILL_NAME_INVALID_CHARACTERS` (en `DOMAIN_FIELD_CODES`), `SKILL_LIMIT_REACHED`, `SKILL_NOT_ALLOWED` (Javadoc en español), al final del `enum`.
- **Pruebas** `ProfileControllerTest`: `addSkill_shouldReturnRequired_whenFieldIsMissingOrBlank` (`skillName` y `level` con ausente, `null`, `""`, `"   "`, `"\t\n"`, `" "`); `addSkill_shouldReturnTooLong_whenNameExceedsLimit` (61 `a`; 60 `a` + `"😀"`; 61 `ñ`); `addSkill_shouldAccept_whenNameIsAtLimit` (60 `a`; 59 `a` + `"😀"`; `"é"` × 60); `addSkill_shouldReturnLevelInvalid_whenLevelIsUnknown` (`EXPERT`, `basic`, `Basic`); `addSkill_shouldReturnBothFields_whenNameIsBlankAndLevelIsUnknown`; `addSkill_shouldRejectControlCharacter_whenNameHasNul` (`"Py\u0000"` → `SKILL_NAME_INVALID_CHARACTERS`, «La habilidad tiene caracteres no permitidos.»). Cada una con `Content-Type` y sin traza.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfileControllerTest,ErrorCatalogTest" test`.

**Resultado T-1 (10-oct-2026).** Rojo: no capturado para las pruebas web (el código del borde se escribió antes que ellas; Desvío 34 de la cola); el rojo de T-2 (`SkillNameTest` sin `SkillName`) sí se vio. Verde: `ProfileControllerTest` 223 pruebas, 0 fallos (cubren requerido, 61 en `a`/`ñ`/emoji, 60 y 59, nivel `EXPERT`/`basic`/`Basic`/`BEGINNER`, los dos campos a la vez, NUL, campos extra). Cambio de contrato: `provenance` sale del cuerpo y de los códigos `PROVENANCE_REQUIRED` y `PROVENANCE_INVALID_VALUE`, que pasan a «Códigos retirados» (Desvío 33 de la cola); `postSkills_returns201` mandaba `EXPERT`, que ya no es un nivel (`ADVANCED`).

## [x] T-2 · `SkillName` — ≤ 30 min, ≈ 120 líneas

- **Crear** `domain/model/SkillName.java`:
  ```java
  /**
   * Nombre de una habilidad del perfil: de 1 a 60 caracteres tras recortar, contados en puntos de código en NFC.
   *
   * <p>El nombre se guarda como la persona lo escribió (recortado y en NFC). Dos nombres son la misma
   * habilidad si coincide su {@link #comparisonKey()}: las mayúsculas, las tildes y los espacios repetidos
   * no hacen distinta una habilidad.</p>
   *
   * @param value el nombre tal como está guardado
   */
  public record SkillName(String value) {
      public static final int MAX_LENGTH = 60;
      public SkillName { Objects.requireNonNull(value, "value"); }

      /**
       * Crea el nombre a partir del texto recibido, aplicando todas sus reglas.
       *
       * @param raw nombre recibido; no {@code null}
       * @return el nombre normalizado
       * @throws InvalidFieldsException con SKILL_NAME_REQUIRED, SKILL_NAME_INVALID_CHARACTERS o SKILL_NAME_TOO_LONG
       */
      public static SkillName of(String raw) { ... }

      /**
       * Clave para detectar repetidas: espacios unidos, minúsculas en {@link Locale#ROOT} y sin marcas
       * combinantes tras la descomposición canónica, así «Programación» y «programacion» —y «Diseño» y
       * «Diseno»— son la misma habilidad.
       *
       * @return la clave de comparación, de a lo sumo {@link #MAX_LENGTH} puntos de código
       */
      public String comparisonKey() { ... }

      /** @param other otro nombre @return {@code true} si los dos son la misma habilidad */
      public boolean sameAs(SkillName other) { return comparisonKey().equals(other.comparisonKey()); }
  }
  ```
  `comparisonKey`: unir espacios con la misma definición de `SingleLineText` (por puntos de código), `toLowerCase(Locale.ROOT)`, `Normalizer.normalize(…, NFD)`, `replaceAll("\\p{M}+", "")`, `Normalizer.normalize(…, NFC)`. Mensajes: «Ingresa una habilidad.», «La habilidad no puede superar los 60 caracteres.». Caracteres de control con `SingleLineText.hasControlCharacter(texto, false)` y la constante que también usa `@DomainRule`: `public static final FieldError CHARACTERS = new FieldError("skillName", ErrorCode.SKILL_NAME_INVALID_CHARACTERS, "La habilidad tiene caracteres no permitidos.");`.
- **Modificar** `ProfileSkill`: campo `SkillName name`; constructor recibe `SkillName`; `getSkillName()` devuelve `name.value()`; agregar `getName()`. Quitar `MAX_NAME_LENGTH` y el uso de `FieldRules` (si `FieldRules` queda sin uso, bórralo).
- **Pruebas** `domain/model/SkillNameTest.java`: `sameAs_shouldMatch_whenNamesDifferOnlyInCaseSpacesOrAccents` (parametrizada con los pares iguales de la spec §5); `sameAs_shouldNotMatch_whenNamesAreDifferentSkills` (`C`/`C++`, `C#`/`C`, `Java`/`JavaScript`); `of_shouldTrimAndCompose_whenNameHasSpacesAndDecomposedLetters`; `of_shouldKeepInnerSpacesAndCase_whenStoring` (`"Machine   Learning"` se guarda igual); límites 59/60/61 con `a`, `ñ`, emoji; `of_shouldRejectRequired_whenNameIsBlank`; `of_shouldRejectControlCharacter_whenNameHasBell`; `constructor_shouldNotValidate_whenRebuildingStoredValue` (100 `a` sin excepción); `comparisonKey_shouldNotExceedMaxLength_whenNameIsAtLimit`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=SkillNameTest" test`.

**Resultado T-2.** Rojo: `SkillNameTest` no compilaba (`SkillName` no existía). Verde: `SkillNameTest` y `SingleLineTextTest.collapseSpaces_…`, 0 fallos. `FieldRules` quedó sin uso y se borró; `ProfileValueRulesTest` pasó de 256/255 a 61/60 con el mensaje de 60.

## [x] T-3 · Agregado: máximo, duplicado y mínimo — ≤ 25 min, ≈ 110 líneas

- **Crear** `SkillLimitReachedException` (`SKILL_LIMIT_REACHED`, «Ya tienes el máximo de 20 habilidades.») y `SkillNotAllowedException` (`SKILL_NOT_ALLOWED`, «No puedes quedarte sin habilidades con el perfil activo. Agrega otra antes de eliminar esta.»); `ErrorCatalog.RESPONSES` con `CONFLICT` («Máximo de habilidades alcanzado», «No se puede eliminar la última habilidad»).
- **Modificar** `ProfessionalProfile`:
  ```java
  public static final int MAX_SKILLS = 20;

  /**
   * Agrega una habilidad. El máximo se comprueba primero: con 20 no se acepta ninguna, repetida o no.
   *
   * @param skill habilidad que se agrega
   * @throws SkillLimitReachedException si el perfil ya tiene el máximo
   * @throws DuplicateSkillException    si el perfil ya tiene la misma habilidad
   */
  public void addSkill(ProfileSkill skill) { ... }

  /**
   * Elimina una habilidad; un perfil Activo conserva al menos una. Las dos comprobaciones corren con la
   * fila del perfil bloqueada, así una finalización en otra pestaña no deja un perfil Activo sin habilidades.
   *
   * @param skillId identificador de la habilidad
   * @throws SkillNotFoundException   si el identificador no es una habilidad de este perfil
   * @throws SkillNotAllowedException si es la única habilidad de un perfil Activo
   */
  public void removeSkill(UUID skillId) { ... }
  ```
  Borrar `normalizarNombreHabilidad`.
- **Modificar** `AddSkillCommand` (solo `requireNonNull(profileId)`) y `ProfileAppService.addSkill`:
  ```java
  @Transactional
  public ProfessionalProfile addSkill(AddSkillCommand cmd) {
      var profile = loadForUserForUpdate(cmd.profileId(), cmd.uid());
      var errors = new InvalidFieldsException.Collector();
      var name = cmd.skillName() == null ? null : collect(errors, () -> SkillName.of(cmd.skillName()));
      if (cmd.skillName() == null) errors.add("skillName", SKILL_NAME_REQUIRED, "Ingresa una habilidad.");
      var level = cmd.level() == null ? null : collect(errors, () -> FieldValues.option(SkillLevel.class, cmd.level(), SKILL_LEVEL_INVALID_VALUE, "level"));
      if (cmd.level() == null) errors.add("level", SKILL_LEVEL_REQUIRED, "Elige un nivel.");
      errors.throwIfAny();
      // Lo que entra por esta ruta es siempre MANUAL: la IA escribe por sus propias rutas.
      profile.addSkill(new ProfileSkill(UUID.randomUUID(), name, level, DataProvenance.MANUAL));
      repository.save(profile);
      return profile;
  }
  ```
  `collect` es el privado genérico que creó CM-54 en `ProfileAppService` (reutilízalo; no lo dupliques).
- **Modificar** `ApiExceptionHandler.handleBusiness`: para `DuplicateSkillException`, `errors` = `[{field:"skillName", code:"SKILL_ALREADY_EXISTS", message:"Esa habilidad ya está en tu perfil."}]` (comentario de línea: «El cliente muestra la repetida bajo el campo de la habilidad.»). Prueba `ProfileControllerTest.addSkill_shouldReturn409_whenSkillIsDuplicated`: 409, `$.code` y `$.errors[0]` con esos tres valores.
- **Pruebas** `ProfessionalProfileTest`: `addSkill_shouldAcceptTwentieth_whenProfileHasNineteen`; `addSkill_shouldRejectTwentyFirst_whenProfileHasTwenty`; `addSkill_shouldRejectLimitFirst_whenProfileHasTwentyAndSkillIsRepeated`; `addSkill_shouldRejectDuplicate_whenOnlyAccentsDiffer`; `removeSkill_shouldRemoveLast_whenProfileIsInProgress`; `removeSkill_shouldRejectLast_whenProfileIsCompleted` (sigue con 1 y `COMPLETED`); `removeSkill_shouldRemove_whenCompletedProfileHasTwo`; `removeSkill_shouldThrowNotFound_whenIdIsForeignInCompletedProfileWithOne`. `ProfileAppServiceTest.addSkill_shouldThrowFieldErrors_whenCommandHasNullRequiredFields`. `ProfileControllerTest`: `addSkill_shouldReturn409_whenSkillIsDuplicated` (con `errors[0].field` `skillName`), `addSkill_shouldReturn409_whenLimitReached`, `removeSkill_shouldReturn409_whenLastSkillOfActiveProfile`, `removeSkill_shouldReturn404_whenSkillIsNotInProfile`, `skills_shouldReturn403_whenProfileBelongsToAnotherUser`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfessionalProfileTest,ProfileAppServiceTest,ProfileControllerTest" test`.

**Resultado T-3.** Rojo: no capturado (el agregado y las excepciones se escribieron antes que las pruebas; Desvío 34 de la cola). Verde: `clean verify` BUILD SUCCESS, 776 unitarias + 207 de integración, 0 fallos (`ejecucion/verify-CM-66-1.log`). Cobertura de lo tocado: `SkillName` 17/17 y 6/6; `ProfileSkill` 11/11; `SingleLineText` 31/31 y 38/38; `ProfessionalProfile` 133/135 y 63/64 (previas); `ProfileAppService` 100/101 y 40/40; `ApiExceptionHandler` 66/66 y 29/30 (previa); `ErrorCatalog` 92/92; `ProfessionalProfileMapping` 98/98 y 26/26; global 97,2 % líneas y 98,4 % ramas. `ProfileAppServiceTest.addSkill_shouldThrowFieldErrors_whenCommandHasNullRequiredFields` reemplaza a la de procedencia desconocida.

## [x] T-4 · Migración V8, entidad, mapeo y traducción del `UNIQUE` — ≤ 30 min, ≈ 130 líneas

- **Crear** `V8__alinear_habilidad_perfil.sql` (número fijo; `git ls-tree --name-only HEAD src/main/resources/db/migration/` debe terminar en `V7__alinear_educacion.sql`; si no, detente) con el texto exacto de la spec §7.
- **Modificar** `ProfileSkillEntity` (`nombre_habilidad` `length = 60`; campo `comparisonKey` → `@Column(name = "nombre_normalizado", nullable = false, length = 60)`), `ProfessionalProfileMapping` (`toSkillEntity` escribe `skill.getName().comparisonKey()`; `toSkillDomain` reconstruye con `new SkillName(e.getSkillName())`).
- **Modificar** `ProfessionalProfileRepositoryAdapter.save`:
  ```java
  @Override
  @Transactional
  public void save(ProfessionalProfile profile) {
      try {
          // Se vacía aquí para que una violación de unicidad salga dentro del adaptador, donde se puede traducir.
          jpa.saveAndFlush(ProfessionalProfileMapping.toEntity(profile));
      } catch (DataIntegrityViolationException e) {
          if (violates(e, SKILL_NAME_CONSTRAINT)) throw new DuplicateSkillException();
          throw e;
      }
  }
  ```
  con `static final String SKILL_NAME_CONSTRAINT = "uq_habilidad_perfil_nombre"` y `violates` que recorre las causas hasta un `SQLException` con estado `23505` cuyo mensaje contiene el nombre de la restricción (mismo patrón de recorrido acotado que `isLockNotAvailable`).
- **Trampa:** las pruebas que construyen `ProfileSkill` con el constructor viejo (`ProfileAppServiceTest`, `ProfessionalProfileTest`, `ProfessionalProfileRepositoryAdapterIT`) pasan a `new ProfileSkill(id, SkillName.of("…"), level, provenance)`.
- **Comandos:** `./mvnw.cmd -B test`.

**Resultado T-4 (10-oct-2026).** `V8__alinear_habilidad_perfil.sql` tal como la spec §7 (la última migración previa era `V7__alinear_educacion.sql`). `ProfessionalProfileRepositoryAdapter.save` ya hacía `saveAndFlush` y traducía por `violates(...)` (lo dejó CM-70); se agregó `uq_habilidad_perfil_nombre` → `DuplicateSkillException`. Rojo: no capturado (migración y mapeo antes que `SkillSchemaIT`; Desvío 34). Verde: `SkillSchemaIT` 8 pruebas (migración sobre datos de V7: «  Programación   Web » → `programacion web`; `ck_` de nombre, nivel y procedencia y `uq_` con su `SQLState`; misma clave en otro perfil; 61 caracteres → `22001`; nombres estándar) y `ProfessionalProfileRepositoryAdapterIT.save_shouldThrowDuplicateSkill_whenComparisonKeyRepeatsInProfile`. Se quitó `getComparisonKey()` de la entidad porque nadie lo lee.

## [x] T-5 · Integración — ≤ 30 min, ≈ 200 líneas

- **Crear** `presentation/SkillsEndToEndIT.java` (forma de `WorkHistoryEndToEndIT`): `addSkill_shouldReturn201WithManualProvenance_whenSkillIsValid` («Python», `INTERMEDIATE`); `addSkill_shouldReturn409_whenNameDiffersOnlyInAccents` («Programación» y luego «programacion» → 409 con `errors[0].field` `skillName`); `addSkill_shouldIgnoreUnexpectedFields_whenBodyHasThem`; `addSkill_shouldReturn422_whenNameIsOneMebibyte`; `addSkill_shouldRejectControlCharacter_whenNameHasNul`; `removeSkill_shouldReturn204_whenDraftHasOnlyOne`; `removeSkill_shouldKeepOtherProfileSkill_whenIdBelongsToAnotherProfile`; `removeSkill_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes` (FIA-02: perfil en Borrador con dos habilidades; 20 `DELETE` de la misma → un 204 y diecinueve 404 `SKILL_NOT_FOUND`; queda una); `addSkill_shouldHaveNoExtraEffect_whenRepeatedTwentyTimes` (FIA-02: el mismo `POST` de «Python» 20 veces → un 201 y diecinueve 409 `SKILL_ALREADY_EXISTS`; una sola habilidad).
- **Crear** `infrastructure/persistence/SkillSchemaIT.java`: `migration_shouldFillComparisonKey_whenAppliedOnDataFromV7` (Flyway `target("7")` sobre un contenedor propio, fila «Programación», migrar → `nombre_normalizado` `programacion`); `constraint_shouldReject_whenRowBreaksIt` (parametrizada: nombre `'  '` → `23514` `ck_habilidad_perfil_nombre_presente`; nivel `'EXPERT'` → `ck_habilidad_perfil_nivel`; procedencia `'HUMAN'`; dos filas del mismo perfil con la misma clave → `23505` `uq_habilidad_perfil_nombre`); `constraints_shouldHaveStandardNames_whenSchemaIsMigrated` (`pk_habilidad_perfil`, `fk_habilidad_perfil_perfil`, `ix_habilidad_perfil_perfil_id`).
- **Agregar** a `ProfessionalProfileRepositoryAdapterIT`: `save_shouldThrowDuplicateSkill_whenComparisonKeyRepeatsInProfile` (agregado reconstituido con «Python» y «python» → `DuplicateSkillException`; la base sin cambios).
- **Agregar** a `ProfileWriteLockIT`: `addSkill_shouldKeepTwenty_whenTwoRequestsArriveWithNineteen`; `addSkill_shouldKeepOne_whenSameSkillArrivesTwiceAtOnce` (una termina bien y la otra `DuplicateSkillException`); `removeSkill_shouldRejectSecond_whenTwoTabsDeleteBothSkillsOfActiveProfile` (perfil `COMPLETED` con dos; una 204 y la otra `SkillNotAllowedException`).
- **Agregar** a `ColumnLengthConsistencyIT`: `habilidad_perfil.nombre_habilidad` y `nombre_normalizado` = `SkillName.MAX_LENGTH`. A `OpenApiDocumentIT`: `maxLength` 60 y `allowableValues` de `level`.
- **Comandos:** `./mvnw.cmd -B "-Dit.test=SkillsEndToEndIT,SkillSchemaIT,ProfessionalProfileRepositoryAdapterIT,ProfileWriteLockIT,ColumnLengthConsistencyIT,OpenApiDocumentIT" verify`.

**Resultado T-5.** `SkillsEndToEndIT` 14 pruebas (201 MANUAL con nombre recortado; 409 por tildes, mayúsculas y espacios con `errors[skillName]`; campos extra ignorados; 1 MiB → 422; NUL → 422; 21.ª y repetida con 20 → `SKILL_LIMIT_REACHED`; 204 en Borrador; 409 `SKILL_NOT_ALLOWED` y la habilidad queda; 204 con dos en perfil activo; habilidad ajena 404; perfil ajeno 403; FIA-02 con veinte repeticiones del alta y de la baja). `ProfileWriteLockIT` (+3): dos altas con 19 dejan 20, el mismo nombre dos veces deja uno, dos bajas con 2 en perfil activo dejan una. `ColumnLengthConsistencyIT` (+1) y `OpenApiDocumentIT` (+1).

## [x] T-6 · OpenAPI, catálogo y cierre del PR 1 — ≤ 25 min

- `ProfileController` (`addSkill`, `removeSkill`): `@ApiResponse` 201/204, 401, 403, 404, 409 (`SKILL_ALREADY_EXISTS`, `SKILL_LIMIT_REACHED`, `SKILL_NOT_ALLOWED`), 415, 422, 409 `PROFILE_UPDATE_IN_PROGRESS`, con ejemplos con `code`. `ProfileResponse.ProfileSkillItem` con `@Schema`.
- `docs/errores.md`: filas de la spec §8; el «Pendiente con destino» de la última habilidad pasa a `SKILL_NOT_ALLOWED` 409; `SKILL_ALREADY_EXISTS` cita `errors[skillName]` y la traducción del `UNIQUE`.
- **Cierre:** `./mvnw.cmd -B clean verify`; cobertura (≥ 90 % líneas y ramas) de las clases de la spec §13 y global sin bajar; diff ≤ 1000.

# PR 2 (`CM-66-postman-habilidades`)

**Resultado T-6.** OpenAPI y `docs/errores.md` quedaron en 1a (el enumerado exige la documentación en la misma capa). Cierre de 1b: `clean verify` BUILD SUCCESS, 776 unitarias + 235 de integración, 0 fallos (`ejecucion/verify-CM-66-1b.log`). Cobertura: `SkillName` 17/17 y 6/6; `ProfessionalProfileMapping` 99/99 y 26/26; `ProfessionalProfileRepositoryAdapter` 39/40 y 22/24 (previas: ramas del bloqueo de la creación); `ProfileSkillEntity` 11/12 (constructor sin argumentos que usa Hibernate); global 97,2 % líneas y 98,4 % ramas.

## [x] T-7 · Postman y Newman — ≤ 30 min, ≈ 550 líneas

- Carpeta `HU-2.5 — Habilidades` con subcarpetas `POST skills` y `DELETE skills/{skillId}`; `pre-request` con Usuario `uid-pm-{{$guid}}` y su perfil; utilidades `expectProblem`/`expectFieldError` de la colección. Para la vigésima y la 21.ª, el `pre-request` agrega 19 habilidades con `pm.sendRequest` (nombres `skill-01` a `skill-19`).
- Peticiones: las de la columna «Postman» de la spec §3 y una por causa de §6 y de §9 (repetidas con mayúsculas, tilde, «ñ», espacios; dos veces la misma eliminación; campos extra; cuerpo ilegible; `text/plain`; sin identidad; ajeno en las dos rutas; inexistente). Cada una con estado, `Content-Type`, `code`, `errors[]`, `requestId` y sin traza.
- **Comandos:** `docker compose down -v`; `docker compose up -d --build`; `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json -e docs/perfil-local.postman_environment.json --folder "HU-2.5 — Habilidades" --reporters cli,junit --reporter-junit-export target/newman/habilidades.xml`, y después la colección completa sin `--folder`; pegar las dos salidas.
- **DES-02:** carpeta `DES-02 — HU-2.5 habilidades` (por iteración: Usuario `uid-des-{{$guid}}`, crear perfil, `POST …/skills` válido y `DELETE` de esa habilidad); `-n 105` con `--reporters cli,json --reporter-json-export target/newman/des02-habilidades.json` y el script de p95 de CM-274 T-E3; p95 ≤ 2000 ms con `n=100` en `POST` y `DELETE`.
- **SEG-04:** `docker compose logs app | Select-String -Pattern 'Programaci|machine learning|Python'` → 0 líneas.

**Resultado T-7 · PR 2a (`POST skills`; 10-oct-2026).** Colección generada con `ejecucion/herramientas/hu25_postman.py post` (no se edita a mano; reescribe de forma idempotente la carpeta, antes de la de HU-2.4). 64 peticiones en `POST skills`: un 201 por cada caso válido (Python intermedio, 60, 59, 60 con emoji, «é» × 60, recorte, NFD→NFC, HTML y `%`/`_` como texto, `provenance`/`id`/`status` ignorados, nombre numérico), siete 409 de duplicado (mayúsculas, tilde, «ñ», espacios, U+00A0 y otro nivel) con `errors[skillName]`, 422 por cada causa de nombre y de nivel con su `code` y su mensaje, cuerpo ilegible, `text/plain` (415), 401, 403, 404 y el límite de 20 (vigésima 201, 21.ª 409 sin `errors[]` y repetida con 20 gana el máximo). Newman contra la imagen empaquetada con la base limpia y el rol `a0000007-…06` en `INACTIVE`: carpeta 84 peticiones (64 de la colección + 18 altas y 2 publicaciones de cuenta.creada auxiliares), 0 fallidas, 489 aserciones (`newman-CM-66-2a.log`). Una primera corrida falló en 6 aserciones porque la habilidad «<b>SQL</b>» de la prueba hacía coincidir el filtro de «sin SQL» de `H.noLeaks`: se renombró a «<b>Excel</b>».

**Resultado T-7 · PR 2b (`DELETE skills/{skillId}`, DES-02, README y corrida completa; 10-oct-2026).** `hu25_postman.py todo` agrega la subcarpeta `DELETE skills/{skillId}` (25 peticiones: 404 de otro perfil e inexistente, 403 en perfil ajeno, 422 de identificadores, 401, 204 de la única habilidad en Borrador y su repetición en 404, perfil activo que conserva su única habilidad con 409 `SKILL_NOT_ALLOWED`, y 204 de una de dos) y la carpeta `DES-02 — HU-2.5`. Newman contra la imagen empaquetada con la base limpia y el rol `a0000007-…06` en `INACTIVE`: carpeta `HU-2.5 — Habilidades` 110 peticiones (89 de la colección + 18 altas y 3 publicaciones de cuenta.creada auxiliares), 0 fallidas, 604 aserciones (`newman-CM-66-2.log`); colección completa 574 peticiones, 0 fallidas, 2 678 aserciones (`newman-CM-66-2-completa.log`); DES-02 con n=100: p95 de 33 ms (crear perfil), 40 ms (agregar habilidad) y 36 ms (eliminarla) (`newman-CM-66-2-des02.log`). Con una copia mutada (tres textos cambiados) fallan 11 aserciones (`newman-CM-66-2-mutada.log`). `docker compose logs app`: dos líneas `ERROR`, ambas el 503 `BIRTH_DATE_UNAVAILABLE` esperado de HU-2.4, y cero apariciones de los nombres de habilidad de las pruebas (SEG-04). Las peticiones de la sección 3 llevan exactamente el nombre de la columna «Postman» (se renombraron cuatro para que coincidieran).

## Autoverificación de la CM (10-oct-2026)

| # | Comprobación | Resultado real |
|---|---|---|
| V-01 | Cada prueba y petición de la sección 3 existe | 22 pruebas citadas en las secciones 3, 13 y 15, todas existen por nombre exacto (`addSkill_shouldReturn409_whenSkillIsDuplicated` se renombró desde `…WithFieldError…` para coincidir con la spec); las 20 peticiones de Postman de la columna «Postman» existen por nombre |
| V-02 | `clean verify` | PR 1a: 776 unitarias + 207 IT, 0 fallos (`verify-CM-66-1.log`); PR 1b: 776 + 235, 0 fallos (`verify-CM-66-1b.log`); PR 2a y 2b: sin cambio de `src` |
| V-03 | Pares de la sección 5 | `SkillNameTest.samePairs` tiene los nueve pares iguales y `differentPairs` los tres distintos; pasan |
| V-04 | 61 caracteres, U+0000, 1 MiB, `text/plain` por petición real | 422 `SKILL_NAME_TOO_LONG`, 422 `SKILL_NAME_INVALID_CHARACTERS`, 422 (1 MiB, Newman), 415; 40 peticiones adversariales y 20 altas simultáneas, ningún 5xx (`adversarial-CM-66.log`) |
| V-05 | 20.ª y 21.ª; repetida con 20 | 201; 409 `SKILL_LIMIT_REACHED` sin `errors[]`; la repetida con 20 también (Newman y `SkillsEndToEndIT`) |
| V-06 | Última en Activo; dos pestañas | 409 `SKILL_NOT_ALLOWED` y el perfil sigue `COMPLETED` con su habilidad; `ProfileWriteLockIT.removeSkill_shouldRejectSecond_whenTwoTabsDeleteBothSkillsOfActiveProfile` |
| V-07 | 409 de repetida | `errors[0]` = `skillName`, `SKILL_ALREADY_EXISTS`, «Esa habilidad ya está en tu perfil.» (`H.fieldError` en siete peticiones de Newman) |
| V-08 | Forma de error de cualquier 4xx | `H.problem` comprueba `application/problem+json` con `charset=UTF-8`, `code`, `detail` y `requestId`, y que no haya traza |
| V-09 | `SkillSchemaIT` | 8 pruebas, 0 fallos: clave rellenada y cada `ck_` y el `uq_` rechazan su fila |
| V-10 | 20 repeticiones | `SkillsEndToEndIT` (alta y baja) y 20 altas simultáneas reales por HTTP: un 201 y diecinueve 409 |
| V-11 | Cobertura | `SkillName` 17/17 y 6/6; `ProfileSkill` 11/11; `ProfessionalProfile` 133/135 y 63/64; `SkillLimitReachedException` y `SkillNotAllowedException` 2/2; `AddSkillRequest` 4/4; `AddSkillCommand` 3/3; `ProfileAppService` 100/101 y 40/40; `ProfessionalProfileMapping` 99/99 y 26/26; `ProfessionalProfileRepositoryAdapter` 39/40 y 22/24; `ApiExceptionHandler` 66/66 y 29/30; `ErrorCatalog` 92/92. Todas sobre 90 %. Global 97,2 % líneas y 98,4 % ramas (la base de CM-54 era 97,1 % y 97,6 %) |
| V-12 | Newman y DES-02 | 0 fallos; p95 de 33, 40 y 36 ms |
| V-13 | Logs; `CM-NNN`; `pom.xml` | Cero nombres de habilidad en los logs; ninguna coincidencia nueva de `CM-NNN` en `src` (las 24 previas son de Sprint 1, Desvío 25); `pom.xml` sin cambios |
| V-14 | Tamaño y revisiones | PR 1a 940, PR 1b 666, PR 2a 660, PR 2b 273 líneas contra su base (todas ≤ 1000); `/code-review` y `/security-review`: worktree de revisión armado, los corre Paula (Prompt H) |

Hallazgos de la autoverificación sin código: (1) un sustituto UTF-16 suelto (`x\ud800y`) se guarda como `x?y` (201), igual que en CM-274 y CM-54: ver la decisión pendiente de la Bandeja; (2) los valores JSON booleanos y numéricos en `skillName` se guardan como texto (`"true"`, `"123"`), el mismo comportamiento fijado en CM-274 (V1).

