# Spec — CM-71: eliminar Roles Objetivo con el último rol de un perfil activo en 409

- **Tarea:** CM-71 · Subtarea «HU-2.11 – Backend: eliminar Roles Objetivo» · responsable: Paula Andrea Muñoz Delgado
- **Repositorio:** `cameia-perfil`. Escrita contra `origin/develop` `2a54ca7`; se ejecuta en la cola de Perfil sobre la punta de CM-70 (PR 70-2).
- **Backlog vigente:** `09102026_01_Backlog_v6.xlsx`, HU-2.11: CA-2.11.3, CA-2.11.9 y CA-2.11.13 (la parte de `DELETE`); RT-03, RT-05, RT-06.
- **Estado:** `LISTA PARA EJECUTAR` (sección 11). Sin pendientes.
- **Atributos de calidad:** fiabilidad (FIA-01, FIA-02), seguridad (SEG-02), interacción (AC-0006), mantenibilidad (MAN-01).

## 1. Contexto y defecto comprobado

| # | Defecto | Dónde | ANTES | DESPUÉS |
|---|---|---|---|---|
| D1 | Eliminar el último Rol Objetivo de un perfil `COMPLETED` responde 422 | `ErrorCatalog`: `TARGET_ROLE_NOT_ALLOWED` → `UNPROCESSABLE_CONTENT`; `ProfileController` (`@ApiResponse` 422) | Perfil `COMPLETED` con un rol, `DELETE …/target-roles/{roleId}` → 422 | **409** `TARGET_ROLE_NOT_ALLOWED` «No puedes quedarte sin roles objetivo con el perfil activo.», rol y estado sin cambios |

La regla ya está en el dominio (`ProfessionalProfile.removeTargetRole`: pertenencia → 404; `COMPLETED` y un solo rol → excepción) y CA-2.11.9 ya se
cumple (en Borrador se elimina el último y el perfil sigue `IN_PROGRESS`), pero sin prueba de servicio ni de punta a punta: `ProfileAppService.removeTargetRole`
tiene 0 líneas cubiertas en la línea base (JaCoCo, 9-oct-2026).

## 2. Alcance y PR

Un PR: `CM-71-eliminar-rol-objetivo`, base la punta de `CM-70-agregar-sustituir-rol-objetivo` (o de la capa 70-3 si existe). ≈ 450 líneas (≈ 220 de
documentos). Fuera de alcance: sección 10.

## 3. Trazabilidad CA → requisito → prueba → Postman

| CA | Técnico (literal) | Hoy | Requisito | Prueba | Postman |
|---|---|---|---|---|---|
| CA-2.11.3 | `DELETE …/target-roles/{roleId}` → 409 Conflict (último Rol Objetivo de un perfil COMPLETED) | 422 (D1) | REQ-71-01 | `ProfileControllerTest.removeTargetRole_shouldReturn409_whenLastRoleOfActiveProfile`; `ProfileAppServiceTest.removeTargetRole_shouldKeepRoleAndStatus_whenLastRoleOfCompletedProfile` | `ERR DELETE /target-roles/{roleId} — 409 último rol de un perfil activo (CA-2.11.3)` (petición existente de la carpeta CM-20, pasa de 422 a 409) |
| CA-2.11.9 | `DELETE …/target-roles/{roleId}` → 204 No Content (perfil en Borrador con un solo rol) | Cumple, sin prueba de servicio | REQ-71-02 | `ProfileAppServiceTest.removeTargetRole_shouldRemoveLastRole_whenProfileIsDraft` | `OK DELETE /target-roles/{roleId} — 204 último rol en Borrador (CA-2.11.9)` y `OK GET /profiles/{id} — sigue en Borrador sin roles` |
| CA-2.11.13 (`DELETE`) | `DELETE` sobre un perfil de otro Usuario → 403 | Cumple (prueba parametrizada de CM-70) | REQ-71-03 | `ProfileAppServiceTest.targetRoleWrites_shouldThrowAccessDenied_whenProfileBelongsToOtherUser` (caso eliminar) | `ERR DELETE /target-roles/{roleId} — 403 perfil ajeno` |
| RT-03 (rol que no es del perfil) | 404 | Cumple | REQ-71-04 | `ProfessionalProfileTest.removeTargetRole_shouldThrowNotFound_whenCompletedProfileHasOneOtherRole` (existe) | `ERR DELETE /target-roles/{ajeno} — 404` |
| RT-06-CA02 | dos eliminaciones a la vez no dejan el perfil Activo sin roles | Lo da el bloqueo del perfil | REQ-71-05 | `TargetRoleConcurrencyIT.removeTargetRole_shouldKeepOneRole_whenTwoRemovalsArriveOnActiveProfile` | — |

## 4. Requisitos (EARS)

- **REQ-71-01 (CA-2.11.3).** Cuando el dueño elimine el único Rol Objetivo de un perfil `COMPLETED`, Perfil debe responder **409**
  `TARGET_ROLE_NOT_ALLOWED` con `detail` «No puedes quedarte sin roles objetivo con el perfil activo.», sin `errors`, y conservar el rol y el estado
  `COMPLETED`.
- **REQ-71-02 (CA-2.11.9).** Cuando el perfil esté en `IN_PROGRESS` con un solo rol, `DELETE` debe responder **204** sin cuerpo, dejar el perfil sin
  roles y en `IN_PROGRESS`.
- **REQ-71-03 (CA-2.11.13).** Sobre un perfil ajeno, **403** `PROFILE_NOT_ALLOWED` «No encontramos lo que buscabas.», sin cambios.
- **REQ-71-04.** Orden (el de REQ-EF-17 de CM-274, sin cuerpo): identificadores (422 `PROFILE_ID_INVALID_FORMAT` / `TARGET_ROLE_ID_INVALID_FORMAT`) → identidad (401)
  → perfil ocupado más de 2 s (409 `PROFILE_UPDATE_IN_PROGRESS`) → perfil inexistente (404) o ajeno (403) → rol en el perfil (404 `TARGET_ROLE_NOT_FOUND`) → último rol de un perfil activo (409).
- **REQ-71-05 (RT-06).** Con un perfil `COMPLETED` y dos roles, dos `DELETE` simultáneos de roles distintos dejan exactamente un rol: uno responde 204
  y el otro 409 `TARGET_ROLE_NOT_ALLOWED`. Repetir el `DELETE` de un rol ya eliminado responde 404 `TARGET_ROLE_NOT_FOUND` (sin efecto adicional).

## 5. Validación, base de datos y casos borde

- **Entrada:** solo los identificadores de la ruta (validados por el manejador existente) y `X-User-Id`. Sin cuerpo: un cuerpo enviado se ignora.
- **Base de datos:** sin migración. `rol_objetivo` ya tiene sus restricciones (CM-70); el borrado lo hace `orphanRemoval`.

| Grupo | Caso | Resultado | Prueba |
|---|---|---|---|
| Colecciones | `COMPLETED` con 2 roles, borrar 1 | 204, queda 1 | `ProfessionalProfileTest.removeTargetRole_removesCorrectlyWhenMultipleExist` (existe) |
| Colecciones | `COMPLETED` con 1 rol | 409 | REQ-71-01 |
| Colecciones | `IN_PROGRESS` con 1 rol | 204, 0 roles, `IN_PROGRESS` | REQ-71-02 |
| Colecciones | `IN_PROGRESS` con 0 roles y un `roleId` cualquiera | 404 `TARGET_ROLE_NOT_FOUND` | `ProfessionalProfileTest.removeTargetRole_shouldThrowNotFound_whenProfileHasNoRoles` |
| Estado | borrar un rol ya borrado (repetición) | 404 | `ProfileAppServiceTest.removeTargetRole_shouldThrowNotFound_whenRoleWasAlreadyRemoved` |
| Estado | `COMPLETED` con 1 rol y `roleId` ajeno | 404 antes que 409 (existe) | `removeTargetRole_shouldThrowNotFound_whenCompletedProfileHasOneOtherRole` |
| Propiedad | perfil ajeno; sin identidad | 403; 401 | REQ-71-03; existente |
| Concurrencia | dos pestañas borran los dos roles de un perfil activo | uno 204, otro 409, queda 1 | REQ-71-05 |
| Dependencia lenta | otra escritura retiene el perfil > 2 s | 409 `PROFILE_UPDATE_IN_PROGRESS` | `ProfileWriteLockIT` (CM-274) |
| Texto, números, fechas, enumerados, carga | no aplican: no hay cuerpo | — | justificación |

## 6. Errores

| Código | HTTP | Mensaje | Excepción | Cambio |
|---|---|---|---|---|
| `TARGET_ROLE_NOT_ALLOWED` | **409** | «No puedes quedarte sin roles objetivo con el perfil activo.» | `LastTargetRoleException` | Estado (el código se conserva, igual que D70-1) |

Ninguna ruta previsible llega al 500: la regla y la pertenencia están en el dominio; la base no tiene restricciones que el borrado pueda violar.

## 7. Seguridad

ASVS 8.2.2 y OWASP API1 (403 a perfil ajeno, prueba parametrizada de CM-70 y Newman); API4 (bloqueo con espera máxima de 2 s); 4.1.1 (charset, existente);
SEG-04 (el log del rechazo no lleva el nombre del rol). API2, API3 (sin cuerpo), API5 a API10: no aplican, sin cambio de identidad, rutas ni integraciones.

## 8. Atributos de calidad

| Atributo | Métrica | Cómo | Prueba |
|---|---|---|---|
| AC-0004 | FIA-01 | el 409 no cambia rol ni estado | REQ-71-01 (afirma rol y `COMPLETED` tras el rechazo) |
| AC-0004 | FIA-02 | repetir el `DELETE` no tiene efecto adicional | `removeTargetRole_shouldThrowNotFound_whenRoleWasAlreadyRemoved` |
| AC-0003 | SEG-02 | Usuario B no borra roles de A | Newman 403 |
| AC-0006 | INT | `code` estable para que Frontend muestre el mensaje del CA | `ProfileControllerTest` |
| AC-0002 | DES-02 | p95 ≤ 2 s | `responseTime < 2000` en Newman |
| MAN-01 | ≥ 90 % | `ProfileAppService.removeTargetRole` deja de estar sin cubrir | JaCoCo |

## 9. Decisiones

| # | Decisión | Porqué | Alternativa descartada |
|---|---|---|---|
| D71-1 | `TARGET_ROLE_NOT_ALLOWED` conserva su nombre y pasa a 409 | Igual que D70-1; precedente CM-66 (`SKILL_NOT_ALLOWED` 409) | Código nuevo |
| D71-2 | 404 antes que 409 cuando el rol no es del perfil | Ya es así en el dominio y en CM-66 (REQ-HB-08) | 409 primero (revelaría reglas sobre un recurso que no es del perfil) |

## 10. Fuera de alcance

Evento `perfil.profesional-actualizado` al eliminar en un perfil `COMPLETED` (CM-67 PR 3); estado `IN_REVIEW` (no alcanzable en el MVP, D26 de CM-271).

## 11. Línea base, lista de verificación y dictamen

**Línea base:** la de CM-70 (spec §15); antes de la primera tarjeta, `clean verify` en la punta de CM-70 y anotarlo en la cola.

| # | Comprobación | Comando o petición | Esperado |
|---|---|---|---|
| V1 | CA-2.11.3 | Newman `409 último rol de un perfil activo` y luego `GET /profiles/{id}` | 409 `TARGET_ROLE_NOT_ALLOWED`; el `GET` muestra el rol y `COMPLETED` |
| V2 | CA-2.11.9 | Newman `204 último rol en Borrador` y `GET` | 204 sin cuerpo; `targetRoles` vacío y `IN_PROGRESS` |
| V3 | 404 rol ajeno al perfil | Newman | 404 `TARGET_ROLE_NOT_FOUND`, «No encontramos lo que buscabas.» |
| V4 | Perfil ajeno (adversarial) | Newman con el Usuario C sobre el perfil del Usuario A | 403 `PROFILE_NOT_ALLOWED`; el perfil de A conserva sus roles |
| V5 | Concurrencia (adversarial) | `TargetRoleConcurrencyIT.removeTargetRole_shouldKeepOneRole_whenTwoRemovalsArriveOnActiveProfile` | 1 rol, un 204 y un 409 |
| V6 | Repetición (adversarial) | `DELETE` del mismo rol dos veces | 204 y 404 |
| V7 | Identificador hostil (adversarial) | `DELETE …/target-roles/%27%20OR%201=1` | 422 `TARGET_ROLE_ID_INVALID_FORMAT`, sin eco |
| V8 | R10 | JaCoCo | `ProfileAppService` y `ErrorCatalog` ≥ 90 % líneas y ramas |
| V9 | Catálogo y arquitectura | `ErrorCatalogTest`, `ErrorCodeDocumentationTest`, `ArquitecturaTest` | verde |
| V10 | R1/R8 y tamaño | `git diff` sin `CM-`; `--shortstat` ≤ 800 | |

**Regla → evidencia prevista:** R1/R8 → V10; R2 → §5 (solo identificadores); R3 → §6 y V7; R4 → no aplica (sin migración); R5 → §5; R6 → §7 y V4;
R7 → sin código nuevo de producción salvo el catálogo; R9 → tarjetas; R10 → V8; R11 → T-71.3; R12 → §3; R13 → §8; R14 → sin push.

**Dictamen: `LISTA PARA EJECUTAR`.** Contraste hecho con `ErrorCatalog:81`, `ProfessionalProfile.removeTargetRole:121-128`, `ProfileAppService:147-150`,
`ProfileController:356-375` y la petición de Postman existente de la carpeta CM-20.
