# Spec — CM-23: Catálogo de roles profesionales

- **Fecha:** 11/09/2026
- **Fuente:** decisión BE-05 del documento `02_cambios-backend.md` (Producto) — **ruta crítica**
- **Prioridad:** P0 — bloquea Sprint 1

## Contexto

Hoy `TargetRole.title` es texto libre. Producto requiere que el candidato seleccione el rol de un catálogo cerrado de roles profesionales para garantizar consistencia entre perfiles y permitir búsqueda/matching en el futuro.

El catálogo es una lista de ~45 roles agrupados por categoría (ver datos semilla en el plan). El backend rechaza referencias a roles que no estén en el catálogo activo.

## Requisitos (notación EARS)

**REQ-1 — Behavior**
Cuando el sistema recibe `GET /api/v1/professional-roles`, el sistema retorna 200 con la lista de roles del catálogo activos, ordenados por categoría y nombre.

**REQ-2 — Behavior**
Cuando el sistema recibe `POST /api/v1/profiles/{id}/target-roles` con un `professionalRoleId` válido (existe en el catálogo activo), el sistema agrega el rol objetivo y retorna 201.

**REQ-3 — Unwanted behavior**
Si `professionalRoleId` no existe en el catálogo activo, el sistema retorna 422 con `ProblemDetail.title = "Rol profesional no encontrado en el catálogo"`.

**REQ-4 — Behavior**
Cuando el sistema recibe `PATCH /api/v1/profiles/{id}/target-roles/{roleId}` con un `professionalRoleId` válido, el sistema actualiza la referencia al catálogo y retorna 200.

**REQ-5 — Constraint (detección de duplicados)**
El sistema detecta duplicados de `TargetRole` por `professionalRoleId` (no por título). No pueden existir dos roles con el mismo `professionalRoleId` en el mismo perfil.

**REQ-6 — Constraint (nombre denormalizado)**
El sistema almacena el nombre del rol (`roleTitle`) junto con el `professionalRoleId` en la tabla `rol_objetivo` para evitar JOINs al leer el perfil.
