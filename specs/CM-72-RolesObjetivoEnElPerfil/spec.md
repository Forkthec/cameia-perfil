# Spec — CM-72: catálogo con solo roles activos y Roles Objetivo estables en el perfil

- **Tarea:** CM-72 · Subtarea «HU-2.11 – Backend: Roles Objetivo en el Perfil Profesional» · responsable: Paula Andrea Muñoz Delgado
- **Repositorio:** `cameia-perfil`. Escrita contra `origin/develop` `2a54ca7`; se ejecuta en la cola sobre la punta de CM-71.
- **Backlog vigente:** `09102026_01_Backlog_v6.xlsx`, HU-2.11: CA-2.11.11, CA-2.11.12, CA-2.11.1 (lo que muestra la lista) y las Observaciones
  («sin seniority, prioridad ni reordenamiento»; «sustituir … conserva el identificador del Rol Objetivo»).
- **Estado:** `LISTA PARA EJECUTAR` (sección 10). Sin pendientes.
- **Atributos de calidad:** interacción (AC-0006), desempeño (DES-02), seguridad (SEG-01), mantenibilidad (MAN-01).

## 1. Contraste con el código y desajustes

| # | Qué pide el backlog | `develop` hoy | Desajuste |
|---|---|---|---|
| D1 | CA-2.11.11: `GET /api/v1/profiles/professional-roles` → 200 **solo roles ACTIVE** | `ProfessionalRoleJpaRepository.findAllOrderedEs/En` devuelven toda la tabla (no había estado hasta CM-70) | **Sí**: con un rol `INACTIVE`, el catálogo lo ofrece |
| D2 | CA-2.11.1 y Observaciones: sustituir conserva el Rol Objetivo; sin reordenamiento | `ProfessionalProfile.updateTargetRole` quita el rol y agrega el reemplazo al final; `ProfessionalProfileEntity.targetRoles` no tiene `@OrderBy` (el orden de lectura no está definido) | **Sí**: tras sustituir el primero de tres roles, la respuesta del `PATCH` lo muestra último y un `GET` posterior puede mostrar cualquier orden |
| — | CA-2.11.12: búsqueda sin coincidencias → 200 (tipo **F**) | El catálogo no tiene parámetro de búsqueda: la filtra `cameia-web` sobre la lista | Ninguno para Backend |
| — | CA-2.11.11: «que todavía no están en el perfil» | El catálogo es global y no conoce el perfil; `cameia-web` excluye los que ya tiene (el Técnico solo exige `ACTIVE`) | Ninguno para Backend |
| — | Lo que muestra el perfil: `targetRoles[]` con `id`, `professionalRoleId`, `roleTitle`, `provenance` | `ProfileResponse.TargetRoleItem` | Ninguno; sin prueba de su mapeo (0 de 2 líneas cubiertas) |

Como hay desajustes, la CM lleva código.

## 2. Alcance y PR

Un PR: `CM-72-catalogo-activos-y-orden-roles`, base la punta de CM-71. ≈ 380 líneas (≈ 200 de documentos). Sin migración (usa `estado` de CM-70).

## 3. Trazabilidad

| CA | Requisito | Prueba | Postman |
|---|---|---|---|
| CA-2.11.11 | REQ-72-01 | `ProfessionalRoleRepositoryAdapterIT.findAllByLang_shouldExcludeInactive_whenCatalogHasInactiveRole` (es y en) | `OK GET /professional-roles — no ofrece el rol inactivo (CA-2.11.11)` |
| CA-2.11.12 (F) | REQ-72-02 | `ProfessionalRoleControllerTest.listAll_shouldReturnEmptyArray_whenNoActiveRoles` | — (lo valida `cameia-web`) |
| CA-2.11.1 (lista) | REQ-72-03 | `ProfessionalProfileTest.updateTargetRole_shouldKeepPosition_whenRoleIsReplaced`; `ProfessionalProfileRepositoryAdapterIT.findById_shouldReturnTargetRolesInInsertionOrder` | `OK GET /profiles/{id} — el rol sustituido conserva su lugar (CA-2.11.1)` |
| Lo que muestra el perfil | REQ-72-04 | `ProfileControllerTest.getProfile_shouldShowTargetRoleFields_whenProfileHasRoles` | la misma |

## 4. Requisitos (EARS)

- **REQ-72-01 (CA-2.11.11).** `GET /api/v1/profiles/professional-roles?lang=es|en` debe devolver **200** con solo las filas `estado = 'ACTIVE'`, en el
  orden actual (categoría y nombre del idioma pedido). Un rol `INACTIVE` no aparece. El cuerpo no expone el estado.
- **REQ-72-02 (CA-2.11.12).** Si no hay roles activos, la respuesta es **200** con `[]` (nunca 404).
- **REQ-72-03 (CA-2.11.1, Observaciones).** Los Roles Objetivo se devuelven en el orden en que se agregaron (`creado_en`, y `id` para desempatar).
  Sustituir un rol conserva su posición: en la respuesta del `PATCH` y en todo `GET` posterior. Eliminar uno no cambia el orden de los demás.
- **REQ-72-04.** Cada elemento de `targetRoles` trae `id`, `professionalRoleId`, `roleTitle` (nombre en español del catálogo al agregarlo o
  sustituirlo) y `provenance`. Un rol cuyo catálogo pasó a `INACTIVE` se sigue mostrando (REQ-70-12).

## 5. Validación, base de datos, casos borde y errores

- **Entrada:** `lang` (`es` por defecto, `en`; otro → 422 `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE`, existente y probado). Sin cambios.
- **Base de datos:** sin migración; la consulta filtra por `estado` (índice `ix_rol_profesional_estado` de CM-70). Las consultas siguen siendo JPQL.
- **Errores:** sin códigos nuevos. Rutas al 500: ninguna nueva.

| Grupo | Caso | Resultado | Prueba |
|---|---|---|---|
| Colecciones | 45 activos | 45 elementos | IT (conteo = `SELECT count(*) … WHERE estado='ACTIVE'`) |
| Colecciones | 44 activos y 1 inactivo (`a0000007-0000-0000-0000-000000000006`) | 44, sin ese id, en `es` y `en` | REQ-72-01 |
| Colecciones | 0 activos | `[]` | REQ-72-02 (controlador con el servicio simulado) |
| Orden | 3 roles A, B, C; sustituir B por D | A, D, C en el `PATCH` y en el `GET` | REQ-72-03 |
| Orden | 3 roles; eliminar A | B, C | `ProfessionalProfileTest.removeTargetRole_shouldKeepOrderOfOthers_whenOneIsRemoved` |
| Enumerados | `lang=EN`, `lang=`, `lang=fr` | 422 (existente: solo `es` y `en` en minúsculas) | `ProfessionalRoleControllerTest` (existente) |
| Propiedad, concurrencia, fechas, texto | no aplican: lectura pública del catálogo y orden de una lista ya protegida por el bloqueo del perfil | — | justificación |
| Dependencia lenta | base lenta en el catálogo | sin tiempo de espera propio; DES-02 se mide con Newman | Newman `responseTime < 2000` |

## 6. Seguridad

ASVS 15.3.1: el catálogo sigue sin exponer el estado ni columnas internas (`ProfessionalRoleResponse` = `id`, `nombre`, `categoria`). ASVS 1.2.4:
JPQL sin concatenar. OWASP API3 (sin campos de más), API9 (ruta ya documentada, única sin identidad). Las demás filas no aplican: no hay identidad,
cuerpo, integración ni URL de entrada.

## 7. Atributos de calidad

| Atributo | Métrica | Cómo | Prueba |
|---|---|---|---|
| AC-0006 | INT | Frontend recibe solo opciones elegibles y una lista estable | Newman y `ProfessionalRoleRepositoryAdapterIT` |
| AC-0002 | DES-02 | filtro con índice; una consulta | Newman `responseTime < 2000` |
| AC-0003 | SEG-01 | 15.3.1 | `ProfessionalRoleControllerTest` (campos exactos) |
| MAN-01 | ≥ 90 % | `ProfessionalRoleRepositoryAdapter`, `ProfessionalProfile`, `ProfileResponse` | JaCoCo |

## 8. Decisiones

| # | Decisión | Porqué | Alternativa descartada |
|---|---|---|---|
| D72-1 | Filtrar en la consulta (`where r.estado = 'ACTIVE'`) y no en memoria | El índice de CM-70 la sirve; no se cargan filas que no se muestran | Filtrar en `ProfessionalRoleAppService` |
| D72-2 | Orden por `creado_en` y `id` con `@OrderBy` en la colección; sustituir reemplaza en el mismo índice | El backlog prohíbe reordenar y la sustitución conserva el Rol Objetivo; `creado_en` ya existe y no se actualiza (`updatable = false`) | Columna de prioridad (el backlog dice «sin prioridad»); ordenar por nombre (cambiaría el orden al sustituir) |
| D72-3 | El catálogo no recibe el perfil para excluir los que ya tiene | El Técnico de CA-2.11.11 solo pide `ACTIVE`; la exclusión es de la interfaz | Parámetro `excludeProfileId` (exigiría identidad en una ruta pública) |

## 9. Fuera de alcance

Nombre del rol en inglés dentro del perfil (`roleTitle` guarda el español del catálogo; ningún CA lo pide); orden de experiencia, formación y
habilidades (CM-274, CM-66).

## 10. Línea base, lista de verificación y dictamen

**Línea base:** `clean verify` en la punta de CM-71 antes de la primera tarjeta.

| # | Comprobación | Comando o petición | Esperado |
|---|---|---|---|
| V1 | CA-2.11.11 | Newman `GET /professional-roles` con el rol inactivo preparado | 200; el id `a0000007-0000-0000-0000-000000000006` no aparece; los demás sí |
| V2 | CA-2.11.11 en inglés | Newman `?lang=en` | igual |
| V3 | CA-2.11.1 (orden) | Newman `GET /profiles/{profile_c}` tras el `PATCH` de CM-70 | `targetRoles[0].id` = `role_c` |
| V4 | Campos del perfil | `ProfileControllerTest.getProfile_shouldShowTargetRoleFields_whenProfileHasRoles` | los cuatro campos |
| V5 | Adversarial | `GET /professional-roles?lang=es&estado=INACTIVE` y `?lang=es'--` | 200 solo activos; 422 `PROFESSIONAL_ROLE_LANGUAGE_INVALID_VALUE` |
| V6 | R10 | JaCoCo | `ProfessionalRoleRepositoryAdapter` y `ProfessionalProfile` ≥ 90 % líneas y ramas |
| V7 | Arquitectura y catálogo | `ArquitecturaTest`, `ErrorCatalogTest`, `ErrorCodeDocumentationTest` | verde |
| V8 | R1/R8 y tamaño | `git diff` sin `CM-`; `--shortstat` ≤ 800 | |

**Regla → evidencia prevista:** R1/R8 → V8; R2 → §5 (`lang`, existente); R3 → sin rutas nuevas al 500; R4 → no aplica (usa el índice de CM-70);
R5 → §5; R6 → §6 y V5; R7 → D72-1 y D72-2; R9 → tarjetas; R10 → V6; R11 → T-72.3; R12 → §3; R13 → §7; R14 → sin push.

**Dictamen: `LISTA PARA EJECUTAR`.** Contraste hecho con `ProfessionalRoleJpaRepository:12-17`, `ProfessionalRoleRepositoryAdapter`,
`ProfessionalProfile.updateTargetRole:130-144`, `ProfessionalProfileEntity:70-71` y `ProfileResponse.TargetRoleItem:56-59`.
