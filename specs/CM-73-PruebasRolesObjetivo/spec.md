# Spec — CM-73: pruebas de los Roles Objetivo con cobertura real ≥ 90 % y una prueba de punta a punta por CA

- **Tarea:** CM-73 · Subtarea «HU-2.11 – Backend: pruebas unitarias» · responsable: Paula Andrea Muñoz Delgado
- **Repositorio:** `cameia-perfil`. Escrita contra `origin/develop` `2a54ca7`; se ejecuta en la cola sobre la punta de CM-72.
- **Backlog vigente:** `09102026_01_Backlog_v6.xlsx`, HU-2.11 (los 12 CA: 2.11.1 a 2.11.5 y 2.11.7 a 2.11.13).
- **Estado:** `LISTA PARA EJECUTAR` (sección 9). Sin pendientes.
- **Atributos de calidad:** mantenibilidad y testabilidad (MAN-01), seguridad (SEG-02), fiabilidad (FIA-01).

## 1. Cobertura real medida (línea base)

Medida con JaCoCo 0.8.15 (`./mvnw.cmd -B clean verify`, `target/site/jacoco/jacoco.csv` y `jacoco.xml`) sobre `origin/develop` `2a54ca7`, el
9-oct-2026, en `cameia-worktrees\perfil-specs-v6`: 310 pruebas unitarias y 20 de integración, 0 fallos. Global del repo: **93,1 % de líneas
(982/1055) y 85,3 % de ramas (215/252)**.

| Clase de roles | Líneas | Ramas | Qué falta (línea de `develop`) | Quién lo cierra |
|---|---|---|---|---|
| `ProfessionalRoleRepositoryAdapter` | 3/11 (27 %) | 0/6 (0 %) | `findById`, `findAll`, `findAllByLang` y `toDomain` sin prueba (no hay IT del catálogo) | CM-70 T-70.2 (IT del adaptador) y CM-72 T-72.1 |
| `ProfessionalRoleAppService` | 3/5 (60 %) | — | `listAll()` (línea 21) y `listAllByLang` (25) sin prueba de servicio | Esta tarea (D73-1) |
| `ProfileResponse.TargetRoleItem` | 0/2 | — | el mapeo de un rol nunca se ejercita | CM-72 T-72.2 |
| `TargetRole` | 13/14 | 2/4 | título en blanco o de más de 255 (líneas 25-26) | Esta tarea |
| `ProfessionalProfile` (roles) | 115/117 global | 41/46 global | ramas de `removeTargetRole` (125), `updateTargetRole` con `professionalRoleId` o `roleTitle` nulos (135-136) y duplicado (139) | Esta tarea (las de 135-136) y CM-70/71 (125, 139) |
| `ProfileAppService` (roles) | 58/61 global | 12/12 | `removeTargetRole` (149) sin prueba | CM-71 T-71.2 |
| `ProfessionalRoleController`, `ErrorCatalog`, `ApiExceptionHandler`, excepciones de roles | 100 % | 100 % | — | — |

La medida que vale es la de la punta de CM-72 (T-73.1): esta spec fija la meta y el método, no el número final.

## 2. Requisitos (EARS)

- **REQ-73-01 (MAN-01).** Al terminar la tarea, cada clase de la lista de la sección 3 debe tener **≥ 90 % de líneas y ≥ 90 % de ramas** en JaCoCo;
  cada línea o rama sin cubrir se reporta con su razón. La cobertura global del repo no baja respecto de la punta de CM-72.
- **REQ-73-02 (R12).** Cada CA de HU-2.11 con columna Técnico `B` o `F y B` debe tener una prueba de punta a punta (servidor real en puerto aleatorio y
  PostgreSQL real) cuyo `@DisplayName` empiece por el identificador del CA, en `TargetRolesEndToEndIT`. CA-2.11.12 (tipo `F`) se cubre con la parte del
  servidor: el catálogo responde 200 y no contiene un texto que no es un rol.
- **REQ-73-03 (R7).** El código muerto del catálogo (`ProfessionalRoleAppService.listAll`, `ProfessionalRoleRepository.findAll` y su implementación)
  se elimina: no tiene llamadores (`git grep -n "listAll()\|\.findAll()" -- src` solo los encuentra a ellos mismos).

## 3. Clases que se miden

`ProfessionalRole`, `TargetRole`, `ProfessionalProfile`, `ProfileAppService`, `ProfessionalRoleAppService`, `ProfessionalRoleController`,
`ProfessionalRoleRepositoryAdapter`, `ProfessionalProfileRepositoryAdapter`, `ProfileResponse` (y `TargetRoleItem`), `ProfessionalRoleResponse`,
`AddTargetRoleRequest`, `UpdateTargetRoleRequest`, `AddTargetRoleCommand`, `UpdateTargetRoleCommand`, `ErrorCatalog`, `ApiExceptionHandler`,
`MaxTargetRolesExceededException`, `LastTargetRoleException`, `DuplicateTargetRoleException`, `ProfessionalRoleNotFoundException`,
`TargetRoleNotFoundException`.

Comando de extracción (Python, sin dependencias):
`python -X utf8 -c "import csv;[print(r['CLASS'],r['LINE_COVERED'],int(r['LINE_COVERED'])+int(r['LINE_MISSED']),r['BRANCH_COVERED'],int(r['BRANCH_COVERED'])+int(r['BRANCH_MISSED'])) for r in csv.DictReader(open('target/site/jacoco/jacoco.csv',encoding='utf-8')) if r['CLASS'] in {'TargetRole','ProfessionalRole','ProfessionalProfile','ProfileAppService','ProfessionalRoleAppService','ProfessionalRoleRepositoryAdapter','ProfessionalProfileRepositoryAdapter','ProfileResponse.TargetRoleItem'}]"`

## 4. Una prueba de punta a punta por CA (`TargetRolesEndToEndIT`)

| CA | `@DisplayName` | Pasos y datos literales | Afirmaciones |
|---|---|---|---|
| 2.11.1 | «CA-2.11.1 Sustituir conserva el Rol Objetivo» | crear perfil; agregar `a0000002-0000-0000-0000-000000000002` (Analista de Datos); `PATCH` a `a0000002-…-000000000001` | 200; mismo `id`; `roleTitle` «Científico de Datos»; `provenance` `MANUAL` |
| 2.11.2 | «CA-2.11.2 Agregar un rol activo» | `POST` `a0000001-0000-0000-0000-000000000002` con `"provenance":"AI_SUGGESTED"` | 201; `MANUAL`; «Desarrollador Backend» |
| 2.11.3 | «CA-2.11.3 Último rol de un perfil activo» | perfil completo y `COMPLETED` con un rol; `DELETE` | 409 `TARGET_ROLE_NOT_ALLOWED`, `detail` literal; `GET` muestra el rol y `COMPLETED` |
| 2.11.4 | «CA-2.11.4 Rol repetido al agregar» | dos `POST` con el mismo rol | 201 y 409 `TARGET_ROLE_ALREADY_EXISTS` «Ese rol objetivo ya está en tu perfil.» |
| 2.11.5 | «CA-2.11.5 Sexto rol» | cinco `POST` (`a0000001-…-01` a `…05`) y un sexto (`…06`) | 409 `TARGET_ROLE_LIMIT_REACHED` «Ya tienes el máximo de 5 roles objetivo.»; 5 roles |
| 2.11.7 | «CA-2.11.7 Rol no activo» | `UPDATE rol_profesional SET estado='INACTIVE'` de `a0000007-0000-0000-0000-000000000006`; `POST` | 422, `errors[0]` `professionalRoleId`/`PROFESSIONAL_ROLE_DISABLED`; sin roles |
| 2.11.8 | «CA-2.11.8 Sustituir por un rol que ya está» | roles Analista y Científico; `PATCH` Analista → Científico | 409 `TARGET_ROLE_ALREADY_EXISTS`; los dos roles sin cambio |
| 2.11.9 | «CA-2.11.9 Último rol en Borrador» | perfil `IN_PROGRESS` con un rol; `DELETE` | 204; `GET`: sin roles, `IN_PROGRESS` |
| 2.11.10 | «CA-2.11.10 Quinto rol» | cuatro roles y un quinto | 201; 5 roles |
| 2.11.11 | «CA-2.11.11 Catálogo con solo activos» | rol inactivo preparado; `GET /professional-roles` | 200; sin `a0000007-…-06`; cuenta = activos |
| 2.11.12 | «CA-2.11.12 Texto que no es un rol del catálogo» | `GET /professional-roles` | 200; ningún `nombre` igual a «Astronauta» (el filtrado lo hace la interfaz) |
| 2.11.13 | «CA-2.11.13 Perfil de otro Usuario» | perfil de `uid-e2e-a`; `POST`, `PATCH`, `DELETE` con `uid-e2e-b` | 403 `PROFILE_NOT_ALLOWED` «No encontramos lo que buscabas.» en las tres; el perfil de A sin cambios |

Cada respuesta de error comprueba además `Content-Type` `application/problem+json` con `charset=UTF-8` y que el cuerpo no contenga `Exception`,
`org.` ni `co.edu`.

## 5. Casos borde que agrega esta tarea

| Caso | Prueba |
|---|---|
| `TargetRole` con título `""`, `"   "`, de 256 caracteres; con 255 | `TargetRoleTest.constructor_shouldRejectTitle_whenBlankOrTooLong` (parametrizada) y `constructor_shouldAccept_whenTitleHas255Characters` |
| `TargetRole` con `professionalRoleId` nulo | `TargetRoleTest.constructor_shouldRequireCatalogRole_whenRoleIdIsNull` |
| `updateTargetRole` con `professionalRoleId` o `roleTitle` nulos (conserva el valor anterior) | `ProfessionalProfileTest.updateTargetRole_shouldKeepPreviousValues_whenArgumentsAreNull` |
| `listAllByLang("en")` delega en el puerto | `ProfessionalRoleAppServiceTest.listAllByLang_shouldDelegateLanguage_whenCalled` |

Las rutas de `TargetRole` con título inválido lanzan `IllegalArgumentException` (500): son invariantes que la entrada no alcanza, porque el título
sale del catálogo (`VARCHAR(255) NOT NULL`). Se prueban para fijar el invariante, no para cambiar su estado HTTP.

## 6. Errores, base de datos y seguridad

Sin códigos nuevos ni migración. Seguridad: la prueba de CA-2.11.13 es la prueba negativa entre dos usuarios de SEG-02 por HTTP real. Sin cambios en
logs ni contrato.

## 7. Atributos de calidad

| Atributo | Métrica | Límite | Prueba |
|---|---|---|---|
| MAN-01 | cobertura de líneas y ramas por clase | ≥ 90 % (estándar de Paula; la rúbrica pide 70 %) | T-73.1 y T-73.4 |
| AC-0003 | SEG-02 | 100 % de accesos cruzados rechazados | CA-2.11.13 de punta a punta |
| AC-0004 | FIA-01 | cada rechazo conserva el estado | las afirmaciones de «sin cambio» de la sección 4 |

## 8. Decisiones

| # | Decisión | Porqué | Alternativa descartada |
|---|---|---|---|
| D73-1 | Eliminar `listAll()` y `findAll()` del catálogo | Código sin llamadores; probarlo solo sube un número | Probarlo |
| D73-2 | Las pruebas de punta a punta usan `java.net.http.HttpClient` como `ResponseCharsetIT` | Patrón ya presente; sin dependencias nuevas | `TestRestTemplate` o `RestAssured` (dependencia nueva) |

## 9. Línea base, lista de verificación y dictamen

| # | Comprobación | Comando | Esperado |
|---|---|---|---|
| V1 | Medida inicial en la punta de CM-72 | `./mvnw.cmd -B clean verify` + comando de la sección 3 | tabla pegada en T-73.1 |
| V2 | Un E2E por CA | `./mvnw.cmd -B "-Dit.test=TargetRolesEndToEndIT" verify` | 12 pruebas en verde |
| V3 | Meta de cobertura | comando de la sección 3 tras T-73.3 | cada clase ≥ 90 % líneas y ramas, o la razón escrita |
| V4 | Global | `jacoco.csv` | ≥ la de la punta de CM-72 |
| V5 | Código muerto | `git grep -n "listAll()\|\.findAll()" -- src/main` | sin resultados del catálogo |
| V6 | Pruebas que pueden fallar | por cada prueba nueva, romper la línea que prueba (temporalmente) y pegar la falla | cada una falla |
| V7 | R1/R8 y tamaño | sin `CM-`; `--shortstat` ≤ 800 | |

**Regla → evidencia prevista:** R9 y R10 → V1 a V4 y V6; R12 → sección 4 y V2; R13 → sección 7; R7 → D73-1 y V5; R1/R8 → V7; R2 a R6 y R11: no aplican
(sin entrada, contrato, esquema ni endpoint nuevos; las pruebas de punta a punta ejercitan los de CM-70 a CM-72); R14 → sin push.

**Dictamen: `LISTA PARA EJECUTAR`.** La medida es real y reproducible; los huecos tienen dueño; la prueba por CA está definida con datos literales.
