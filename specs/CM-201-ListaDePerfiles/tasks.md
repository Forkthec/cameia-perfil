# Tarjetas · CM-201 Lista de perfiles

**Reglas para todas las tarjetas:** identificadores y nombres de prueba en inglés (`method_shouldX_whenY`) con `@DisplayName` en español; Javadoc en todo lo nuevo; sin `CM-201` en comentarios; sin dependencias nuevas; sin tocar migraciones ni `.github/`; nunca registrar `status`, nombres ni roles. Detenerse y reportar a Paula con la salida real si algo no coincide. Docker Desktop encendido (los `*IT` corren con Failsafe en `verify`).

---

## T0 · Preparación

1. `git fetch`; rama `CM-201-lista-de-perfiles` desde `origin/develop` (`0854e5c` o posterior; anotar el SHA).
2. Línea base: `./mvnw.cmd -B -ntp clean verify` → `BUILD SUCCESS`, con los `*IT` ejecutados (no omitidos). Si falla, detenerse.
3. Mover CM-201 en Jira a «En curso».

---

## T1 · Dominio (REQ-05, REQ-07)

**`domain/model/ProfileStatusFilter.java`**

```java
/**
 * Interpreta el filtro de estado de la lista de perfiles. Solo admite el nombre exacto de un estado
 * del ciclo de vida; cualquier otra cosa es un error del cliente, no «sin filtro».
 */
public final class ProfileStatusFilter {
    private ProfileStatusFilter() { }

    /**
     * @param raw valor de {@code ?status=}; {@code null} cuando no llegó
     * @return el estado pedido, o vacío si no hay filtro
     * @throws ProfileStatusInvalidException si llegó vacío, en minúsculas, desconocido o con caracteres extra
     */
    public static Optional<ProfileStatus> parse(String raw) {
        if (raw == null) return Optional.empty();
        for (ProfileStatus status : ProfileStatus.values()) {
            if (status.name().equals(raw)) return Optional.of(status);
        }
        throw new ProfileStatusInvalidException();
    }
}
```

**`domain/model/ProfileSummary.java`**

```java
/** Vista mínima de un perfil para la lista: lo que la pantalla necesita y nada más. */
public record ProfileSummary(ProfileId id, String name, ProfileStatus status, Instant createdAt,
                             List<TargetRoleSummary> targetRoles) {
    /** Rol objetivo tal como se muestra en la lista. */
    public record TargetRoleSummary(UUID professionalRoleId, String roleTitle) { }
    // constructor compacto: requireNonNull en id, status, createdAt y targetRoles; targetRoles = List.copyOf(...)
}
```

**`domain/exception/ProfileStatusInvalidException.java`**: copia de `UnsupportedLanguageException` con `ErrorCode.PROFILE_STATUS_INVALID_VALUE` y el mensaje «Selecciona una opción.».

**`ErrorCode.java`:** agregar `PROFILE_STATUS_INVALID_VALUE` junto a `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE`, con Javadoc «El filtro de estado de la lista no es un estado del perfil.».

**`presentation/advice/ErrorCatalog.java`:** en `RESPONSES`, junto a la del idioma: `entry(PROFILE_STATUS_INVALID_VALUE, UNPROCESSABLE_CONTENT, "Estado no válido", null)`.

**`docs/errores.md`:** fila en la tabla de códigos (hacerlo aquí, si no `ErrorCodeDocumentationTest` falla): `| \`PROFILE_STATUS_INVALID_VALUE\` | 422 | \`GET /api/v1/profiles?status=\` | — | «Selecciona una opción.» | \`ProfileStatusInvalidException\` | \`ProfileStatusFilterTest.parse_shouldThrow_whenInvalid\`, \`ProfileControllerTest.listProfiles_shouldReturn422_whenStatusInvalid\` |`.

**Pruebas** `src/test/java/co/edu/unicauca/cameia/perfil/domain/model/ProfileStatusFilterTest.java`:

| Prueba | Entrada | Esperado |
|---|---|---|
| `parse_shouldReturnEmpty_whenNull` | `null` | `Optional.empty()` |
| `parse_shouldReturnStatus_whenExact` (`@EnumSource(ProfileStatus.class)`) | `status.name()` | `Optional.of(status)` |
| `parse_shouldThrow_whenInvalid` (`@ValueSource`) | `""`, `"   "`, `"completed"`, `"ARCHIVED"`, `"COMPLETED,IN_PROGRESS"`, `"COMPLETED "`, `"COMPLETED'--"`, `"A".repeat(1000)` vía `@MethodSource` | `ProfileStatusInvalidException`, código `PROFILE_STATUS_INVALID_VALUE`, mensaje «Selecciona una opción.» |

`ErrorCatalogTest` y `ErrorCodeDocumentationTest` existentes deben seguir verdes.

**Comando:** `./mvnw.cmd -B -ntp test -Dtest="ProfileStatusFilterTest,ErrorCatalogTest,ErrorCodeDocumentationTest,ErrorCodeTest"`.

**Commit:** `CM-201 | feat(perfil): filtro de estado y vista resumida del perfil`.

---

## T2 · Puerto, consulta y adaptador (REQ-02, REQ-04, REQ-10)

**Puerto** `ProfessionalProfileRepository`: agregar

```java
/**
 * Perfiles de un usuario con sus roles objetivo, del primero creado al último (a igual instante, por id),
 * en una sola lectura. No carga experiencia, formación ni habilidades.
 *
 * @param owner  dueño de los perfiles, tomado de la identidad del Gateway
 * @param status estado a filtrar, o {@code null} para todos
 * @return la lista, vacía si no hay perfiles
 */
List<ProfileSummary> findSummariesByOwner(FirebaseUid owner, ProfileStatus status);
```

**`ProfessionalProfileJpaRepository`:** dos consultas (trampa: `:status is null` con un enumerado falla en Hibernate al inferir el tipo). Sin `distinct`: Hibernate 6 ya quita los perfiles repetidos por la unión, y un `DISTINCT` en SQL solo agrega costo:

```java
@Query("select p from ProfessionalProfileEntity p left join fetch p.targetRoles "
        + "where p.firebaseUid = :uid order by p.createdAt asc, p.id asc")
List<ProfessionalProfileEntity> findWithTargetRolesByFirebaseUid(@Param("uid") String uid);

@Query("select p from ProfessionalProfileEntity p left join fetch p.targetRoles "
        + "where p.firebaseUid = :uid and p.status = :status order by p.createdAt asc, p.id asc")
List<ProfessionalProfileEntity> findWithTargetRolesByFirebaseUidAndStatus(@Param("uid") String uid,
        @Param("status") ProfileStatus status);
```

Comprobar el nombre real del atributo de `firebase_uid` en `ProfessionalProfileEntity` (`firebaseUid` según `countByFirebaseUid`); si difiere, usar el real.

**Adaptador:** `@Override @Transactional(readOnly = true) public List<ProfileSummary> findSummariesByOwner(...)`: elige la consulta según `status == null` y mapea con `ProfessionalProfileMapping::toSummary`.

**`ProfessionalProfileMapping.toSummary(ProfessionalProfileEntity e)`:** `new ProfileSummary(ProfileId.of(e.getId()), e.getName(), e.getStatus(), e.getCreatedAt(), roles)`, con `roles` = los `TargetRoleEntity` ordenados por `getCreatedAt()` y luego `getId()` (comparador explícito: no depender de `@OrderBy` con `join fetch`) y mapeados a `TargetRoleSummary(e.getRolProfesionalId(), e.getRoleTitle())`. Usar los nombres reales de los getters (`getName`, `getStatus`…); comprobarlos en la entidad.

**Prueba** `src/test/java/co/edu/unicauca/cameia/perfil/infrastructure/persistence/ProfileSummariesQueryIT.java` (forma de `ProfessionalRoleRepositoryAdapterIT`: `@SpringBootTest`, `@Testcontainers`, `@ServiceConnection`, `@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")`; datos con `JdbcTemplate`; limpieza `DELETE FROM perfil_profesional WHERE firebase_uid LIKE 'uid-cm201-%'`):

| Prueba | Datos | Esperado |
|---|---|---|
| `findSummaries_shouldOrderByCreationThenId` | `uid-cm201-a`: perfiles creados `2026-10-01T10:00Z` (P1), `2026-10-02T10:00Z` (P2), `2026-09-30T10:00Z` (P3); dos más con `2026-10-03T10:00Z` y ids `…0001` y `…0002` | orden P3, P1, P2, `…0001`, `…0002` |
| `findSummaries_shouldOrderTargetRolesByCreation` | perfil con roles insertados con `creado_en` 12:00, 10:00 y 11:00 | roles 10:00, 11:00, 12:00; cada uno con `professionalRoleId` y `roleTitle` |
| `findSummaries_shouldReturnOnlyOwnerProfiles` | 2 perfiles de `uid-cm201-a`, 1 de `uid-cm201-b` | A ve 2; B ve 1 |
| `findSummaries_shouldFilterByStatus_whenGiven` | uno `COMPLETED`, uno `IN_PROGRESS` | con `COMPLETED`: 1; con `null`: 2 |
| `findSummaries_shouldReturnEmpty_whenNoProfiles` | `uid-cm201-vacio` | lista vacía |
| `findSummaries_shouldReturnNullName_andEmptyRoles` | perfil sin nombre ni roles | `name` nulo, `targetRoles` vacío |
| `findSummaries_shouldUseSingleQuery` | 5 perfiles con 5 roles cada uno | tras `statistics.clear()`: `getCollectionFetchCount()` = 0 y `getEntityFetchCount()` = 0 (ninguna carga perezosa), y la consulta de la lista ejecutada 1 vez (`getQueryStatistics(<la JPQL>).getExecutionCount()`). **Trampa:** no usar `getPrepareStatementCount()`: cuenta también las sentencias de tareas en segundo plano del contexto y la prueba se vuelve inestable |
| `findSummaries_shouldNotWrite` | la misma lectura | `getEntityInsertCount()`, `getEntityUpdateCount()` y `getEntityDeleteCount()` en 0 |

**Commit:** `CM-201 | feat(perfil): consulta de los perfiles del usuario con sus roles en una sola lectura`.

---

## T3 · Servicio, DTO y controlador (REQ-01, REQ-03, REQ-06, REQ-08, REQ-09)

**`ProfileAppService`:**

```java
/**
 * Lista los perfiles de quien llama, con sus roles objetivo, del primero creado al último.
 *
 * <p>La identidad se revisa antes que el filtro: sin identidad la respuesta es 401 aunque el filtro también
 * sea inválido. El dueño sale siempre del Gateway; ningún parámetro de la consulta lo cambia.</p>
 *
 * @param uid    valor de {@code X-User-Id}
 * @param status valor de {@code ?status=}, o {@code null}
 * @return los perfiles, vacía si no hay
 * @throws IdentityRequiredException      si la identidad falta o no es válida
 * @throws ProfileStatusInvalidException  si el filtro no es un estado
 */
public List<ProfileSummary> listProfiles(String uid, String status) {
    var owner = FirebaseUid.required(uid);
    var filter = ProfileStatusFilter.parse(status).orElse(null);
    return repository.findSummariesByOwner(owner, filter);
}
```

**`presentation/dto/ProfileListResponse.java`:**

```java
@Schema(description = "Perfiles del usuario, del primero creado al último")
public record ProfileListResponse(
        @Schema(description = "Perfiles; vacía si el usuario no tiene ninguno. Como máximo 5 por el plan")
        List<ProfileListItem> profiles) {
    // Nombres largos a propósito: springdoc nombra los esquemas por el nombre simple de la clase, y «Item» o «Role» chocarían con otros DTO.

    @Schema(description = "Perfil en la lista")
    public record ProfileListItem(
            @Schema(description = "Identificador del perfil", format = "uuid") UUID id,
            @Schema(description = "Nombre del perfil; null si aún no tiene", example = "Analista de datos", nullable = true) String name,
            @Schema(description = "Estado del perfil", allowableValues = {"PENDING", "IN_PROGRESS", "IN_REVIEW", "COMPLETED"}, example = "COMPLETED") String status,
            @Schema(description = "Roles objetivo en el orden en que se agregaron") List<ProfileListTargetRole> targetRoles) { }

    @Schema(description = "Rol objetivo del perfil")
    public record ProfileListTargetRole(
            @Schema(description = "Rol del catálogo", format = "uuid") UUID professionalRoleId,
            @Schema(description = "Nombre del rol guardado en el perfil", example = "Analista de Datos") String roleTitle) { }

    /** Arma la respuesta desde la vista del dominio. */
    public static ProfileListResponse from(List<ProfileSummary> summaries) { … }
}
```

**`ProfileController`:** nuevo método antes de `createProfile`:

```java
@Operation(summary = "Listar los perfiles del usuario",
        description = "Devuelve los perfiles de quien llama, del primero creado al último, con su nombre, estado y roles objetivo. "
                + "Sin filtro devuelve todos los estados; con status, solo ese. Cualquier otro parámetro se ignora.")
@ApiResponses({ 200 (ProfileListResponse, encabezado Cache-Control no-store), 401 IDENTITY_REQUIRED, 406 MEDIA_TYPE_NOT_ACCEPTABLE,
        422 PROFILE_STATUS_INVALID_VALUE — con ApiErrorResponse y ejemplos con code, como getProfile })
@GetMapping
ResponseEntity<ProfileListResponse> listProfiles(
        @Parameter(description = "Estado a filtrar: PENDING, IN_PROGRESS, IN_REVIEW o COMPLETED", example = "COMPLETED")
        @RequestParam(value = "status", required = false) String status,
        @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String uid) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(ProfileListResponse.from(profileAppService.listProfiles(uid, status)));
}
```

**Pruebas:**

`ProfileAppServiceTest` (mocks existentes):

| Prueba | Esperado |
|---|---|
| `listProfiles_shouldReturnRepositoryResult` | `uid-a`, sin filtro → `verify(repository).findSummariesByOwner(new FirebaseUid("uid-a"), null)`; devuelve la misma lista |
| `listProfiles_shouldPassStatus_whenGiven` | `"COMPLETED"` → se pasa `ProfileStatus.COMPLETED` |
| `listProfiles_shouldCheckIdentityBeforeStatus` | uid `null`, status `"x"` → `IdentityRequiredException`; `verifyNoInteractions(repository)` |
| `listProfiles_shouldThrowStatusInvalid_whenUnknown` | `"ARCHIVED"` → `ProfileStatusInvalidException`; sin llamar al repositorio |

`ProfileControllerTest` (standalone con `ApiExceptionHandler`):

| Prueba | Petición | Esperado |
|---|---|---|
| `listProfiles_shouldReturnContractShape` | servicio devuelve 1 perfil con 1 rol | 200; `$.profiles[0]` con exactamente las claves `id`, `name`, `status`, `targetRoles`; `$.profiles[0].targetRoles[0]` con exactamente `professionalRoleId` y `roleTitle` |
| `listProfiles_shouldReturnEmptyArray_whenNoProfiles` | lista vacía | `{"profiles":[]}` |
| `listProfiles_shouldSendNoStore` | — | encabezado `Cache-Control: no-store` |
| `listProfiles_shouldReturn422_whenStatusInvalid` | servicio lanza `ProfileStatusInvalidException` | 422, `code`, `detail` «Selecciona una opción.», `title` «Estado no válido», sin `errors` |
| `listProfiles_shouldReturn401_whenIdentityMissing` / `…_whenIdentityTooLong` | servicio lanza `IdentityRequiredException` | 401 `IDENTITY_REQUIRED` |
| `listProfiles_shouldIgnoreUnknownParameters` | `?firebaseUid=uid-otro` con `X-User-Id: uid-a` | el servicio recibe `uid-a` y `status` nulo |

**Commit:** `CM-201 | feat(perfil): GET /api/v1/profiles con la lista ordenada y sin caché`.

---

## T4 · Punta a punta, OpenAPI y Postman

`src/test/java/co/edu/unicauca/cameia/perfil/presentation/ProfileListEndToEndIT.java` (forma de `TargetRolesEndToEndIT`; datos de perfiles adicionales con `JdbcTemplate`, porque hoy el plan Free solo deja crear 1 por la API):

| Prueba | Esperado |
|---|---|
| `list_shouldReturnEmptyList_whenUserHasNoProfiles` | 200 `{"profiles":[]}` |
| `list_shouldReturnOnlyDrafts_whenNoneActive` | 1 `IN_PROGRESS` |
| `list_shouldReturnSingleActive_whenOnlyOne` | 1 `COMPLETED` |
| `list_shouldReturnDraftsAndActive_whenUserHasBoth` | 2, en orden de creación |
| `list_shouldFilterCompleted` | `?status=COMPLETED` → 1 |
| `list_shouldIgnoreForeignUidParameter` | B pide `?firebaseUid=<uid de A>` → solo los de B |
| `list_shouldReturnTargetRolesWithCatalogIdAndTitle` | rol agregado por la API: `professionalRoleId` = el del catálogo, `roleTitle` no vacío |
| `list_shouldKeepRole_whenCatalogRoleInactive` | rol desactivado por SQL sigue en la lista |
| `list_shouldReturn422_whenStatusRepeated` | `?status=COMPLETED&status=IN_PROGRESS` → 422 |
| `list_shouldNotEchoStatus_whenHostile` | `?status=COMPLETED%27--` → 422; el cuerpo no contiene `'--` |
| `list_shouldDeclareCharsetAndNoStore` | `Content-Type` contiene `charset=UTF-8`; `Cache-Control: no-store` |
| `list_shouldNotLogStatusValue` (`OutputCaptureExtension`) | `?status=VALOR-SECRETO` → el log tiene `PROFILE_STATUS_INVALID_VALUE` y no `VALOR-SECRETO` |
| `list_shouldReturnSameResult_whenRepeated20Times` (FIA-02) | 20 `GET` iguales | las 20 respuestas son idénticas y el número de filas de `perfil_profesional` y `rol_objetivo` no cambia |

**Trampa:** `OpenApiDocumentIT.apiDocs_shouldDescribeCommonErrorAndCodes_whenRequested` y `ErrorCatalogTest` pueden recorrer todos los códigos; si fallan por `PROFILE_STATUS_INVALID_VALUE`, falta su fila o su ejemplo, no se debilita la prueba.

`OpenApiDocumentIT.apiDocs_shouldDescribeListProfiles_whenRequested`: `/v3/api-docs` tiene `paths./api/v1/profiles.get` con respuestas `200`, `401`, `406` y `422`, y el esquema `ProfileListResponse` con `profiles`.

**Postman:** carpeta «HU-4.2 · Lista de perfiles» con las 11 peticiones de la spec §13 (L-01 a L-11) y carpeta aparte «DES-02 · HU-4.2» (L-12), con la misma forma que las carpetas DES-02 existentes; se corre con `--folder "DES-02 · HU-4.2" -n 110 --reporters cli,json`, se descartan las 10 primeras y el p95 de las 100 restantes se calcula del JSON y se guarda en `ejecucion/evidencia/CM-201/des-02.txt`, usuarios `uid-cm201-a` y `uid-cm201-b`, con `pm.test` de estado, `code`, mensaje, `requestId`, `Content-Type` con `charset=UTF-8` y, en L-02, `pm.expect(pm.response.responseTime).to.be.below(2000)`. Para L-03, la cuenta debe tener fecha de nacimiento replicada si la finalización lo exige: reutilizar la carpeta «Preparación» (publicar `cuenta.creada` para `uid-cm201-a`) o, si no aplica, documentarlo en el README de la colección.

**Comando Newman:** con `docker compose up -d` (base y RabbitMQ) y el jar arrancado: `npx newman run docs/CAMEIA_Perfil_Sprint1.postman_collection.json -e docs/perfil-local.postman_environment.json --folder "HU-4.2 · Lista de perfiles" --reporters cli,junit --reporter-junit-export ../../ejecucion/evidencia/CM-201/newman.xml` → 11 peticiones, 0 fallos.

**Commit:** `CM-201 | test(perfil): pruebas de punta a punta y colección de Postman de la lista`.

---

## T5 · Documentación

1. `docs/adr/0007-lista-de-perfiles.md`: contexto (HU-4.2, HU-2.13), decisión (objeto con `profiles`, orden por creación y `id`, filtro con los 4 estados, 422 sin `errors`, `no-store`, sin paginación por el tope de 5), consecuencias (cambio aditivo; Frontend avisado el 17-oct), alternativas descartadas (arreglo sin objeto, paginación, perfil completo, solo `COMPLETED`).
2. `docs/bitacora-ia/02_provenance-ignorado-en-el-patch-de-informacion-general_prompt.md`: cambiar el enlace «Salida asociada» de `../salidas/13_provenance-ignorado-en-el-patch-de-informacion-general.md` a `../../specs/CM-54-informacion-general-resumen/spec.md` (sección de decisiones, D5). Comprobar que ese archivo existe en el repo; si no existe, detenerse y reportarlo.
3. Revisar que la fila de `docs/errores.md` de T1 esté completa.

**Commit:** `CM-201 | docs(perfil): catálogo de errores, ADR 0007 y enlace de la bitácora`.

---

## T6 · Verificación única

1. `./mvnw.cmd -B -ntp clean verify` → `BUILD SUCCESS`, `*IT` ejecutados.
2. `jacoco.csv`: ≥ 90 % de líneas y ramas en cada clase de la spec §12; cada línea o rama sin cubrir, con su razón.
3. Newman (T4).
4. Lista de verificación de la spec §18, fila por fila.
5. `ejecucion/evidencia/CM-201/resumen.md` con la tabla R1 a R14 y la de atributos con resultados reales. Explicar a Paula y detenerse (sin push ni PR).

## Resultados

| Tarjeta | Resultado real | Fecha |
|---|---|---|
| T0 | Línea base verde (904 + 296); Jira en «En curso» | 17-oct |
| T1 | 24 pruebas verdes; commit `e6304a2` | 17-oct |
| T2 | `ProfileSummariesQueryIT` 9/9; commit `8ae4504` (mapeo llamado `toProfileSummary`) | 17-oct |
| T3 | 377 pruebas verdes; commit `7e6f4e8` | 17-oct |
| T4 | IT 15/15, OpenAPI verde, Newman 18 peticiones 0 fallos, DES-02 p95 24 ms; commits `f216c26`, `95bfe02` | 17-oct |
| T5 | ADR 0007 y enlace de la bitácora; commit `c3d52be` | 17-oct |
| T6 | `clean verify` 930 + 321 verdes; cobertura ≥ 90 %; hallazgos en `ejecucion/evidencia/CM-201/resumen.md` | 17-oct |
