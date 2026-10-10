# Spec — CM-70: agregar y sustituir Roles Objetivo solo con roles activos del catálogo

- **Tarea:** CM-70 · Subtarea «HU-2.11 – Backend: consultar y agregar Roles Objetivo» · responsable: Paula Andrea Muñoz Delgado
- **Repositorio:** `cameia-perfil`. Se escribe contra `origin/develop` `2a54ca7` y se ejecuta en la cadena de la cola de Perfil, después de la capa
  `CM-274-base-escritura-perfil` (PR 1 de CM-274), que trae el bloqueo del perfil en toda escritura, `ErrorCodeDocumentationTest` y la sección
  «Códigos retirados» de `docs/errores.md`.
- **Backlog vigente:** `09102026_01_Backlog_v6.xlsx`, hoja `HE-02`, HU-2.11 (CA-2.11.1, 2.11.2, 2.11.4, 2.11.5, 2.11.7, 2.11.8, 2.11.10, 2.11.13) y
  RT-01, RT-02, RT-03, RT-05 y RT-06.
- **Estado:** `LISTA PARA EJECUTAR` (sección 15). Dos PENDIENTES que no bloquean (sección 14).
- **Atributos de calidad:** seguridad (SEG-01, SEG-02, SEG-04), fiabilidad (FIA-01, FIA-02), desempeño (DES-02, DES-04), interacción (AC-0006),
  mantenibilidad (MAN-01). Detalle en la sección 11.

## 1. Contexto y defectos comprobados

HU-2.11 deja agregar Roles Objetivo «del catálogo de roles activos» (RN-045), de 1 a 5 por perfil y sin repetir (RN-046, RN-047). Comprobado en
`origin/develop` `2a54ca7`:

| # | Defecto | Dónde | Cómo se reproduce ANTES | Esperado DESPUÉS |
|---|---|---|---|---|
| D1 | El catálogo no tiene estado: no existe «rol activo» | `V3__catalogo_roles_profesionales.sql` (tabla `rol_profesional` sin columna de estado), `ProfessionalRole` (record sin estado) | `\d rol_profesional` no muestra estado | Columna `estado` (`ACTIVE`, `INACTIVE`) con `CHECK`, y `ProfessionalRole.active()` |
| D2 | Un rol no activo se agrega y se sustituye | `ProfileAppService.addTargetRole` y `updateTargetRole` (`findById` sin mirar estado) | Con un rol `INACTIVE` en la tabla, `POST …/target-roles` → 201 | 422 `VALIDATION_FAILED`, `errors[0]` = `professionalRoleId` / `PROFESSIONAL_ROLE_DISABLED` |
| D3 | El sexto rol responde 422 | `ErrorCatalog`: `TARGET_ROLE_LIMIT_REACHED` → `UNPROCESSABLE_CONTENT` | Perfil con 5 roles, `POST …/target-roles` → 422 | 409 `TARGET_ROLE_LIMIT_REACHED` «Ya tienes el máximo de 5 roles objetivo.» |
| D4 | La procedencia la decide el cliente | `AddTargetRoleRequest.provenance` (`@NotNull`), `AddTargetRoleCommand` | `POST …/target-roles` con `"provenance":"AI_SUGGESTED"` → 201 con `AI_SUGGESTED` | Siempre `MANUAL`; el campo se ignora si llega (CA-2.11.2, RT-01-CA06) |
| D5 | `rol_objetivo` sin las restricciones que repiten el dominio | `V1`, `V3`: `rol_profesional_id` admite `NULL`, sin `UNIQUE (perfil_id, rol_profesional_id)`, sin índice en la clave foránea, sin `CHECK` de `procedencia` y `estado_revision`, clave foránea sin `ON DELETE` explícito | `\d rol_objetivo` | Restricciones con nombre (sección 6) y su prueba que las viola |

Los demás CA de agregar y sustituir ya se cumplen (sección 3) y aquí reciben la prueba que les falta.

## 2. Alcance y PR

| PR | Rama | Base | Qué entrega | Tamaño estimado |
|---|---|---|---|---|
| 70-0 | `CM-70-spec-roles-objetivo` | punta de `CM-274-base-escritura-perfil` | solo spec, plan y tarjetas (no caben con el código: ≈ 645 líneas) | ≈ 645 |
| 70-1 | `CM-70-estado-catalogo-roles` | 70-0 | migración `V5_1`; `ProfessionalRole.active`; entidad y adaptador del catálogo; traducción de la violación de unicidad; pruebas de esquema y de adaptador | ≈ 420 |
| 70-2 | `CM-70-agregar-sustituir-rol-objetivo` | 70-1 | regla de rol activo, 409 del máximo, procedencia `MANUAL`, OpenAPI, `docs/errores.md`, pruebas y Postman | ≈ 650 |

Se mide con `git diff --shortstat <base>...HEAD` antes de cada commit; si 70-2 pasa de 800, la tarjeta T-70.9 (Postman) sale a una capa 70-3 propia.
Fuera de alcance: sección 13.

## 3. Trazabilidad CA → requisito → prueba → Postman

| CA | Técnico (literal) | Hoy | Requisito | Prueba (nombre exacto) | Petición de Postman (carpeta «HU-2.11 — Roles objetivo») |
|---|---|---|---|---|---|
| CA-2.11.1 | `PATCH …/target-roles/{roleId}` → 200 (cambia la referencia al catálogo; conserva el id del Rol Objetivo y su procedencia) | Cumple, sin prueba de procedencia | REQ-70-07 | `ProfileAppServiceTest.updateTargetRole_shouldKeepIdAndProvenance_whenRoleIsReplaced` | `OK PATCH /target-roles/{roleId} — 200 conserva id y procedencia (CA-2.11.1)` |
| CA-2.11.2 | `POST …/target-roles` → 201 (procedencia MANUAL, sin origen de IA ni prioridad) | 201, procedencia del cliente (D4) | REQ-70-01, REQ-70-04 | `ProfileAppServiceTest.addTargetRole_shouldStoreManualProvenance_whenRoleIsActive`; `ProfileControllerTest.postTargetRoles_shouldIgnoreProvenance_whenClientSendsIt` | `OK POST /target-roles — 201 MANUAL aunque llegue provenance (CA-2.11.2)` |
| CA-2.11.4 | `POST …/target-roles` → 409 (rol duplicado) | Cumple | REQ-70-05 | `ProfessionalProfileTest.addTargetRole_throwsDuplicateForSameProfessionalRoleId` (existe); `TargetRoleSchemaIT.insert_shouldFail_whenRoleRepeatsInProfile` | `ERR POST /target-roles — 409 rol repetido (CA-2.11.4)` |
| CA-2.11.5 | `POST …/target-roles` → 409 (máximo de 5) | 422 (D3) | REQ-70-03 | `ApiExceptionHandlerTest.businessException_shouldReturnItsStatusAndCode_whenThrown` (fila 409); `ProfileControllerTest.postTargetRoles_shouldReturn409_whenMaxReached` | `ERR POST /target-roles — 409 sexto rol (CA-2.11.5)` |
| CA-2.11.7 | `POST …/target-roles` con un rol no ACTIVE → 422 (no seleccionable) | 201 (D2) | REQ-70-02 | `ProfessionalRoleTest.requireSelectable_shouldRejectField_whenRoleIsInactive`; `ProfileAppServiceTest.addTargetRole_shouldRejectInactiveRole_whenCatalogRoleIsDisabled` | `ERR POST /target-roles — 422 rol inactivo (CA-2.11.7)` |
| CA-2.11.7 (sustituir, por RN-045) | — (la regla RN-045 rige «al sustituir» según las Observaciones) | 200 | REQ-70-06 | `ProfileAppServiceTest.updateTargetRole_shouldRejectInactiveRole_whenCatalogRoleIsDisabled` | `ERR PATCH /target-roles/{roleId} — 422 rol inactivo` |
| CA-2.11.8 | `PATCH` → 409 (duplicado) | Cumple | REQ-70-08 | `ProfessionalProfileTest.updateTargetRole_shouldThrowDuplicate_whenNewRoleIsAlreadyInProfile` (existe) | `ERR PATCH /target-roles/{roleId} — 409 sustituir por uno que ya está` (existe en la carpeta CM-271) |
| CA-2.11.10 | `POST` → 201 (quinto rol) | Cumple, sin prueba | REQ-70-03 | `ProfessionalProfileTest.addTargetRole_shouldAccept_whenProfileHasFourRoles` | `OK POST /target-roles — 201 quinto rol (CA-2.11.10)` |
| CA-2.11.13 | `POST`, `PATCH` y `DELETE` sobre un perfil de otro Usuario → 403 | Cumple, sin prueba de las tres rutas | REQ-70-09 | `ProfileAppServiceTest.targetRoleWrites_shouldThrowAccessDenied_whenProfileBelongsToOtherUser` (parametrizada: agregar, sustituir, eliminar) | `ERR POST /target-roles — 403 perfil ajeno (CA-2.11.13)` |
| RT-01-CA05/CA06 | 422 con la lista de campos; campos no esperados ignorados | Cumple, salvo `provenance` (D4) | REQ-70-04, REQ-70-10 | `ProfileControllerTest.postTargetRoles_shouldReturnRoleIdRequired_whenBodyIsEmpty` | `ERR POST /target-roles — 422 sin professionalRoleId` |
| RT-06-CA02 | Doble clic: una sola operación | Lo cubre el bloqueo del perfil (PR 1 de CM-274) | REQ-70-11 | `TargetRoleConcurrencyIT.addTargetRole_shouldStoreOnce_whenSameRoleArrivesTwiceAtOnce` | — (no reproducible con Newman) |

CA-2.11.3 y 2.11.9 son de CM-71; CA-2.11.11 y 2.11.12, de CM-72; la prueba de punta a punta por CA, de CM-73.

## 4. Requisitos (EARS)

- **REQ-70-01 (CA-2.11.2).** Cuando el dueño envíe `POST /api/v1/profiles/{id}/target-roles` con un `professionalRoleId` de un rol `ACTIVE` que no
  está en el perfil y el perfil tenga menos de 5 roles, Perfil debe responder **201** con el perfil, agregar el rol con `provenance` `MANUAL`, un
  `id` nuevo, `professionalRoleId` y `roleTitle` (el `nombre` en español del catálogo).
- **REQ-70-02 (CA-2.11.7).** Cuando el `professionalRoleId` exista en el catálogo con `estado` distinto de `ACTIVE`, Perfil debe responder **422**
  `VALIDATION_FAILED`, `detail` «Revisa los campos marcados.», `errors` = `[{"field":"professionalRoleId","code":"PROFESSIONAL_ROLE_DISABLED",
  "message":"No encontramos ese rol en el catálogo."}]`, sin modificar el perfil.
- **REQ-70-03 (CA-2.11.5, 2.11.10).** Con 4 roles, el quinto responde 201. Con 5 roles, Perfil debe responder **409** `TARGET_ROLE_LIMIT_REACHED`
  con `detail` «Ya tienes el máximo de 5 roles objetivo.», sin `errors` y sin modificar el perfil.
- **REQ-70-04 (CA-2.11.2, RT-01-CA06).** El cuerpo de `POST …/target-roles` solo admite `professionalRoleId`. Cualquier otro campo
  (`provenance`, `id`, `roleTitle`, `firebaseUid`, `status`) se ignora sin error.
- **REQ-70-05 (CA-2.11.4).** Si el rol ya está en el perfil, Perfil debe responder **409** `TARGET_ROLE_ALREADY_EXISTS` «Ese rol objetivo ya está en
  tu perfil.». La base lo garantiza también con `uq_rol_objetivo_perfil_rol_profesional`; una violación de esa restricción se traduce al mismo 409.
- **REQ-70-06 (RN-045 al sustituir).** Cuando el dueño envíe `PATCH …/target-roles/{roleId}` con un rol del catálogo que no está `ACTIVE`, Perfil
  debe responder el mismo 422 de REQ-70-02, sin cambiar el perfil.
- **REQ-70-07 (CA-2.11.1).** La sustitución por un rol `ACTIVE` que no está en el perfil responde **200**, conserva el `id` del Rol Objetivo y su
  `provenance`, y cambia `professionalRoleId` y `roleTitle`.
- **REQ-70-08 (CA-2.11.8).** Sustituir por un rol que ya está en otro Rol Objetivo del perfil responde **409** `TARGET_ROLE_ALREADY_EXISTS`.
- **REQ-70-09 (CA-2.11.13, RT-03).** Sobre un perfil de otro Usuario, `POST`, `PATCH` y `DELETE` de roles responden **403** `PROFILE_NOT_ALLOWED`
  «No encontramos lo que buscabas.», sin modificar el perfil.
- **REQ-70-10.** Orden de las comprobaciones (la primera que falla decide): cuerpo ilegible o con tipo incorrecto (422 `REQUEST_BODY_INVALID_FORMAT`) →
  forma del cuerpo (422) → identidad (401) → perfil ocupado más de 2 s (409 `PROFILE_UPDATE_IN_PROGRESS`, de CM-274) → perfil inexistente (404) o ajeno
  (403), porque la existencia y el dueño se leen con la fila ya bloqueada (REQ-EF-17 de CM-274) → rol en el catálogo (404 `PROFESSIONAL_ROLE_NOT_FOUND`) → rol activo
  (422) → máximo (409) → duplicado (409).
- **REQ-70-11 (RT-06-CA02).** Dos `POST` simultáneos con el mismo rol sobre el mismo perfil dejan una sola fila; uno responde 201 y el otro 409
  `TARGET_ROLE_ALREADY_EXISTS`. Seis `POST` simultáneos con roles distintos sobre un perfil con 0 roles dejan exactamente 5 filas.
- **REQ-70-12 (RN-045).** Un Rol Objetivo que ya estaba en el perfil sigue en él y se muestra aunque su rol del catálogo pase a `INACTIVE`.

## 5. Matriz de validación por campo

| Petición | Campo | Caso (valor literal) | Estado | `code` (arriba / en `errors[]`) | Mensaje | Prueba |
|---|---|---|---|---|---|---|
| `POST …/target-roles` | `professionalRoleId` | ausente, `null` | 422 | `VALIDATION_FAILED` / `PROFESSIONAL_ROLE_ID_REQUIRED` | «Selecciona una opción.» | `ProfileControllerTest.postTargetRoles_shouldReturnRoleIdRequired_whenBodyIsEmpty` |
| ídem | ídem | `""`, `"no-es-uuid"`, `123`, `{}` | 422 | `REQUEST_BODY_INVALID_FORMAT` | «Revisa el formato de los datos enviados.» | `ProfileControllerTest.postTargetRoles_shouldReturnBodyInvalid_whenRoleIdIsNotUuid` |
| ídem | ídem | `"00000000-0000-0000-0000-000000000000"` (no existe) | 404 | `PROFESSIONAL_ROLE_NOT_FOUND` | «No encontramos ese rol en el catálogo.» | `ProfileAppServiceTest.addTargetRole_shouldThrowNotFound_whenRoleIsNotInCatalog` |
| ídem | ídem | rol con `estado = 'INACTIVE'` | 422 | `VALIDATION_FAILED` / `PROFESSIONAL_ROLE_DISABLED` | «No encontramos ese rol en el catálogo.» | REQ-70-02 |
| ídem | `provenance` | `"AI_SUGGESTED"`, `"X"`, `null`, ausente | 201 | — | — (se ignora) | `ProfileControllerTest.postTargetRoles_shouldIgnoreProvenance_whenClientSendsIt` |
| ídem | cuerpo | `{` (JSON roto) | 422 | `REQUEST_BODY_INVALID_FORMAT` | «Revisa el formato de los datos enviados.» | existe en `ApiExceptionHandlerTest` |
| ídem | `Content-Type` | `text/plain` | 415 | `MEDIA_TYPE_NOT_ALLOWED` | «Tipo de contenido no admitido.» | existe |
| ídem | `{id}` | `no-es-uuid` | 422 | `PROFILE_ID_INVALID_FORMAT` | «El identificador del perfil no es válido.» | existe |
| `PATCH …/target-roles/{roleId}` | `professionalRoleId` | ausente, `null` | 422 | `VALIDATION_FAILED` / `PROFESSIONAL_ROLE_ID_REQUIRED` | «Selecciona una opción.» | `ProfileControllerTest.updateTargetRole_shouldReturnRoleIdRequired_whenBodyIsEmpty` (existe) |
| ídem | ídem | rol inexistente / `INACTIVE` | 404 / 422 | como en `POST` | | REQ-70-06 |
| ídem | `{roleId}` | `no-es-uuid` | 422 | `TARGET_ROLE_ID_INVALID_FORMAT` | «El identificador del rol objetivo no es válido.» | existe |
| ídem | `{roleId}` | UUID que no está en el perfil | 404 | `TARGET_ROLE_NOT_FOUND` | «No encontramos lo que buscabas.» | existe |
| ambas | `X-User-Id` | ausente, `""`, `"   "`, 129 caracteres | 401 | `IDENTITY_REQUIRED` | «Tu sesión expiró. Inicia sesión de nuevo.» | existe (`FirebaseUid.required`) |

`professionalRoleId` es un UUID: no aplican longitud, NFC ni texto hostil (un valor que no es UUID no llega al dominio).

## 6. Base de datos — `V5_1__estado_catalogo_y_restricciones_rol_objetivo.sql`

**Número.** La cadena ya tiene `V5` (CM-279 P1) y reserva `V6` a `V9` (CM-274, CM-66, CM-67). Esta migración va entre `V5` y `V6`: Flyway acepta la
versión `5.1` (`V5_1__…`), se aplica después de `V5` y antes de `V6` en cualquier base y no obliga a renumerar specs aprobadas. Consecuencia: el PR
70-1 se fusiona después de los PR de CM-279 P1 (ya es así por el orden de la cadena).

```sql
-- Estado del catálogo: un rol deja de ofrecerse sin borrarse, porque los Roles Objetivo existentes lo referencian.
ALTER TABLE rol_profesional
    ADD COLUMN estado VARCHAR(8) NOT NULL DEFAULT 'ACTIVE',
    ADD CONSTRAINT ck_rol_profesional_estado CHECK (estado IN ('ACTIVE', 'INACTIVE'));
CREATE INDEX ix_rol_profesional_estado ON rol_profesional (estado);

-- Rol Objetivo: el esquema repite las reglas del dominio.
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

- `VARCHAR(8)` = largo de `INACTIVE`; el enumerado de Java no tiene otro valor. `@Column(name = "estado", nullable = false, length = 8)`.
- El índice `uq_rol_objetivo_perfil_rol_profesional` empieza por `perfil_id`: sirve también como índice de esa clave foránea.
- Las 45 filas del catálogo quedan `ACTIVE`. Ninguna se desactiva en esta tarea.
- **Verificación previa en staging (la ejecuta Paula, solo lectura, antes de desplegar el PR 70-1):**
  `BEGIN READ ONLY;`
  1. `SELECT count(*) FROM rol_objetivo WHERE rol_profesional_id IS NULL;` → esperado `0`.
  2. `SELECT count(*) FROM (SELECT perfil_id, rol_profesional_id FROM rol_objetivo GROUP BY 1, 2 HAVING count(*) > 1) d;` → esperado `0`.
  3. `SELECT count(*) FROM rol_objetivo WHERE procedencia NOT IN ('MANUAL','AI_SUGGESTED','AI_EDITED') OR estado_revision NOT IN ('PENDING_REVIEW','REVIEWED');` → esperado `0`.
  `ROLLBACK;`
  Si alguna da distinto de 0, la migración falla al arrancar (Flyway la deshace entera y Cloud Run conserva la revisión anterior): no se despliega y
  queda como PENDIENTE de Paula decidir la corrección de esas filas. Una fila con `rol_profesional_id` nulo ya rompe hoy la lectura de su perfil
  (`TargetRole` lo exige), así que la consulta 1 también detecta un 500 existente.

## 7. Casos borde (R5)

| Grupo | Caso con datos literales | Resultado | Prueba |
|---|---|---|---|
| Colecciones | 0→1, 3→4, 4→5 roles | 201 | `ProfessionalProfileTest.addTargetRole_shouldAccept_whenProfileHasFourRoles` y existentes |
| Colecciones | 5 roles + rol nuevo `ACTIVE` | 409 `TARGET_ROLE_LIMIT_REACHED` | REQ-70-03 |
| Colecciones | 5 roles + rol `INACTIVE` | 422 (el rol se valida antes que el máximo, REQ-70-10) | `ProfileAppServiceTest.addTargetRole_shouldRejectInactiveRole_beforeLimit_whenProfileIsFull` |
| Colecciones | 5 roles + rol repetido | 409 `TARGET_ROLE_LIMIT_REACHED` (el máximo va antes que el duplicado; código existente) | `ProfessionalProfileTest.addTargetRole_shouldReportLimit_whenFullAndRepeated` |
| Duplicados | mismo `professionalRoleId` en dos pestañas a la vez | una fila, 201 y 409 | `TargetRoleConcurrencyIT` (REQ-70-11) |
| Duplicados | fila repetida escrita sin pasar por el dominio | 409 `TARGET_ROLE_ALREADY_EXISTS`, nunca 500 | `ProfessionalProfileRepositoryAdapterIT.save_shouldTranslateUniqueViolation_whenRoleRepeats` |
| Estado | sustituir un rol por sí mismo (mismo `professionalRoleId`, activo) | 200 sin cambios | `ProfessionalProfileTest.updateTargetRole_shouldKeepRole_whenReplacedBySameCatalogRole` |
| Estado | sustituir un rol por sí mismo cuando ese rol pasó a `INACTIVE` | 422 (no se elige un rol inactivo) | `ProfileAppServiceTest.updateTargetRole_shouldRejectInactiveRole_whenCatalogRoleIsDisabled` |
| Estado | rol del perfil cuyo catálogo pasó a `INACTIVE`, `GET /{id}` | 200, el rol sigue con su `roleTitle` | `ProfessionalRoleRepositoryAdapterIT.findById_shouldReturnInactiveRole_whenCatalogRowIsInactive` (lectura) y REQ-70-12 |
| Estado | perfil `COMPLETED` | agregar y sustituir igual que en Borrador | `ProfileAppServiceTest.addTargetRole_shouldStoreManualProvenance_whenRoleIsActive` (parametrizada por estado) |
| Propiedad | perfil ajeno; sin identidad; `firebaseUid` en el cuerpo | 403; 401; se ignora | REQ-70-09, matriz de validación, REQ-70-04 |
| Concurrencia | 6 `POST` simultáneos con roles distintos, perfil vacío | 5 filas, cinco 201 y un 409 | `TargetRoleConcurrencyIT.addTargetRole_shouldNeverExceedFive_whenSixArriveAtOnce` |
| Dependencia lenta | otra escritura retiene el perfil más de 2 s | 409 `PROFILE_UPDATE_IN_PROGRESS` (lo prueba `ProfileWriteLockIT` de CM-274) | se cita, no se repite |
| Dependencia caída | PostgreSQL no responde | 500 `INTERNAL_ERROR` (fallo no previsible por el cliente; `ERROR` con traza) | existente (`ApiExceptionHandlerTest`) |
| Falla parcial | error de base al guardar tras validar | nada guardado (una transacción) | `TargetRoleConcurrencyIT` comprueba el conteo final |
| Carga | cuerpo de 1 MB con un `professionalRoleId` válido y basura | 201 o 422 según el UUID; sin 500 | Newman `ERR POST /target-roles — 422 sin professionalRoleId` usa campos extra |
| Texto, números, fechas, enumerados del cuerpo | no aplica: el único campo es un UUID | — | justificación |

## 8. Errores (R3)

| Código | HTTP | Mensaje | Excepción | Cambia |
|---|---|---|---|---|
| `TARGET_ROLE_LIMIT_REACHED` | **409** | «Ya tienes el máximo de 5 roles objetivo.» | `MaxTargetRolesExceededException` | Estado: el código se conserva y cambia su estado (CA-2.11.5) |
| `PROFESSIONAL_ROLE_DISABLED` | 422 (`errors[]`) | «No encontramos ese rol en el catálogo.» | `InvalidFieldsException` desde `ProfessionalRole.requireSelectable` | Nuevo, de campo del dominio |
| `TARGET_ROLE_ALREADY_EXISTS` | 409 | «Ese rol objetivo ya está en tu perfil.» | `DuplicateTargetRoleException`, también desde la violación de `uq_rol_objetivo_perfil_rol_profesional` | Solo la traducción nueva |
| `PROFESSIONAL_ROLE_NOT_FOUND`, `PROFESSIONAL_ROLE_ID_REQUIRED`, `TARGET_ROLE_NOT_FOUND`, `PROFILE_NOT_ALLOWED`, `IDENTITY_REQUIRED` | sin cambio | | | |

Rutas al 500 revisadas: `TargetRole` con `roleTitle` en blanco o de más de 255 (el título sale del catálogo, `NOT NULL VARCHAR(255)`: inalcanzable
desde la entrada); violación de `NOT NULL`/`CHECK` de `rol_objetivo` (el mapeo siempre escribe valores del enumerado y el dominio exige
`professionalRoleId`: inalcanzable); violación de `UNIQUE` (traducida a 409); `findById` de un rol que no existe (404). Ninguna ruta previsible llega
al genérico.

## 9. Contrato

- `POST /api/v1/profiles/{id}/target-roles`, cuerpo `{"professionalRoleId": "<uuid>"}`. Respuestas: 201 (perfil), 401, 403, 404, 409
  (`TARGET_ROLE_ALREADY_EXISTS`, `TARGET_ROLE_LIMIT_REACHED`, `PROFILE_UPDATE_IN_PROGRESS`), 415, 422 (`VALIDATION_FAILED` con
  `PROFESSIONAL_ROLE_ID_REQUIRED` o `PROFESSIONAL_ROLE_DISABLED`; `REQUEST_BODY_INVALID_FORMAT`; `PROFILE_ID_INVALID_FORMAT`).
- `PATCH /api/v1/profiles/{id}/target-roles/{roleId}`: igual, con 200 y sin `TARGET_ROLE_LIMIT_REACHED`; 404 suma `TARGET_ROLE_NOT_FOUND`.
- Cambios para Frontend: 409 del sexto rol (antes 422), 422 del rol inactivo (nuevo) y `provenance` ya no se lee. Van en el documento de Frontend de
  `comunicaciones/` (aditivo para quien lee `code`; Frontend ya resuelve por `code`).
- `@Schema` de `AddTargetRoleRequest.professionalRoleId`: `format = "uuid"`, `requiredMode = REQUIRED`, ejemplo `a0000001-0000-0000-0000-000000000002`.

## 10. Seguridad

| Control | Aplica | Cómo lo cumple | Prueba |
|---|---|---|---|
| ASVS 2.2.1 / 2.2.2 (validación en servidor, lista permitida) | Sí | DTO con un solo campo; regla de rol activo en el dominio | matriz de validación |
| ASVS 8.2.2 (anti-IDOR) | Sí | `loadForUserForUpdate` compara el dueño en las tres rutas | `targetRoleWrites_shouldThrowAccessDenied_whenProfileBelongsToOtherUser` |
| ASVS 1.2.4 (consultas parametrizadas) | Sí | Solo JPQL y Spring Data; la migración no recibe entrada | revisión del diff (`git grep nativeQuery`) |
| ASVS 4.1.1 (charset) | Sí | Lo da el manejador existente | `ResponseCharsetIT` (existe) |
| ASVS 15.3.1 (solo campos necesarios) | Sí | Respuesta por `ProfileResponse`; el catálogo no expone `estado` | `ProfileControllerTest` |
| OWASP API1 BOLA | Sí | 403 a perfil ajeno | ídem 8.2.2 |
| OWASP API3 asignación masiva | Sí | `provenance`, `id`, `firebaseUid` ignorados | `postTargetRoles_shouldIgnoreProvenance_whenClientSendsIt` |
| OWASP API4 consumo | Sí | máximo de 5 por perfil; bloqueo con espera de 2 s | REQ-70-03, REQ-70-11 |
| OWASP API2, API5, API7, API8, API9, API10 | No aplica | Identidad del Gateway sin cambios; sin URL de entrada; sin integración externa; ruta bajo `/api/v1/profiles` ya documentada | — |
| SEG-02 (dos usuarios) | Sí | Usuario B sobre el perfil de A: 403 en `POST`, `PATCH`, `DELETE` | prueba parametrizada y Newman 403 |
| SEG-04 (logs) | Sí | El rechazo registra `code`, `requestId`, `firebaseUid`; nunca el nombre del rol ni el cuerpo | `ApiExceptionHandlerTest` (existente) |

La matriz de `cameia-infra` no cambia por esta tarea.

## 11. Atributos de calidad

| Atributo | Métrica | Límite | Cómo lo cumple | Prueba o comando |
|---|---|---|---|---|
| AC-0003 Seguridad | SEG-01, SEG-02 | 100 % de accesos cruzados rechazados | 403 en las tres rutas | prueba parametrizada + Newman |
| AC-0003 Seguridad | SEG-04 | 0 datos del perfil en logs | sin cambios en el registro | `ApiExceptionHandlerTest` |
| AC-0004 Fiabilidad | FIA-01 | el fallo conserva el último estado confirmado | todo rechazo antes de `save`; una transacción | `verify(repository, never()).save(any())` en cada prueba de rechazo |
| AC-0004 Fiabilidad | FIA-02 | 0 efectos adicionales al repetir | duplicado → 409 sin fila nueva; 20 repeticiones del mismo `POST` dejan 1 fila | `TargetRoleConcurrencyIT.addTargetRole_shouldStoreOnce_whenSameRoleIsSentTwentyTimes` |
| AC-0002 Desempeño | DES-02 | p95 ≤ 2 s | una lectura del catálogo por id (PK) y el perfil bloqueado | Newman: `pm.expect(pm.response.responseTime).to.be.below(2000)` en cada petición de la carpeta |
| AC-0002 Desempeño | DES-04 | 5xx propios < 1 % | ninguna ruta previsible al 500 (sección 8) | Newman sin 5xx |
| AC-0006 Interacción | INT | `code` y `errors[]` por campo | 422 con `errors[0].field = professionalRoleId` | `ProfileControllerTest.postTargetRoles_shouldReturn422WithField_whenRoleIsInactive` |
| Mantenibilidad | MAN-01 | ≥ 90 % líneas y ramas en lo nuevo | | JaCoCo por clase (sección 15) |

## 12. Decisiones

| # | Decisión | Porqué | Alternativas descartadas |
|---|---|---|---|
| D70-1 | `TARGET_ROLE_LIMIT_REACHED` conserva su nombre y pasa a 409 | La causa no cambió (máximo alcanzado); Frontend ya resuelve por `code`; el estándar prohíbe renombrar, no corregir el estado que fija el CA. Precedente: CM-274 y CM-66 usan `*_LIMIT_REACHED` con 409 | Retirar el código y crear otro (obliga a Frontend a cambiar su tabla sin motivo) |
| D70-2 | Rol inactivo: 422 `VALIDATION_FAILED` con un elemento en `errors[]` (`professionalRoleId`, `PROFESSIONAL_ROLE_DISABLED`) | El cambio v6 lo justifica así: «lo inválido es el rol inactivo que llega en el cuerpo»; RT-01-CA05 pide 422 con la lista de campos; reutiliza `InvalidFieldsException` | Código propio en la raíz de la respuesta; 404 (lo que el v6 corrige) |
| D70-3 | Mensaje del rol inactivo: «No encontramos ese rol en el catálogo.» | El CA no fija texto; es el texto del mismo HU para un rol que el buscador no ofrece (CA-2.11.12), y el buscador solo ofrece activos. Queda PENDIENTE de Vela confirmarlo (no bloquea) | Un texto nuevo de Backend (R14 lo prohíbe) |
| D70-4 | Rol que no existe en el catálogo: sigue 404 `PROFESSIONAL_ROLE_NOT_FOUND` | Contrato publicado y ningún CA lo cambia | 422 por analogía con CA-2.11.7 (sería un cambio de contrato sin CA) |
| D70-5 | `provenance` se ignora en `POST …/target-roles` y se guarda `MANUAL` | CA-2.11.2 lo fija; mismo ajuste ya decidido y avisado a Frontend para las demás rutas manuales | Mantenerlo obligatorio (permite que el cliente declare `AI_SUGGESTED`) |
| D70-6 | La regla «solo roles activos» vive en `ProfessionalRole.requireSelectable()` (dominio) | Reglas de negocio en el dominio, probadas sin Spring | Filtrar en el adaptador (`findActiveById`): esconde la regla y no distingue inactivo de inexistente |
| D70-7 | Migración `V5_1` | No renumera las specs aprobadas de CM-274, CM-66 y CM-67 y respeta el orden de la cadena | `V6` (choca con CM-274, que se detiene si ya existe un `V6`); `V10` (falla en una base con `V6` a `V9` sin aplicar) |
| D70-8 | Restricciones de `rol_objetivo` dentro de esta tarea | La tarea toca la tabla y el estándar exige que el esquema repita el dominio (unicidad por restricción, no solo por código) | Dejarlas a CM-283 parte 2 (un defecto conocido en código que se toca) |
| D70-9 | Violación de `uq_rol_objetivo_perfil_rol_profesional` → 409 `TARGET_ROLE_ALREADY_EXISTS` en el adaptador (`saveAndFlush` + nombre de la restricción) | Una violación nunca termina en 500 (R4); el adaptador es el único que conoce la base | Traducirla en el manejador (la presentación conocería nombres de la base) |

## 13. Fuera de alcance y hallazgos con destino

| Tema | Destino |
|---|---|
| Eliminar roles (CA-2.11.3, 2.11.9) | CM-71 |
| Catálogo solo activos y orden de los roles en el perfil (CA-2.11.11, 2.11.12) | CM-72 |
| Una prueba de punta a punta por CA y cobertura de lo de roles | CM-73 |
| Evento `perfil.profesional-actualizado` al editar roles de un perfil `COMPLETED` (HT-03) | CM-67 PR 3 (bloqueado por Q-67-1) |
| `AI_EDITED` al sustituir un rol sugerido (HU-2.10) | Fuera del MVP |
| Clave foránea de `perfil_id` sin nombre `fk_` (V1) y `creado_en` que el mapeo sobrescribe en memoria | CM-283 parte 2 (alineación de esquema) |
| Administración del catálogo (pasar un rol a `INACTIVE`) | Sin HU: se hace por migración cuando Producto lo pida |

## 14. Pendientes (no bloquean)

| # | Qué | De quién | Qué depende |
|---|---|---|---|
| P70-1 | Confirmar el texto del 422 del rol inactivo (D70-3) | Juan David Vela Coronado | Solo el `message`; el `code` y el estado no cambian |
| P70-2 | Ejecutar las tres consultas de la sección 6 en staging antes de desplegar el PR 70-1 | Paula Andrea Muñoz Delgado | El despliegue, no la ejecución local |

## 15. Línea base, lista de verificación y dictamen

**Línea base.** `origin/develop` `2a54ca7`: `./mvnw.cmd -B clean verify` → BUILD SUCCESS, 310 pruebas unitarias y 20 de integración, 0 fallos
(medido el 9-oct-2026 en `cameia-worktrees\perfil-specs-v6`, JDK 21, Docker 29.7). La base real de ejecución es la punta de `CM-274-base-escritura-perfil`:
el ejecutor corre `clean verify` ahí antes de la primera tarjeta y anota el resultado en la cola.

**Lista de verificación (el ejecutor la corre entera antes de entregar).**

| # | Comprobación | Comando o petición | Esperado |
|---|---|---|---|
| V1 | Migración aplicada y restricciones | `./mvnw.cmd -B "-Dit.test=TargetRoleSchemaIT" verify` | 9 pruebas en verde |
| V2 | CA-2.11.7 agregar y sustituir | Newman `422 rol inactivo` (dos peticiones) | 422, `errors[0].code` `PROFESSIONAL_ROLE_DISABLED` |
| V3 | CA-2.11.5 | Newman `409 sexto rol` | 409 `TARGET_ROLE_LIMIT_REACHED`, sin `errors` |
| V4 | CA-2.11.10 | Newman `201 quinto rol` | 201, 5 roles |
| V5 | CA-2.11.2 | Newman `201 MANUAL aunque llegue provenance` | `provenance` `MANUAL` |
| V6 | CA-2.11.1 | Newman `200 conserva id y procedencia` | mismo `id`, `provenance` sin cambio |
| V7 | CA-2.11.4, 2.11.8 | Newman 409 duplicado al agregar y al sustituir | 409 `TARGET_ROLE_ALREADY_EXISTS` |
| V8 | CA-2.11.13 (adversarial) | Newman con `X-User-Id` del Usuario B sobre el perfil de A en `POST`, `PATCH`, `DELETE` | 403 `PROFILE_NOT_ALLOWED`, perfil de A sin cambios (`GET` después) |
| V9 | Asignación masiva (adversarial) | `POST` con `{"professionalRoleId":…,"provenance":"AI_SUGGESTED","firebaseUid":"otro","id":"…"}` | 201, `MANUAL`, dueño sin cambio |
| V10 | Concurrencia (adversarial) | `TargetRoleConcurrencyIT` | 1 fila con el mismo rol; nunca más de 5 |
| V11 | Datos hostiles (adversarial) | `POST` con `professionalRoleId` = `"' OR 1=1 --"`, `"<script>"`, `12345`, `[]` | 422 `REQUEST_BODY_INVALID_FORMAT`, sin 500 ni eco del valor |
| V12 | Ningún 500 previsible | revisar la salida de Newman de toda la carpeta | 0 respuestas 5xx |
| V13 | R1/R8 | `git diff` sin `CM-` en código ni comentarios; Javadoc en español en lo nuevo | 0 coincidencias de `git diff <base> -- src | grep -n "CM-[0-9]"` |
| V14 | R10 | `target/site/jacoco/jacoco.csv` | ≥ 90 % líneas y ramas en `ProfessionalRole`, `ProfessionalRoleRepositoryAdapter`, `ProfessionalProfileRepositoryAdapter`, `ProfileAppService`, `ErrorCatalog` |
| V15 | Catálogo de errores | `ErrorCodeDocumentationTest`, `ErrorCatalogTest` | verde |
| V16 | Arquitectura | `ArquitecturaTest` | verde |
| V17 | Tamaño | `git diff --shortstat <base>...HEAD` por capa | ≤ 800 (meta), nunca ≥ 1000 |

**Tabla regla → evidencia prevista:** R1 y R8 → V13 y Javadoc de las tarjetas; R2 → sección 5 y V11; R3 → sección 8 y V12; R4 → sección 6 y V1;
R5 → sección 7; R6 → sección 10, V8 y V9; R7 → D70-6 y `ArquitecturaTest`; R9 → pruebas por capa de `tasks.md`; R10 → V14; R11 → tarjeta T-70.9 y
V2 a V9; R12 → sección 3; R13 → sección 11; R14 → sin push ni PR; PENDIENTES en la sección 14.

**Dictamen: `LISTA PARA EJECUTAR`.** Todos los CA de la parte de agregar y sustituir tienen requisito, prueba y petición; el contraste con el código real
está hecho (`ProfileAppService:130-145`, `ErrorCatalog:79`, `AddTargetRoleRequest`, `V1`, `V3`, `ProfessionalRoleRepositoryAdapter`); los dos
PENDIENTES no bloquean la ejecución.
