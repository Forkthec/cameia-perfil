# Tarjetas — CM-71: eliminar Roles Objetivo con el último rol de un perfil activo en 409

## Reglas para todas las tarjetas (quien ejecuta no lee la spec ni el plan)

1. **Rutas.** Código: `src/main/java/co/edu/unicauca/cameia/perfil/`. Pruebas: `src/test/java/co/edu/unicauca/cameia/perfil/`.
2. **Idioma (R1).** Inglés: identificadores, nombres de métodos de prueba y códigos de error. Español: Javadoc, comentarios, `@DisplayName`,
   OpenAPI, logs y mensajes. Los textos entre « » se copian exactos (tildes y punto final).
3. **Errores.** Nunca 500 por una entrada del cliente. `application/problem+json;charset=UTF-8` con `code` y `requestId`. Ningún mensaje repite
   el valor recibido.
4. **Código.** Métodos de ≈ 20 líneas, retornos tempranos. `domain` no importa Spring, JPA ni Jakarta. Sin dependencias nuevas.
5. **Documentación.** Javadoc en todo método nuevo o modificado. Sin `CM-NNN`, sin rutas a otros archivos, sin `TODO`. Todo código que cambia de
   estado se refleja en `docs/errores.md` en la misma tarjeta.
6. **Pruebas.** `method_shouldResult_whenCondition`, `@DisplayName` en español, Arrange-Act-Assert, datos literales, sin `Thread.sleep`. Primero la
   prueba que falla: pega su salida antes de tocar el código. Las `*IT` usan Testcontainers (`postgres:16-alpine`) y las corre Failsafe en `verify`.
7. **Base de la rama.** Las escrituras usan `loadForUserForUpdate`; en `ProfileAppServiceTest` se simulan con
   `when(repository.findByIdForUpdate(any())).thenReturn(Optional.of(profile))`.
8. **Comandos.** Una clase: `./mvnw.cmd -q -B "-Dtest=<Clase>" test`; una IT: `./mvnw.cmd -B "-Dit.test=<Clase>" verify`. Cierre:
   `./mvnw.cmd -B clean verify`. Las órdenes con `^{tree}` o `-D` van por Bash.
9. **Detente y reporta** con la salida real si la tarjeta no coincide con el código o una prueba existente se rompe sin causa clara.
10. **Commit:** `CM-71 | <tipo>(perfil): <resultado>`, en español, sin `[IA-ASISTIDO]`, con el trailer `Co-Authored-By`. Sin push ni PR.

---

# PR único (`CM-71-eliminar-rol-objetivo`, sobre la punta de CM-70)

## [ ] T-71.0 · Copiar la spec — ≤ 5 min

Copiar `specs/CM-71-EliminarRolObjetivo/` desde `C:\Users\paanm\Documents\cameia\cameia-worktrees\perfil-specs-v6`. Commit:
`CM-71 | docs(spec): spec, plan y tarjetas de eliminar roles objetivo`.

## [x] T-71.1 · El último rol de un perfil activo responde 409 — ≤ 20 min, ≈ 60 líneas

- **Prueba que falla primero** `ProfileControllerTest.removeTargetRole_shouldReturn409_whenLastRoleOfActiveProfile`: el servicio lanza
  `new LastTargetRoleException()` → `status().isConflict()`, `$.code` `TARGET_ROLE_NOT_ALLOWED`, `$.detail` «No puedes quedarte sin roles objetivo con
  el perfil activo.», `$.errors` no existe, `Content-Type` contiene `application/problem+json`.
- **Modificar** `ErrorCatalog.RESPONSES`: `entry(TARGET_ROLE_NOT_ALLOWED, CONFLICT, "No se puede eliminar el último rol objetivo", null)`.
- **Modificar** `ApiExceptionHandlerTest` (fila de `LastTargetRoleException`: `409`) y cualquier otra prueba que esperara 422 para este código
  (`git grep -n "TARGET_ROLE_NOT_ALLOWED\|LastTargetRoleException" -- src/test`).
- **Modificar** `ProfileController`, `@ApiResponses` de `DELETE …/target-roles/{roleId}`: quitar `TARGET_ROLE_NOT_ALLOWED` del 422 (queda «Identificador
  mal escrito (code PROFILE_ID_INVALID_FORMAT, TARGET_ROLE_ID_INVALID_FORMAT)») y agregar
  `@ApiResponse(responseCode = "409", description = "No se puede eliminar el único rol objetivo de un perfil activo (code TARGET_ROLE_NOT_ALLOWED) u otra escritura en curso (PROFILE_UPDATE_IN_PROGRESS)", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))`.
- **Modificar** `docs/errores.md`, fila de `TARGET_ROLE_NOT_ALLOWED`: HTTP `409`; prueba `ProfileControllerTest.removeTargetRole_shouldReturn409_whenLastRoleOfActiveProfile`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfileControllerTest,ApiExceptionHandlerTest,ErrorCatalogTest,ErrorCodeDocumentationTest" test`.
- **Commit:** `CM-71 | fix(perfil): eliminar el último rol objetivo de un perfil activo responde 409`.
- **Resultado (10-oct):** rojo: `ApiExceptionHandlerTest ... Response status expected:<409> but was:<422>` y `ProfileControllerTest.removeTargetRole_shouldReturn409_whenLastRoleOfActiveProfile:773 Status expected:<409> but was:<422>`. Verde: 267 pruebas, 0 fallos (`ProfileControllerTest`, `ApiExceptionHandlerTest`, `ErrorCatalogTest`, `ErrorCodeDocumentationTest`, `ProfileAppServiceTest`, `ProfessionalProfileTest`, `ArquitecturaTest`). Además de la fila de `ApiExceptionHandlerTest`, cambió `businessException_shouldOmitErrors_whenItRejectsNoField` (esperaba 422 para `LastTargetRoleException`) por el CA-2.11.3.

## [x] T-71.2 · Pruebas de eliminar en el servicio y el dominio — ≤ 25 min, ≈ 120 líneas

- `ProfileAppServiceTest` (perfiles con los ayudantes existentes `freshProfile()` y `completeProfile()`):
  - `removeTargetRole_shouldRemoveLastRole_whenProfileIsDraft` (CA-2.11.9): perfil `IN_PROGRESS` con un rol → sin roles, `getStatus()` `IN_PROGRESS`,
    `verify(repository).save(profile)`.
  - `removeTargetRole_shouldKeepRoleAndStatus_whenLastRoleOfCompletedProfile` (CA-2.11.3): `LastTargetRoleException`; el perfil conserva 1 rol y
    `COMPLETED`; `verify(repository, never()).save(any())`.
  - `removeTargetRole_shouldRemoveRole_whenCompletedProfileHasTwo`: queda 1, sigue `COMPLETED`.
  - `removeTargetRole_shouldThrowNotFound_whenRoleWasAlreadyRemoved`: segunda llamada con el mismo `roleId` → `TargetRoleNotFoundException`.
- `ProfessionalProfileTest.removeTargetRole_shouldThrowNotFound_whenProfileHasNoRoles` (perfil vacío, `UUID.fromString("33333333-3333-4333-8333-333333333333")`).
- `ProfileControllerTest.removeTargetRole_shouldReturn204WithoutBody_whenRemoved` ya existe: no se toca.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfileAppServiceTest,ProfessionalProfileTest" test`.
- **Commit:** `CM-71 | test(perfil): eliminar roles objetivo en Borrador y en perfil activo`.
- **Resultado (10-oct):** `ProfileAppServiceTest` 57/57 y `ProfessionalProfileTest` 28/28 en verde. Son pruebas de comportamiento que ya cumplía el dominio: pasan desde la primera corrida (no hay rojo previo que capturar).

## [ ] T-71.3 · Concurrencia, Postman y Newman — ≤ 30 min, ≈ 230 líneas

- `TargetRoleConcurrencyIT.removeTargetRole_shouldKeepOneRole_whenTwoRemovalsArriveOnActiveProfile`: crear el perfil, agregar los roles
  `a0000001-0000-0000-0000-000000000001` y `…02`, completarlo con lo que exija `completeProfile` (nombre, resumen, formación, habilidad, rol: usar los
  casos de uso del servicio con datos literales «Perfil de prueba», «Resumen de prueba», etc.; si falta un requisito, leer `getMissingRequirements()`
  del error y agregarlo); luego 2 hilos que eliminan cada uno un rol distinto → queda 1 fila en `rol_objetivo`, un resultado correcto y un
  `LastTargetRoleException`, y el perfil sigue `COMPLETED`.
- Postman (`docs/CAMEIA_Perfil_Sprint1.postman_collection.json`, editar con `Edit`):
  - En la carpeta «CM-20 — Roles objetivo», la petición `ERR DELETE /target-roles/{roleId} — 422 último rol de un perfil activo (CA-2.11.3)` pasa a
    llamarse `… — 409 …` y sus pruebas: `Status 409`, `code TARGET_ROLE_NOT_ALLOWED`, `detail` literal; agregar la de `Content-Type` y la de
    `responseTime < 2000`.
  - Al final de la carpeta «HU-2.11 — Roles objetivo» (variables nuevas `user_d` = `firebase-uid-roles-borrador`, `profile_d`, `role_d`):
    1. `OK POST /profiles — Usuario D crea su perfil (guarda profile_d)` → 201.
    2. `OK POST /target-roles — Usuario D agrega un rol (guarda role_d)` con `a0000001-0000-0000-0000-000000000007` → 201.
    3. `ERR DELETE /target-roles/{roleId} — 403 perfil ajeno` con `X-User-Id` = `{{user_c}}` sobre `{{profile_d}}/target-roles/{{role_d}}` → 403 `PROFILE_NOT_ALLOWED`.
    4. `ERR DELETE /target-roles/{ajeno} — 404` con `11111111-2222-4333-8444-555555555555` → 404 `TARGET_ROLE_NOT_FOUND`, «No encontramos lo que buscabas.».
    5. `OK DELETE /target-roles/{roleId} — 204 último rol en Borrador (CA-2.11.9)` → 204, cuerpo vacío.
    6. `OK GET /profiles/{id} — sigue en Borrador sin roles` → 200, `status` `IN_PROGRESS`, `targetRoles` vacío.
    7. `ERR DELETE /target-roles/{roleId} — 404 al repetir la eliminación` → 404 `TARGET_ROLE_NOT_FOUND`.
- **Comandos:** `./mvnw.cmd -B "-Dit.test=TargetRoleConcurrencyIT" verify`; Newman como en CM-70 (base limpia, rol inactivo preparado con el
  `UPDATE` de CM-70 y restaurado al final). Pega el resumen.
- **Commit:** `CM-71 | test(perfil): concurrencia y peticiones de Postman de eliminar roles objetivo`.

## [ ] T-71.4 · Autoverificación y cierre — ≤ 20 min

Lista V1 a V10 de la spec (§11) con salida real; `./mvnw.cmd -B clean verify`; cobertura de `ProfileAppService` y `ErrorCatalog`; `git diff
--shortstat <punta de CM-70>...HEAD` (≤ 800); `/simplify` y `/code-review high`. Sin migración ni identidad nueva: no necesita Prompt H.
