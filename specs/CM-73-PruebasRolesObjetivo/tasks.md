# Tarjetas — CM-73: pruebas de los Roles Objetivo con cobertura real ≥ 90 % y una prueba por CA

## Reglas para todas las tarjetas (quien ejecuta no lee la spec ni el plan)

1. **Rutas.** Código: `src/main/java/co/edu/unicauca/cameia/perfil/`. Pruebas: `src/test/java/co/edu/unicauca/cameia/perfil/`.
2. **Idioma (R1).** Inglés: identificadores y nombres de métodos de prueba. Español: Javadoc, comentarios y `@DisplayName`.
3. **Pruebas.** `method_shouldResult_whenCondition`, Arrange-Act-Assert, datos literales, sin `Thread.sleep`, aserciones concretas (nunca solo «no
   lanza»). Las `*IT` usan Testcontainers (`postgres:16-alpine`) con `@Container @ServiceConnection` y las corre Failsafe en `verify`.
4. **Toda prueba debe poder fallar:** tras escribirla, rompe a propósito la línea que prueba, corre, pega la falla y deshaz el cambio.
5. **Comandos.** `./mvnw.cmd -q -B "-Dtest=<Clase>" test`; `./mvnw.cmd -B "-Dit.test=<Clase>" verify`; cierre `./mvnw.cmd -B clean verify`.
6. **Sin `CM-NNN`** en código ni comentarios. Sin dependencias nuevas. **Detente y reporta** con la salida real si algo no coincide.
7. **Commit:** `CM-73 | test(perfil): <resultado>` (o `refactor` para T-73.2), en español, sin `[IA-ASISTIDO]`, con `Co-Authored-By`. Sin push ni PR.

---

# PR único (`CM-73-pruebas-roles-objetivo`, sobre la punta de CM-72)

## [x] T-73.0 · Copiar la spec — ≤ 5 min

Copiar `specs/CM-73-PruebasRolesObjetivo/` desde `C:\Users\paanm\Documents\cameia\cameia-worktrees\perfil-specs-v6`. Commit:
`CM-73 | docs(spec): spec, plan y tarjetas de las pruebas de roles objetivo`.

## [x] T-73.1 · Medir — ≤ 15 min
- **Resultado (10-oct-2026):** medida sobre la punta de CM-72 (`verify-CM-72.log`): `TargetRole` 13/14 y 2/4; `ProfessionalRoleAppService` 3/5; `ProfessionalRoleRepositoryAdapter` 10/11 y 6/6; `ProfessionalProfile` 114/116 y 43/46; `ProfileAppService` 64/66 y 12/12; `ProfessionalProfileRepositoryAdapter` 36/36 y 20/22; `ProfessionalRole` 4/4 y 2/2; `ProfessionalRoleController` 6/6 y 2/2; `ErrorCatalog` 72/72 y 2/2; global 95,3 % líneas y 91,7 % ramas.

`./mvnw.cmd -B clean verify` y el comando de extracción de abajo; pega la tabla (clase, líneas, ramas) en esta tarjeta junto con el global.
```
python -X utf8 -c "import csv;[print(r['CLASS'],r['LINE_COVERED'],int(r['LINE_COVERED'])+int(r['LINE_MISSED']),r['BRANCH_COVERED'],int(r['BRANCH_COVERED'])+int(r['BRANCH_MISSED'])) for r in csv.DictReader(open('target/site/jacoco/jacoco.csv',encoding='utf-8')) if r['CLASS'] in {'TargetRole','ProfessionalRole','ProfessionalProfile','ProfileAppService','ProfessionalRoleAppService','ProfessionalRoleRepositoryAdapter','ProfessionalProfileRepositoryAdapter','ProfileResponse.TargetRoleItem','ProfessionalRoleController','ErrorCatalog'}]"
```
Para ver las líneas sin cubrir de una clase: `target/site/jacoco/<paquete>/<Clase>.java.html` (líneas en rojo o amarillo).

## [x] T-73.2 · Borrar el código muerto del catálogo — ≤ 10 min
- **Resultado (10-oct-2026):** `git grep` confirmó que solo ellos se llamaban; borrados `listAll`, `findAll` del puerto y `findAll` del adaptador. `./mvnw.cmd -q -B test` en verde (commit `9ce3fcb`).

- Confirmar: `git grep -n "listAll()\|\.findAll()" -- src` solo muestra `ProfessionalRoleAppService.listAll`, el puerto y el adaptador.
- Borrar `ProfessionalRoleAppService.listAll()`, `ProfessionalRoleRepository.findAll()` y `ProfessionalRoleRepositoryAdapter.findAll()`.
- `./mvnw.cmd -q -B test` en verde. Commit: `CM-73 | refactor(perfil): quitar la lectura del catálogo sin idioma, que nadie usa`.

## [x] T-73.3 · Pruebas unitarias que faltan — ≤ 25 min, ≈ 120 líneas
- **Resultado (10-oct-2026):** `TargetRoleTest` (7 casos), `ProfessionalProfileTest.updateTargetRole_shouldKeepPreviousValues_whenArgumentsAreNull` y `ProfessionalRoleAppServiceTest` en verde. Que pueden fallar: con `>=` en el largo del título falla `constructor_shouldAccept_whenTitleHas255Characters`; con `professionalRoleId` sin conservar el anterior falla la prueba de valores nulos (cambios revertidos).

- `domain/model/TargetRoleTest` (`ROLE_ID = UUID.fromString("a0000001-0000-0000-0000-000000000002")`):
  - `constructor_shouldRejectTitle_whenBlankOrTooLong` con `@ParameterizedTest` y `""`, `"   "`, `"a".repeat(256)` → `IllegalArgumentException`.
  - `constructor_shouldAccept_whenTitleHas255Characters` (`"a".repeat(255)`).
  - `constructor_shouldRequireCatalogRole_whenRoleIdIsNull` → `NullPointerException` con el mensaje «professionalRoleId es obligatorio».
  - `isSameRoleAs_shouldCompareCatalogRole_whenIdsDiffer`: dos `TargetRole` de ids distintos y mismo `ROLE_ID` → `true`; con otro rol → `false`.
- `ProfessionalProfileTest.updateTargetRole_shouldKeepPreviousValues_whenArgumentsAreNull`: `updateTargetRole(id, null, null)` → conserva
  `professionalRoleId` y `roleTitle`.
- `application/service/ProfessionalRoleAppServiceTest` (Mockito): `listAllByLang_shouldDelegateLanguage_whenCalled` (`"en"` → `verify(repository).findAllByLang("en")`
  y devuelve la misma lista).
- Si T-73.1 mostró otra línea o rama de roles sin cubrir, agregar aquí la prueba que la cubre (con nombre y datos literales) o escribir su razón.
- `./mvnw.cmd -q -B "-Dtest=TargetRoleTest,ProfessionalProfileTest,ProfessionalRoleAppServiceTest" test`. Commit:
  `CM-73 | test(perfil): invariantes del rol objetivo y lectura del catálogo por idioma`.

## [x] T-73.4 · Una prueba de punta a punta por CA — ≤ 30 min, ≈ 300 líneas
- **Resultado (10-oct-2026):** `TargetRolesEndToEndIT` 12/12 (`e2e-CM-73.log`). Correcciones propias durante la ejecución: el id del rol sale de `targetRoles[0].id` y `completeProfile` envía `provenance` y comprueba cada paso. Que pueden fallar: con el catálogo sin filtro de activos falla CA-2.11.11; con el máximo en 6 falla CA-2.11.5 (revertidos).

- **Crear** `presentation/TargetRolesEndToEndIT.java` con la forma de `ResponseCharsetIT` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`,
  `@Testcontainers`, `HttpClient`, `@LocalServerPort`). Para leer JSON, el mapeador que ya usan las pruebas del repo
  (`git grep -n "ObjectMapper\|JsonMapper" -- src/test`). `@Autowired JdbcTemplate` para preparar el rol inactivo y limpiar
  (`DELETE FROM perfil_profesional WHERE firebase_uid LIKE 'uid-e2e-%'`, y `UPDATE rol_profesional SET estado = 'ACTIVE'` en `@AfterEach`).
- Ayudantes privados: `createProfile(uid)`, `addRole(uid, profileId, roleId)`, `replaceRole(...)`, `removeRole(...)`, `getProfile(...)`,
  `completeProfile(uid, profileId)` (agrega lo que pida `POST …/completion`; si responde 422 `PROFILE_INCOMPLETE`, leer `missingRequirements` y
  completar con los endpoints del perfil y datos literales «Perfil E2E», «Resumen E2E», etc.).
- Una prueba por fila de la tabla de la spec §4 (la tabla completa está aquí):

  | CA | Método | Datos y afirmaciones |
  |---|---|---|
  | 2.11.1 | `replace_shouldKeepTargetRole_whenCatalogRoleChanges` | agregar `a0000002-0000-0000-0000-000000000002`; `PATCH` a `a0000002-0000-0000-0000-000000000001` → 200, mismo `id`, «Científico de Datos», `MANUAL` |
  | 2.11.2 | `add_shouldStoreManualRole_whenRoleIsActive` | `{"professionalRoleId":"a0000001-0000-0000-0000-000000000002","provenance":"AI_SUGGESTED"}` → 201, `MANUAL`, «Desarrollador Backend» |
  | 2.11.3 | `remove_shouldReturn409_whenLastRoleOfActiveProfile` | perfil `COMPLETED` con un rol → 409 `TARGET_ROLE_NOT_ALLOWED`, «No puedes quedarte sin roles objetivo con el perfil activo.»; `GET` con el rol y `COMPLETED` |
  | 2.11.4 | `add_shouldReturn409_whenRoleRepeats` | dos `POST` iguales → 201 y 409 `TARGET_ROLE_ALREADY_EXISTS` «Ese rol objetivo ya está en tu perfil.» |
  | 2.11.5 | `add_shouldReturn409_whenSixthRole` | `a0000001-…-000000000001` a `…05` y `…06` → 409 `TARGET_ROLE_LIMIT_REACHED` «Ya tienes el máximo de 5 roles objetivo.»; 5 roles |
  | 2.11.7 | `add_shouldReturn422_whenRoleIsInactive` | `UPDATE rol_profesional SET estado='INACTIVE' WHERE id='a0000007-0000-0000-0000-000000000006'`; `POST` → 422, `errors[0].field` `professionalRoleId`, `errors[0].code` `PROFESSIONAL_ROLE_DISABLED`; sin roles |
  | 2.11.8 | `replace_shouldReturn409_whenRoleIsAlreadyInProfile` | Analista (`a0000002-…-02`) y Científico (`a0000002-…-01`); `PATCH` Analista → Científico → 409; los dos sin cambio |
  | 2.11.9 | `remove_shouldReturn204_whenLastRoleOfDraft` | un rol en `IN_PROGRESS` → 204; `GET`: `targetRoles` vacío, `IN_PROGRESS` |
  | 2.11.10 | `add_shouldReturn201_whenFifthRole` | cuatro y un quinto → 201; 5 roles |
  | 2.11.11 | `catalog_shouldOfferOnlyActiveRoles_whenSomeAreInactive` | rol inactivo preparado; `GET /api/v1/profiles/professional-roles` → 200, sin ese id, tamaño = `SELECT count(*) FROM rol_profesional WHERE estado='ACTIVE'` |
  | 2.11.12 | `catalog_shouldNotContainFreeText_whenSearchedTextIsNotARole` | `GET` → 200; ningún `nombre` es «Astronauta» |
  | 2.11.13 | `writes_shouldReturn403_whenProfileBelongsToOtherUser` | perfil de `uid-e2e-a` con un rol; `POST`, `PATCH` y `DELETE` con `uid-e2e-b` → 403 `PROFILE_NOT_ALLOWED` «No encontramos lo que buscabas.»; `GET` de A sin cambio |

  `@DisplayName` de cada una: el de la spec §4 (empieza por «CA-2.11.N»). Cada error comprueba `Content-Type` con `application/problem+json` y
  `charset=UTF-8`, y que el cuerpo no contiene `Exception`, `org.` ni `co.edu`.
- `./mvnw.cmd -B "-Dit.test=TargetRolesEndToEndIT" verify` → 12 en verde. Commit: `CM-73 | test(perfil): una prueba de punta a punta por criterio de HU-2.11`.

## [x] T-73.5 · Medir otra vez, autoverificación y cierre — ≤ 20 min
- **Resultado (10-oct-2026):** `clean verify` BUILD SUCCESS: 510 unitarias + 120 IT, 0 fallos (`verify-CM-73.log`; el primer reporte de JaCoCo salió incompleto y se repitió). Cobertura: `TargetRole` 14/14 y 4/4; `ProfessionalRoleAppService` 4/4; `ProfessionalRoleRepositoryAdapter` 10/10 y 6/6; `ProfessionalProfile` 114/116 y 45/46 (previas, Desvío 13); `ProfileAppService` 64/66 y 12/12 (previas); `ProfessionalProfileRepositoryAdapter` 36/36 y 20/22 (previas); `ProfessionalRoleController` 6/6 y 2/2; `ErrorCatalog` 72/72 y 2/2; `ProfileResponse` 16/16 y 7/8 (la rama sin cubrir es de salario o fecha de fin, ajena a roles); global 96,2 % líneas y 93,5 % ramas, por encima del 95,3 % y 91,7 % de la punta de CM-72. Capa de 628 líneas contra la punta de CM-72. Sin cambio de contrato: Newman no se repite. `/simplify` y `/code-review high` no se corren desde esta sesión.

`./mvnw.cmd -B clean verify`; el comando de T-73.1; tabla final por clase (≥ 90 % líneas y ramas, o la razón de cada línea sin cubrir); global
≥ el de T-73.1; lista V1 a V7 de la spec §9; `git diff --shortstat <punta de CM-72>...HEAD` (≤ 800); `/simplify` y `/code-review high`.
