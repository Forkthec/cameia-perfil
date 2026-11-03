# Tasks — CM-21: Eliminar seniority del contrato MVP

- **HU asociada:** CM-21
- **Fecha:** 11/09/2026
- **Estado:** completado

## Bloque 1 — Dominio

- [x] `TargetRole.java`: quitar campo `seniority`, quitar del constructor, actualizar `isSameRoleAs()` para comparar solo por `title`
- [x] `WorkExperience.java`: quitar campo `seniority`, quitar del constructor y del `requireNonNull`
- [x] `ProfessionalProfile.java`: quitar parámetro `seniority` de `updateTargetRole()`

## Bloque 2 — Aplicación

- [x] `AddTargetRoleCommand.java`: quitar `seniority`
- [x] `UpdateTargetRoleCommand.java`: quitar `seniority`
- [x] `AddWorkExperienceCommand.java`: quitar `seniority`
- [x] `ProfileAppService.java`: quitar todas las referencias a `Seniority.valueOf(...)`

## Bloque 3 — Presentación

- [x] `AddTargetRoleRequest.java`: quitar `seniority`
- [x] `UpdateTargetRoleRequest.java`: quitar `seniority`
- [x] `AddWorkExperienceRequest.java`: quitar `seniority`
- [x] `ProfileResponse.java`: quitar `seniority` de `TargetRoleItem` y de `WorkExperienceItem`

## Bloque 4 — Infraestructura

- [x] `TargetRoleEntity.java`: quitar campo `seniority`
- [x] `WorkExperienceEntity.java`: quitar campo `seniority`
- [x] `ProfessionalProfileRepositoryAdapter.java`: quitar `setSeniority` en mappers y `getSeniority` en reconstitución

## Bloque 5 — Persistencia

- [x] Crear `V2__eliminar_seniority.sql` con DROP COLUMN en ambas tablas

## Bloque 6 — Enum

- [x] `Seniority.java`: agregar comentario indicando que está sin uso en MVP desde CM-21
