# Tasks — CM-23: Catálogo de roles profesionales

- **HU asociada:** CM-23
- **Fecha:** 11/09/2026
- **Estado:** completado

## Bloque 1 — Dominio

- [x] Crear `ProfessionalRole.java` (record: id UUID, nombre String, categoria String)
- [x] Crear `ProfessionalRoleNotFoundException.java`
- [x] `TargetRole.java`: reemplazar `title (String)` por `professionalRoleId (UUID)` + `roleTitle (String)` (denormalizado); actualizar `isSameRoleAs()` para comparar por `professionalRoleId`
- [x] `ProfessionalProfile.java`: actualizar `addTargetRole()` y `updateTargetRole()` para usar `professionalRoleId`
- [x] Crear `ProfessionalRoleRepository.java` (port: `findAll()`, `findById(UUID)`)

## Bloque 2 — Infraestructura

- [x] Crear `ProfessionalRoleEntity.java` — tabla `rol_profesional`
- [x] Crear `ProfessionalRoleJpaRepository.java`
- [x] Crear `ProfessionalRoleRepositoryAdapter.java`
- [x] `TargetRoleEntity.java`: agregar `rolProfesionalId (UUID)` (columna `rol_profesional_id`)
- [x] `ProfessionalProfileRepositoryAdapter.java`: actualizar mappers de `TargetRole` para `professionalRoleId` y `roleTitle`

## Bloque 3 — Aplicación

- [x] Crear `ProfessionalRoleAppService.java` con `getAllRoles()`
- [x] `ProfileAppService.java`: en `addTargetRole()` y `updateTargetRole()` validar que `professionalRoleId` existe en catálogo; obtener `roleTitle` del catálogo para denormalizar

## Bloque 4 — Presentación

- [x] Crear `ProfessionalRoleResponse.java` (record: id, nombre, categoria)
- [x] Crear `ProfessionalRoleController.java` con `GET /api/v1/professional-roles`
- [x] `AddTargetRoleRequest.java`: reemplazar `title` por `professionalRoleId (UUID)`
- [x] `UpdateTargetRoleRequest.java`: reemplazar `title` por `professionalRoleId (UUID)`
- [x] `ProfileResponse.TargetRoleItem`: incluir `professionalRoleId` y `roleTitle` (quitar `title` libre)

## Bloque 5 — Persistencia

- [x] Crear `V3__catalogo_roles_profesionales.sql`:
  - CREATE TABLE `rol_profesional`
  - INSERT ~45 roles semilla
  - ALTER TABLE `rol_objetivo` ADD COLUMN `rol_profesional_id UUID`
