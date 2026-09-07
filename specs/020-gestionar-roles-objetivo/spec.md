# Spec — CM-20: Gestionar roles objetivo (HU-2.11)

> **Estado:** RETROACTIVO — implementado antes de adoptar SDD (07/09/2026).
> Spec reconstruido desde el código y los tests en develop.

---

## Contexto

Un candidato declara los roles a los que aspira (ej. "Backend Developer SENIOR"). El perfil puede tener entre 1 y 5 roles objetivo. No pueden existir dos roles con el mismo título (duplicado). El último rol no puede eliminarse porque un perfil sin roles no puede considerarse válido.

---

## Requisitos (notación EARS)

**REQ-1** — Behavior
Cuando el sistema recibe `POST /api/v1/profiles/{id}/target-roles` con `title`, `seniority` y `provenance`, el sistema agrega el rol objetivo a la colección y retorna 201 con el perfil actualizado.

**REQ-2** — Unwanted behavior
Si ya existe un rol con el mismo `title`, el sistema rechaza la solicitud con 409 y `ProblemDetail.title = "Rol objetivo duplicado"`.

**REQ-3** — Unwanted behavior
Si la colección ya tiene 5 roles objetivo, el sistema rechaza la solicitud con 422 y `ProblemDetail.title = "Máximo de roles objetivo alcanzado"`.

**REQ-4** — Behavior
Cuando el sistema recibe `PATCH /api/v1/profiles/{id}/target-roles/{roleId}` con `title` y/o `seniority`, el sistema actualiza el rol y retorna 200. Los campos `null` no modifican el valor existente.

**REQ-5** — Unwanted behavior
Si `{roleId}` no corresponde a ningún rol del perfil, el sistema retorna 404.

**REQ-6** — Behavior
Cuando el sistema recibe `DELETE /api/v1/profiles/{id}/target-roles/{roleId}` y el perfil tiene más de un rol, el sistema elimina el rol y retorna 200.

**REQ-7** — Unwanted behavior
Si el perfil tiene exactamente un rol objetivo y se intenta eliminar, el sistema rechaza la operación con 422 y `ProblemDetail.title = "No se puede eliminar el único rol objetivo"`.

**REQ-8** — State
Mientras el perfil tenga al menos un rol objetivo, `isComplete()` puede ser `true` si también tiene nombre y resumen.

---

## Campos — `AddTargetRoleRequest`

| Campo | Tipo | Valores |
|---|---|---|
| `title` | `String` | Nombre del rol (ej. "Backend Developer") |
| `seniority` | `String` | `JUNIOR` / `SEMI_SENIOR` / `SENIOR` / `LEAD` |
| `provenance` | `String` | `MANUAL` / `AI` |

## Campos — `UpdateTargetRoleRequest`

| Campo | Tipo | Descripción |
|---|---|---|
| `title` | `String` | Nuevo título; `null` = no cambiar |
| `seniority` | `String` | Nueva seniority; `null` = no cambiar |

---

## Invariantes del dominio

```
targetRoles.size() ∈ [1, 5]         -- 0 roles solo al crear; no puede quedar 0 tras delete
targetRoles.title  ∈ UNIQUE          -- sin duplicados por título
```

- `addTargetRole` lanza `MaxTargetRolesExceededException` si `size >= 5`.
- `addTargetRole` lanza `DuplicateTargetRoleException` si ya existe rol con mismo título.
- `removeTargetRole` lanza `LastTargetRoleException` si `size == 1`.
- `updateTargetRole` preserva el `provenance` original del rol al actualizarlo.

---

## Endpoints implementados

| Método | Ruta | Código OK |
|---|---|---|
| POST | `/api/v1/profiles/{id}/target-roles` | 201 |
| PATCH | `/api/v1/profiles/{id}/target-roles/{roleId}` | 200 |
| DELETE | `/api/v1/profiles/{id}/target-roles/{roleId}` | 200 |

---

## Pruebas de referencia

- `ProfileControllerTest#postTargetRoles_returns201`
- `ProfileControllerTest#postTargetRoles_returns409WhenDuplicate`
- `ProfileControllerTest#deleteTargetRole_returns422WhenLastRole`
- `ProfessionalProfileTest` — addTargetRole duplicado, máximo excedido, eliminar último
- Postman: carpeta **CM-20** en `docs/CAMEIA_Perfil_Sprint1.postman_collection.json`
