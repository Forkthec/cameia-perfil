# Plan — CM-21: Eliminar seniority del contrato MVP

## Capas afectadas

| Capa | Archivo | Cambio |
|---|---|---|
| Dominio | `TargetRole.java` | Quitar campo `seniority`; `isSameRoleAs()` compara solo por `title` |
| Dominio | `WorkExperience.java` | Quitar campo `seniority` |
| Dominio | `ProfessionalProfile.java` | Quitar parámetro `seniority` de `updateTargetRole()` |
| Aplicación | `AddTargetRoleCommand.java` | Quitar `seniority` |
| Aplicación | `UpdateTargetRoleCommand.java` | Quitar `seniority` |
| Aplicación | `AddWorkExperienceCommand.java` | Quitar `seniority` |
| Aplicación | `ProfileAppService.java` | Quitar referencias a `Seniority.valueOf(...)` |
| Presentación | `AddTargetRoleRequest.java` | Quitar `seniority` |
| Presentación | `UpdateTargetRoleRequest.java` | Quitar `seniority` |
| Presentación | `AddWorkExperienceRequest.java` | Quitar `seniority` |
| Presentación | `ProfileResponse.java` | Quitar `seniority` de `TargetRoleItem` y `WorkExperienceItem` |
| Infraestructura | `TargetRoleEntity.java` | Quitar campo `seniority` |
| Infraestructura | `WorkExperienceEntity.java` | Quitar campo `seniority` |
| Infraestructura | `ProfessionalProfileRepositoryAdapter.java` | Quitar `setSeniority` y lecturas de `getSeniority` |
| Persistencia | `V2__eliminar_seniority.sql` | `ALTER TABLE rol_objetivo DROP COLUMN seniority` + `ALTER TABLE experiencia_laboral DROP COLUMN seniority` |

## Decisiones

- El enum `Seniority.java` **no se elimina** del código fuente para no romper compilaciones de otras ramas en vuelo. Se marca con un comentario `// sin uso en MVP desde CM-21`.
- `WorkExperienceEntity.seniority` era nullable en la BD (`nullable` omitido en `@Column`). El DROP COLUMN es seguro.
- `TargetRoleEntity.seniority` era `nullable = false`. El DROP COLUMN requiere que no haya constraint NOT NULL bloqueante — en PostgreSQL `DROP COLUMN` elimina la columna con sus constraints.
