# Tarjetas — CM-271: el cupo de Perfiles Profesionales del plan responde 403 `PLAN_LIMIT`

## Reglas para todas las tarjetas (quien ejecuta no lee la spec ni el plan)

1. **Rutas.** Código: `src/main/java/co/edu/unicauca/cameia/perfil/`. Pruebas: `src/test/java/co/edu/unicauca/cameia/perfil/`.
2. **Idioma (R1).** Inglés: identificadores, nombres de métodos de prueba y códigos. Español: Javadoc, comentarios, `@DisplayName`, OpenAPI, logs y
   mensajes. Los textos entre « » se copian exactos.
3. **Errores.** Un código = un estado = un mensaje = una excepción = al menos una prueba. Un código publicado no se renombra: se retira y se agrega
   otro. `application/problem+json;charset=UTF-8` con `code` y `requestId`.
4. **Documentación.** Javadoc en todo lo modificado; sin `CM-NNN`, sin rutas a otros archivos, sin `TODO`. Todo código nuevo o retirado se refleja
   en `docs/errores.md` en la misma tarjeta.
5. **Pruebas.** `method_shouldResult_whenCondition`, `@DisplayName` en español, datos literales. Primero la prueba que falla (pega su salida). Las
   `*IT` usan Testcontainers (`postgres:16-alpine`) y las corre Failsafe en `verify`.
6. **Comandos.** `./mvnw.cmd -q -B "-Dtest=<Clase>" test`; `./mvnw.cmd -B "-Dit.test=<Clase>" verify`; cierre `./mvnw.cmd -B clean verify`.
7. **Detente y reporta** con la salida real si la tarjeta no coincide con el código.
8. **Commit:** `CM-271 | <tipo>(perfil): <resultado>`, en español, sin `[IA-ASISTIDO]`, con `Co-Authored-By`. Sin push ni PR.

---

# PR único (`CM-271-cupo-del-plan-en-403`, sobre la punta de CM-73)

## [x] T-271.0 · Copiar la spec — ≤ 5 min

Copiar `specs/CM-271-CupoDelPlanEn403/` desde `C:\Users\paanm\Documents\cameia\cameia-worktrees\perfil-specs-v6`. Commit:
`CM-271 | docs(spec): spec, plan y tarjetas del cupo del plan en 403 PLAN_LIMIT`.

## [x] T-271.1 · `PLAN_LIMIT` reemplaza a `PROFILE_LIMIT_REACHED` — ≤ 25 min, ≈ 120 líneas
- **Resultado (10-oct-2026):** rojo antes del cambio: `ProfileControllerTest.postProfiles_shouldReturn403PlanLimit_whenUserAlreadyHasProfile`, `Status expected:<403> but was:<409>` (de 117 pruebas, 1 fallo). Verde después con `ProfileControllerTest`, `ApiExceptionHandlerTest`, `ErrorCatalogTest`, `ErrorCodeTest` y `ErrorCodeDocumentationTest`. Tres pruebas del registro de `ApiExceptionHandlerTest` (líneas 255, 268 y 279) afirmaban el 409 del cupo y pasaron a 403.

- **Prueba que falla primero** `ProfileControllerTest.postProfiles_shouldReturn403PlanLimit_whenUserAlreadyHasProfile` (reemplaza a
  `postProfiles_shouldReturn409WithPlanMessage_whenUserAlreadyHasProfile`): el servicio lanza `new ProfileLimitReachedException()` →
  `status().isForbidden()`, `$.code` `PLAN_LIMIT`, `$.title` «Cupo del plan alcanzado», `$.detail` «Tu Plan Free permite 1 Perfil Profesional.»,
  `$.errors` no existe, `Content-Type` con `application/problem+json`.
- **Modificar** `ErrorCode`: quitar `PROFILE_LIMIT_REACHED`; agregar
  ```java
  /**
   * El Usuario ya tiene el máximo de Perfiles Profesionales que permite su plan; no se creó nada.
   * Es el código común del cupo del plan en todos los servicios, por eso no sigue la forma sujeto y causa.
   */
  PLAN_LIMIT,
  ```
- **Modificar** `ProfileLimitReachedException`: `super(ErrorCode.PLAN_LIMIT, "Tu Plan Free permite 1 Perfil Profesional.");` y su Javadoc («Se lanza
  cuando el Usuario ya tenía el máximo de perfiles de su plan antes de pedir otro.»).
- **Modificar** `ErrorCatalog.RESPONSES`: `entry(PLAN_LIMIT, FORBIDDEN, "Cupo del plan alcanzado", null)` en lugar de la de `PROFILE_LIMIT_REACHED`.
- **Modificar** las pruebas que nombran el código viejo (`git grep -n PROFILE_LIMIT_REACHED -- src/test`): `ApiExceptionHandlerTest` (fila
  `new ProfileLimitReachedException(), 403, "PLAN_LIMIT"` y las del registro: `code=PLAN_LIMIT`). `ProfileCreationConcurrencyIT` y
  `ProfileCreationAppServiceTest` afirman el tipo de excepción: no cambian.
- **Modificar** `docs/errores.md`: la fila de `PROFILE_LIMIT_REACHED` se reemplaza por
  `| \`PLAN_LIMIT\` | 403 | \`POST /api/v1/profiles\` | — | «Tu Plan Free permite 1 Perfil Profesional.» (CA-2.2.3) | \`ProfileLimitReachedException\` (el Usuario ya tenía el máximo de perfiles antes de pedir) | \`ProfileControllerTest.postProfiles_shouldReturn403PlanLimit_whenUserAlreadyHasProfile\`, \`PlanLimitIT.createProfile_shouldReturn403_whenFreeUserHasOneProfile\` |`;
  en «Códigos retirados»: `| \`PROFILE_LIMIT_REACHED\` | Ya no se emite; lo reemplaza \`PLAN_LIMIT\` (403). |` (con las columnas que tenga esa sección).
  En «Estados que difieren del mapa base», fila: `| Cupo del plan (\`PLAN_LIMIT\`) | 403 sin permiso | 403 | Código común del cupo del plan que reserva el estándar de Backend; lo distingue el \`code\` del 403 de perfil ajeno |`.
- **Comandos:** `./mvnw.cmd -q -B "-Dtest=ProfileControllerTest,ApiExceptionHandlerTest,ErrorCatalogTest,ErrorCodeTest,ErrorCodeDocumentationTest" test`.
- **Commit:** `CM-271 | feat(perfil): el cupo de perfiles del plan responde 403 PLAN_LIMIT`.

## [x] T-271.2 · OpenAPI y ADR 0003 — ≤ 15 min, ≈ 40 líneas
- **Resultado (10-oct-2026):** el 409 de `POST /profiles` en OpenAPI queda solo para `PROFILE_CREATION_IN_PROGRESS`; el 403 `PLAN_LIMIT` lleva su ejemplo. ADR 0003 actualizado (pasos, consecuencias y alternativa descartada). `OpenApiDocumentIT` 1/1.

- `ProfileController`: renombrar la constante `PROFILE_LIMIT_REACHED_EXAMPLE` a `PLAN_LIMIT_EXAMPLE` con `"title": "Cupo del plan alcanzado"`,
  `"status": 403`, `"code": "PLAN_LIMIT"`; en `@ApiResponses` del `POST`, la respuesta del cupo pasa a
  `@ApiResponse(responseCode = "403", description = "El Usuario ya tenía el máximo de perfiles de su plan; el Plan Free permite uno (code PLAN_LIMIT)", …, examples = @ExampleObject(value = PLAN_LIMIT_EXAMPLE))`.
  El 409 que queda es el de `PROFILE_CREATION_IN_PROGRESS`.
- ADR 0003: el paso 6 dice «responde 403 `PLAN_LIMIT`»; en «Alternativas descartadas» y «Consecuencias», cambiar «409» por «403 `PLAN_LIMIT`» donde se
  refiere al cupo (no donde se refiere a la creación en curso).
- **Prueba:** `OpenApiDocumentIT` en verde (si compara el documento, que contenga `PLAN_LIMIT` y no `PROFILE_LIMIT_REACHED`).
- **Commit:** `CM-271 | docs(perfil): OpenAPI y ADR del cupo del plan en 403`.

## [x] T-271.3 · Integración con base real, Postman y Newman — ≤ 30 min, ≈ 220 líneas
- **Resultado (10-oct-2026):** `PlanLimitIT` 5/5 (la parametrizada cuenta dos). Que puede fallar: con el catálogo en `CONFLICT` fallan 4 de 5 (revertido). Postman: la petición del cupo pasa a 403 y se agrega la de CA-2.12.15. Newman contra la imagen empaquetada con base limpia y el rol `a0000007-…-000000000006` inactivo: 111 peticiones, 0 fallidas, 108 scripts, 361 aserciones (`ejecucion/newman-CM-271.log`).

- **Crear** `infrastructure/persistence/PlanLimitIT.java` con la forma de `ResponseCharsetIT` (servidor en puerto aleatorio, `HttpClient`,
  Testcontainers, `JdbcTemplate` para contar y limpiar `firebase_uid like 'uid-plan-%'`):
  - `createProfile_shouldReturn403_whenFreeUserHasOneProfile` (parametrizada con el perfil en `IN_PROGRESS` y en `COMPLETED`; para `COMPLETED`, un
    `UPDATE perfil_profesional SET estado = 'COMPLETED' …` con la columna real de estado de `V1`): 403, `code` `PLAN_LIMIT`, `detail` literal,
    `Content-Type` con `charset=UTF-8`.
  - `createProfile_shouldCreateNothing_whenLimitIsReached`: tras el 403, `SELECT count(*) … WHERE firebase_uid = 'uid-plan-1'` = 1.
  - `createProfile_shouldReturn403_whenFreeUserKeptThreeProfiles` (CA-2.12.18): crear 1 por la API y 2 más con `INSERT` directo (columnas
    `NOT NULL` de `V1`); `POST` → 403; siguen 3 y ninguno cambió de estado.
  - `createProfile_shouldStayAtOne_whenRepeatedTwentyTimes`: 20 `POST` seguidos tras el primero → 20 × 403 y 1 perfil.
- **Postman:** en la carpeta «CM-16 — Crear perfil profesional», la petición `ERR POST /profiles — 409 cupo del Plan Free alcanzado` pasa a
  `ERR POST /profiles — 403 cupo del Plan Free alcanzado (CA-2.2.3)` con pruebas: `Status 403`; `code PLAN_LIMIT`; `detail` «Tu Plan Free permite 1
  Perfil Profesional.»; `Content-Type` con `application/problem+json` y `charset=utf-8`; `responseTime < 2000`. Agregar a continuación
  `ERR POST /profiles — 403 con cuerpo que pide otro plan (CA-2.12.15)`: cuerpo `{"plan":"PREMIUM","status":"COMPLETED"}` → 403 `PLAN_LIMIT`.
- **Newman:** como en CM-70 (paquete, `docker compose up -d --build app`, base limpia, rol inactivo preparado y restaurado). Pega el resumen.
- **Commit:** `CM-271 | test(perfil): cupo del plan en 403 contra la base real y en Postman`.

## [x] T-271.4 · Autoverificación y cierre — ≤ 20 min
- **Resultado (10-oct-2026):** `clean verify` BUILD SUCCESS: 510 unitarias + 125 IT, 0 fallos (`ejecucion/verify-CM-271.log`; el primer reporte de JaCoCo salió incompleto y se repitió). Cobertura: `ProfileLimitReachedException` 2/2; `ErrorCatalog` 72/72 y 2/2; `ErrorCode` 73/73; `ProfileCreationAppService` 15/15 y 4/4; `ProfileController` 34/34; global 96,2 % líneas y 93,5 % ramas (igual que en CM-73). `git grep PROFILE_LIMIT_REACHED -- src docs` solo en «Códigos retirados». `src/main` sin `CM-`. Capa de 455 líneas contra la punta de CM-73. `/simplify` y `/code-review high` no se corren desde esta sesión. Cambio de contrato (409 → 403 `PLAN_LIMIT`): el aviso a Frontend está en `comunicaciones/10102026_v1_roles-objetivo-cupo-y-mi-cuenta-para-frontend.md` y debe enviarse antes de desplegar el tramo.

Lista V1 a V9 de la spec §11 con salida real; `./mvnw.cmd -B clean verify`; cobertura de `ProfileLimitReachedException`, `ErrorCatalog`,
`ProfileCreationAppService` y `ProfileController`; `git diff --shortstat <punta de CM-73>...HEAD` (≤ 800); `/simplify` y `/code-review high`. Escribir en
la «Bandeja de Paula» que el aviso a Frontend está en `comunicaciones/10102026_v1_roles-objetivo-cupo-y-mi-cuenta-para-frontend.md` y debe enviarse
antes de desplegar el tramo.
