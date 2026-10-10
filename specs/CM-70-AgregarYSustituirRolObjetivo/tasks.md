# Tarjetas — CM-70: agregar y sustituir Roles Objetivo solo con roles activos

## Reglas para todas las tarjetas (quien ejecuta no lee la spec ni el plan)

1. **Rutas.** Código: `src/main/java/co/edu/unicauca/cameia/perfil/`. Pruebas: `src/test/java/co/edu/unicauca/cameia/perfil/`. Migraciones:
   `src/main/resources/db/migration/`.
2. **Idioma (R1).** Inglés: identificadores, nombres de métodos de prueba y códigos de error. Español: Javadoc, comentarios (también los de SQL),
   `@DisplayName`, OpenAPI, logs y mensajes. Los textos entre « » se copian exactos (tildes y punto final).
3. **Errores.** Nunca 500 por una entrada del cliente. `application/problem+json;charset=UTF-8` con `code`, `requestId` y, en 422, `errors[]` y
   `detail` «Revisa los campos marcados.». Ningún mensaje repite el valor recibido.
4. **Base de datos.** Nunca editar `V1` a `V5`. Cada restricción nueva tiene una prueba que la viola y afirma el nombre de la restricción.
5. **Código.** Métodos de ≈ 20 líneas, ≤ 3 parámetros, retornos tempranos. `domain` no importa Spring, JPA ni Jakarta. `@Transactional` solo en
   `application.service` (los adaptadores conservan los suyos). Antes de crear una clase: `git grep -n <Nombre> -- src`. Sin dependencias nuevas.
6. **Documentación.** Javadoc en toda clase, `record` y método nuevo o modificado (con el porqué, `@param`, `@return`, `@throws`). Sin `CM-NNN`,
   sin rutas a otros archivos, sin `TODO`, sin código comentado. Todo código nuevo o que cambia de estado se refleja en `docs/errores.md` en la misma
   tarjeta.
7. **Pruebas.** Nombre `method_shouldResult_whenCondition`, `@DisplayName` en español, Arrange-Act-Assert, datos literales, sin `Thread.sleep`.
   Primero la prueba que falla: pega su salida en el resultado de la tarjeta antes de tocar el código. Las `*IT` las corre Failsafe en `verify` con
   Testcontainers (`postgres:16-alpine`), nunca H2.
8. **Comandos.** Una clase: `./mvnw.cmd -q -B "-Dtest=<Clase>" test`; una IT: `./mvnw.cmd -B "-Dit.test=<Clase>" verify` (Docker encendido).
   Cierre: `./mvnw.cmd -B clean verify`. Las órdenes con `^{tree}` o `-D` van por Bash.
9. **Base de la rama.** Las escrituras del perfil ya usan `loadForUserForUpdate` y el repositorio `findByIdForUpdate` (PR 1 de CM-274). En
   `ProfileAppServiceTest`, las escrituras se simulan con `when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile))`.
10. **Detente y reporta** (con la salida real) si la tarjeta no coincide con el código, una prueba existente se rompe sin causa clara o falta algo.
11. **Commit:** `CM-70 | <tipo>(perfil): <resultado>`, en español, sin `[IA-ASISTIDO]`, con el trailer `Co-Authored-By`. Sin push ni PR.

---

# PR 70-0 — Solo documentos (`CM-70-spec-roles-objetivo`, desde la punta de `CM-274-base-escritura-perfil`)

## [ ] T-70.0 · Copiar la spec y reproducir los defectos — ≤ 20 min

- Copiar `specs/CM-70-AgregarYSustituirRolObjetivo/` desde `C:\Users\paanm\Documents\cameia\cameia-worktrees\perfil-specs-v6` (sin moverla).
- Reproducir con la app de la base (`docker compose up -d --build app`, puerto 8082) y pegar la salida en esta tarjeta:
  1. `curl -s -X POST localhost:8082/api/v1/profiles -H "X-User-Id: uid-cm70-a"` (guardar `id`).
  2. Agregar 5 roles (`a0000001-…-000000000001` a `…05`) y un sexto (`a0000001-…-000000000006`): se espera ver **422** hoy.
  3. Agregar con `"provenance":"AI_SUGGESTED"`: se espera ver `AI_SUGGESTED` hoy.
- Commit: `CM-70 | docs(spec): spec, plan y tarjetas de agregar y sustituir roles objetivo`. Medir (`git diff --shortstat <base>...HEAD`, ≈ 645) y
  anotar en la cola.

# PR 70-1 — Estado del catálogo y restricciones (`CM-70-estado-catalogo-roles`, sobre 70-0)

## [x] T-70.1 · Migración `V5_1` y su prueba — ≤ 30 min, ≈ 160 líneas

- **Antes:** `ls src/main/resources/db/migration` → `V1` a `V5`; si existe `V5_1` o `V6`, detente.
- **Crear** `V5_1__estado_catalogo_y_restricciones_rol_objetivo.sql` con el SQL exacto de abajo (comentarios en español):
  ```sql
  -- Estado del catálogo: un rol deja de ofrecerse sin borrarse, porque los Roles Objetivo existentes lo referencian.
  ALTER TABLE rol_profesional
      ADD COLUMN estado VARCHAR(8) NOT NULL DEFAULT 'ACTIVE',
      ADD CONSTRAINT ck_rol_profesional_estado CHECK (estado IN ('ACTIVE', 'INACTIVE'));
  CREATE INDEX ix_rol_profesional_estado ON rol_profesional (estado);

  -- Rol Objetivo: el esquema repite las reglas del dominio (rol del catálogo obligatorio, sin repetir en el perfil).
  ALTER TABLE rol_objetivo
      ALTER COLUMN rol_profesional_id SET NOT NULL,
      ADD CONSTRAINT uq_rol_objetivo_perfil_rol_profesional UNIQUE (perfil_id, rol_profesional_id),
      ADD CONSTRAINT ck_rol_objetivo_procedencia CHECK (procedencia IN ('MANUAL', 'AI_SUGGESTED', 'AI_EDITED')),
      ADD CONSTRAINT ck_rol_objetivo_estado_revision CHECK (estado_revision IN ('PENDING_REVIEW', 'REVIEWED'));
  CREATE INDEX ix_rol_objetivo_rol_profesional_id ON rol_objetivo (rol_profesional_id);

  -- Un rol del catálogo no se borra mientras un Rol Objetivo lo use: se desactiva.
  ALTER TABLE rol_objetivo DROP CONSTRAINT fk_rol_objetivo_rol_profesional;
  ALTER TABLE rol_objetivo
      ADD CONSTRAINT fk_rol_objetivo_rol_profesional
          FOREIGN KEY (rol_profesional_id) REFERENCES rol_profesional (id) ON DELETE RESTRICT;
  ```
- **Crear** `infrastructure/persistence/TargetRoleSchemaIT.java` con la forma de `BirthDateReplicaSchemaIT` (`@SpringBootTest`, `@Testcontainers`,
  `@Container @ServiceConnection static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")`, `JdbcTemplate`). Datos:
  inserta un perfil con `INSERT INTO perfil_profesional (...)` copiando las columnas `NOT NULL` de `V1` (leer el archivo) con
  `id = '11111111-1111-4111-8111-111111111111'`, `firebase_uid = 'uid-schema-roles'`; `@AfterEach` borra `rol_objetivo` y ese perfil.
  Constante: `INSERT INTO rol_objetivo (id, perfil_id, nombre_rol, rol_profesional_id, procedencia, estado_revision, creado_en) VALUES (?::uuid, ?::uuid, ?, ?::uuid, ?, ?, now())`.
  Pruebas (cada una afirma el nombre de la restricción en el mensaje, como `BirthDateReplicaSchemaIT`):
  - `catalog_shouldStartActive_whenMigrationRuns`: `SELECT count(*) FROM rol_profesional WHERE estado <> 'ACTIVE'` = 0 y `count(*) > 0`.
  - `catalog_shouldRejectUnknownStatus_whenUpdated`: `UPDATE rol_profesional SET estado = 'PAUSED' WHERE id = 'a0000001-0000-0000-0000-000000000001'` → `ck_rol_profesional_estado`.
  - `catalog_shouldAcceptInactive_whenUpdated`: el mismo `UPDATE` con `'INACTIVE'` → 1 fila; restaurar a `'ACTIVE'` al final de la prueba.
  - `insert_shouldSucceed_whenRowIsValid`.
  - `insert_shouldFail_whenCatalogRoleIsNull` → mensaje con `rol_profesional_id` y estado SQL `23502`.
  - `insert_shouldFail_whenRoleRepeatsInProfile` → `uq_rol_objetivo_perfil_rol_profesional` (`23505`).
  - `insert_shouldFail_whenProvenanceIsUnknown` (`'IMPORTED'`) → `ck_rol_objetivo_procedencia`.
  - `insert_shouldFail_whenReviewStatusIsUnknown` (`'DONE'`) → `ck_rol_objetivo_estado_revision`.
  - `deleteCatalogRole_shouldFail_whenTargetRoleUsesIt` → `DELETE FROM rol_profesional WHERE id = …` con un Rol Objetivo que lo usa → `fk_rol_objetivo_rol_profesional` (`23503`).
- **Comandos:** `./mvnw.cmd -B "-Dit.test=TargetRoleSchemaIT" verify` → 9 pruebas en verde. Pega la salida.
- **Trampa:** `ddl-auto=validate` falla al arrancar si la entidad no tiene la columna `estado`: hasta T-70.2 la entidad no la mapea, y eso es válido
  (Hibernate solo valida las columnas que la entidad declara).
- **Commit:** `CM-70 | feat(perfil): estado del catálogo de roles y restricciones de rol_objetivo`.
- **Resultado (10-oct):** sin la migración, `TargetRoleSchemaIT`: Tests run: 9, Failures: 5, Errors: 4 (BUILD FAILURE). Con `V5_1`: Tests run: 9, Failures: 0, Errors: 0 (BUILD SUCCESS).

## [x] T-70.2 · Estado del rol en el dominio y en el adaptador — ≤ 30 min, ≈ 150 líneas

- **Modificar** `domain/model/ProfessionalRole.java`:
  ```java
  /**
   * Rol del catálogo de roles profesionales TI (glosario: Rol profesional).
   *
   * <p>Solo un rol activo se puede elegir como Rol Objetivo. Un rol inactivo sigue existiendo porque los Roles Objetivo
   * que ya lo usan lo referencian, pero no se ofrece ni se acepta en una elección nueva.</p>
   *
   * @param id        identificador del rol en el catálogo
   * @param nombre    nombre visible en el idioma pedido
   * @param categoria categoría visible en el idioma pedido
   * @param active    {@code true} si el catálogo lo ofrece hoy
   */
  public record ProfessionalRole(UUID id, String nombre, String categoria, boolean active) {

      /** Mensaje del rol que el buscador no ofrece; es el mismo del rol que no está en el catálogo. */
      static final String NOT_SELECTABLE_MESSAGE = "No encontramos ese rol en el catálogo.";

      /**
       * Confirma que el rol se puede elegir como Rol Objetivo.
       *
       * @return este mismo rol, para encadenar
       * @throws InvalidFieldsException si el rol no está activo: 422 con el campo {@code professionalRoleId}
       */
      public ProfessionalRole requireSelectable() {
          if (!active) {
              throw InvalidFieldsException.of("professionalRoleId", ErrorCode.PROFESSIONAL_ROLE_DISABLED, NOT_SELECTABLE_MESSAGE);
          }
          return this;
      }
  }
  ```
  `ErrorCode.PROFESSIONAL_ROLE_DISABLED` se crea aquí (con Javadoc «El rol del catálogo existe pero no está activo; no se puede elegir.») porque
  el método lo necesita para compilar; su documentación y su lugar en las pruebas del catálogo se completan en T-70.5.
- **Modificar** `ProfessionalRoleEntity`: `@Column(name = "estado", nullable = false, length = 8) private String estado;` con getter/setter.
  (Se mapea como texto: el adaptador lo traduce; el dominio solo conoce `active`).
- **Modificar** `ProfessionalRoleRepositoryAdapter.toDomain`: `new ProfessionalRole(e.getId(), nombre, categoria, "ACTIVE".equals(e.getEstado()))`,
  con la constante `private static final String ACTIVE = "ACTIVE";`.
- **Actualizar** las construcciones de `ProfessionalRole` en pruebas (`git grep -n "new ProfessionalRole(" -- src/test`): agregar `, true`.
- **Pruebas:**
  - `domain/model/ProfessionalRoleTest`: `requireSelectable_shouldReturnRole_whenRoleIsActive`; `requireSelectable_shouldRejectField_whenRoleIsInactive`
    (afirma `getErrors()` = un elemento `professionalRoleId` / `PROFESSIONAL_ROLE_DISABLED` / «No encontramos ese rol en el catálogo.»).
  - `infrastructure/persistence/ProfessionalRoleRepositoryAdapterIT` (Testcontainers): `findById_shouldReturnActiveRole_whenCatalogRowIsActive`
    (`a0000001-0000-0000-0000-000000000002`, nombre «Desarrollador Backend», `active` true); `findById_shouldReturnInactiveRole_whenCatalogRowIsInactive`
    (con `UPDATE … SET estado = 'INACTIVE'` y restaurar en `@AfterEach`); `findById_shouldReturnEmpty_whenRoleDoesNotExist`;
    `findAllByLang_shouldOrderByCategoryAndName_whenSpanish` (primer elemento categoría «Calidad»); `findAllByLang_shouldUseEnglish_whenLangIsEn`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfessionalRoleTest,ProfileAppServiceTest,ProfessionalRoleControllerTest" test` y
  `./mvnw.cmd -B "-Dit.test=ProfessionalRoleRepositoryAdapterIT" verify`.
- **Commit:** `CM-70 | feat(perfil): el rol del catálogo conoce su estado y rechaza su elección si está inactivo`.
- **Resultado (10-oct):** `ProfessionalRoleTest`, `ProfileAppServiceTest`, `ProfessionalRoleControllerTest`, `ErrorCatalogTest`, `ErrorCodeDocumentationTest` en verde; `ProfessionalRoleRepositoryAdapterIT`: Tests run: 5, Failures: 0 (BUILD SUCCESS). No se capturó la salida roja previa: el código se escribió antes que las pruebas (desvío de proceso anotado en la cola). El código `PROFESSIONAL_ROLE_DISABLED` se documentó aquí (fila en `docs/errores.md` y `ErrorCatalogTest`) y no en T-70.5, porque `ErrorCodeDocumentationTest` y `ErrorCatalogTest` fallan en la capa sin eso.

## [x] T-70.3 · La unicidad del Rol Objetivo responde 409 — ≤ 25 min, ≈ 80 líneas

- **Modificar** `ProfessionalProfileRepositoryAdapter.save`:
  ```java
  /** Restricción que impide repetir un rol del catálogo dentro de un perfil. */
  static final String UNIQUE_TARGET_ROLE = "uq_rol_objetivo_perfil_rol_profesional";

  @Override
  @Transactional
  public void save(ProfessionalProfile profile) {
      try {
          // Se escribe ya para que una violación de restricción se traduzca aquí, y no al confirmar fuera del adaptador.
          jpa.saveAndFlush(ProfessionalProfileMapping.toEntity(profile));
      } catch (DataIntegrityViolationException e) {
          throw violates(e, UNIQUE_TARGET_ROLE) ? new DuplicateTargetRoleException() : e;
      }
  }

  /** @return {@code true} si alguna causa es la violación de la restricción indicada */
  static boolean violates(Throwable failure, String constraint) { ... }
  ```
  `violates` recorre las causas (hasta `MAX_CAUSES`, constante que ya existe) buscando `org.hibernate.exception.ConstraintViolationException` con
  `getConstraintName()` igual (sin distinguir mayúsculas) a `constraint`.
- **Pruebas** en `ProfessionalProfileRepositoryAdapterIT`: `save_shouldTranslateUniqueViolation_whenRoleRepeats` (perfil reconstruido con
  `ProfessionalProfile.reconstitute(...)` —o la fábrica de reconstrucción vigente: `git grep -n "static ProfessionalProfile" -- src/main`— con dos
  `TargetRole` de ids distintos y el mismo `professionalRoleId` `a0000001-0000-0000-0000-000000000002`, dentro de un `TransactionTemplate`) → lanza
  `DuplicateTargetRoleException` y la tabla no tiene filas de ese perfil; `save_shouldRethrow_whenOtherConstraintFails` (unitaria en
  `ProfessionalProfileRepositoryAdapterTest` si existe, o con `violates(new RuntimeException(), UNIQUE_TARGET_ROLE)` → `false` y con una cadena que
  contiene la excepción de Hibernate con otro nombre → `false`).
- **Comandos:** `./mvnw.cmd -B "-Dit.test=ProfessionalProfileRepositoryAdapterIT,ProfileWriteLockIT,ProfileCreationConcurrencyIT" verify`.
- **Commit:** `CM-70 | fix(perfil): la violación de unicidad del rol objetivo responde 409 y no 500`.
- **Resultado (10-oct):** rojo: `ProfessionalProfileRepositoryAdapterTest` Tests run: 6, Errors: 2 (`violates` y `UNIQUE_TARGET_ROLE` no existían). Verde: `ProfessionalProfileRepositoryAdapterTest` 6/6; ITs `ProfessionalProfileRepositoryAdapterIT`, `ProfileWriteLockIT`, `ProfileCreationConcurrencyIT`: Tests run: 22, Failures: 0 (BUILD SUCCESS). La prueba del duplicado no comprueba «sin filas»: la clase IT es `@Transactional` con rollback y la transacción queda abortada; la atomicidad queda cubierta por `TargetRoleConcurrencyIT` (T-70.8).

## [ ] T-70.4 · Cierre del PR 70-1 — ≤ 20 min

- `./mvnw.cmd -B clean verify` en verde; cobertura de `ProfessionalRole`, `ProfessionalRoleRepositoryAdapter`, `ProfessionalProfileRepositoryAdapter`
  (≥ 90 % líneas y ramas, cada línea sin cubrir con su razón); `git diff --shortstat <punta de 70-0>...HEAD` (≤ 800).
- Anotar en la cola: SHA, `git rev-parse HEAD^{tree}`, pruebas y cobertura.

---

# PR 70-2 — Regla del rol activo, máximo en 409 y procedencia `MANUAL` (`CM-70-agregar-sustituir-rol-objetivo`)

## [x] T-70.5 · Códigos y catálogo — ≤ 20 min, ≈ 60 líneas

- `ErrorCatalog.RESPONSES`: `entry(TARGET_ROLE_LIMIT_REACHED, CONFLICT, "Máximo de roles objetivo alcanzado", null)` (antes `UNPROCESSABLE_CONTENT`).
- `ErrorCatalogTest.DOMAIN_FIELD_CODES`: agregar `PROFESSIONAL_ROLE_DISABLED`.
- `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown`: la fila de `MaxTargetRolesExceededException` pasa a `409`.
- `docs/errores.md`: fila de `TARGET_ROLE_LIMIT_REACHED` con `409` y prueba `ProfileControllerTest.postTargetRoles_shouldReturn409_whenMaxReached`;
  fila nueva en «Campos rechazados por el dominio»: `| \`PROFESSIONAL_ROLE_DISABLED\` | \`POST …/target-roles\`, \`PATCH …/target-roles/{roleId}\` |
  \`professionalRoleId\` | «No encontramos ese rol en el catálogo.» (CA-2.11.7; texto de CA-2.11.12) | \`ProfessionalRole.requireSelectable\` |
  \`ProfessionalRoleTest.requireSelectable_shouldRejectField_whenRoleIsInactive\` |`.
- **Prueba que falla primero:** `ProfileControllerTest.postTargetRoles_shouldReturn409_whenMaxReached` (reemplaza a `postTargetRoles_returns422WhenMaxExceeded`):
  `status().isConflict()`, `$.code` `TARGET_ROLE_LIMIT_REACHED`, `$.detail` «Ya tienes el máximo de 5 roles objetivo.», `$.errors` no existe.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ErrorCatalogTest,ApiExceptionHandlerTest,ProfileControllerTest,ErrorCodeDocumentationTest" test`.
- **Commit:** `CM-70 | fix(perfil): el sexto rol objetivo responde 409 y el rol inactivo tiene su código`.
- **Resultado (10-oct):** rojo (con el catálogo en 422): `ApiExceptionHandlerTest` `expected:<409> but was:<422>` y `ProfileControllerTest.postTargetRoles_shouldReturn409_whenMaxReached` `Status expected:<409> but was:<422>`. Verde: 170 pruebas, 0 fallos (`ProfileControllerTest`, `ApiExceptionHandlerTest`, `ErrorCatalogTest`, `ErrorCodeDocumentationTest`). `PROFESSIONAL_ROLE_DISABLED` ya estaba hecho en T-70.2.

## [x] T-70.6 · Agregar y sustituir solo roles activos, con procedencia `MANUAL` — ≤ 30 min, ≈ 160 líneas

- **Modificar** `AddTargetRoleRequest`: un solo componente, `@NotNull @Schema(description = "Rol del catálogo; debe estar activo", format = "uuid",
  example = "a0000001-0000-0000-0000-000000000002", requiredMode = Schema.RequiredMode.REQUIRED) UUID professionalRoleId`, con Javadoc en español
  («El servidor ignora cualquier otro campo: la procedencia de un rol agregado a mano es siempre MANUAL.»).
- **Modificar** `ErrorCatalog.FIELD_CODES`: quitar `"AddTargetRoleRequest.provenance.NotNull"`.
- **Modificar** `AddTargetRoleCommand`: `record AddTargetRoleCommand(UUID profileId, String uid, UUID professionalRoleId)`; el controlador lo construye
  sin `provenance`.
- **Modificar** `ProfileAppService`:
  ```java
  @Transactional
  public ProfessionalProfile addTargetRole(AddTargetRoleCommand cmd) {
      var p = loadForUserForUpdate(cmd.profileId(), cmd.uid());
      var role = selectableRole(cmd.professionalRoleId());
      // Un rol agregado a mano nunca trae origen de IA: la procedencia la fija el servidor.
      p.addTargetRole(new TargetRole(UUID.randomUUID(), role.id(), role.nombre(), DataProvenance.MANUAL));
      repository.save(p); return p;
  }

  @Transactional
  public ProfessionalProfile updateTargetRole(UpdateTargetRoleCommand cmd) {
      var p = loadForUserForUpdate(cmd.profileId(), cmd.uid());
      var role = selectableRole(cmd.professionalRoleId());
      p.updateTargetRole(cmd.roleId(), role.id(), role.nombre());
      repository.save(p); return p;
  }

  /** Busca el rol en el catálogo y exige que se pueda elegir: 404 si no existe, 422 si está inactivo. */
  private ProfessionalRole selectableRole(UUID professionalRoleId) {
      return roleRepository.findById(professionalRoleId)
              .orElseThrow(ProfessionalRoleNotFoundException::new)
              .requireSelectable();
  }
  ```
  (Conservar el nombre exacto de `loadForUserForUpdate` que haya en la base; si `provenance(...)` queda sin uso, no se borra: lo usan otras rutas.)
- **Pruebas que fallan primero** (`ProfileAppServiceTest`; roles: `ACTIVE = new ProfessionalRole(UUID.fromString("a0000001-0000-0000-0000-000000000002"), "Desarrollador Backend", "Desarrollo", true)`,
  `INACTIVE = new ProfessionalRole(UUID.fromString("a0000007-0000-0000-0000-000000000006"), "Desarrollador Blockchain", "Otros TI", false)`):
  - `addTargetRole_shouldStoreManualProvenance_whenRoleIsActive` (parametrizada con el perfil en `IN_PROGRESS` y en `COMPLETED`): rol con
    `provenance` `MANUAL`, `roleTitle` «Desarrollador Backend», `verify(repository).save(profile)`.
  - `addTargetRole_shouldRejectInactiveRole_whenCatalogRoleIsDisabled`: `InvalidFieldsException` con `professionalRoleId`/`PROFESSIONAL_ROLE_DISABLED`;
    `verify(repository, never()).save(any())`; perfil sin roles.
  - `addTargetRole_shouldRejectInactiveRole_beforeLimit_whenProfileIsFull`: perfil con 5 roles + `INACTIVE` → `InvalidFieldsException` (no
    `MaxTargetRolesExceededException`).
  - `addTargetRole_shouldThrowNotFound_whenRoleIsNotInCatalog`: `findById` vacío → `ProfessionalRoleNotFoundException`, sin `save`.
  - `updateTargetRole_shouldRejectInactiveRole_whenCatalogRoleIsDisabled`: el perfil conserva su rol original.
  - `updateTargetRole_shouldKeepIdAndProvenance_whenRoleIsReplaced`: rol con `id` `R1` y `provenance` `AI_SUGGESTED` (reconstruido) sustituido por
    `ACTIVE` → mismo `id`, `provenance` `AI_SUGGESTED`, `professionalRoleId` y `roleTitle` nuevos.
  - `targetRoleWrites_shouldThrowAccessDenied_whenProfileBelongsToOtherUser` (parametrizada: agregar, sustituir, eliminar) con el perfil de
    `uid-owner` y la llamada con `uid-other` → `ProfileAccessDeniedException`, sin `save` y sin consultar el catálogo.
- **Pruebas** `ProfessionalProfileTest`: `addTargetRole_shouldAccept_whenProfileHasFourRoles`; `addTargetRole_shouldReportLimit_whenFullAndRepeated`;
  `updateTargetRole_shouldKeepRole_whenReplacedBySameCatalogRole`.
- **Pruebas** `ProfileControllerTest`: `postTargetRoles_shouldIgnoreProvenance_whenClientSendsIt` (cuerpo con `provenance`, `firebaseUid`, `id`;
  `ArgumentCaptor<AddTargetRoleCommand>` afirma `professionalRoleId` y `uid` del encabezado); `postTargetRoles_shouldReturn422WithField_whenRoleIsInactive`
  (el servicio lanza `InvalidFieldsException.of("professionalRoleId", PROFESSIONAL_ROLE_DISABLED, "No encontramos ese rol en el catálogo.")` → 422,
  `$.code` `VALIDATION_FAILED`, `$.errors[0].field` `professionalRoleId`, `$.errors[0].code` `PROFESSIONAL_ROLE_DISABLED`);
  `postTargetRoles_shouldReturnRoleIdRequired_whenBodyIsEmpty` (`{}` → `errors[0].code` `PROFESSIONAL_ROLE_ID_REQUIRED`);
  `postTargetRoles_shouldReturnBodyInvalid_whenRoleIdIsNotUuid` (`{"professionalRoleId":"no-es-uuid"}` → `REQUEST_BODY_INVALID_FORMAT`, el texto
  `no-es-uuid` no aparece en la respuesta). Actualizar las pruebas existentes que enviaban `provenance` (puede quedarse en el cuerpo: se ignora).
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfileAppServiceTest,ProfessionalProfileTest,ProfileControllerTest,ErrorCatalogTest,ArquitecturaTest" test`.
- **Commit:** `CM-70 | feat(perfil): agregar y sustituir roles objetivo solo con roles activos y procedencia MANUAL`.
- **Resultado (10-oct):** rojo: `ProfileAppServiceTest` Tests run: 39, Errors: 39 (`Unresolved compilation problems`: el comando de 3 componentes no existía). Verde: `ProfileAppServiceTest` 53/53, `ProfessionalProfileTest` 27/27, `ProfileControllerTest` 115/115, `ErrorCatalogTest`, `ErrorCodeDocumentationTest`, `ApiExceptionHandlerTest`, `ArquitecturaTest`: 234 pruebas, 0 fallos. Prueba existente cambiada por el CA-2.11.2: se quitó la fila `TARGET_ROLES provenance` de `ProfileControllerTest.fieldCodes` (el campo ya no existe en el contrato; ahora se ignora).

## [ ] T-70.7 · OpenAPI de agregar y sustituir — ≤ 15 min, ≈ 30 líneas

- `ProfileController`, `@ApiResponses` de `POST …/target-roles`: 409 con «Ya existe ese rol objetivo en el perfil (code TARGET_ROLE_ALREADY_EXISTS),
  máximo de 5 roles objetivo (TARGET_ROLE_LIMIT_REACHED) u otra escritura en curso (PROFILE_UPDATE_IN_PROGRESS)»; 422 con «Campos inválidos (code
  VALIDATION_FAILED, con errors[]: PROFESSIONAL_ROLE_ID_REQUIRED, PROFESSIONAL_ROLE_DISABLED), cuerpo ilegible (REQUEST_BODY_INVALID_FORMAT) o
  identificador del perfil mal escrito (PROFILE_ID_INVALID_FORMAT)» (sin `TARGET_ROLE_LIMIT_REACHED` ni `PROVENANCE_*`). `PATCH`: 422 suma
  `PROFESSIONAL_ROLE_DISABLED`; 409 suma `PROFILE_UPDATE_IN_PROGRESS` si la base aún no lo lista.
- **Prueba:** `OpenApiDocumentIT` sigue en verde; si compara descripciones, actualizarla.
- **Commit:** `CM-70 | docs(perfil): OpenAPI de agregar y sustituir rol objetivo con los estados del backlog`.

## [ ] T-70.8 · Concurrencia y repetición — ≤ 30 min, ≈ 160 líneas

- **Crear** `infrastructure/persistence/TargetRoleConcurrencyIT.java` con la forma de `ProfileCreationConcurrencyIT` (Testcontainers,
  `ExecutorService`, `CountDownLatch` de arranque, limpieza de `firebase_uid like 'uid-roles-%'`). Usa `ProfileCreationAppService` para crear el
  perfil y `ProfileAppService` para agregar.
  - `addTargetRole_shouldStoreOnce_whenSameRoleArrivesTwiceAtOnce`: 2 hilos, mismo rol `a0000001-0000-0000-0000-000000000002` → 1 fila en
    `rol_objetivo`, un resultado correcto y un `DuplicateTargetRoleException`.
  - `addTargetRole_shouldNeverExceedFive_whenSixArriveAtOnce`: 6 hilos con `a0000001-…-000000000001` a `…06` → 5 filas, cinco correctos y un
    `MaxTargetRolesExceededException`.
  - `addTargetRole_shouldStoreOnce_whenSameRoleIsSentTwentyTimes` (FIA-02): 20 llamadas seguidas con el mismo rol → 1 fila, 19 `DuplicateTargetRoleException`.
- **Comandos:** `./mvnw.cmd -B "-Dit.test=TargetRoleConcurrencyIT" verify`.
- **Detente si** quedan dos filas con el mismo rol: el bloqueo del perfil no se está tomando.
- **Commit:** `CM-70 | test(perfil): roles objetivo sin duplicados ni sexto rol con peticiones simultáneas`.

## [ ] T-70.9 · Postman y Newman — ≤ 30 min, ≈ 330 líneas

- En `docs/CAMEIA_Perfil_Sprint1.postman_collection.json` (UTF-8, editar con `Edit`, nunca con un script que reescriba el archivo entero), crear al
  final la carpeta **«HU-2.11 — Roles objetivo»** con variables de colección nuevas `user_c` = `firebase-uid-roles-v6`, `profile_c`, `role_c`. Cada
  petición lleva `X-User-Id`, `Content-Type: application/json` en las de cuerpo y estas pruebas: estado; `code` (si es error); `detail` literal;
  `Content-Type` incluye `application/problem+json` y `charset=utf-8` (errores); `responseTime < 2000`; el texto no coincide con
  `/Exception|org\.|co\.edu|SQL/`.
  1. `OK POST /profiles — Usuario C crea su perfil (guarda profile_c)` → 201.
  2. `OK POST /target-roles — 201 MANUAL aunque llegue provenance (CA-2.11.2)`: cuerpo `{"professionalRoleId":"a0000001-0000-0000-0000-000000000001","provenance":"AI_SUGGESTED","firebaseUid":"otro"}` → 201, el rol tiene `provenance` `MANUAL`; guarda `role_c`.
  3. `ERR POST /target-roles — 409 rol repetido (CA-2.11.4)`: mismo rol → 409 `TARGET_ROLE_ALREADY_EXISTS`, «Ese rol objetivo ya está en tu perfil.».
  4. `ERR POST /target-roles — 422 rol inactivo (CA-2.11.7)`: `a0000007-0000-0000-0000-000000000006` → 422, `errors[0].field` `professionalRoleId`, `errors[0].code` `PROFESSIONAL_ROLE_DISABLED`.
  5. `ERR PATCH /target-roles/{roleId} — 422 rol inactivo`: `{{role_c}}` con el mismo rol inactivo → 422 igual.
  6. `OK PATCH /target-roles/{roleId} — 200 conserva id y procedencia (CA-2.11.1)`: `a0000002-0000-0000-0000-000000000001` → 200; el rol con `id` = `role_c` tiene `roleTitle` «Científico de Datos» y `provenance` `MANUAL`.
  7. Tres `OK POST /target-roles` con `a0000001-…-000000000002`, `…03`, `…04`; el tercero se llama `OK POST /target-roles — 201 quinto rol (CA-2.11.10)` y comprueba 5 roles.
  8. `ERR POST /target-roles — 409 sexto rol (CA-2.11.5)`: `a0000001-…-000000000005` → 409 `TARGET_ROLE_LIMIT_REACHED`, «Ya tienes el máximo de 5 roles objetivo.», sin `errors`.
  9. `ERR POST /target-roles — 403 perfil ajeno (CA-2.11.13)`: `X-User-Id` = `{{user_id}}` sobre `{{profile_c}}` → 403 `PROFILE_NOT_ALLOWED`, «No encontramos lo que buscabas.».
  10. `ERR POST /target-roles — 422 sin professionalRoleId`: `{"provenance":"MANUAL","extra":"<script>"}` → 422, `errors[0].code` `PROFESSIONAL_ROLE_ID_REQUIRED`.
- **Preparar el rol inactivo** (solo en la base local, antes de Newman):
  `docker exec cameia-perfil-db psql -U cameia_perfil -d cameia_perfil -c "UPDATE rol_profesional SET estado='INACTIVE' WHERE id='a0000007-0000-0000-0000-000000000006';"`
  y al terminar el mismo `UPDATE` con `'ACTIVE'`.
- **Actualizar** la petición existente `OK POST /profiles/{id}/target-roles — agrega …` solo si falla por el cambio (no debería: `provenance` se ignora).
- **Comandos:** `./mvnw.cmd -B -DskipTests package`, `docker compose up -d --build app`, `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json --reporters cli,junit --reporter-junit-export target/newman-cm70.xml`
  contra una base limpia (`docker compose down -v` antes). Pega el resumen (peticiones, pruebas, fallos).
- **Commit:** `CM-70 | test(perfil): peticiones de Postman de agregar y sustituir roles objetivo`.

## [ ] T-70.10 · Autoverificación y cierre — ≤ 30 min

- Correr la lista de verificación V1 a V17 de la spec (§15) fila por fila y pegar el resultado; V8, V9 y V11 son peticiones reales adversariales.
- `./mvnw.cmd -B clean verify`; cobertura por clase de lo tocado (≥ 90 % líneas y ramas, cada línea sin cubrir con su razón);
  `git diff --shortstat <punta de 70-1>...HEAD` (≤ 800; si pasa, T-70.9 va en una capa 70-3).
- `/simplify` y `/code-review high` sobre la capa; corregir o descartar con razón.
- CM-70 toca migraciones: preparar el worktree de revisión del Prompt H (`flujo/ejecucion-en-cola.md` §7.2) y dejar la línea en la bandeja.
