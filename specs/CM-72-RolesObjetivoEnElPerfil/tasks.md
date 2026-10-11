# Tarjetas — CM-72: catálogo con solo roles activos y Roles Objetivo estables en el perfil

## Reglas para todas las tarjetas (quien ejecuta no lee la spec ni el plan)

1. **Rutas.** Código: `src/main/java/co/edu/unicauca/cameia/perfil/`. Pruebas: `src/test/java/co/edu/unicauca/cameia/perfil/`.
2. **Idioma (R1).** Inglés: identificadores y nombres de métodos de prueba. Español: Javadoc, comentarios, `@DisplayName`, OpenAPI y mensajes.
3. **Código.** Métodos de ≈ 20 líneas. `domain` no importa Spring, JPA ni Jakarta. Solo JPQL (sin SQL nativo ni concatenación). Sin dependencias.
4. **Documentación.** Javadoc en todo método nuevo o modificado, con el porqué. Sin `CM-NNN`, sin rutas a otros archivos, sin `TODO`.
5. **Pruebas.** `method_shouldResult_whenCondition`, `@DisplayName` en español, datos literales. Primero la prueba que falla (pega su salida).
   Las `*IT` usan Testcontainers (`postgres:16-alpine`) y las corre Failsafe en `verify`.
6. **Comandos.** Una clase: `./mvnw.cmd -q -B "-Dtest=<Clase>" test`; una IT: `./mvnw.cmd -B "-Dit.test=<Clase>" verify`. Cierre: `./mvnw.cmd -B clean verify`.
7. **Detente y reporta** con la salida real si algo no coincide con el código.
8. **Commit:** `CM-72 | <tipo>(perfil): <resultado>`, en español, sin `[IA-ASISTIDO]`, con el trailer `Co-Authored-By`. Sin push ni PR.

---

# PR único (`CM-72-catalogo-activos-y-orden-roles`, sobre la punta de CM-71)

## [x] T-72.0 · Copiar la spec — ≤ 5 min

Copiar `specs/CM-72-RolesObjetivoEnElPerfil/` desde `C:\Users\paanm\Documents\cameia\cameia-worktrees\perfil-specs-v6`. Commit:
`CM-72 | docs(spec): spec, plan y tarjetas del catálogo de roles activos y el orden de los roles objetivo`.

## [x] T-72.1 · El catálogo ofrece solo roles activos — ≤ 20 min, ≈ 80 líneas

- **Prueba que falla primero** en `ProfessionalRoleRepositoryAdapterIT`: `findAllByLang_shouldExcludeInactive_whenCatalogHasInactiveRole`
  (parametrizada con `"es"` y `"en"`): `UPDATE rol_profesional SET estado = 'INACTIVE' WHERE id = 'a0000007-0000-0000-0000-000000000006'` (restaurar en
  `@AfterEach`); la lista no contiene ese id y su tamaño es `SELECT count(*) FROM rol_profesional WHERE estado = 'ACTIVE'`.
- **Modificar** `ProfessionalRoleJpaRepository`:
  ```java
  /** Roles que el catálogo ofrece hoy, en español, por categoría y nombre. Los inactivos no se ofrecen. */
  @Query("SELECT r FROM ProfessionalRoleEntity r WHERE r.estado = 'ACTIVE' ORDER BY r.categoria ASC, r.nombre ASC")
  List<ProfessionalRoleEntity> findAllOrderedEs();

  /** Roles que el catálogo ofrece hoy, en inglés, por categoría y nombre. Los inactivos no se ofrecen. */
  @Query("SELECT r FROM ProfessionalRoleEntity r WHERE r.estado = 'ACTIVE' ORDER BY r.categoriaEn ASC, r.nombreEn ASC")
  List<ProfessionalRoleEntity> findAllOrderedEn();
  ```
  Los nombres de método no cambian (los usa el adaptador). `findById` no se toca.
- **Prueba** `ProfessionalRoleControllerTest.listAll_shouldReturnEmptyArray_whenNoActiveRoles`: el servicio devuelve `List.of()` → 200 y `$` vacío.
- **Modificar** `ProfessionalRoleController`: `@Operation(summary = "Listar catálogo de roles profesionales TI", description = "Devuelve solo los roles activos, que son los que se pueden elegir como rol objetivo. Sin coincidencias, la lista vacía.")`.
- **Comandos:** `./mvnw.cmd -B "-Dit.test=ProfessionalRoleRepositoryAdapterIT" verify`; `./mvnw.cmd -q -B "-Dtest=ProfessionalRoleControllerTest" test`.
- **Commit:** `CM-72 | feat(perfil): el catálogo de roles ofrece solo los roles activos`.
- **Resultado (10-oct-2026):** rojo antes del cambio: `ProfessionalRoleRepositoryAdapterIT` 7 pruebas, 2 fallos (`findAllByLang_shouldExcludeInactive_whenCatalogHasInactiveRole[1]` y `[2]`, línea 91, el rol inactivo seguía en la lista). Verde después: `ProfessionalRoleRepositoryAdapterIT` 7/7 y `ProfessionalRoleControllerTest` 7/7, BUILD SUCCESS.

## [x] T-72.2 · Los Roles Objetivo conservan su orden — ≤ 25 min, ≈ 120 líneas

- **Pruebas que fallan primero** (`ProfessionalProfileTest`; roles con `professionalRoleId` `a0000001-…-000000000001`, `…02`, `…03`, `…04`):
  - `updateTargetRole_shouldKeepPosition_whenRoleIsReplaced`: roles A, B, C; sustituir B por D → orden de `professionalRoleId` = A, D, C y el `id`
    de la posición 1 es el de B.
  - `removeTargetRole_shouldKeepOrderOfOthers_whenOneIsRemoved`: A, B, C; quitar A → B, C.
- **Modificar** `ProfessionalProfile.updateTargetRole`: en lugar de `removeIf` + `add`, reemplazar en el mismo índice:
  ```java
  int index = targetRoles.indexOf(existing);
  // Sustituir conserva el Rol Objetivo y su lugar en la lista: el backlog no permite reordenar.
  targetRoles.set(index, replacement);
  ```
  (La comprobación de duplicado se queda antes, igual que hoy.)
- **Modificar** `ProfessionalProfileEntity.targetRoles`: `@OrderBy("createdAt ASC, id ASC")` (import `jakarta.persistence.OrderBy`) con un comentario de
  línea: «Orden en que se agregaron: sustituir no cambia la fecha de creación de la fila.».
- **Prueba** `ProfessionalProfileRepositoryAdapterIT.findById_shouldReturnTargetRolesInInsertionOrder`: crea un perfil y agrega A, B, C en tres
  guardados (tres transacciones); sustituye B por D en un cuarto; `findById` devuelve A, D, C.
- **Prueba** `ProfileControllerTest.getProfile_shouldShowTargetRoleFields_whenProfileHasRoles`: perfil con un rol (`id` `44444444-4444-4444-8444-444444444444`,
  `professionalRoleId` `a0000001-0000-0000-0000-000000000002`, «Desarrollador Backend», `MANUAL`) → `$.targetRoles[0].id`, `.professionalRoleId`,
  `.roleTitle`, `.provenance` con esos valores.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfessionalProfileTest,ProfileControllerTest" test`;
  `./mvnw.cmd -B "-Dit.test=ProfessionalProfileRepositoryAdapterIT" verify`.
- **Commit:** `CM-72 | fix(perfil): sustituir un rol objetivo conserva su lugar y el perfil los devuelve en orden`.
- **Resultado (10-oct-2026):** rojo antes del cambio: `ProfessionalProfileTest` 30 pruebas, 1 fallo (`updateTargetRole_shouldKeepPosition_whenRoleIsReplaced`, línea 142, el rol sustituido quedaba al final). `ProfileControllerTest` y `removeTargetRole_shouldKeepOrderOfOthers_whenOneIsRemoved` ya pasaban (regresión). La IT de orden no se vio en rojo porque Surefire falla antes que Failsafe. Verde después: 501 unitarias y `ProfessionalProfileRepositoryAdapterIT` 10/10, BUILD SUCCESS.

## [x] T-72.3 · Postman, Newman, autoverificación y cierre — ≤ 30 min, ≈ 150 líneas

- Al final de la carpeta «HU-2.11 — Roles objetivo» (con el rol inactivo preparado como en CM-70):
  1. `OK GET /professional-roles — no ofrece el rol inactivo (CA-2.11.11)` → 200; `pm.expect(json.map(r => r.id)).to.not.include('a0000007-0000-0000-0000-000000000006')`;
     `pm.expect(json.length).to.be.above(0)`; ningún elemento tiene `estado`.
  2. `OK GET /professional-roles?lang=en — tampoco en inglés` → lo mismo.
  3. `OK GET /profiles/{id} — el rol sustituido conserva su lugar (CA-2.11.1)` sobre `{{profile_c}}` → `targetRoles[0].id` = `{{role_c}}`.
  4. `ERR GET /professional-roles?lang=es'-- — 422 idioma no disponible` → 422 `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE`, sin eco del valor.
  Cada una con `responseTime < 2000`.
- Newman completo (base limpia); lista V1 a V8 de la spec (§10) con salida real; `./mvnw.cmd -B clean verify`; cobertura de
  `ProfessionalRoleRepositoryAdapter`, `ProfessionalProfile`, `ProfileResponse`; `git diff --shortstat <punta de CM-71>...HEAD`; `/simplify` y
  `/code-review high`.
- **Commit:** `CM-72 | test(perfil): peticiones de Postman del catálogo de roles activos y del orden de los roles`.
- **Resultado (10-oct-2026):** carpeta «HU-2.11 — Roles objetivo» con 24 peticiones (4 nuevas). Newman contra la imagen empaquetada con base limpia y el rol `a0000007-…-000000000006` inactivo: 110 peticiones, 0 fallidas, 107 scripts, 354 aserciones, 0 fallos (`ejecucion/newman-CM-72.log`). `clean verify` BUILD SUCCESS: 501 unitarias + 108 IT, 0 fallos (`ejecucion/verify-CM-72.log`). Autoverificación: V1 y V2 Newman 200 sin el rol inactivo y con 45 roles; V3 `targetRoles[0].id` = `role_c`; V4 `ProfileControllerTest.getProfile_shouldShowTargetRoleFields_whenProfileHasRoles` verde; V5 `?estado=INACTIVE` → 200, 45 roles, sin el inactivo; `?lang=es'--` → 422 `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE` sin eco, 0 «Fallo no controlado» (`ejecucion/adversarial-CM-72.log`); V6 `ProfessionalRoleRepositoryAdapter` 10/11 y 6/6 (línea sin cubrir: `findAll()` heredada, sin llamador), `ProfessionalProfile` 114/116 y 43/46 (previas, Desvío 13), `ProfessionalRoleController` 6/6 y 2/2, global 95,3 % líneas y 91,7 % ramas; V7 verde dentro de `clean verify`; V8 `src/main` sin `CM-` y capa de 489 líneas contra la punta de CM-71. `/simplify` y `/code-review high` no se corren desde esta sesión (misma razón que CM-70). Sin migración ni identidad nueva: no necesita Prompt H.
